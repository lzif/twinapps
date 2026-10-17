package io.github.lzif.twinapps.provisioning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Detects manual provisioning.
 *
 * When the profile owner is granted via adb, the managed-profile intent flow
 * never runs, so setup must be completed another way:
 *
 *     adb shell dpm set-profile-owner \
 *         io.github.lzif.twinapps/.admin.TwinAppsDeviceAdmin
 *
 * This receiver handles the USER_INITIALIZE broadcast; the same check also
 * runs on every resume via [ProvisioningManager.checkManualProvisioning],
 * which is the primary detection path. Never crashes when the app is not
 * profile owner.
 */
class ManualProvisioningReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_INITIALIZE) return
        val completed = ProvisioningManager(context).checkManualProvisioning()
        Log.i(TAG, "USER_INITIALIZE received; provisioning completed=$completed")
    }

    companion object {
        private const val TAG = "ManualProvisioningReceiver"
    }
}
