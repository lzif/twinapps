package io.github.lzif.twinapps.launcher

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import io.github.lzif.twinapps.sharing.ProfileClosure

/**
 * Runs [LauncherIntegration.createShortcut] inside the target profile via
 * the cross-profile shuttle. Shortcut pinning must execute with a
 * profile-side context, which the personal side cannot provide directly.
 */
class ShortcutClosure(
    val packageName: String,
) : ProfileClosure {

    override fun execute(context: Context): Any? =
        LauncherIntegration(context).createShortcut(packageName)

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(packageName)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ShortcutClosure> {
        override fun createFromParcel(p: Parcel): ShortcutClosure =
            ShortcutClosure(p.readString() ?: "")

        override fun newArray(size: Int): Array<ShortcutClosure?> =
            arrayOfNulls(size)
    }
}
