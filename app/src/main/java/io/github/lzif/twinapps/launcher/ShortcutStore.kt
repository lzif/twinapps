package io.github.lzif.twinapps.launcher

import android.content.Context

/**
 * Minimal persistent record of the shortcuts TwinApps has pinned, keyed by
 * (userId, packageName). Lets syncShortcuts() reconcile launcher state after
 * reboots, app updates, or uninstalls that bypassed removeShortcut().
 */
internal class ShortcutStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(userId: Int, packageName: String) {
        prefs.edit().putBoolean(key(userId, packageName), true).apply()
    }

    fun remove(userId: Int, packageName: String) {
        prefs.edit().remove(key(userId, packageName)).apply()
    }

    /** Returns every recorded (userId, packageName) pair. */
    fun all(): Set<Pair<Int, String>> =
        prefs.all.keys.mapNotNull { raw ->
            val userId = raw.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            val packageName = raw.substringAfter(':')
            if (packageName.isEmpty()) null else userId to packageName
        }.toSet()

    private fun key(userId: Int, packageName: String) = "$userId:$packageName"

    companion object {
        private const val PREFS_NAME = "twinapps_shortcuts"
    }
}
