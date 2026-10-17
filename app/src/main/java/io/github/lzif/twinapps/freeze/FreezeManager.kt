package io.github.lzif.twinapps.freeze

import android.content.Context

/**
 * Freezes / suspends work-profile apps without uninstalling them.
 *
 * Functional behavior is defined by the project spec,
 * section 4 (Freeze / suspend / hide).
 */
class FreezeManager(private val context: Context) {

    /** Freezes [packageName]: it can no longer run until unfrozen. */
    fun freeze(packageName: String) {
        TODO("See spec section 4")
    }

    /** Unfreezes [packageName]. */
    fun unfreeze(packageName: String) {
        TODO("See spec section 4")
    }

    /** Returns true when [packageName] is currently frozen. */
    fun isFrozen(packageName: String): Boolean {
        TODO("See spec section 4")
    }
}
