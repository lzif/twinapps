package io.github.lzif.twinapps.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device-admin receiver required for managed-profile provisioning.
 *
 * Functional behavior is defined by the project spec, section 1 (Provisioning).
 */
class TwinAppsDeviceAdmin : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        // TODO: post-provisioning setup — see spec section 1.
    }
}
