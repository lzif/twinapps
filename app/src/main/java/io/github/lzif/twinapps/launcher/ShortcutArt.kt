package io.github.lzif.twinapps.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * Internal helpers for building badged shortcut art.
 *
 * Badging goes through the platform APIs (PackageManager.getUserBadgedIcon
 * and getUserBadgedLabel), so the badge is the system's own managed-profile
 * badge and stays correct across OEM skins and Android versions. This is the
 * supported equivalent of drawing a custom badge overlay.
 */

/**
 * Resolves the Island (work profile) user handle from the calling context,
 * or null when no managed profile exists.
 */
internal fun Context.islandUserHandle(): UserHandle? {
    val userManager = getSystemService(UserManager::class.java) ?: return null
    val self = Process.myUserHandle()
    return userManager.userProfiles.firstOrNull { it != self }
}

/**
 * Builds the badged launcher [Icon] for [appInfo] as seen by [profileUser].
 * The drawable is rendered into an adaptive-bitmap canvas so the launcher
 * can apply its own mask.
 */
internal fun badgedLauncherIcon(
    pm: PackageManager,
    appInfo: ApplicationInfo,
    profileUser: UserHandle,
    context: Context,
): Icon {
    val raw: Drawable = pm.getApplicationIcon(appInfo)
    val badged: Drawable = pm.getUserBadgedIcon(raw, profileUser)
    return Icon.createWithAdaptiveBitmap(badged.toShortcutBitmap(context))
}

private fun Drawable.toShortcutBitmap(context: Context): Bitmap {
    // Adaptive-icon canvas: 108dp outer size, the launcher applies the mask.
    val density = context.resources.displayMetrics.density
    val size = (108 * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bitmap
}
