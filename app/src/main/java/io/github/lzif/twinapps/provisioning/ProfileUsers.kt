package io.github.lzif.twinapps.provisioning

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

/**
 * Resolves the user handles this app operates on, and toggles quiet mode
 * for the managed profile.
 *
 * Functional behavior is defined by the project spec, section 2 (Profile model).
 */
class ProfileUsers(private val context: Context) {

    data class Handles(
        /** The user this process currently runs on. */
        val current: UserHandle,
        /** The managed profile owned by this app, or null when absent. */
        val managedProfile: UserHandle?
    )

    /**
     * Resolves the handles. Inside the profile (where this app is profile
     * owner) the managed profile is the current user. On the parent side it
     * is the extra profile among this user's profiles (best effort; the exact
     * identification should be verified on device).
     */
    fun resolve(): Handles {
        val um = context.getSystemService(UserManager::class.java)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val current = Process.myUserHandle()
        val managedProfile = if (dpm.isProfileOwnerApp(context.packageName)) {
            current
        } else {
            um.userProfiles.firstOrNull { it != current }
        }
        return Handles(current, managedProfile)
    }

    /** True when the managed profile is currently in quiet mode. */
    fun isQuietModeEnabled(): Boolean {
        val profile = resolve().managedProfile ?: return false
        return try {
            context.getSystemService(UserManager::class.java).isQuietModeEnabled(profile)
        } catch (e: Exception) {
            Log.w(TAG, "isQuietModeEnabled failed", e)
            false
        }
    }

    /**
     * Requests quiet mode on/off for the managed profile. Returns false when
     * there is no profile or the request was rejected.
     */
    fun setQuietModeEnabled(enabled: Boolean): Boolean {
        val profile = resolve().managedProfile ?: return false
        return try {
            context.getSystemService(UserManager::class.java)
                .requestQuietModeEnabled(enabled, profile)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "requestQuietModeEnabled rejected", e)
            false
        }
    }

    companion object {
        private const val TAG = "ProfileUsers"
    }
}
