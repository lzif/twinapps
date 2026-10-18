package io.github.lzif.twinapps.clone

/**
 * Reflective access to the Shizuku API.
 *
 * This avoids a compile-time dependency on the Shizuku client library; all
 * calls go through reflection and degrade to "unavailable" when the API is
 * absent. Add `dev.rikka.shizuku:api` to use the typed API instead (see the
 * final M2 report).
 */
object ShizukuCompat {

    private const val SHIZUKU_CLASS = "rikka.shizuku.Shizuku"

    /** Minimum Shizuku server API version required for cloning. Spec isl.clone.shizuku-path. */
    private const val MIN_SERVER_VERSION = 11

    /** PackageManager.PERMISSION_GRANTED, inlined to avoid importing it here. */
    private const val PERMISSION_GRANTED = 0

    /** Result of running a shell command through Shizuku. */
    data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

    /**
     * Shizuku usability gate from the spec: binder alive, our permission
     * granted, server API version >= 11.
     */
    fun isUsable(): Boolean {
        return try {
            val clazz = Class.forName(SHIZUKU_CLASS)
            val pingAlive = clazz.getMethod("pingBinder").invoke(null) as? Boolean ?: false
            if (!pingAlive) return false
            val permission = clazz.getMethod("checkSelfPermission").invoke(null) as? Int
            if (permission != PERMISSION_GRANTED) return false
            val version = clazz.getMethod("getVersion").invoke(null) as? Int ?: 0
            version >= MIN_SERVER_VERSION
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when the Shizuku binder is alive (app installed, server running),
     * regardless of whether our permission is granted.
     */
    fun isBinderAlive(): Boolean {
        return try {
            val clazz = Class.forName(SHIZUKU_CLASS)
            clazz.getMethod("pingBinder").invoke(null) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** True when Shizuku granted us permission (checkSelfPermission == GRANTED). */
    fun isPermissionGranted(): Boolean {
        return try {
            val clazz = Class.forName(SHIZUKU_CLASS)
            val permission = clazz.getMethod("checkSelfPermission").invoke(null) as? Int
            permission == PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Triggers the Shizuku permission dialog. Call from the UI thread.
     * After the user grants it, [isUsable] returns true.
     */
    fun requestPermission(requestCode: Int): Boolean {
        return try {
            val clazz = Class.forName(SHIZUKU_CLASS)
            clazz.getMethod("requestPermission", Int::class.javaPrimitiveType)
                .invoke(null, requestCode)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Runs [command] as the shell user through Shizuku (`Shizuku.newProcess`).
     * Returns null when Shizuku is not available.
     */
    fun newProcess(command: Array<String>): ShellResult? {
        return try {
            val clazz = Class.forName(SHIZUKU_CLASS)
            val method = clazz.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = method.invoke(null, command, null, null) as? Process ?: return null
            // pm output is tiny; draining both streams before waitFor is safe here.
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            ShellResult(exitCode, stdout.trim(), stderr.trim())
        } catch (_: Exception) {
            null
        }
    }
}
