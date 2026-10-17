package io.github.lzif.twinapps.sharing

import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import java.io.Serializable

/**
 * Packs and unpacks closure results into a reply [Bundle] with an explicit
 * type tag, so the caller can decode the value without knowing its static
 * type in advance.
 *
 * Supported value types: null/Unit, Boolean, Int, Long, Float, Double,
 * String, CharSequence, Bundle, Parcelable, ByteArray, primitive arrays,
 * String arrays, Parcelable arrays, IBinder, Serializable. Anything else is
 * reported back as a typed error instead of crashing the provider.
 *
 * Spec: isl.share.closure-rpc (typed result round-trip).
 */
internal object ShuttleResultCodec {
    const val KEY_TYPE = "shuttle_result_type"
    const val KEY_VALUE = "shuttle_result_value"

    const val TYPE_NULL = 0
    const val TYPE_BOOLEAN = 1
    const val TYPE_INT = 2
    const val TYPE_LONG = 3
    const val TYPE_FLOAT = 4
    const val TYPE_DOUBLE = 5
    const val TYPE_STRING = 6
    const val TYPE_CHAR_SEQUENCE = 7
    const val TYPE_BUNDLE = 8
    const val TYPE_PARCELABLE = 9
    const val TYPE_BYTE_ARRAY = 10
    const val TYPE_BOOLEAN_ARRAY = 11
    const val TYPE_INT_ARRAY = 12
    const val TYPE_LONG_ARRAY = 13
    const val TYPE_FLOAT_ARRAY = 14
    const val TYPE_DOUBLE_ARRAY = 15
    const val TYPE_STRING_ARRAY = 16
    const val TYPE_PARCELABLE_ARRAY = 17
    const val TYPE_BINDER = 18
    const val TYPE_SERIALIZABLE = 19
    const val TYPE_ERROR = -1

    fun packResult(out: Bundle, value: Any?) {
        when (value) {
            null, Unit -> out.putInt(KEY_TYPE, TYPE_NULL)
            is Boolean -> { out.putInt(KEY_TYPE, TYPE_BOOLEAN); out.putBoolean(KEY_VALUE, value) }
            is Int -> { out.putInt(KEY_TYPE, TYPE_INT); out.putInt(KEY_VALUE, value) }
            is Long -> { out.putInt(KEY_TYPE, TYPE_LONG); out.putLong(KEY_VALUE, value) }
            is Float -> { out.putInt(KEY_TYPE, TYPE_FLOAT); out.putFloat(KEY_VALUE, value) }
            is Double -> { out.putInt(KEY_TYPE, TYPE_DOUBLE); out.putDouble(KEY_VALUE, value) }
            is String -> { out.putInt(KEY_TYPE, TYPE_STRING); out.putString(KEY_VALUE, value) }
            is CharSequence -> { out.putInt(KEY_TYPE, TYPE_CHAR_SEQUENCE); out.putCharSequence(KEY_VALUE, value) }
            is Bundle -> { out.putInt(KEY_TYPE, TYPE_BUNDLE); out.putBundle(KEY_VALUE, value) }
            is Parcelable -> { out.putInt(KEY_TYPE, TYPE_PARCELABLE); out.putParcelable(KEY_VALUE, value) }
            is ByteArray -> { out.putInt(KEY_TYPE, TYPE_BYTE_ARRAY); out.putByteArray(KEY_VALUE, value) }
            is BooleanArray -> { out.putInt(KEY_TYPE, TYPE_BOOLEAN_ARRAY); out.putBooleanArray(KEY_VALUE, value) }
            is IntArray -> { out.putInt(KEY_TYPE, TYPE_INT_ARRAY); out.putIntArray(KEY_VALUE, value) }
            is LongArray -> { out.putInt(KEY_TYPE, TYPE_LONG_ARRAY); out.putLongArray(KEY_VALUE, value) }
            is FloatArray -> { out.putInt(KEY_TYPE, TYPE_FLOAT_ARRAY); out.putFloatArray(KEY_VALUE, value) }
            is DoubleArray -> { out.putInt(KEY_TYPE, TYPE_DOUBLE_ARRAY); out.putDoubleArray(KEY_VALUE, value) }
            is Array<*> -> packArray(out, value)
            is IBinder -> { out.putInt(KEY_TYPE, TYPE_BINDER); out.putBinder(KEY_VALUE, value) }
            is Serializable -> { out.putInt(KEY_TYPE, TYPE_SERIALIZABLE); out.putSerializable(KEY_VALUE, value) }
            else -> packError(out, "Unsupported result type: ${value.javaClass.name}")
        }
    }

    private fun packArray(out: Bundle, value: Array<*>) {
        val component = value.javaClass.componentType
        when {
            component == String::class.java -> {
                out.putInt(KEY_TYPE, TYPE_STRING_ARRAY)
                @Suppress("UNCHECKED_CAST")
                out.putStringArray(KEY_VALUE, value as Array<String>)
            }
            component != null && Parcelable::class.java.isAssignableFrom(component) -> {
                out.putInt(KEY_TYPE, TYPE_PARCELABLE_ARRAY)
                @Suppress("UNCHECKED_CAST")
                out.putParcelableArray(KEY_VALUE, value as Array<Parcelable>)
            }
            else -> packError(out, "Unsupported array element type: $component")
        }
    }

    fun packError(out: Bundle, message: String?) {
        out.putInt(KEY_TYPE, TYPE_ERROR)
        out.putString(KEY_VALUE, message)
    }

    /**
     * Decodes a reply bundle produced by [packResult]. Returns null for
     * null/Unit results. Throws [ShuttleRemoteException] when the remote
     * side reported an error or the tag is unknown.
     */
    @Suppress("DEPRECATION")
    fun unpackResult(bundle: Bundle?): Any? {
        if (bundle == null) return null
        return when (bundle.getInt(KEY_TYPE, TYPE_NULL)) {
            TYPE_NULL -> null
            TYPE_BOOLEAN -> bundle.getBoolean(KEY_VALUE)
            TYPE_INT -> bundle.getInt(KEY_VALUE)
            TYPE_LONG -> bundle.getLong(KEY_VALUE)
            TYPE_FLOAT -> bundle.getFloat(KEY_VALUE)
            TYPE_DOUBLE -> bundle.getDouble(KEY_VALUE)
            TYPE_STRING -> bundle.getString(KEY_VALUE)
            TYPE_CHAR_SEQUENCE -> bundle.getCharSequence(KEY_VALUE)
            TYPE_BUNDLE -> bundle.getBundle(KEY_VALUE)
            TYPE_PARCELABLE -> bundle.getParcelable(KEY_VALUE)
            TYPE_BYTE_ARRAY -> bundle.getByteArray(KEY_VALUE)
            TYPE_BOOLEAN_ARRAY -> bundle.getBooleanArray(KEY_VALUE)
            TYPE_INT_ARRAY -> bundle.getIntArray(KEY_VALUE)
            TYPE_LONG_ARRAY -> bundle.getLongArray(KEY_VALUE)
            TYPE_FLOAT_ARRAY -> bundle.getFloatArray(KEY_VALUE)
            TYPE_DOUBLE_ARRAY -> bundle.getDoubleArray(KEY_VALUE)
            TYPE_STRING_ARRAY -> bundle.getStringArray(KEY_VALUE)
            TYPE_PARCELABLE_ARRAY -> bundle.getParcelableArray(KEY_VALUE)
            TYPE_BINDER -> bundle.getBinder(KEY_VALUE)
            TYPE_SERIALIZABLE -> bundle.getSerializable(KEY_VALUE)
            TYPE_ERROR -> throw ShuttleRemoteException(bundle.getString(KEY_VALUE))
            else -> throw ShuttleRemoteException("Unknown shuttle result type tag")
        }
    }
}

/** Raised when the remote profile reports an error or a reply is malformed. */
class ShuttleRemoteException(message: String?) : RuntimeException(message)
