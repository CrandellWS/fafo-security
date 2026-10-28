package ws.crandell.fafo.mail

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.storage.CeStore
import java.io.File

/**
 * Open Wi-Fi fallback (spec F12): when photo email is queued and there is no connection, suggest nearby
 * open networks to Android and send once one is VALIDATED. Only gets a connection; MailDispatcher sends.
 * Suggestions are always removed afterwards; the list is persisted so a crash still cleans up.
 */
object OpenWifiFallback {
    private const val TAG = "OpenWifiFallback"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var waiting: Job? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var suggestedAt = 0L
    @Volatile private var lastScanAt = 0L

    private fun saved(ctx: Context) = File(ctx.filesDir, "open_wifi_suggestions.txt")

    /** Called by MailDispatcher when mail is waiting and no validated network is up. */
    fun onNoNetwork(ctx: Context) {
        if (Build.VERSION.SDK_INT < 33 || waiting?.isActive == true) return
        val app = ctx.applicationContext
        waiting = scope.launch {
            delay(OpenWifiPlanner.NO_NETWORK_MS)
            val enabled = runCatching { CeStore.get(app).read().mailOpenWifi }.getOrDefault(false)
            val cm = app.getSystemService(ConnectivityManager::class.java) ?: return@launch
            val trigger = OpenWifiPlanner.shouldTrigger(
                pendingMail = MailDispatcher.outbox(app).pending().isNotEmpty(),
                enabled = enabled,
                sdk = Build.VERSION.SDK_INT,
                noValidatedNetworkForMs = if (hasValidated(cm)) 0 else OpenWifiPlanner.NO_NETWORK_MS,
                cellularInternet = hasCellular(cm),
            )
            if (trigger) suggest(app, cm)
        }
    }

    @Suppress("DEPRECATION")
    private fun hasValidated(cm: ConnectivityManager) = cm.allNetworks.any { n ->
        cm.getNetworkCapabilities(n)?.let(MailDispatcher::usable) == true
    }

    @Suppress("DEPRECATION")
    private fun hasCellular(cm: ConnectivityManager) = cm.allNetworks.any { n ->
        cm.getNetworkCapabilities(n)?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true
    }

    @SuppressLint("MissingPermission")
    private fun suggest(ctx: Context, cm: ConnectivityManager) {
        val wm = ctx.getSystemService(WifiManager::class.java) ?: return
        val now = System.currentTimeMillis()
        if (now - lastScanAt >= OpenWifiPlanner.SCAN_EVERY_MS) {
            lastScanAt = now
            @Suppress("DEPRECATION") runCatching { wm.startScan() }
        }
        val seen = runCatching { wm.scanResults }.getOrNull().orEmpty().map {
            @Suppress("DEPRECATION") WifiSeen(it.SSID ?: "", it.capabilities ?: "", it.level)
        }
        val ssids = OpenWifiPlanner.pick(seen)
        if (ssids.isEmpty()) { Log.i(TAG, "no open networks in the scan results"); return }
        val list = ssids.map { WifiNetworkSuggestion.Builder().setSsid(it).setIsAppInteractionRequired(false).setPriority(1).build() }
        val status = runCatching { wm.addNetworkSuggestions(list) }.getOrDefault(-1)
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) { Log.w(TAG, "suggestions refused: $status"); return }
        saved(ctx).writeText(ssids.joinToString("\n"))
        suggestedAt = now
        watch(ctx, cm)
        scope.launch {
            delay(OpenWifiPlanner.LIFETIME_MS)
            cleanup(ctx)
        }
    }

    /** Uses a Wi-Fi network only once Android validated it (no captive portal). */
    private fun watch(ctx: Context, cm: ConnectivityManager) {
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (MailDispatcher.usable(caps)) MailDispatcher.useNetwork(network)
                maybeCleanup(ctx)
            }
            override fun onLost(network: Network) { if (MailDispatcher.preferredNetwork == network) MailDispatcher.useNetwork(null) }
        }
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        runCatching { cm.registerNetworkCallback(req, cb) }.onSuccess { callback = cb }
    }

    /** Removes the suggestions when mail is out, after 30 min, or when FAFO is no longer armed. */
    fun maybeCleanup(ctx: Context) {
        val s = AlarmStatusBus.state.value
        val pending = MailDispatcher.outbox(ctx).pending().isNotEmpty()
        if (OpenWifiPlanner.shouldRemove(pending, suggestedAt, System.currentTimeMillis(), s != AlarmState.Disarmed)) cleanup(ctx)
    }

    fun cleanup(ctx: Context) {
        val app = ctx.applicationContext
        val f = saved(app)
        if (f.isFile) {
            val ssids = f.readLines().filter { it.isNotBlank() }
            val wm = app.getSystemService(WifiManager::class.java)
            if (wm != null && ssids.isNotEmpty()) {
                runCatching { wm.removeNetworkSuggestions(ssids.map { WifiNetworkSuggestion.Builder().setSsid(it).build() }) }
            }
            f.delete()
        }
        callback?.let { cb -> runCatching { app.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
        callback = null
        MailDispatcher.useNetwork(null)
        waiting?.cancel()
        suggestedAt = 0L
    }

    /** Setup step: add and remove a suggestion for a name that is never around, to raise Android's approval prompt. */
    fun requestApproval(ctx: Context) {
        val wm = ctx.getSystemService(WifiManager::class.java) ?: return
        watchApproval(ctx)
        val s = WifiNetworkSuggestion.Builder().setSsid(OpenWifiPlanner.SETUP_SSID).build()
        runCatching { wm.addNetworkSuggestions(listOf(s)) }
        scope.launch { delay(30_000); runCatching { wm.removeNetworkSuggestions(listOf(s)) } }
    }

    private var listener: Any? = null

    /** Android reports the approval only through a listener; the answer is remembered for the Setup step. */
    fun watchApproval(ctx: Context) {
        if (Build.VERSION.SDK_INT < 31 || listener != null) return
        val app = ctx.applicationContext
        val wm = app.getSystemService(WifiManager::class.java) ?: return
        val l = WifiManager.SuggestionUserApprovalStatusListener { status ->
            prefs(app).edit().putInt(KEY_APPROVAL, status).apply()
        }
        runCatching { wm.addSuggestionUserApprovalStatusListener(app.mainExecutor, l) }.onSuccess { listener = l }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("open_wifi", Context.MODE_PRIVATE)
    private const val KEY_APPROVAL = "approval"

    /** Android 13+: did the owner allow FAFO to suggest Wi-Fi networks? */
    fun approved(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return prefs(ctx.applicationContext).getInt(KEY_APPROVAL, -1) == WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER
    }
}
