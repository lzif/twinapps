package io.github.lzif.twinapps.provisioning

import android.content.Context

/**
 * Creates and manages the work profile that hosts cloned apps.
 *
 * Functional behavior is defined by the project spec, section 1 (Provisioning).
 */
class ProvisioningManager(private val context: Context) {

    /** Returns true when a work profile owned by this app already exists. */
    fun isProvisioned(): Boolean {
        TODO("See spec section 1")
    }

    /** Starts the managed-profile provisioning flow. */
    fun startProvisioning() {
        TODO("See spec section 1")
    }

    /** Removes the work profile and everything inside it. */
    fun removeProfile() {
        TODO("See spec section 1")
    }
}
