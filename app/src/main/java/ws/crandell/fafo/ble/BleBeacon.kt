package ws.crandell.fafo.ble

import ws.crandell.fafo.net.BeaconTag

/**
 * AC9.7 Bluetooth beacon: service data under the FAFO service UUID = 1 state byte (1 = alarming,
 * 0 = reachable) + the 8-byte beacon tag in 2-minute slots. No label, no name: strangers see a value that
 * changes every slot. It fits a legacy advertisement: flags (3) + service data AD (2 + 16 + 9) = 30 bytes.
 */
object BleBeacon {
    const val SIZE = 1 + BeaconTag.SIZE

    data class Beacon(val alarming: Boolean, val tag: ByteArray)

    fun encode(alarming: Boolean, key: ByteArray, now: Long): ByteArray =
        byteArrayOf(if (alarming) 1 else 0) + BeaconTag.current(key, now, BeaconTag.BLE_SLOT_MS)

    fun decode(data: ByteArray?): Beacon? {
        if (data == null || data.size != SIZE) return null
        val state = data[0].toInt()
        if (state != 0 && state != 1) return null
        return Beacon(state == 1, data.copyOfRange(1, SIZE))
    }

    /** Which trusted peer sent [data] (current or previous slot), or null for strangers and garbage. */
    fun <T> owner(data: ByteArray?, keys: Map<T, ByteArray>, now: Long): Pair<T, Beacon>? {
        val b = decode(data) ?: return null
        val who = BeaconTag.findOwner(keys, b.tag, now, BeaconTag.BLE_SLOT_MS) ?: return null
        return who to b
    }
}
