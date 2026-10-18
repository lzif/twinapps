package io.github.lzif.twinapps.provisioning

import android.content.Context

/** Provisioning path that created the work profile. */
enum class ProvisionType(val code: Int) {
    MANAGED(0),
    MANUAL(1);

    companion object {
        fun fromCode(code: Int): ProvisionType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Persists the provisioning state machine.
 *
 * States: 0 = none, 1 = started, 9 = done. Each side (parent profile and
 * managed profile) keeps its own copy in its own preferences.
 */
class ProvisioningState(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markStarted(type: ProvisionType) {
        prefs.edit()
            .putInt(KEY_STATE, STATE_STARTED)
            .putInt(KEY_TYPE, type.code)
            .apply()
    }

    fun markDone(type: ProvisionType) {
        prefs.edit()
            .putInt(KEY_STATE, STATE_DONE)
            .putInt(KEY_TYPE, type.code)
            .apply()
    }

    fun isDone(): Boolean = prefs.getInt(KEY_STATE, STATE_NONE) == STATE_DONE

    fun isStarted(): Boolean = prefs.getInt(KEY_STATE, STATE_NONE) == STATE_STARTED

    fun getType(): ProvisionType? =
        if (prefs.contains(KEY_TYPE)) ProvisionType.fromCode(prefs.getInt(KEY_TYPE, -1))
        else null

    fun reset() {
        prefs.edit().clear().apply()
    }

    /** Records that the user asked to destroy the profile (for scoped stale recovery). */
    fun markDestroyRequested() {
        prefs.edit().putLong(KEY_DESTROY_REQ, System.currentTimeMillis()).apply()
    }

    fun clearDestroyRequest() {
        prefs.edit().remove(KEY_DESTROY_REQ).apply()
    }

    fun destroyRequestedAt(): Long = prefs.getLong(KEY_DESTROY_REQ, 0L)

    /** Best-effort record of the managed profile's user id (for destroy targeting). */
    fun setProfileUserId(userId: Int) {
        prefs.edit().putInt(KEY_PROFILE_USER_ID, userId).apply()
    }

    fun getProfileUserId(): Int? =
        if (prefs.contains(KEY_PROFILE_USER_ID)) prefs.getInt(KEY_PROFILE_USER_ID, -1)
        else null

    companion object {
        private const val PREFS_NAME = "twinapps_provisioning"
        private const val KEY_STATE = "provision.state"
        private const val KEY_TYPE = "provision.type"
        private const val KEY_DESTROY_REQ = "provision.destroy_requested_at"
        private const val KEY_PROFILE_USER_ID = "provision.profile_user_id"

        const val STATE_NONE = 0
        const val STATE_STARTED = 1
        const val STATE_DONE = 9
    }
}
