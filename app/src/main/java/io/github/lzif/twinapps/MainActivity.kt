package io.github.lzif.twinapps

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.lzif.twinapps.clone.AppInventory
import io.github.lzif.twinapps.clone.CloneManager
import io.github.lzif.twinapps.clone.CloneResult
import io.github.lzif.twinapps.clone.CloneStore
import io.github.lzif.twinapps.databinding.ActivityMainBinding
import io.github.lzif.twinapps.dev.DevTestActivity
import io.github.lzif.twinapps.freeze.FreezeClosure
import io.github.lzif.twinapps.freeze.FreezeManager
import io.github.lzif.twinapps.launcher.LauncherIntegration
import io.github.lzif.twinapps.launcher.ShortcutClosure
import io.github.lzif.twinapps.provisioning.ProfileLauncherSetup
import io.github.lzif.twinapps.provisioning.ProfileUsers
import io.github.lzif.twinapps.provisioning.ProvisioningActivity
import io.github.lzif.twinapps.provisioning.ProvisioningManager
import io.github.lzif.twinapps.sharing.ShuttleClient
import io.github.lzif.twinapps.sharing.ShuttleResultCodec
import io.github.lzif.twinapps.ui.AppRow
import io.github.lzif.twinapps.ui.AppRowAdapter
import io.github.lzif.twinapps.ui.RowAction

/**
 * Entry point. Routes by provisioning state: setup when no work profile
 * exists, product UI (clone + island lists, management) when provisioned.
 *
 * The UI is context-aware: inside the work profile (where this app is
 * profile owner) freeze/shortcut run locally; on the personal side they go
 * through the cross-profile shuttle.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var provisioningManager: ProvisioningManager
    private lateinit var profileUsers: ProfileUsers
    private lateinit var cloneManager: CloneManager
    private lateinit var cloneStore: CloneStore
    private lateinit var freezeManager: FreezeManager

    private val cloneAdapter = AppRowAdapter { row, action -> onCloneAction(row, action) }
    private val islandAdapter = AppRowAdapter { row, action -> onIslandAction(row, action) }

    /** Packages known-frozen on the personal side (optimistic; the profile side queries directly). */
    private val frozen = mutableSetOf<String>()

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
        cloneManager = CloneManager(this)
        cloneStore = CloneStore(this)
        freezeManager = FreezeManager(this)

        // Keep exactly one launcher entry visible per side.
        ProfileLauncherSetup.sync(this)

        binding.cloneList.layoutManager = LinearLayoutManager(this)
        binding.cloneList.adapter = cloneAdapter
        binding.islandList.layoutManager = LinearLayoutManager(this)
        binding.islandList.adapter = islandAdapter

        binding.setupButton.setOnClickListener {
            setupLauncher.launch(Intent(this, ProvisioningActivity::class.java))
        }
        binding.quietButton.setOnClickListener { toggleQuietMode() }
        binding.destroyButton.setOnClickListener { destroyProfile() }
        binding.devTestsButton.setOnClickListener {
            startActivity(Intent(this, DevTestActivity::class.java))
        }

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

        val productVisibility = if (provisioned) View.VISIBLE else View.GONE
        val inProfile = provisioned && provisioningManager.isProfileOwner()
        // Clone section only makes sense on the personal side.
        val cloneVisibility = if (provisioned && !inProfile) View.VISIBLE else View.GONE
        binding.cloneSectionTitle.visibility = cloneVisibility
        binding.cloneList.visibility = cloneVisibility
        binding.islandSectionTitle.visibility = productVisibility
        binding.islandList.visibility = productVisibility
        binding.quietButton.visibility = productVisibility
        binding.destroyButton.visibility = productVisibility
        binding.devTestsButton.visibility = productVisibility

        if (provisioned) {
            binding.quietButton.text = getString(
                if (profileUsers.isQuietModeEnabled()) R.string.quiet_disable
                else R.string.quiet_enable
            )
            if (!inProfile) refreshCloneList()
            refreshIslandList(inProfile)
        }
    }

    /** Personal side: apps that can be cloned into the work profile. */
    private fun refreshCloneList() {
        val apps = AppInventory(this).listInstalledApps()
            .filter { !it.isSystemApp }
        cloneAdapter.rows = apps.map { entry ->
            val cloned = cloneStore.contains(entry.packageName)
            AppRow(
                packageName = entry.packageName,
                label = entry.label,
                icon = iconOf(entry.packageName),
                actions = if (cloned) emptyList() else listOf(RowAction("clone", "Clone")),
            )
        }
    }

    /** Island section: personal side reads the clone record, profile side lists its own apps. */
    private fun refreshIslandList(inProfile: Boolean) {
        islandAdapter.rows = if (inProfile) {
            AppInventory(this).listInstalledApps()
                .filter { !it.isSystemApp }
                .map { entry -> islandRow(entry.packageName, entry.label, local = true) }
        } else {
            cloneStore.all().map { pkg ->
                islandRow(pkg, labelOf(pkg), local = false)
            }
        }
    }

    private fun islandRow(packageName: String, label: CharSequence, local: Boolean): AppRow {
        val isFrozen = if (local) {
            runCatching { freezeManager.isFrozen(packageName) }.getOrDefault(false)
        } else {
            packageName in frozen
        }
        val actions = mutableListOf(
            RowAction(if (isFrozen) "unfreeze" else "freeze", if (isFrozen) "Unfreeze" else "Freeze"),
            RowAction("shortcut", "Shortcut"),
        )
        if (!local) actions.add(RowAction("remove", "Remove"))
        return AppRow(packageName, label, iconOf(packageName), actions)
    }

    private fun onCloneAction(row: AppRow, actionId: String) {
        if (actionId != "clone") return
        when (val result = cloneManager.cloneToProfile(row.packageName)) {
            is CloneResult.Success -> {
                cloneStore.add(row.packageName)
                toast("Cloned ${row.label}")
                refreshUi()
            }
            is CloneResult.Failure -> toast("Clone failed: ${result.reason}")
        }
    }

    private fun onIslandAction(row: AppRow, actionId: String) {
        val pkg = row.packageName
        when (actionId) {
            "freeze" -> setFrozen(pkg, freeze = true)
            "unfreeze" -> setFrozen(pkg, freeze = false)
            "shortcut" -> createShortcut(pkg)
            "remove" -> removeClone(pkg)
        }
    }

    private fun setFrozen(packageName: String, freeze: Boolean) {
        if (provisioningManager.isProfileOwner()) {
            val ok = runCatching {
                if (freeze) freezeManager.freeze(packageName)
                else freezeManager.unfreeze(packageName)
            }.getOrDefault(false)
            toast(if (ok) "${if (freeze) "Froze" else "Unfroze"} $packageName" else "Failed")
            refreshUi()
            return
        }
        val handle = profileUsers.resolve().managedProfile
        if (handle == null) {
            toast("No work profile")
            return
        }
        ShuttleClient.executeInProfile(this, handle, FreezeClosure(packageName, freeze)) { reply ->
            val ok = runCatching { ShuttleResultCodec.unpackResult(reply) as? Boolean }.getOrNull() == true
            runOnUiThread {
                if (ok) {
                    if (freeze) frozen.add(packageName) else frozen.remove(packageName)
                    refreshUi()
                }
                toast(if (ok) "${if (freeze) "Froze" else "Unfroze"} $packageName" else "Freeze failed")
            }
        }
    }

    private fun createShortcut(packageName: String) {
        if (provisioningManager.isProfileOwner()) {
            val ok = runCatching { LauncherIntegration(this).createShortcut(packageName) }.getOrDefault(false)
            toast(if (ok) "Shortcut pinned" else "Shortcut failed")
            return
        }
        val handle = profileUsers.resolve().managedProfile
        if (handle == null) {
            toast("No work profile")
            return
        }
        ShuttleClient.executeInProfile(this, handle, ShortcutClosure(packageName)) { reply ->
            val ok = runCatching { ShuttleResultCodec.unpackResult(reply) as? Boolean }.getOrNull() == true
            runOnUiThread { toast(if (ok) "Shortcut pinned" else "Shortcut failed") }
        }
    }

    private fun removeClone(packageName: String) {
        when (val result = cloneManager.removeFromProfile(packageName)) {
            is CloneResult.Success -> {
                cloneStore.remove(packageName)
                frozen.remove(packageName)
                toast("Removed $packageName")
                refreshUi()
            }
            is CloneResult.Failure -> toast("Remove failed: ${result.reason}")
        }
    }

    private fun labelOf(packageName: String): CharSequence {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            )
        } catch (e: Exception) {
            packageName
        }
    }

    private fun iconOf(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
