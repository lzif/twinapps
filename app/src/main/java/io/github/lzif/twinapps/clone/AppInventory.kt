package io.github.lzif.twinapps.clone

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * One installed app, as shown by the clone picker.
 */
data class AppEntry(
    val packageName: String,
    val label: CharSequence,
    val isSystemApp: Boolean,
    /** Base APK plus split APK paths, used by the owner-path session install. */
    val apkPaths: List<String>
)

/**
 * Per-profile app inventory for the current user.
 * Reference: spec isl.apps.inventory (listing side; per-user maps, refresh on
 * package changes, and the hidden/critical-system distinctions are M7 scope).
 */
class AppInventory(private val context: Context) {

    /**
     * Lists installed apps in the current profile, sorted by label.
     * The app itself is excluded. System apps are excluded unless requested.
     */
    @Suppress("DEPRECATION")
    fun listInstalledApps(includeSystemApps: Boolean = false): List<AppEntry> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { includeSystemApps || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { appInfo ->
                val paths = mutableListOf(appInfo.publicSourceDir)
                appInfo.splitPublicSourceDirs?.let { paths.addAll(it) }
                AppEntry(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    apkPaths = paths
                )
            }
            .sortedBy { it.label.toString().lowercase() }
            .toList()
    }
}
