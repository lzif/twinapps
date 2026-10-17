package io.github.lzif.twinapps.clone

/**
 * Outcome of a clone/install/uninstall operation.
 *
 * Success is reported when the platform call was accepted; the final
 * install status additionally arrives via [InstallStatusReceiver].
 */
sealed class CloneResult {
    /** The operation was accepted by the platform. */
    object Success : CloneResult()

    /** The operation could not be performed or was rejected. */
    data class Failure(val reason: String, val cause: Throwable? = null) : CloneResult()
}
