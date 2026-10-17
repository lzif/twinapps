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

    companion object {
        private const val PREFS_NAME = "twinapps_provisioning"
        private const val KEY_STATE = "provision.state"
        private const val KEY_TYPE = "provision.type"

        const val STATE_NONE = 0
        const val STATE_STARTED = 1
        const val STATE_DONE = 9
    }
}
