package io.github.lzif.twinapps.provisioning

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Drives the managed-profile setup flow.
 *
 * Fires the provisioning intent built by [ProvisioningManager], then finishes
 * with RESULT_OK on success (the caller shows the main UI) or RESULT_CANCELED
 * when the user backs out or provisioning cannot start.
 */
class ProvisioningActivity : AppCompatActivity() {

    private lateinit var provisioningManager: ProvisioningManager

    private val provisioningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val success = result.resultCode == Activity.RESULT_OK
        provisioningManager.onManagedProvisioningResult(success)
        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        provisioningManager = ProvisioningManager(this)

        if (provisioningManager.isProvisioned()) {
            Log.i(TAG, "Already provisioned; nothing to do")
            setResult(Activity.RESULT_OK)
            finish()
            return
        }
        try {
            provisioningLauncher.launch(provisioningManager.buildProvisioningIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Could not start provisioning", e)
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    companion object {
        private const val TAG = "ProvisioningActivity"
    }
}
