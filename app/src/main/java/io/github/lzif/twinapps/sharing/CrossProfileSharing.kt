package io.github.lzif.twinapps.sharing

import android.content.Context

/**
 * Shares files and intents between the personal profile and the work profile.
 *
 * Functional behavior is defined by the project spec,
 * section 5 (Cross-profile sharing & RPC).
 */
class CrossProfileSharing(private val context: Context) {

    /** Shares a file with the work profile. */
    fun shareFileToProfile(filePath: String) {
        TODO("See spec section 5")
    }
}
