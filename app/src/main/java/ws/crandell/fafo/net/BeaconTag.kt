package ws.crandell.fafo.net

import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.crypto.Hkdf
import java.nio.ByteBuffer

/**
 * AC9.1 / AC9.7: `tag = first 8 bytes of HMAC-SHA256(beaconKey, timeSlot)`. Peers that hold the key
 * recognise the device; strangers see a value that changes every slot. Receivers accept the current and
 * the previous slot, so a tag made just before a slot edge still matches.
 */
object BeaconTag {
    const val NSD_SLOT_MS = 10 * 60 * 1000L
    const val BLE_SLOT_MS = 2 * 60 * 1000L
    const val SIZE = 8

    fun slot(now: Long, slotMs: Long): Long = now / slotMs

    fun tag(key: ByteArray, slot: Long): ByteArray =
        Hkdf.hmac(key, ByteBuffer.allocate(8).putLong(slot).array()).copyOf(SIZE)

    fun current(key: ByteArray, now: Long, slotMs: Long): ByteArray = tag(key, slot(now, slotMs))

    fun matches(key: ByteArray, tag: ByteArray, now: Long, slotMs: Long): Boolean {
        if (tag.size != SIZE) return false
        val s = slot(now, slotMs)
        return Crypto.constantTimeEquals(tag, tag(key, s)) || Crypto.constantTimeEquals(tag, tag(key, s - 1))
    }

    /** First key (by owner) whose tag matches, or null. */
    fun <T> findOwner(keys: Map<T, ByteArray>, tag: ByteArray, now: Long, slotMs: Long): T? =
        keys.entries.firstOrNull { matches(it.value, tag, now, slotMs) }?.key
}
