package io.github.lzif.twinapps.launcher

import android.content.Context

/**
 * Creates badged launcher shortcuts for work-profile apps so they can be
 * launched without opening TwinApps first.
 *
 * Functional behavior is defined by the project spec,
 * section 6 (Launcher integration).
 */
class LauncherIntegration(private val context: Context) {

    /** Creates a launcher shortcut for [packageName] in the work profile. */
    fun createShortcut(packageName: String) {
        TODO("See spec section 6")
    }

    /** Removes the launcher shortcut for [packageName]. */
    fun removeShortcut(packageName: String) {
        TODO("See spec section 6")
    }
}
