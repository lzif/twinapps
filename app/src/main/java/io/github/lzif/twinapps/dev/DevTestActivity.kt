package io.github.lzif.twinapps.dev

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.lzif.twinapps.clone.CloneManager
import io.github.lzif.twinapps.clone.CloneResult
import io.github.lzif.twinapps.databinding.ActivityDevTestBinding
import io.github.lzif.twinapps.freeze.FreezeManager
import io.github.lzif.twinapps.launcher.LauncherIntegration
import io.github.lzif.twinapps.sharing.CrossProfileSharing

/**
 * Dev-only test screen for exercising the M2-M5 managers on a real device.
 * Not part of the product UI.
 */
class DevTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDevTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val clone = CloneManager(this)
        val freeze = FreezeManager(this)
        val launcher = LauncherIntegration(this)
        val sharing = CrossProfileSharing(this)

        binding.cloneButton.setOnClickListener {
            val pkg = pkg() ?: return@setOnClickListener
            val result = clone.cloneToProfile(pkg)
            val msg = when (result) {
                is CloneResult.Success -> "clone $pkg: SUCCESS"
                is CloneResult.Failure -> "clone $pkg: FAILED: ${result.reason}"
            }
            report(msg)
        }
        binding.removeCloneButton.setOnClickListener {
            val pkg = pkg() ?: return@setOnClickListener
            val result = clone.removeFromProfile(pkg)
            val msg = when (result) {
                is CloneResult.Success -> "remove $pkg: SUCCESS"
                is CloneResult.Failure -> "remove $pkg: FAILED: ${result.reason}"
            }
            report(msg)
        }
        binding.freezeButton.setOnClickListener {
            val pkg = pkg() ?: return@setOnClickListener
            report("freeze $pkg: ${ok(freeze.freeze(pkg))} (frozen=${freeze.isFrozen(pkg)})")
        }
        binding.unfreezeButton.setOnClickListener {
            val pkg = pkg() ?: return@setOnClickListener
            report("unfreeze $pkg: ${ok(freeze.unfreeze(pkg))} (frozen=${freeze.isFrozen(pkg)})")
        }
        binding.suspendButton.setOnClickListener {
            val pkg = pkg() ?: return@setOnClickListener
            report("suspend $pkg: ${ok(freeze.suspend(pkg))} (suspended=${freeze.isSuspended(pkg)})")
        }
        binding.unsuspendButton.setOnClickListener {
            val pkg = pkg() ?: return@setOnClickListener
            report("unsuspend $pkg: ${ok(freeze.unsuspend(pkg))} (suspended=${freeze.isSuspended(pkg)})")
        }
        binding.shortcutButton.setOnClickListener {
            val pkg = pkg() ?: return@setOnClickListener
            report("create shortcut $pkg: ${ok(launcher.createShortcut(pkg))}")
        }
        binding.removeShortcutButton.setOnClickListener {
            val pkg = pkg() ?: return@setOnClickListener
            launcher.removeShortcut(pkg)
            report("remove shortcut $pkg: done")
        }
        binding.shareTextButton.setOnClickListener {
            sharing.shareTextToProfile("Hello from TwinApps dev screen")
            report("share text: chooser launched")
        }
        binding.workProfileIdButton.setOnClickListener {
            report("work profile user id: ${clone.resolveWorkProfileUserId()}")
        }
    }

    private fun pkg(): String? {
        val pkg = binding.pkgInput.text.toString().trim()
        if (pkg.isEmpty()) {
            Toast.makeText(this, "Enter a package name first", Toast.LENGTH_SHORT).show()
            return null
        }
        return pkg
    }

    private fun ok(value: Boolean): String = if (value) "SUCCESS" else "FAILED"

    private fun report(msg: String) {
        binding.resultText.text = "${binding.resultText.text}\n$msg"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
