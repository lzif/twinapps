package io.github.lzif.twinapps.freeze

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import io.github.lzif.twinapps.admin.TwinAppsDeviceAdmin

/**
 * Freezes, suspends, and restores work-profile apps without uninstalling them.
 *
 * Two distinct mechanisms, per the functional spec section 4:
 * - Freeze (isl.freeze.hide): hides the app via
 *   [DevicePolicyManager.setApplicationHidden]. A frozen app disappears from
 *   the launcher and cannot run at all, including in the background.
 * - Suspend (isl.freeze.suspend): suspends the app via
 *   [DevicePolicyManager.setPackagesSuspended]. A suspended app keeps its
 *   launcher icon (rendered in a suspended state) but cannot be launched.
 *
 * The caller must be the profile owner of the profile hosting the target
 * apps. Operations targeting another profile are routed through
 * [CrossProfileFreeze] (isl.freeze.cross-profile).
 */
class FreezeManager(
    private val context: Context,
    private val admin: ComponentName = ComponentName(context, TwinAppsDeviceAdmin::class.java),
    private val remote: CrossProfileFreeze? = null,
) {
    private val dpm: DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    // Freeze (hide)

    /**
     * Freezes [packageName]: the app is hidden, disappears from the launcher,
     * and cannot run until [unfreeze] is called.
     *
     * @return true when the hidden setting was applied.
     * @throws IllegalArgumentException if [packageName] is TwinApps itself.
     */
    fun freeze(packageName: String): Boolean {
        require(packageName != context.packageName) {
            "Refusing to freeze TwinApps itself"
        }
        return dpm.setApplicationHidden(admin, packageName, true)
    }

    /**
     * Restores a frozen app.
     *
     * @return true when the hidden setting was applied.
     */
    fun unfreeze(packageName: String): Boolean =
        dpm.setApplicationHidden(admin, packageName, false)

    /** Returns true when [packageName] is currently frozen (hidden). */
    fun isFrozen(packageName: String): Boolean =
        dpm.isApplicationHidden(admin, packageName)

    // Suspend

    /**
     * Suspends [packageName]: the launcher icon stays visible in a suspended
     * state, but the app cannot be launched until [unsuspend] is called.
     *
     * @return true when the suspension was applied.
     * @throws IllegalArgumentException if [packageName] is TwinApps itself.
     */
    fun suspend(packageName: String): Boolean {
        require(packageName != context.packageName) {
            "Refusing to suspend TwinApps itself"
        }
        return dpm.setPackagesSuspended(admin, arrayOf(packageName), true).isEmpty()
    }

    /**
     * Lifts the suspension on [packageName].
     *
     * @return true when the change was applied.
     */
    fun unsuspend(packageName: String): Boolean =
        dpm.setPackagesSuspended(admin, arrayOf(packageName), false).isEmpty()

    /** Returns true when [packageName] is currently suspended. */
    fun isSuspended(packageName: String): Boolean =
        dpm.isPackageSuspended(admin, packageName)

    // Guard helpers

    /**
     * Returns true when [packageName] is a system app. Callers should warn the
     * user before freezing such packages (isl.freeze.hide negative case:
     * freezing a critical system package shows a warning and is guarded).
     */
    fun isSystemApp(packageName: String): Boolean =
        try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    // Cross-profile routing

    /**
     * Freezes [packageName] in [targetUser]. When the target is the current
     * profile the local path is used; otherwise the request is delegated to
     * [CrossProfileFreeze].
     *
     * @throws RemoteNotReadyException when the other profile's TwinApps
     *   instance is not reachable. Never fails silently
     *   (isl.freeze.cross-profile negative case).
     */
    fun freeze(packageName: String, targetUser: UserHandle): Boolean =
        if (targetUser == Process.myUserHandle()) {
            freeze(packageName)
        } else {
            remote?.freeze(packageName, targetUser)
                ?: throw RemoteNotReadyException(targetUser)
        }

    /**
     * Cross-profile variant of [unfreeze]. See [freeze] for routing behavior.
     */
    fun unfreeze(packageName: String, targetUser: UserHandle): Boolean =
        if (targetUser == Process.myUserHandle()) {
            unfreeze(packageName)
        } else {
            remote?.unfreeze(packageName, targetUser)
                ?: throw RemoteNotReadyException(targetUser)
        }
}
