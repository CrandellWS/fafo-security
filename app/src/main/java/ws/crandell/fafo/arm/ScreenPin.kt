package ws.crandell.fafo.arm

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import ws.crandell.fafo.core.AlarmState

/**
 * Pin app while armed (round 5, spec F14). Android screen pinning (`startLockTask` without device-owner
 * rights): Android asks "Pin app?" itself, and the person at the phone can always leave with Android's own
 * unpin gesture. FAFO pins only its status screen ([AlarmActivity]), only right after the owner's Arm tap,
 * and unpins on disarm. Pure rules here, so they are tested on the JVM.
 */
object PinPolicy {
    /** The owner's Arm tap asks for the pinned status screen only with the setting on. */
    fun requestOnArm(settingOn: Boolean): Boolean = settingOn

    /**
     * Pin only for the owner's own Arm tap ([requestedByArm]: the status screen was opened by Arm with the
     * setting on, never by boot, a notification or the alarm), while ARMING or ARMED, and not twice.
     */
    fun shouldPin(requestedByArm: Boolean, state: AlarmState, pinned: Boolean): Boolean =
        requestedByArm && !pinned && (state is AlarmState.Arming || state is AlarmState.Armed)

    /** A pending pin request is dropped once it can no longer apply (an alarm started first). */
    fun dropRequest(state: AlarmState): Boolean =
        state is AlarmState.PreAlarm || state is AlarmState.Triggered || state is AlarmState.IncidentPersisted

    /** Unpin as soon as FAFO is disarmed (owner Disarm, remote Disarm, cancelled arming). */
    fun shouldUnpin(pinned: Boolean, state: AlarmState): Boolean = pinned && state == AlarmState.Disarmed

    /**
     * The status screen normally closes when an alarm ends. While pinned it stays as long as FAFO is still
     * armed (a remote Reset), because closing it would end the pin the owner asked for.
     */
    fun closeWhenAlarmEnds(pinned: Boolean, state: AlarmState): Boolean = !pinned || state == AlarmState.Disarmed
}

/** Screen pinning, replaceable in tests (Robolectric has no lock task mode). */
interface ScreenPin {
    fun isPinned(context: Context): Boolean
    /** Asks Android to pin [activity]'s task; Android shows its own confirmation. Never throws. */
    fun pin(activity: Activity)
    /** Ends pinning. With Android's "Ask for PIN before unpinning" on, Android locks the phone. Never throws. */
    fun unpin(activity: Activity)
}

object AndroidScreenPin : ScreenPin {
    override fun isPinned(context: Context): Boolean = runCatching {
        context.getSystemService(ActivityManager::class.java)?.lockTaskModeState?.let { it != ActivityManager.LOCK_TASK_MODE_NONE }
    }.getOrNull() ?: false

    override fun pin(activity: Activity) {
        runCatching { activity.startLockTask() }.onFailure { Log.w("ScreenPin", "startLockTask refused", it) }
    }

    override fun unpin(activity: Activity) {
        runCatching { activity.stopLockTask() }.onFailure { Log.w("ScreenPin", "stopLockTask refused", it) }
    }
}
