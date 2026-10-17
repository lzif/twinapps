package io.github.lzif.twinapps

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.github.lzif.twinapps.databinding.ActivityMainBinding
import io.github.lzif.twinapps.provisioning.ProfileLauncherSetup
import io.github.lzif.twinapps.provisioning.ProfileUsers
import io.github.lzif.twinapps.provisioning.ProvisioningActivity
import io.github.lzif.twinapps.provisioning.ProvisioningManager

/**
 * Entry point. Routes by provisioning state: setup when no work profile
 * exists, management UI (quiet mode, destroy) when provisioned.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var provisioningManager: ProvisioningManager
    private lateinit var profileUsers: ProfileUsers

    private val setupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // ProvisioningActivity reports its own result; just refresh.
        refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        provisioningManager = ProvisioningManager(this)
        profileUsers = ProfileUsers(this)

        // Keep exactly one launcher entry visible per side.
        ProfileLauncherSetup.sync(this)

        binding.setupButton.setOnClickListener {
            setupLauncher.launch(Intent(this, ProvisioningActivity::class.java))
        }
        binding.quietButton.setOnClickListener { toggleQuietMode() }
        binding.destroyButton.setOnClickListener { destroyProfile() }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        // Manual (adb) provisioning is detected here.
        if (provisioningManager.checkManualProvisioning()) {
            ProfileLauncherSetup.sync(this)
        }
        refreshUi()
    }

    private fun refreshUi() {
        val provisioned = provisioningManager.isProvisioned()
        binding.statusText.text = if (provisioned) {
            getString(R.string.status_provisioned)
        } else {
            getString(R.string.status_not_provisioned)
        }
        binding.setupButton.visibility = if (provisioned) View.GONE else View.VISIBLE
        val managementVisibility = if (provisioned) View.VISIBLE else View.GONE
        binding.quietButton.visibility = managementVisibility
        binding.destroyButton.visibility = managementVisibility
        if (provisioned) {
            binding.quietButton.text = getString(
                if (profileUsers.isQuietModeEnabled()) R.string.quiet_disable
                else R.string.quiet_enable
            )
        }
    }

    private fun toggleQuietMode() {
        val enable = !profileUsers.isQuietModeEnabled()
        if (!profileUsers.setQuietModeEnabled(enable)) {
            Toast.makeText(this, R.string.quiet_failed, Toast.LENGTH_SHORT).show()
        }
        refreshUi()
    }

    private fun destroyProfile() {
        if (!provisioningManager.destroyProfile()) {
            Toast.makeText(this, R.string.destroy_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
