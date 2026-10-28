package ws.crandell.fafo.auth

/**
 * The unlock trail recorded while armed (Bill 2026-10-28, device test: "I hit armed and it's not actually
 * armed because you can just touch it to unlock it"). Times are `SystemClock.elapsedRealtime()` and
 * [bootId] is the boot count, so changing the wall clock or rebooting cannot fake a recent unlock.
 *
 * @param armedAt when the phone entered ARMED (after the ARMING countdown)
 * @param lastScreenOffAt the last ACTION_SCREEN_OFF after [armedAt]
 * @param lastUserPresentAt the last ACTION_USER_PRESENT (keyguard dismissed) after [armedAt]
 */
data class UnlockTrail(
    val armedAt: Long? = null,
    val lastScreenOffAt: Long? = null,
    val lastUserPresentAt: Long? = null,
    val bootId: Long = 0,
)

/**
 * Disarm skips the prompt only when the phone was locked (screen off) AFTER it was armed, then unlocked
 * after that, the unlock is inside the grace window, and there was no reboot since. Everything else asks.
 * Arming keeps the plain grace window ([ArmAuthenticator.authorize]).
 *
 * This is necessary, not sufficient: [ArmAuthenticator.authorizeDisarm] also needs the grace-window
 * Keystore key to accept, which proves the unlock used the real credential or a strong biometric (Smart
 * Lock and Extend Unlock dismiss the keyguard without an auth token).
 */
object DisarmPolicy {
    fun canSkipPrompt(t: UnlockTrail, nowBootId: Long, now: Long, graceS: Int): Boolean {
        if (graceS <= 0 || t.bootId != nowBootId) return false
        val armed = t.armedAt ?: return false
        val off = t.lastScreenOffAt ?: return false
        val present = t.lastUserPresentAt ?: return false
        if (off < armed || present <= off) return false
        val age = now - present
        return age in 0..graceS * 1000L
    }
}
