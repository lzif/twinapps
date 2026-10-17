package io.github.lzif.twinapps.sharing

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Executes [ProfileClosure] instances sent from the other profile and
 * returns typed results.
 *
 * Callers use `ContentResolver.call(uri, "execute", null, extras)` with the
 * closure under [KEY_CLOSURE]. The closure runs in this provider's process
 * (i.e. in the profile where this provider instance lives) and the return
 * value is marshalled back with [ShuttleResultCodec]; null/Unit yields a
 * null reply.
 *
 * Manifest must declare this provider as exported (it is a cross-profile
 * endpoint). See the integration report for the exact declaration.
 *
 * Spec: isl.share.closure-rpc.
 */
class ShuttleProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_EXECUTE) return null
        val appContext = context?.applicationContext ?: return null
        checkCaller(appContext)
        val closure = extras?.parcelable<ProfileClosure>(KEY_CLOSURE) ?: return null
        val reply = Bundle()
        try {
            ShuttleResultCodec.packResult(reply, closure.execute(appContext))
        } catch (e: Exception) {
            ShuttleResultCodec.packError(reply, e.toString())
        }
        return reply
    }

    /**
     * Only our own package may invoke the shuttle.
     *
     * SECURITY NOTE (spec isl.share.closure-rpc, residual unknown): the
     * functional spec records that no caller authentication was observed in
     * the reference behavior and flags this as needing runtime verification
     * (a foreign app could otherwise craft calls, since closures are plain
     * Parcelables). This implementation takes the safe default and rejects
     * foreign callers. Revisit only after dynamic verification.
     */
    private fun checkCaller(appContext: Context) {
        if (callingPackage != appContext.packageName) {
            throw SecurityException("ShuttleProvider: rejected caller $callingPackage")
        }
    }

    // Unused CRUD surface; this provider only serves call().
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val METHOD_EXECUTE = "execute"
        const val KEY_CLOSURE = "closure"

        fun authority(context: Context): String = "${context.packageName}.shuttle"

        fun uri(context: Context): Uri = Uri.parse("content://${authority(context)}")
    }
}
