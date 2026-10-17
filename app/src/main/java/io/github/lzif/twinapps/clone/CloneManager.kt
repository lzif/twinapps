package io.github.lzif.twinapps.clone

import android.content.Context

/**
 * Installs copies of apps into the work profile ("cloning").
 *
 * Functional behavior is defined by the project spec,
 * section 3 (App cloning & installation).
 */
class CloneManager(private val context: Context) {

    /** Installs [packageName] into the work profile. */
    fun cloneToProfile(packageName: String) {
        TODO("See spec section 3")
    }

    /** Installs an APK file into the work profile. */
    fun installApkIntoProfile(apkPath: String) {
        TODO("See spec section 3")
    }

    /** Uninstalls [packageName] from the work profile. */
    fun removeFromProfile(packageName: String) {
        TODO("See spec section 3")
    }
}
