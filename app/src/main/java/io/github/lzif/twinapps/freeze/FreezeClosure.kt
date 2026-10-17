package io.github.lzif.twinapps.freeze

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import io.github.lzif.twinapps.sharing.ProfileClosure

/**
 * Runs [FreezeManager] freeze/unfreeze inside the target profile via the
 * cross-profile shuttle. Both profiles run the same APK, so this class is
 * available on the executing side.
 */
class FreezeClosure(
    val packageName: String,
    val freeze: Boolean,
) : ProfileClosure {

    override fun execute(context: Context): Any? {
        val manager = FreezeManager(context)
        return if (freeze) manager.freeze(packageName) else manager.unfreeze(packageName)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(packageName)
        dest.writeInt(if (freeze) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FreezeClosure> {
        override fun createFromParcel(p: Parcel): FreezeClosure =
            FreezeClosure(p.readString() ?: "", p.readInt() == 1)

        override fun newArray(size: Int): Array<FreezeClosure?> =
            arrayOfNulls(size)
    }
}
