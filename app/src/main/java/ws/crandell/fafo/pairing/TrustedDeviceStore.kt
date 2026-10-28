package ws.crandell.fafo.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

/** A paired peer: public keys and metadata only (CE storage). Private keys never leave Keystore. */
@Serializable
data class TrustedDevice(
    val fingerprint: String,
    val identityPublicB64: String,
    val commandPublicB64: String,
    val name: String,
    val addedAt: Long,
    /** Peer's beacon key (AC9.6), base64, once received in the first session. */
    val beaconKeyB64: String? = null,
) {
    val identityPublic: ByteArray get() = Base64.getDecoder().decode(identityPublicB64)
    val commandPublic: ByteArray get() = Base64.getDecoder().decode(commandPublicB64)
    val beaconKey: ByteArray? get() = beaconKeyB64?.let { Base64.getDecoder().decode(it) }

    companion object {
        fun from(keys: PeerKeys, addedAt: Long) = TrustedDevice(
            fingerprint = keys.fingerprint,
            identityPublicB64 = Base64.getEncoder().encodeToString(keys.identityPublic),
            commandPublicB64 = Base64.getEncoder().encodeToString(keys.commandPublic),
            name = keys.name.take(24).ifBlank { "Device" },
            addedAt = addedAt,
        )
    }
}

/** Trusted-device list in credential-protected storage (`filesDir/trusted.json`). */
class TrustedDeviceStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(TrustedDevice.serializer())
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<TrustedDevice>> = _devices

    private fun load(): List<TrustedDevice> =
        if (file.exists()) runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList()) else emptyList()

    @Synchronized private fun save(list: List<TrustedDevice>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(serializer, list))
        tmp.renameTo(file)
        _devices.value = list
    }

    fun all(): List<TrustedDevice> = _devices.value
    fun get(fingerprint: String): TrustedDevice? = _devices.value.firstOrNull { it.fingerprint == fingerprint }

    @Synchronized fun put(d: TrustedDevice) = save(_devices.value.filterNot { it.fingerprint == d.fingerprint } + d)

    @Synchronized fun remove(fingerprint: String) = save(_devices.value.filterNot { it.fingerprint == fingerprint })

    @Synchronized fun setBeaconKey(fingerprint: String, key: ByteArray) {
        val d = get(fingerprint) ?: return
        put(d.copy(beaconKeyB64 = Base64.getEncoder().encodeToString(key)))
    }
}

sealed interface PairingResult {
    data class Paired(val device: TrustedDevice) : PairingResult
    data object CodeMismatch : PairingResult
    data object PeerDeclined : PairingResult
    data class Refused(val gate: GateResult) : PairingResult
}

/**
 * Last step of pairing (AC6.1, AC6.3, AC6.6): the code matched on both screens, then this owner passes
 * the device credential, then the peer is stored with no powers (circles grant them, M10).
 */
class PairingFinisher(private val gate: CredentialGate, private val store: TrustedDeviceStore, private val now: () -> Long) {
    /** Code check plus this owner's credential. Persists nothing (the peer must confirm too). */
    suspend fun verify(peer: PeerKeys, codeMatched: Boolean): PairingResult {
        if (!codeMatched) return PairingResult.CodeMismatch
        val r = gate.authenticate("Add trusted device", "Confirm with your screen lock to trust ${peer.name}")
        if (r !is GateResult.Success) return PairingResult.Refused(r)
        return PairingResult.Paired(TrustedDevice.from(peer, now()))
    }

    fun store(device: TrustedDevice) = store.put(device)

    /** Code check, credential, then store: for a caller that already knows the peer confirmed. */
    suspend fun finish(peer: PeerKeys, codeMatched: Boolean): PairingResult {
        val r = verify(peer, codeMatched)
        if (r is PairingResult.Paired) store(r.device)
        return r
    }

    /** AC6.4: removing a trusted device also needs the device credential. */
    suspend fun remove(fingerprint: String): Boolean {
        val r = gate.authenticate("Remove trusted device", "Confirm with your screen lock")
        if (r !is GateResult.Success) return false
        store.remove(fingerprint)
        return true
    }
}
