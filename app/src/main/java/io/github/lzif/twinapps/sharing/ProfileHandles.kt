package io.github.lzif.twinapps.sharing

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * Best-effort resolution of the managed (work) profile handle.
 *
 * M1 owns authoritative profile tracking (spec isl.profile.handles); this
 * helper exists so the sharing flows do not depend on M1's implementation
 * order. Assumption: a single managed profile besides the current user,
 * which holds for the standard TwinApps setup.
 */
internal fun Context.workProfileHandle(): UserHandle? {
    val userManager = getSystemService(UserManager::class.java) ?: return null
    val me = Process.myUserHandle()
    return userManager.userProfiles.firstOrNull { it != me }
}
