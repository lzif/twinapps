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
     */
    fun isProvisioned(): Boolean {
        if (state.isDone()) return true
        if (!isProfileOwner()) {
            val um = context.getSystemService(UserManager::class.java)
            val hasExtraProfile = um.userProfiles.any { it != Process.myUserHandle() }
            if (hasExtraProfile) {
                Log.i(TAG, "Managed profile exists without recorded state; marking done")
                state.markDone(ProvisionType.MANUAL)
                return true
            }
        }
        return false
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
            state.reset()
            dpm.wipeData(0)
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

    /** API 29+: grants this app the overlay AppOp when it cannot draw overlays. */
    private fun grantSelfOverlayAppOp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (Settings.canDrawOverlays(context)) return
        val appOps = context.getSystemService(AppOpsManager::class.java)
        try {
            appOps.setMode(
                AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
                Process.myUid(),
                context.packageName,
                AppOpsManager.MODE_ALLOWED
            )
            Log.i(TAG, "Granted self overlay AppOp")
        } catch (e: Exception) {
            Log.w(TAG, "setMode for overlay AppOp failed", e)
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
