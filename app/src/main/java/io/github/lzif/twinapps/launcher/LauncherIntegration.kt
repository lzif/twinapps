package io.github.lzif.twinapps.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.UserHandle

/**
 * Creates and manages badged launcher shortcuts for work-profile (Island)
 * apps, so cloned apps can be launched without opening TwinApps first.
 *
 * This class is meant to run from the profile-side instance of TwinApps
 * (the copy installed inside the work profile). The pinned shortcut then
 * belongs to the Island user and ShortcutManager.requestPin() targets the
 * profile's launcher directly.
 *
 * The shortcut icon and label carry the platform managed-profile badge
 * (via PackageManager.getUserBadgedIcon / getUserBadgedLabel), which is
 * what visually distinguishes an Island clone from its Mainland original.
 *
 * Functional behavior is defined by the project spec,
 * section 6 (Launcher integration), obligation isl.launcher.shortcuts.
 */
class LauncherIntegration(private val context: Context) {

    private val store = ShortcutStore(context)

    /**
     * Builds and pins a launcher shortcut for [packageName] as installed in
     * the Island profile.
     *
     * @return true if the pin was requested, false when there is no Island
     * profile, the package has no launchable activity, or the launcher does
     * not support pinned shortcuts.
     */
    fun createShortcut(packageName: String): Boolean {
        val profileUser = context.islandUserHandle() ?: return false
        val shortcut = buildShortcut(packageName, profileUser) ?: return false
        val manager = shortcutManager() ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        manager.requestPin(shortcut)
        store.add(profileUser.identifier, packageName)
        return true
    }

    /**
     * Removes the pinned shortcut previously created for [packageName].
     * Safe to call when no shortcut exists.
     */
    fun removeShortcut(packageName: String) {
        val profileUser = context.islandUserHandle() ?: return
        val manager = shortcutManager() ?: return
        manager.disableShortcuts(listOf(shortcutId(profileUser.identifier, packageName)))
        store.remove(profileUser.identifier, packageName)
    }

    /**
     * Rebuilds and pushes an updated ShortcutInfo for [packageName]
     * (label/icon refresh). Wraps ShortcutManager.updateShortcuts, which is
     * the "updateAll" sync primitive from the spec.
     *
     * @return the boolean returned by updateShortcuts, false when the
     * shortcut cannot be built or no profile/manager is available.
     */
    fun updateShortcut(packageName: String): Boolean {
        val profileUser = context.islandUserHandle() ?: return false
        val shortcut = buildShortcut(packageName, profileUser) ?: return false
        val manager = shortcutManager() ?: return false
        return manager.updateShortcuts(listOf(shortcut))
    }

    /**
     * Reconciles pinned shortcuts with the local store: re-pins shortcuts
     * that went missing from the launcher and drops store entries whose
     * package is no longer installed (uninstall cleanup).
     */
    fun syncShortcuts() {
        val profileUser = context.islandUserHandle() ?: return
        val manager = shortcutManager() ?: return
        val pinnedIds = manager.pinnedShortcuts.map { it.id }.toSet()
        for ((userId, packageName) in store.all()) {
            if (userId != profileUser.identifier) continue
            val id = shortcutId(userId, packageName)
            if (!isInstalled(packageName)) {
                manager.disableShortcuts(listOf(id))
                store.remove(userId, packageName)
                continue
            }
            if (!pinnedIds.contains(id)) {
                val shortcut = buildShortcut(packageName, profileUser) ?: continue
                if (manager.isRequestPinShortcutSupported) {
                    manager.requestPin(shortcut)
                }
            }
        }
    }

    /**
     * Returns true when a pinned shortcut for [packageName] currently exists
     * in the launcher.
     */
    fun hasShortcut(packageName: String): Boolean {
        val profileUser = context.islandUserHandle() ?: return false
        val manager = shortcutManager() ?: return false
        val id = shortcutId(profileUser.identifier, packageName)
        return manager.pinnedShortcuts.any { it.id == id }
    }

    // ---------- internals ----------

    private fun shortcutManager(): ShortcutManager? =
        context.getSystemService(ShortcutManager::class.java)

    /**
     * Builds the per app+user ShortcutInfo: badged label, badged adaptive
     * icon, and an explicit launch intent for the profile-side install.
     * Returns null when the package is not installed or not launchable.
     */
    private fun buildShortcut(packageName: String, profileUser: UserHandle): ShortcutInfo? {
        val pm = context.packageManager
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val icon = badgedLauncherIcon(pm, appInfo, profileUser, context)
        val label = pm.getUserBadgedLabel(pm.getApplicationLabel(appInfo), profileUser)

        return ShortcutInfo.Builder(context, shortcutId(profileUser.identifier, packageName))
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()
    }

    private fun isInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    companion object {
        /**
         * Stable shortcut id per app+user, so create/update/remove always
         * address the same pinned shortcut.
         */
        internal fun shortcutId(userId: Int, packageName: String): String =
            "twinapps-$userId-$packageName"
    }
}
