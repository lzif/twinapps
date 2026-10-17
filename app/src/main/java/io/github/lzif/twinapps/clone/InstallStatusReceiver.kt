package io.github.lzif.twinapps.clone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Receives the PackageInstaller session commit/uninstall status callbacks.
 * Spec isl.clone.owner-path ("commit via IntentSender -> status callback receiver").
 *
 * Must be declared in AndroidManifest.xml (see the M2 report); delivery goes
 * through the PendingIntent, so no intent-filter is required.
 */
class InstallStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_STATUS = "io.github.lzif.twinapps.clone.INSTALL_STATUS"
        private const val TAG = "InstallStatusReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = getConfirmationIntent(intent)
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirmation)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not launch pending user action", e)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Install succeeded: $packageName")
            }
            else -> {
                Log.w(TAG, "Install failed: status=$status package=$packageName message=$message")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getConfirmationIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }
}
