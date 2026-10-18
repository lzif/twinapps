package io.github.lzif.twinapps.provisioning

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import io.github.lzif.twinapps.sharing.ProfileClosure

/**
 * Runs [ProvisioningManager.destroyProfile] inside the target profile via
 * the cross-profile shuttle. Wiping the profile must execute as profile
 * owner, which only the profile-side instance is.
 */
class DestroyClosure : ProfileClosure {

    override fun execute(context: Context): Any? =
        ProvisioningManager(context).destroyProfile()

    override fun writeToParcel(dest: Parcel, flags: Int) = Unit

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<DestroyClosure> {
        override fun createFromParcel(source: Parcel): DestroyClosure = DestroyClosure()

        override fun newArray(size: Int): Array<DestroyClosure?> =
            arrayOfNulls(size)
    }
}
