package io.github.lzif.twinapps.clone

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * Installs copies of apps into the work profile ("cloning").
 *
 * Functional behavior is defined by the project spec,
 * section 3 (App cloning & installation).
 *
 * Two install paths, per the spec:
 * - Shizuku privileged worker (isl.clone.shizuku-path): used whenever
 *   Shizuku is available and authorized, regardless of which profile
 *   this instance runs in.
 * - Profile/device owner session install (isl.clone.owner-path): used
 *   otherwise; it must execute inside the target profile because
 *   PackageInstaller sessions are per-user. A parent-profile caller gets a
 *   Failure explaining this; forwarding to the profile-side instance is a
 *   cross-profile trampoline (M5 scope).
 *
 * A clone is always a fresh install: only the APK binaries are shared, the
 * copy starts with empty data (isl.clone.semantics).
 */
class CloneManager(private val context: Context) {

    private val sessionInstaller by lazy { OwnerSessionInstaller(context) }

    /**
     * Installs [packageName] (installed in the parent profile) as a fresh
     * copy into the work profile.
     */
    fun cloneToProfile(packageName: String): CloneResult {
        val targetUserId = resolveWorkProfileUserId()
            ?: return CloneResult.Failure("No work profile found. Set up the work profile first (M1).")
        return cloneToProfile(packageName, targetUserId)
    }

    /**
     * Installs [packageName] as a fresh copy into [targetUserId].
     */
    fun cloneToProfile(packageName: String, targetUserId: Int): CloneResult {
        if (targetUserId == myUserId()) {
            return CloneResult.Failure("Target user is the current user; nothing to clone.")
        }
        if (ShizukuCompat.isUsable()) {
            return ShizukuCloneWorker.installExistingPackage(packageName, targetUserId)
        }
        if (isOwner() && myUserId() == targetUserId) {
            return sessionInstaller.installExistingApp(packageName)
        }
        return CloneResult.Failure(
            "Cannot clone $packageName: Shizuku is not available, and this call is not " +
                "running inside the work profile as profile/device owner."
        )
    }

    /**
     * Installs an APK file into the work profile (fresh-install mode).
     * Runs through the owner session path, so it must execute inside the
     * work profile as profile/device owner.
     */
    fun installApkIntoProfile(apkPath: String): CloneResult {
        val targetUserId = resolveWorkProfileUserId()
            ?: return CloneResult.Failure("No work profile found. Set up the work profile first (M1).")
        if (!isOwner() || myUserId() != targetUserId) {
            return CloneResult.Failure(
                "APK install must run inside the work profile as profile/device owner."
            )
        }
        return sessionInstaller.installApk(apkPath)
    }

    /** Installs an APK from a content/file URI into the work profile. */
    fun installApkIntoProfile(apkUri: Uri): CloneResult {
        val targetUserId = resolveWorkProfileUserId()
            ?: return CloneResult.Failure("No work profile found. Set up the work profile first (M1).")
        if (!isOwner() || myUserId() != targetUserId) {
            return CloneResult.Failure(
                "APK install must run inside the work profile as profile/device owner."
            )
        }
        return sessionInstaller.installApk(apkUri)
    }

    /** Uninstalls [packageName] from the work profile. */
    fun removeFromProfile(packageName: String): CloneResult {
        val targetUserId = resolveWorkProfileUserId()
            ?: return CloneResult.Failure("No work profile found.")
        if (targetUserId == myUserId()) {
            return CloneResult.Failure("Target user is the current user.")
        }
        if (ShizukuCompat.isUsable()) {
            return ShizukuCloneWorker.uninstall(packageName, targetUserId)
        }
        if (isOwner() && myUserId() == targetUserId) {
            return sessionInstaller.uninstall(packageName)
        }
        return CloneResult.Failure(
            "Cannot uninstall $packageName: Shizuku is not available, and this call is not " +
                "running inside the work profile as profile/device owner."
        )
    }

    /**
     * Resolves the managed (work) profile user id, or null when there is none.
     *
     * Android allows at most one managed profile per user, so the work
     * profile is the first profile entry that is not the current user.
     * UserHandle exposes no public int accessor, so the id is read from its
     * parcel form (a single int, stable across platform versions).
     */
    fun resolveWorkProfileUserId(): Int? {
        val um = context.getSystemService(UserManager::class.java) ?: return null
        val myId = myUserId()
        return um.userProfiles
            .map { it.toUserId() }
            .firstOrNull { it != myId }
    }

    /**
     * Destinations for the clone chooser: the parent profile ("Mainland")
     * plus every managed profile. Spec isl.clone.ui.
     */
    fun resolveCloneTargets(): List<CloneTarget> {
        val um = context.getSystemService(UserManager::class.java) ?: return emptyList()
        val myId = myUserId()
        val targets = mutableListOf(
            CloneTarget(userId = myId, displayName = "Mainland", isParentProfile = true)
        )
        um.userProfiles
            .map { it.toUserId() }
            .filter { it != myId }
            .forEachIndexed { index, id ->
                val name = if (index == 0) "Island" else "Island ${index + 1}"
                targets.add(CloneTarget(userId = id, displayName = name, isParentProfile = false))
            }
        return targets
    }

    private fun isOwner(): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        val pkg = context.packageName
        return dpm.isProfileOwnerApp(pkg) || dpm.isDeviceOwnerApp(pkg)
    }

    private fun myUserId(): Int = Process.myUserHandle().toUserId()

    /**
     * UserHandle exposes no public int accessor; its parcel form is a single
     * int holding the user id, stable across platform versions.
     */
    private fun UserHandle.toUserId(): Int {
        val p = Parcel.obtain()
        try {
            writeToParcel(p, 0)
            p.setDataPosition(0)
            return p.readInt()
        } finally {
            p.recycle()
        }
    }
}
