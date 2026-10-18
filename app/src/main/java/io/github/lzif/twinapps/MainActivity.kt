package io.github.lzif.twinapps

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.lzif.twinapps.clone.AppInventory
import io.github.lzif.twinapps.clone.CloneManager
import io.github.lzif.twinapps.clone.CloneResult
import io.github.lzif.twinapps.clone.CloneStore
import io.github.lzif.twinapps.clone.ShizukuCompat
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
import io.github.lzif.twinapps.provisioning.DestroyClosure
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

    private val islandAdapter = AppRowAdapter { row, action -> onIslandAction(row, action) }

    /** Packages known-frozen on the personal side (optimistic; the profile side queries directly). */
    private val frozen = mutableSetOf<String>()

    /** Guards the async destroy flow against double-taps. */
    private var destroyInFlight = false

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

        // Show the build version so testers know which APK is installed.
        binding.versionText.text = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            "v${info.versionName} (${info.versionCode})"
        } catch (e: Exception) {
            ""
        }

        binding.islandList.layoutManager = LinearLayoutManager(this)
        binding.islandList.adapter = islandAdapter

        binding.setupButton.setOnClickListener {
            setupLauncher.launch(Intent(this, ProvisioningActivity::class.java))
        }
        binding.cloneButton.setOnClickListener { cloneFromInput() }
        binding.pkgInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                cloneFromInput()
                true
            } else false
        }
        setupPkgAutocomplete()
        binding.quietButton.setOnClickListener { toggleQuietMode() }
        binding.destroyButton.setOnClickListener { confirmDestroy() }
        binding.devTestsButton.setOnClickListener {
            startActivity(Intent(this, DevTestActivity::class.java))
        }
        binding.refreshButton.setOnClickListener { refreshState() }
        binding.shizukuButton.setOnClickListener { requestShizukuPermission() }

        refreshUi()
    }

    /**
     * Requests Shizuku permission via the system dialog.
     * After granting, TwinApps appears in Shizuku's authorized apps.
     */
    private fun requestShizukuPermission() {
        if (!ShizukuCompat.isBinderAlive()) {
            toast("Shizuku is not running. Start Shizuku first.")
            return
        }
        if (ShizukuCompat.isPermissionGranted()) {
            toast("Shizuku already authorized")
            refreshUi()
            return
        }
        if (ShizukuCompat.requestPermission(SHIZUKU_REQUEST_CODE)) {
            toast("Approve TwinApps in the Shizuku dialog")
        } else {
            toast("Could not request Shizuku permission")
        }
    }

    /**
     * Manual refresh: re-checks provisioning state against ground truth.
     * If Shizuku confirms no managed profile exists, clears stale state.
     */
    private fun refreshState() {
        toast("Refreshing...")
        Thread {
            val result = provisioningManager.checkAndFixStaleStateDetailed()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    refreshUi()
                    val msg = when (result) {
                        ProvisioningManager.RefreshResult.STALE_CLEARED ->
                            "Stale state cleared. Set up the work profile again."
                        ProvisioningManager.RefreshResult.NO_STALE_STATE ->
                            "State looks OK"
                        ProvisioningManager.RefreshResult.SHIZUKU_UNAVAILABLE ->
                            "Can't verify: Shizuku not running or not authorized"
                        ProvisioningManager.RefreshResult.NOT_APPLICABLE ->
                            "Nothing to refresh"
                    }
                    toast(msg)
                }
            }
        }.start()
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
        binding.pkgInput.visibility = cloneVisibility
        binding.cloneButton.visibility = cloneVisibility
        binding.islandSectionTitle.visibility = productVisibility
        binding.islandList.visibility = productVisibility
        binding.quietButton.visibility = productVisibility
        binding.destroyButton.visibility = productVisibility
        binding.devTestsButton.visibility = productVisibility
        binding.refreshButton.visibility = productVisibility
        // Always show when provisioned — tapping gives diagnostics if Shizuku is down.
        binding.shizukuButton.visibility = productVisibility

        if (provisioned) {
            binding.quietButton.text = getString(
                if (profileUsers.isQuietModeEnabled()) R.string.quiet_disable
                else R.string.quiet_enable
            )
            refreshIslandList(inProfile)
        }
    }

    /** Personal side: clone the package named in the input field. */
    private fun cloneFromInput() {
        val pkg = binding.pkgInput.text.toString().trim()
        if (pkg.isEmpty()) {
            toast(getString(R.string.pkg_input_empty))
            return
        }
        if (cloneStore.contains(pkg)) {
            toast(getString(R.string.pkg_already_cloned, pkg))
            return
        }
        // Fail fast on typos: check locally before the Shizuku round-trip.
        try {
            packageManager.getPackageInfo(pkg, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            toast(getString(R.string.pkg_not_installed, pkg))
            return
        }
        when (val result = cloneManager.cloneToProfile(pkg)) {
            is CloneResult.Success -> {
                cloneStore.add(pkg)
                binding.pkgInput.text.clear()
                toast(getString(R.string.clone_done, pkg))
                refreshUi()
            }
            is CloneResult.Failure -> toast(getString(R.string.clone_failed, result.reason))
        }
    }

    /**
     * Suggests installed package names as the user types. The list is only
     * used for local filtering; it is never displayed in full.
     */
    private fun setupPkgAutocomplete() {
        // Off the main thread: PackageManager queries are slow with many apps.
        Thread {
            val names = AppInventory(this).listInstalledApps()
                .filter { !it.isSystemApp }
                .map { it.packageName }
                .sorted()
            runOnUiThread {
                binding.pkgInput.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
                )
            }
        }.start()
        binding.pkgInput.threshold = 2
    }

    /** Island section: personal side reads the clone record, profile side lists its own apps. */
    private fun refreshIslandList(inProfile: Boolean) {
        if (!inProfile) {
            islandAdapter.rows = cloneStore.all().map { pkg ->
                islandRow(pkg, labelOf(pkg), local = false)
            }
            return
        }
        // Profile side: PackageManager queries + icon loading are slow;
        // build the rows off the main thread.
        Thread {
            val rows = AppInventory(this).listInstalledApps()
                .filter { !it.isSystemApp }
                .map { entry -> islandRow(entry.packageName, entry.label, local = true) }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    islandAdapter.rows = rows
                }
            }
        }.start()
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

    /** In-memory cache: icons/labels never change between refreshes. */
    private val labelCache = mutableMapOf<String, CharSequence>()
    private val iconCache = mutableMapOf<String, Drawable?>()

    private fun labelOf(packageName: String): CharSequence {
        return labelCache.getOrPut(packageName) {
            try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                )
            } catch (e: Exception) {
                packageName
            }
        }
    }

    private fun iconOf(packageName: String): Drawable? {
        return iconCache.getOrPut(packageName) {
            try {
                packageManager.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
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

    private fun confirmDestroy() {
        if (destroyInFlight) return
        AlertDialog.Builder(this)
            .setTitle(R.string.destroy_confirm_title)
            .setMessage(R.string.destroy_confirm_message)
            .setPositiveButton(R.string.destroy_confirm_yes) { _, _ -> destroyProfile() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun destroyProfile() {
        if (destroyInFlight) return
        if (provisioningManager.isProfileOwner()) {
            // Inside the profile: wipe directly.
            if (!provisioningManager.destroyProfile()) {
                toast(getString(R.string.destroy_unavailable))
            }
            return
        }
        val handle = profileUsers.resolve().managedProfile
            ?: provisioningManager.getPersistedProfileHandle()
        // Quiet mode stops the profile's processes, so the shuttle could
        // never reach it. Turn quiet off first (only when we have a target).
        if (handle != null && profileUsers.isQuietModeEnabled()) {
            toast("Turning quiet mode off to reach the work profile...")
            profileUsers.setQuietModeEnabled(false)
        }
        if (handle == null) {
            // Don't silently clear state here: on devices where the profile
            // list is unreliable this branch also fires while the profile is
            // alive, and clearing would strand the user in a setup loop.
            toast(getString(R.string.destroy_no_profile))
            return
        }
        destroyInFlight = true
        updateDestroyButton()
        provisioningManager.markDestroyRequested()
        toast(getString(R.string.destroy_in_progress))
        // Fire-and-forget: the reply may lose the race against profile
        // teardown, so cleanup is verified independently below.
        ShuttleClient.executeInProfile(this, handle, DestroyClosure()) { reply ->
            val ok = runCatching { ShuttleResultCodec.unpackResult(reply) as? Boolean }.getOrNull() == true
            if (ok) runOnUiThread { verifyDestroyCompleted() }
        }
        binding.root.postDelayed({ verifyDestroyCompleted() }, DESTROY_VERIFY_DELAY_MS)
        binding.root.postDelayed({
            if (destroyInFlight) {
                destroyInFlight = false
                provisioningManager.clearDestroyRequest()
                updateDestroyButton()
                refreshUi()
                toast(getString(R.string.destroy_failed_settings))
            }
        }, DESTROY_TIMEOUT_MS)
    }

    /**
     * Authoritative destroy verification, independent of the shuttle reply
     * race. Idempotent: only acts while a destroy is in flight.
     */
    private fun verifyDestroyCompleted() {
        if (!destroyInFlight) return
        if (!provisioningManager.hasManagedProfile()) {
            destroyInFlight = false
            provisioningManager.clearDestroyRequest()
            provisioningManager.resetState()
            cloneStore.clear()
            frozen.clear()
            updateDestroyButton()
            refreshUi()
            toast(getString(R.string.destroy_done))
        }
        // Else: profile still visible; keep waiting for the hard timeout.
    }

    private fun updateDestroyButton() {
        binding.destroyButton.isEnabled = !destroyInFlight
    }

    companion object {
        private const val DESTROY_VERIFY_DELAY_MS = 3000L
        private const val DESTROY_TIMEOUT_MS = 15000L
        private const val SHIZUKU_REQUEST_CODE = 1001
    }
}
