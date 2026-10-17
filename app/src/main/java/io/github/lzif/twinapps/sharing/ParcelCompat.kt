package io.github.lzif.twinapps.sharing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable

/**
 * Version-safe Parcelable extraction (the one-arg getters are deprecated
 * on API 33+ but still required below it).
 */
internal inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key)
    }

internal inline fun <reified T : Parcelable> Intent.parcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }

internal inline fun <reified T : Parcelable> Intent.parcelableArrayListExtra(key: String): ArrayList<T>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(key)
    }
