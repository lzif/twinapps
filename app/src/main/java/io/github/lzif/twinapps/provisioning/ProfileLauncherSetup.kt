package io.github.lzif.twinapps.provisioning

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.util.Log

/**
 * Keeps exactly one launcher entry visible per side: the personal alias on
 * the parent profile, the work alias inside the managed profile.
 *
 * The aliases are declared in the manifest; this only flips their enabled
 * state. Later milestones may build richer launcher integration on top.
 */
object ProfileLauncherSetup {

    /**
     * Enables this side's launcher alias and disables the other one.
     * Idempotent; safe to call on every launch.
     */
    fun sync(context: Context) {
        val inManagedProfile = try {
            context.getSystemService(UserManager::class.java).isManagedProfile
        } catch (e: Exception) {
            Log.w(TAG, "isManagedProfile failed; assuming parent", e)
            false
        }
        val pm = context.packageManager
        val pkg = context.packageName
        setEnabled(pm, ComponentName(pkg, "$pkg.LauncherPersonal"), !inManagedProfile)
        setEnabled(pm, ComponentName(pkg, "$pkg.LauncherWork"), inManagedProfile)
    }

    private fun setEnabled(pm: PackageManager, component: ComponentName, enabled: Boolean) {
        try {
            pm.setComponentEnabledSetting(
                component,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.w(TAG, "setComponentEnabledSetting failed for $component", e)
        }
    }

    private const val TAG = "ProfileLauncherSetup"
}
