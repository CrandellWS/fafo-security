package ws.crandell.fafo.net

enum class Transport { WIFI, BLUETOOTH }

/**
 * AC9.3: Wi-Fi when both phones are on the same network (the peer was discovered on this Wi-Fi), else
 * Bluetooth when its beacon was seen recently. Order of candidates to try; empty = unreachable.
 */
object TransportSelector {
    fun order(onWifi: Boolean, seenOnWifi: Boolean, seenOnBluetooth: Boolean, bluetoothOn: Boolean): List<Transport> = buildList {
        if (onWifi && seenOnWifi) add(Transport.WIFI)
        if (bluetoothOn && seenOnBluetooth) add(Transport.BLUETOOTH)
    }
}
