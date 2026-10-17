package io.github.lzif.twinapps.sharing

import android.content.Context
import android.os.Parcelable

/**
 * A unit of work that can be sent to the app instance running in the other
 * profile and executed there by [ShuttleProvider].
 *
 * Both profiles run the same APK, so concrete implementations are
 * class-compatible on both sides. Implementations must be Parcelable:
 * write a small concrete class, implement [execute], and provide a CREATOR.
 *
 * Example:
 * ```
 * class ProfileName(val fallback: String) : ProfileClosure {
 *     override fun execute(context: Context): Any? = fallback
 *     override fun writeToParcel(dest: Parcel, flags: Int) {
 *         dest.writeString(fallback)
 *     }
 *     override fun describeContents(): Int = 0
 *     companion object CREATOR : Parcelable.Creator<ProfileName> {
 *         override fun createFromParcel(p: Parcel) =
 *             ProfileName(p.readString() ?: "")
 *         override fun newArray(size: Int) =
 *             arrayOfNulls<ProfileName>(size)
 *     }
 * }
 * ```
 *
 * Spec: isl.share.closure-rpc.
 */
interface ProfileClosure : Parcelable {
    /**
     * Runs in the target profile's process with that profile's context.
     * The return value is marshalled back to the caller by
     * [ShuttleResultCodec]. Returning null (or Unit) yields a null reply.
     */
    fun execute(context: Context): Any?
}
