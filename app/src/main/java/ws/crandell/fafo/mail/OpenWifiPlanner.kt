package ws.crandell.fafo.mail

/** A Wi-Fi scan result, reduced to what the planner needs. */
data class WifiSeen(val ssid: String, val capabilities: String, val rssi: Int)

/**
 * Open Wi-Fi fallback rules (item 7, spec F12). Pure, so every rule is tested on the JVM.
 */
object OpenWifiPlanner {
    const val NO_NETWORK_MS = 60_000L
    const val MAX_SUGGESTIONS = 5
    const val MIN_RSSI = -80
    const val LIFETIME_MS = 30 * 60_000L
    /** Android throttles background scans to about one per 30 min; ask at most that often. */
    const val SCAN_EVERY_MS = 30 * 60_000L
    const val SETUP_SSID = "FAFO-setup-check"

    fun shouldTrigger(
        pendingMail: Boolean,
        enabled: Boolean,
        sdk: Int,
        noValidatedNetworkForMs: Long,
        cellularInternet: Boolean,
    ): Boolean = pendingMail && enabled && sdk >= 33 && noValidatedNetworkForMs >= NO_NETWORK_MS && !cellularInternet

    /**
     * Open networks only: no PSK, EAP, SAE or WEP in the capabilities (OWE "Enhanced Open" is fine, a
     * transition network that also offers SAE/PSK is treated as secured). Hidden names and weak signals are
     * skipped; strongest first, at most five, one per name.
     */
    fun isOpen(capabilities: String): Boolean {
        val c = capabilities.uppercase()
        return listOf("PSK", "EAP", "SAE", "WEP", "802.1X").none { it in c }
    }

    fun pick(seen: List<WifiSeen>): List<String> = seen
        .filter { it.ssid.isNotBlank() && it.ssid != "<unknown ssid>" && isOpen(it.capabilities) && it.rssi > MIN_RSSI }
        .sortedByDescending { it.rssi }
        .map { it.ssid }
        .distinct()
        .take(MAX_SUGGESTIONS)

    /** Suggestions must go once mail is out, after [LIFETIME_MS], or on disarm. */
    fun shouldRemove(pendingMail: Boolean, suggestedAt: Long, now: Long, armedOrAlarming: Boolean): Boolean =
        !pendingMail || now - suggestedAt >= LIFETIME_MS || !armedOrAlarming
}
