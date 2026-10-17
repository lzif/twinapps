package io.github.lzif.twinapps.provisioning

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Receives ACTION_PROVISION_MANAGED_PROFILE and drives the setup wizard.
 *
 * Functional behavior is defined by the project spec, section 1 (Provisioning).
 */
class ProvisioningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: see spec section 1.
        finish()
    }
}
