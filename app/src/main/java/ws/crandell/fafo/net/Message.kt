package ws.crandell.fafo.net

import ws.crandell.fafo.crypto.WireReader
import ws.crandell.fafo.crypto.WireWriter

/**
 * AC9.5 session messages. There is deliberately no message that arms, starts room mode, opens a mic or
 * changes circles/permissions on the receiver: CircleInvite only asks the receiving owner, who decides
 * with their own credential. Replies ([CommandResult], [IntercomListenReply], [PttBusy]) only answer a
 * request. Unknown types decode to [Unknown] and are ignored. Max frame 64 KiB.
 */
sealed interface Message {
    val type: Int
    fun body(w: WireWriter)
    fun encode(): ByteArray = WireWriter().u8(type).also { body(it) }.build()

    /** First message of every session: the sender's suggested label and, once, its beacon key (AC9.6). */
    data class Hello(val label: String, val beaconKey: ByteArray?) : Message {
        override val type = T_HELLO
        override fun body(w: WireWriter) { w.str(label).bytes(beaconKey ?: ByteArray(0)) }
    }

    /** AC8.6: [permissions] is what accepting would let the inviter do to the receiving phone. */
    data class CircleInvite(val circleId: String, val name: String, val permissions: Int, val inviterLabel: String, val expiresAt: Long) : Message {
        override val type = T_CIRCLE_INVITE
        override fun body(w: WireWriter) { w.str(circleId).str(name).u32(permissions).str(inviterLabel).u64(expiresAt) }
    }

    data class CircleAccept(val circleId: String, val accepted: Boolean, val label: String) : Message {
        override val type = T_CIRCLE_ACCEPT
        override fun body(w: WireWriter) { w.str(circleId).bool(accepted).str(label) }
    }

    /** AC9.7: [granted] lists what this circle lets the receiver do to the sender (Reset, Disarm bits). */
    data class Alert(val circleId: String, val state: String, val incidentId: String, val startedAt: Long, val label: String, val granted: Int) : Message {
        override val type = T_ALERT
        override fun body(w: WireWriter) { w.str(circleId).str(state).str(incidentId).u64(startedAt).str(label).u32(granted) }
    }

    /** AC9.8: empty [challenge] = a request; the target answers with a fresh 128-bit challenge. */
    data class CommandChallenge(val circleId: String, val command: Int, val incidentId: String, val challenge: ByteArray) : Message {
        override val type = T_COMMAND_CHALLENGE
        override fun body(w: WireWriter) { w.str(circleId).u8(command).str(incidentId).bytes(challenge) }
    }

    data class Reset(val circleId: String, val incidentId: String, val challenge: ByteArray, val signature: ByteArray) : Message {
        override val type = T_RESET
        override fun body(w: WireWriter) { w.str(circleId).str(incidentId).bytes(challenge).bytes(signature) }
    }

    data class Disarm(val circleId: String, val incidentId: String, val challenge: ByteArray, val signature: ByteArray) : Message {
        override val type = T_DISARM
        override fun body(w: WireWriter) { w.str(circleId).str(incidentId).bytes(challenge).bytes(signature) }
    }

    data class CommandResult(val command: Int, val ok: Boolean, val reason: String) : Message {
        override val type = T_COMMAND_RESULT
        override fun body(w: WireWriter) { w.u8(command).bool(ok).str(reason) }
    }

    /** F10: ask to listen to a phone that has room mode on. [udpPort] is where the listener receives audio. */
    data class IntercomListen(val circleId: String, val streamId: Int, val udpPort: Int, val codecs: Int) : Message {
        override val type = T_INTERCOM_LISTEN
        override fun body(w: WireWriter) { w.str(circleId).u32(streamId).u32(udpPort).u32(codecs) }
    }

    /** Room phone's answer: refused, or accepted with codec, its csd (OpusHead etc.) and its UDP port. */
    data class IntercomListenReply(val streamId: Int, val ok: Boolean, val codec: Int, val csd: ByteArray, val udpPort: Int, val reason: String) : Message {
        override val type = T_INTERCOM_LISTEN_REPLY
        override fun body(w: WireWriter) { w.u32(streamId).bool(ok).u8(codec).bytes(csd).u32(udpPort).str(reason) }
    }

    data class IntercomStop(val streamId: Int) : Message {
        override val type = T_INTERCOM_STOP
        override fun body(w: WireWriter) { w.u32(streamId) }
    }

    data class PttStart(val circleId: String, val streamId: Int, val codec: Int, val csd: ByteArray, val udpPort: Int) : Message {
        override val type = T_PTT_START
        override fun body(w: WireWriter) { w.str(circleId).u32(streamId).u8(codec).bytes(csd).u32(udpPort) }
    }

    data class PttStop(val streamId: Int) : Message {
        override val type = T_PTT_STOP
        override fun body(w: WireWriter) { w.u32(streamId) }
    }

    /** Half duplex (AC10.4): the receiver is busy; the later talker is dropped. */
    data class PttBusy(val streamId: Int) : Message {
        override val type = T_PTT_BUSY
        override fun body(w: WireWriter) { w.u32(streamId) }
    }

    data object Ping : Message {
        override val type = T_PING
        override fun body(w: WireWriter) {}
    }

    data class Unknown(override val type: Int) : Message {
        override fun body(w: WireWriter) {}
    }

    companion object {
        const val T_HELLO = 1
        const val T_CIRCLE_INVITE = 2
        const val T_CIRCLE_ACCEPT = 3
        const val T_ALERT = 4
        const val T_COMMAND_CHALLENGE = 5
        const val T_RESET = 6
        const val T_DISARM = 7
        const val T_INTERCOM_LISTEN = 8
        const val T_INTERCOM_STOP = 9
        const val T_PTT_START = 10
        const val T_PTT_STOP = 11
        const val T_PING = 12
        const val T_COMMAND_RESULT = 13
        const val T_INTERCOM_LISTEN_REPLY = 14
        const val T_PTT_BUSY = 15

        fun decode(b: ByteArray): Message = WireReader(b).run {
            when (val t = u8()) {
                T_HELLO -> Hello(str(96), bytes(64).takeIf { it.isNotEmpty() })
                T_CIRCLE_INVITE -> CircleInvite(str(64), str(96), u32(), str(96), u64())
                T_CIRCLE_ACCEPT -> CircleAccept(str(64), bool(), str(96))
                T_ALERT -> Alert(str(64), str(32), str(64), u64(), str(96), u32())
                T_COMMAND_CHALLENGE -> CommandChallenge(str(64), u8(), str(64), bytes(64))
                T_RESET -> Reset(str(64), str(64), bytes(64), bytes(256))
                T_DISARM -> Disarm(str(64), str(64), bytes(64), bytes(256))
                T_COMMAND_RESULT -> CommandResult(u8(), bool(), str(256))
                T_INTERCOM_LISTEN -> IntercomListen(str(64), u32(), u32(), u32())
                T_INTERCOM_LISTEN_REPLY -> IntercomListenReply(u32(), bool(), u8(), bytes(1024), u32(), str(256))
                T_INTERCOM_STOP -> IntercomStop(u32())
                T_PTT_START -> PttStart(str(64), u32(), u8(), bytes(1024), u32())
                T_PTT_STOP -> PttStop(u32())
                T_PTT_BUSY -> PttBusy(u32())
                T_PING -> Ping
                else -> Unknown(t)
            }
        }
    }
}
