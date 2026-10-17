package io.github.lzif.twinapps.clone

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileInputStream

/**
 * Installs into the current user via [PackageInstaller] sessions.
 * Spec isl.clone.owner-path.
 *
 * This must run inside the target profile: as profile/device owner the app
 * is installed in the managed profile too, and sessions created there land
 * in that user. Callers in the parent profile must forward the request to
 * the profile-side instance (cross-profile trampoline, see M5 notes).
 *
 * Session parameters follow the spec: MODE_FULL_INSTALL, package name,
 * label and originating UID set, install reason 4 (INSTALL_REASON_UNKNOWN)
 * on API 26+, USER_ACTION_NOT_REQUIRED on API 31+.
 */
class OwnerSessionInstaller(private val context: Context) {

    companion object {
        private const val INSTALL_REASON_UNKNOWN = 4
        private const val STATUS_REQUEST_CODE = 1001
    }

    /**
     * Clone mode: streams the installed app's base APK plus named splits
     * (publicSourceDir + splitPublicSourceDirs) into one session.
     */
    @Suppress("DEPRECATION")
    fun installExistingApp(packageName: String): CloneResult {
        val pm = context.packageManager
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: Exception) {
            return CloneResult.Failure("Package not installed: $packageName", e)
        }
        val apkPaths = mutableListOf(appInfo.publicSourceDir)
        appInfo.splitPublicSourceDirs?.let { apkPaths.addAll(it) }
        return commitSession(
            packageName = packageName,
            appLabel = pm.getApplicationLabel(appInfo),
            originatingUid = appInfo.uid,
            apkPaths = apkPaths
        )
    }

    /** Fresh-install mode: installs an APK file. */
    @Suppress("DEPRECATION")
    fun installApk(apkPath: String): CloneResult {
        val file = File(apkPath)
        if (!file.isFile) {
            return CloneResult.Failure("APK file not found: $apkPath")
        }
        val packageName = context.packageManager
            .getPackageArchiveInfo(apkPath, 0)?.packageName
            ?: return CloneResult.Failure("Could not parse package name from $apkPath")
        return commitSession(
            packageName = packageName,
            appLabel = packageName,
            originatingUid = android.os.Process.myUid(),
            apkPaths = listOf(apkPath)
        )
    }

    /** Fresh-install mode: installs an APK from a content/file URI. */
    fun installApk(apkUri: Uri): CloneResult {
        val tmp = File(context.cacheDir, "twinapps-install-${System.currentTimeMillis()}.apk")
        try {
            val input = context.contentResolver.openInputStream(apkUri)
                ?: return CloneResult.Failure("Could not open $apkUri")
            input.use { ins ->
                tmp.outputStream().use { out -> ins.copyTo(out) }
            }
            return installApk(tmp.absolutePath)
        } catch (e: Exception) {
            return CloneResult.Failure("Could not stage APK from $apkUri: ${e.message}", e)
        } finally {
            tmp.delete()
        }
    }

    /** Removes an installed package from the current user. */
    fun uninstall(packageName: String): CloneResult {
        return try {
            context.packageManager.packageInstaller.uninstall(packageName, statusIntentSender())
            CloneResult.Success
        } catch (e: Exception) {
            CloneResult.Failure("Uninstall failed for $packageName: ${e.message}", e)
        }
    }

    private fun commitSession(
        packageName: String,
        appLabel: CharSequence,
        originatingUid: Int,
        apkPaths: List<String>
    ): CloneResult {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.appPackageName = packageName
        params.appLabel = appLabel
        params.originatingUid = originatingUid
        params.installReason = INSTALL_REASON_UNKNOWN
        params.sizeBytes = apkPaths.sumOf { File(it).length() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        var session: PackageInstaller.Session? = null
        return try {
            session = installer.openSession(installer.createSession(params))
            val active = session!!
            for (path in apkPaths) {
                val entryName = File(path).name.ifEmpty { "base.apk" }
                FileInputStream(path).use { input ->
                    active.openWrite(entryName, 0, -1).use { output ->
                        input.copyTo(output)
                        active.fsync(output)
                    }
                }
            }
            active.commit(statusIntentSender())
            CloneResult.Success
        } catch (e: Exception) {
            runCatching { session?.abandon() }
            CloneResult.Failure("Session install failed for $packageName: ${e.message}", e)
        } finally {
            runCatching { session?.close() }
        }
    }

    private fun statusIntentSender(): android.content.IntentSender {
        val intent = Intent(context, InstallStatusReceiver::class.java).apply {
            action = InstallStatusReceiver.ACTION_INSTALL_STATUS
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The system fills in install status extras, so the PendingIntent must be mutable.
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(context, STATUS_REQUEST_CODE, intent, flags).intentSender
    }
}
