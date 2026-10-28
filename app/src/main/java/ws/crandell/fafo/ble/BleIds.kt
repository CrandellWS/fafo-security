package ws.crandell.fafo.ble

import java.util.UUID

/** The FAFO GATT service (F6) and its characteristics. */
object BleIds {
    val SERVICE: UUID = UUID.fromString("7a4e0001-0f4f-4b5a-9a1e-3c5ec0de0f6a")

    /** Pairing: initiator writes here. */
    val PAIR_RX: UUID = UUID.fromString("7a4e0002-0f4f-4b5a-9a1e-3c5ec0de0f6a")

    /** Pairing: responder notifies here. */
    val PAIR_TX: UUID = UUID.fromString("7a4e0003-0f4f-4b5a-9a1e-3c5ec0de0f6a")

    /** Sessions (F9 over Bluetooth, M11): client writes here. */
    val SESSION_RX: UUID = UUID.fromString("7a4e0004-0f4f-4b5a-9a1e-3c5ec0de0f6a")

    /** Sessions: server notifies here. */
    val SESSION_TX: UUID = UUID.fromString("7a4e0005-0f4f-4b5a-9a1e-3c5ec0de0f6a")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val WANTED_MTU = 517
}
