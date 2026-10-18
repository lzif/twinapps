package io.github.lzif.twinapps.provisioning

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import io.github.lzif.twinapps.admin.TwinAppsDeviceAdmin

/**
 * Creates and manages the work profile that hosts cloned apps.
 *
 * Covers the managed-provisioning intent flow, manual (adb) provisioning
 * detection, post-provisioning setup inside the profile, provisioning state
 * tracking, and profile destruction.
 *
 * Functional behavior is defined by the project spec, section 1 (Provisioning).
 */
class ProvisioningManager(private val context: Context) {

    private val dpm: DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)
    private val state = ProvisioningState(context)

    /** The device-admin component that becomes profile owner. */
    fun adminComponent(): ComponentName =
        ComponentName(context, TwinAppsDeviceAdmin::class.java)

    /** True when this app is profile owner on the calling user. */
    fun isProfileOwner(): Boolean =
        dpm.isProfileOwnerApp(context.packageName)

    /**
     * True when provisioning completed. Each side (parent profile and managed
     * profile) records its own completion; the parent additionally recovers
     * when a managed profile exists but was never recorded (for example after
     * manual adb provisioning).
     *
     * A DONE state is trusted unconditionally: an earlier version also reset
     * DONE when no managed profile was visible in UserManager, but that
     * signal proved unreliable on some devices and broke the normal
     * post-provisioning UI. Stale states are cleaned up through the destroy
     * flow (scoped to an explicit destroy request) instead.
     */
    fun isProvisioned(): Boolean {
        if (state.isDone()) {
            // DONE is trusted here. Stale-state verification (via Shizuku)
            // lives in checkAndFixStaleState(), called from a background
            // thread by the manual refresh — never on the main thread, and
            // only on verified-absent (not on "couldn't check").
            return true
        }
        if (!isProfileOwner() && !destroyRecentlyRequested() && hasManagedProfile()) {
            Log.i(TAG, "Managed profile exists without recorded state; marking done")
            state.markDone(ProvisionType.MANUAL)
            persistProfileUserId()
            return true
        }
        // Scoped stale recovery: we asked for a destroy and the profile is
        // gone — finish the cleanup the (possibly lost) callback couldn't.
        if (state.destroyRequestedAt() > 0 && !hasManagedProfile()) {
            Log.i(TAG, "Destroy was requested and no profile is visible; resetting state")
            state.reset()
            return false
        }
        return false
    }

    /** Clears the local provisioning record (used after the profile is destroyed). */
    fun resetState() {
        state.reset()
    }

    /**
     * Checks for stale DONE state using Shizuku as ground truth.
     * Returns true if stale state was found and cleared.
     * Tri-state: only resets on verified-absent, never on "couldn't check".
     */
    fun checkAndFixStaleState(): Boolean {
        if (!state.isDone() || isProfileOwner()) return false
        return when (hasManagedProfileViaShizuku()) {
            true -> false  // Profile exists, state is valid
            false -> {
                Log.w(TAG, "Manual refresh: verified no managed profile; resetting stale state")
                state.reset()
                true
            }
            null -> {
                Log.d(TAG, "Manual refresh: could not verify profile state; keeping DONE")
                false
            }
        }
    }

    /**
     * Tri-state Shizuku check: true = managed profile found,
     * false = verified absent, null = couldn't verify (Shizuku down, pm failed).
     */
    private fun hasManagedProfileViaShizuku(): Boolean? {
        val result = io.github.lzif.twinapps.clone.ShizukuCompat.newProcess(
            arrayOf("pm", "list", "users")
        ) ?: return null
        if (result.exitCode != 0) return null
        val regex = Regex("""UserInfo\{(\d+):[^:]*:([0-9a-fA-F]+)\}""")
        val myId = Process.myUserHandle().toUserId()
        return regex.findAll(result.stdout).any { match ->
            val id = match.groupValues[1].toIntOrNull() ?: return@any false
            val flags = match.groupValues[2].toIntOrNull(16) ?: return@any false
            id != myId && (flags and 32) != 0
        }
    }

    private fun android.os.UserHandle.toUserId(): Int {
        val p = android.os.Parcel.obtain()
        try {
            writeToParcel(p, 0)
            p.setDataPosition(0)
            return p.readInt()
        } finally {
            p.recycle()
        }
    }

    /** Records that the user asked to destroy the profile. */
    fun markDestroyRequested() {
        state.markDestroyRequested()
    }

    fun clearDestroyRequest() {
        state.clearDestroyRequest()
    }

    /** Parent side: true when a profile other than the current user exists. */
    fun hasManagedProfile(): Boolean {
        val um = context.getSystemService(UserManager::class.java) ?: return false
        return um.userProfiles.any { it != Process.myUserHandle() }
    }

    /** Best-effort managed-profile user id, for destroy targeting. */
    fun getPersistedProfileUserId(): Int? = state.getProfileUserId()

    /**
     * Reconstructs the managed profile's [UserHandle] from the persisted id.
     * Used as a fallback when the live user list is unreliable. Returns null
     * when no id was ever recorded or it matches the current user.
     */
    fun getPersistedProfileHandle(): android.os.UserHandle? {
        val id = state.getProfileUserId() ?: return null
        if (id < 0) return null
        val p = android.os.Parcel.obtain()
        return try {
            p.writeInt(id)
            p.setDataPosition(0)
            android.os.UserHandle.CREATOR.createFromParcel(p).also { handle ->
                if (handle == Process.myUserHandle()) return null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not reconstruct profile handle", e)
            null
        } finally {
            p.recycle()
        }
    }

    private fun destroyRecentlyRequested(): Boolean {
        val at = state.destroyRequestedAt()
        return at > 0 && System.currentTimeMillis() - at < DESTROY_GRACE_MS
    }

    private fun persistProfileUserId() {
        try {
            val um = context.getSystemService(UserManager::class.java) ?: return
            val myHandle = Process.myUserHandle()
            val profile = um.userProfiles.firstOrNull { it != myHandle } ?: return
            state.setProfileUserId(profile.toUserId())
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist profile user id", e)
        }
    }

    /**
     * Builds the managed-profile provisioning intent. Fire it and route the
     * result to [onManagedProvisioningResult].
     */
    fun buildProvisioningIntent(): Intent {
        state.markStarted(ProvisionType.MANAGED)
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent()
            )
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE, true)
            }
        }
    }

    /**
     * Handles the managed-provisioning activity result. Called on the side
     * that fired the intent (the parent profile). Post-provisioning itself
     * runs inside the new profile via
     * [TwinAppsDeviceAdmin.onProfileProvisioningComplete].
     */
    fun onManagedProvisioningResult(success: Boolean) {
        if (success) {
            state.markDone(ProvisionType.MANAGED)
            persistProfileUserId()
        } else {
            // User cancelled or provisioning failed: drop the half state.
            state.reset()
        }
    }

    /**
     * Detects manual provisioning (profile owner granted via adb) and
     * completes setup. Returns true when setup was completed by this call.
     * Safe to call on every resume; no-ops when already provisioned and never
     * crashes when the app is not profile owner.
     */
    fun checkManualProvisioning(): Boolean {
        if (state.isDone()) return false
        if (!isProfileOwner()) {
            Log.d(TAG, "Not profile owner; manual provisioning skipped")
            return false
        }
        Log.i(TAG, "Manual provisioning detected; completing setup")
        completeProvisioning(ProvisionType.MANUAL)
        return true
    }

    /**
     * Marks provisioning done and runs post-provisioning. Call only where
     * [isProfileOwner] is true.
     */
    fun completeProvisioning(type: ProvisionType) {
        state.markDone(type)
        runPostProvisioning()
    }

    /**
     * Post-provisioning: configures the new profile. Runs inside the managed
     * profile as profile owner. Every step is best-effort and logged; one
     * failing step never aborts the rest.
     */
    fun runPostProvisioning() {
        val admin = adminComponent()
        if (!dpm.isProfileOwnerApp(context.packageName)) {
            Log.w(TAG, "runPostProvisioning called without profile ownership; aborting")
            return
        }
        enableCriticalSystemApps(admin)
        hideRedundantContactPickers(admin)
        ProfileLauncherSetup.sync(context)
        registerCrossProfileFilters(admin)
        grantSelfOverlayAppOp()
        setAffiliation(admin)
        Log.i(TAG, "Post-provisioning complete")
    }

    /**
     * Destroys the work profile and everything inside it. Works when called
     * from inside the managed profile, where this app is profile owner.
     * Returns false when not in a position to destroy; the caller should then
     * direct the user to system Settings > Accounts > Remove work profile
     * (this also covers the orphan case where the app was uninstalled without
     * destroying the profile first).
     */
    fun destroyProfile(): Boolean {
        if (!isProfileOwner()) {
            Log.w(TAG, "Destroy requested outside the managed profile")
            return false
        }
        return try {
            dpm.wipeData(0)
            state.reset()
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Profile wipe failed", e)
            false
        }
    }

    // ---- post-provisioning steps ----

    /**
     * Enables system apps the profile needs. Starter list; refine on device
     * (the exact "critical set" is a spec residual unknown).
     */
    private fun enableCriticalSystemApps(admin: ComponentName) {
        for (pkg in CRITICAL_SYSTEM_PACKAGES) {
            try {
                dpm.enableSystemApp(admin, pkg)
            } catch (e: Exception) {
                Log.w(TAG, "enableSystemApp failed for $pkg", e)
            }
        }
    }

    /**
     * Hides redundant system contact-picker apps: system apps with a launcher
     * entry that handle contact picking, excluding the framework itself.
     */
    private fun hideRedundantContactPickers(admin: ComponentName) {
        val pm = context.packageManager
        val pick = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        val candidates = pm.queryIntentActivities(pick, 0)
        for (resolve in candidates) {
            val pkg = resolve.activityInfo.packageName
            if (pkg == "android") continue
            val isSystem =
                (resolve.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val hasLauncher = pm.getLaunchIntentForPackage(pkg) != null
            if (isSystem && hasLauncher) {
                try {
                    dpm.setApplicationHidden(admin, pkg, true)
                    Log.i(TAG, "Hid redundant contact picker: $pkg")
                } catch (e: Exception) {
                    Log.w(TAG, "setApplicationHidden failed for $pkg", e)
                }
            }
        }
    }

    /** Registers cross-profile intent filters in both directions. */
    private fun registerCrossProfileFilters(admin: ComponentName) {
        val flags = DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT or
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED

        fun add(filter: IntentFilter, dataType: String? = null) {
            try {
                if (dataType != null) filter.addDataType(dataType)
                dpm.addCrossProfileIntentFilter(admin, filter, flags)
            } catch (e: Exception) {
                Log.w(TAG, "addCrossProfileIntentFilter failed", e)
            }
        }

        add(IntentFilter(Intent.ACTION_SEND), "text/plain")
        add(IntentFilter(Intent.ACTION_SEND_MULTIPLE), "*/*")
        add(IntentFilter(Intent.ACTION_VIEW), "*/*")
        add(IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE))
        add(IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme("http")
            addDataScheme("https")
            addDataScheme("ftp")
        })
        add(
            IntentFilter(Intent.ACTION_INSTALL_PACKAGE).apply {
                addDataScheme("content")
            },
            "application/vnd.android.package-archive"
        )
        add(
            IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                addDataScheme("package")
            }
        )

        // App-management actions resolved cross-profile from day one; their
        // handling belongs to later milestones.
        for (action in listOf(ACTION_FREEZE_APP, ACTION_UNFREEZE_APP, ACTION_LAUNCH_APP)) {
            add(
                IntentFilter(action).apply {
                    addDataScheme("package")
                    addDataScheme("packages")
                    addDataScheme("intent")
                }
            )
        }
    }

    /**
     * API 29+: the overlay AppOp cannot be granted programmatically through
     * public APIs (AppOpsManager.setMode is hidden and needs a signature
     * permission), so this only verifies and logs guidance for the user.
     */
    private fun grantSelfOverlayAppOp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission missing; enable 'Display over other apps' for TwinApps in Settings")
        }
    }

    private fun setAffiliation(admin: ComponentName) {
        try {
            dpm.setAffiliationIds(admin, setOf(context.packageName))
        } catch (e: Exception) {
            Log.w(TAG, "setAffiliationIds failed", e)
        }
        // API 30+: let the profile handle app links for its own apps instead
        // of always deferring to the parent profile. Verify on device.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                dpm.addUserRestriction(admin, UserManager.ALLOW_PARENT_PROFILE_APP_LINKING)
            } catch (e: Exception) {
                Log.w(TAG, "addUserRestriction ALLOW_PARENT_PROFILE_APP_LINKING failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "ProvisioningManager"

        /**
         * After a destroy is requested, suppress the auto-markDone recovery
         * for this long: a dying profile may still linger in the user list
         * while teardown runs, and must not resurrect the DONE state.
         */
        private const val DESTROY_GRACE_MS = 30_000L

        /** Management actions resolved cross-profile; handled by later milestones. */
        const val ACTION_FREEZE_APP = "io.github.lzif.twinapps.action.FREEZE_APP"
        const val ACTION_UNFREEZE_APP = "io.github.lzif.twinapps.action.UNFREEZE_APP"
        const val ACTION_LAUNCH_APP = "io.github.lzif.twinapps.action.LAUNCH_APP"

        /**
         * System packages enabled in a fresh profile. Starter list; refine on
         * device (the exact "critical set" is a spec residual unknown).
         */
        private val CRITICAL_SYSTEM_PACKAGES = listOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.android.providers.downloads",
            "com.android.providers.media",
            "com.android.providers.contacts",
            "com.android.systemui",
            "com.android.documentsui"
        )
    }
}
