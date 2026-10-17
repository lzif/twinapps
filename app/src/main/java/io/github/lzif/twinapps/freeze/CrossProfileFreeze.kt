package io.github.lzif.twinapps.freeze

import android.os.UserHandle

/**
 * Executes freeze operations inside another profile on behalf of this one.
 *
 * Implemented by the cross-profile RPC layer (spec section 5). Until that
 * layer exists, [FreezeManager] throws [RemoteNotReadyException] for
 * cross-profile requests instead of failing silently
 * (isl.freeze.cross-profile negative case).
 */
interface CrossProfileFreeze {
    fun freeze(packageName: String, targetUser: UserHandle): Boolean
    fun unfreeze(packageName: String, targetUser: UserHandle): Boolean
}

/**
 * Thrown when a freeze or unfreeze targets another profile whose TwinApps
 * instance is not reachable. Callers should surface this to the user
 * (profile not ready) rather than swallowing it.
 */
class RemoteNotReadyException(targetUser: UserHandle) :
    IllegalStateException("TwinApps instance not ready in profile $targetUser")
