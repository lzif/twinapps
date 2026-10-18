package io.github.lzif.twinapps.clone

import android.content.Context

/**
 * Remembers which packages this app instance cloned into the work profile.
 * This is the personal side's record; the profile side instead lists its
 * own installed apps directly.
 */
class CloneStore(context: Context) {

    private val prefs =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(packageName: String) {
        prefs.edit().putStringSet(KEY, (all() + packageName).toSet()).apply()
    }

    fun remove(packageName: String) {
        prefs.edit().putStringSet(KEY, (all() - packageName).toSet()).apply()
    }

    fun contains(packageName: String): Boolean = packageName in all()

    fun all(): Set<String> =
        prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val PREFS = "clone_store"
        private const val KEY = "cloned_packages"
    }
}
