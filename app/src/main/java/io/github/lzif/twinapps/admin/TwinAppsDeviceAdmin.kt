package io.github.lzif.twinapps.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.lzif.twinapps.provisioning.ProvisionType
import io.github.lzif.twinapps.provisioning.ProvisioningManager

/**
 * Device-admin receiver required for managed-profile provisioning.
 *
 * When the system finishes creating the managed profile it invokes
 * [onProfileProvisioningComplete] inside the new profile, where
 * post-provisioning runs.
 */
class TwinAppsDeviceAdmin : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(TAG, "Profile provisioning complete; running post-provisioning")
        ProvisioningManager(context).completeProvisioning(ProvisionType.MANAGED)
    }

    companion object {
        private const val TAG = "TwinAppsDeviceAdmin"
    }
}
