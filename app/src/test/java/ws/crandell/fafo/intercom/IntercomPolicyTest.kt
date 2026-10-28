package ws.crandell.fafo.intercom

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.Member
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.net.Message

class IntercomPolicyTest {
    private val home = Circle("home", "Home", setOf(Permission.INTERCOM, Permission.ALERTS), listOf(Member("reg", "Register", setOf(Permission.INTERCOM))))
    private val work = Circle("work", "Work", setOf(Permission.RESET), listOf(Member("reg", "Register", setOf(Permission.RESET)), Member("till", "Till", setOf(Permission.RESET))))
    private val p = IntercomPolicy { listOf(home, work) }

    @Test fun roomModeOnlyFromLocalUi() {
        for (src in RoomModeSource.entries - RoomModeSource.LOCAL_UI) {
            assertThat(p.startRoomMode(setOf("home"), src, true)).isEqualTo(RoomDecision.REFUSED_NOT_LOCAL)
            assertThat(p.roomMode).isFalse()
        }
        assertThat(p.startRoomMode(setOf("home"), RoomModeSource.LOCAL_UI, true)).isEqualTo(RoomDecision.STARTED)
    }

    @Test fun noMessageTypeStartsRoomMode() {
        // The policy has no message entry point that starts room mode; every message only ever asks to listen.
        val msgs = listOf(
            Message.IntercomListen("home", 1, 5000, 3), Message.PttStart("home", 2, 1, ByteArray(0), 5000),
            Message.Hello("x", null), Message.CircleInvite("home", "Home", 15, "x", Long.MAX_VALUE), Message.Ping,
        )
        msgs.forEach { m ->
            if (m is Message.IntercomListen) assertThat(p.addListener("reg", m.circleId)).isFalse()
            assertThat(p.roomMode).isFalse()
            assertThat(p.micOpen).isFalse()
        }
    }

    @Test fun refusesWhileIncidentOpenOrNotificationsBlocked() {
        p.onIncident(true)
        assertThat(p.startRoomMode(setOf("home"), RoomModeSource.LOCAL_UI, true)).isEqualTo(RoomDecision.REFUSED_INCIDENT)
        p.onIncident(false)
        assertThat(p.startRoomMode(setOf("home"), RoomModeSource.LOCAL_UI, false)).isEqualTo(RoomDecision.REFUSED_NOTIFICATIONS)
        assertThat(p.startRoomMode(setOf("work"), RoomModeSource.LOCAL_UI, true)).isEqualTo(RoomDecision.REFUSED_NO_INTERCOM_CIRCLE)
    }

    @Test fun listenRules() {
        assertThat(p.mayListen("reg", "home")).isEqualTo(ListenDecision.ROOM_OFF)
        p.startRoomMode(setOf("home"), RoomModeSource.LOCAL_UI, true)
        assertThat(p.mayListen("reg", "home")).isEqualTo(ListenDecision.OK)
        assertThat(p.mayListen("reg", "work")).isEqualTo(ListenDecision.CIRCLE_NOT_CHOSEN)
        assertThat(p.mayListen("stranger", "home")).isEqualTo(ListenDecision.NOT_PERMITTED)
        assertThat(p.mayListen("till", "home")).isEqualTo(ListenDecision.NOT_PERMITTED)
    }

    @Test fun circleWithoutIntercomCannotListen() {
        val q = IntercomPolicy { listOf(home.copy(members = listOf(Member("reg", "Register", setOf(Permission.ALERTS))))) }
        q.startRoomMode(setOf("home"), RoomModeSource.LOCAL_UI, true)
        assertThat(q.mayListen("reg", "home")).isEqualTo(ListenDecision.NOT_PERMITTED)
    }

    @Test fun micFollowsListenerCount() {
        p.startRoomMode(setOf("home"), RoomModeSource.LOCAL_UI, true)
        assertThat(p.micOpen).isFalse()
        assertThat(p.addListener("reg", "home")).isTrue()
        assertThat(p.micOpen).isTrue()
        p.removeListener("reg")
        assertThat(p.micOpen).isFalse()
        assertThat(p.roomMode).isTrue()
    }

    @Test fun alarmEndsRoomModeDropsListenersAndClosesMic() {
        p.startRoomMode(setOf("home"), RoomModeSource.LOCAL_UI, true)
        p.addListener("reg", "home")
        p.setLocalTalking(true)
        val dropped = p.onIncident(true)
        assertThat(dropped).containsExactly("reg")
        assertThat(p.roomMode).isFalse()
        assertThat(p.micOpen).isFalse()
        assertThat(p.listeners).isEmpty()
        assertThat(p.addListener("reg", "home")).isFalse()
        assertThat(p.mayReceivePtt("reg", "home")).isFalse()
        p.setLocalTalking(true)
        assertThat(p.micOpen).isFalse()
    }

    @Test fun pttNeedsIntercomInSendersCircle() {
        assertThat(p.mayReceivePtt("reg", "home")).isTrue()
        assertThat(p.mayReceivePtt("reg", "work")).isFalse()
        assertThat(p.mayReceivePtt("till", "home")).isFalse()
    }

    @Test fun floorControlBusyOnCollision() {
        val f = FloorControl()
        assertThat(f.onStart("a", 1, localTalking = false)).isTrue()
        assertThat(f.onStart("b", 2, localTalking = false)).isFalse() // later talker dropped
        f.onStop("a", 1)
        assertThat(f.onStart("b", 3, localTalking = false)).isTrue()
        assertThat(FloorControl().onStart("a", 1, localTalking = true)).isFalse()
        f.onDisconnected("b")
        assertThat(f.talker).isNull()
    }
}
