package ws.crandell.fafo.system

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Monotonic time since boot plus a boot identifier, for the disarm unlock trail. Neither can be moved by
 * changing the wall clock, and a reboot changes [bootId]. Replaceable in tests.
 */
object BootClock {
    var now: () -> Long = { SystemClock.elapsedRealtime() }
    var bootId: (Context) -> Long = ::systemBootId

    /** `Settings.Global.BOOT_COUNT`; some OEMs leave it unset, then the boot time rounded to a minute. */
    fun systemBootId(context: Context): Long {
        val count = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
        if (count >= 0) return count.toLong()
        return -((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L)
    }

    fun reset() {
        now = { SystemClock.elapsedRealtime() }
        bootId = ::systemBootId
    }
}
