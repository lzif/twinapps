package io.github.lzif.twinapps.clone

/**
 * Cloning through the Shizuku (shell/ADB) identity. Spec isl.clone.shizuku-path.
 *
 * The spec describes a privileged binder worker (exact binding is a spec
 * residual unknown). Functionally, the requirement is performing the
 * privileged install under the Shizuku identity; this implementation does
 * that with `pm` shell commands executed in the Shizuku remote process via
 * [ShizukuCompat.newProcess], which needs no compile-time Shizuku dependency.
 *
 * A clone created this way is a fresh install: only the APK binaries are
 * shared, the new copy starts with empty data (spec isl.clone.semantics).
 */
object ShizukuCloneWorker {

    /**
     * Installs an already-installed package into [userId].
     * Equivalent to `adb shell pm install-existing --user <id> <package>`.
     */
    fun installExistingPackage(packageName: String, userId: Int): CloneResult {
        val result = ShizukuCompat.newProcess(
            arrayOf("pm", "install-existing", "--user", userId.toString(), packageName)
        ) ?: return CloneResult.Failure("Shizuku is not available.")
        return if (result.exitCode == 0) {
            CloneResult.Success
        } else {
            CloneResult.Failure(
                "pm install-existing failed for $packageName " +
                    "(exit ${result.exitCode}): ${result.stderr.ifEmpty { result.stdout }}"
            )
        }
    }

    /**
     * Uninstalls a package from [userId].
     * Equivalent to `adb shell pm uninstall --user <id> <package>`.
     */
    fun uninstall(packageName: String, userId: Int): CloneResult {
        val result = ShizukuCompat.newProcess(
            arrayOf("pm", "uninstall", "--user", userId.toString(), packageName)
        ) ?: return CloneResult.Failure("Shizuku is not available.")
        return if (result.exitCode == 0) {
            CloneResult.Success
        } else {
            CloneResult.Failure(
                "pm uninstall failed for $packageName " +
                    "(exit ${result.exitCode}): ${result.stderr.ifEmpty { result.stdout }}"
            )
        }
    }
}
