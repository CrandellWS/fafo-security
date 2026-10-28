package ws.crandell.fafo.net

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import ws.crandell.fafo.crypto.Crypto

class FrameCryptoTest {
    private val k1 = Crypto.randomBytes(32)
    private val k2 = Crypto.randomBytes(32)
    private val sid = Crypto.randomBytes(8)
    private val a = FrameCrypto(k1, k2, sid)
    private val b = FrameCrypto(k2, k1, sid)

    @Test fun roundTrip() {
        assertThat(String(b.open(a.seal("hi".toByteArray())))).isEqualTo("hi")
        assertThat(String(a.open(b.seal("yo".toByteArray())))).isEqualTo("yo")
    }

    @Test fun rejectsReplayAndReorder() {
        val f1 = a.seal(byteArrayOf(1))
        val f2 = a.seal(byteArrayOf(2))
        val f3 = a.seal(byteArrayOf(3))
        b.open(f1)
        b.open(f3)
        assertThrows(ReplayRejected::class.java) { b.open(f2) } // below the last counter
        assertThrows(ReplayRejected::class.java) { b.open(f3) } // replay
    }

    @Test fun rejectsTamperingAndWrongSession() {
        val f = a.seal(byteArrayOf(9, 9, 9))
        val t = f.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertThrows(Exception::class.java) { b.open(t) }
        val other = FrameCrypto(k2, k1, Crypto.randomBytes(8))
        assertThrows(Exception::class.java) { other.open(a.seal(byteArrayOf(1))) }
    }

    @Test fun messagesRoundTripAndUnknownIgnored() {
        val msgs = listOf(
            Message.Hello("Back room", Crypto.randomBytes(32)),
            Message.CircleInvite("c1", "Work", 10, "Register", 99L),
            Message.CircleAccept("c1", true, "Back room"),
            Message.Alert("c1", "TRIGGERED", "inc", 5L, "Back room", 6),
            Message.CommandChallenge("c1", 1, "inc", ByteArray(0)),
            Message.CommandResult(2, false, "nope"),
            Message.IntercomStop(3), Message.PttStop(4), Message.PttBusy(5), Message.Ping,
        )
        msgs.forEach { assertThat(Message.decode(it.encode()).encode()).isEqualTo(it.encode()) }
        assertThat(Message.decode(byteArrayOf(99))).isEqualTo(Message.Unknown(99))
    }
}
