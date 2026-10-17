package io.github.lzif.twinapps.sharing

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import java.util.UUID

/**
 * Client side of the cross-profile shuttle.
 *
 * There are two ways to reach the other profile:
 *
 * 1. [callLocal]: invokes [ShuttleProvider] in the CURRENT profile. Used by
 *    [ShuttleCarrierActivity] after it lands in the target profile, and by
 *    tests. No cross-user permission involved.
 * 2. [executeInProfile] / [ferryIntentForResult]: sends a request to the
 *    [ShuttleCarrierActivity] living in the target profile via
 *    [CrossProfileApps] (API 30+). The carrier executes locally there and
 *    sends the result back; the callback fires with the raw reply bundle.
 *    Decode it with [ShuttleResultCodec.unpackResult].
 *
 * Direct cross-user `ContentResolver` calls are not possible for normal apps
 * (they require INTERACT_ACROSS_USERS), which is why the carrier activity
 * exists as the transport.
 *
 * Spec: isl.share.closure-rpc, isl.share.carrier-activity.
 */
object ShuttleClient {

    private const val TAG = "ShuttleClient"
    private val pendingCallbacks = mutableMapOf<String, (Bundle?) -> Unit>()

    fun providerUri(context: Context): Uri = ShuttleProvider.uri(context)

    /**
     * Calls the shuttle provider in the current profile and returns the raw
     * reply bundle (null when there is nothing to return).
     */
    fun callLocal(context: Context, closure: ProfileClosure): Bundle? {
        val extras = Bundle().apply {
            putParcelable(ShuttleProvider.KEY_CLOSURE, closure)
        }
        return context.contentResolver.call(
            providerUri(context),
            ShuttleProvider.METHOD_EXECUTE,
            null,
            extras
        )
    }

    /**
     * Executes [closure] in [targetUser]'s profile. The typed result arrives
     * asynchronously through [callback] as a raw reply bundle; decode it
     * with [ShuttleResultCodec.unpackResult]. A null bundle means the round
     * trip could not be completed.
     */
    fun executeInProfile(
        callingActivity: Activity,
        targetUser: UserHandle,
        closure: ProfileClosure,
        callback: (Bundle?) -> Unit
    ) {
        val extras = Bundle().apply {
            putParcelable(ShuttleCarrierActivity.EXTRA_CLOSURE, closure)
        }
        sendShuttle(callingActivity, targetUser, extras, callback)
    }

    /**
     * Starts [forward] inside [targetUser]'s profile and reports its activity
     * result through [callback]. This is the ferry mode used for flows like
     * "pick a file in the other profile and return the URI".
     */
    fun ferryIntentForResult(
        callingActivity: Activity,
        targetUser: UserHandle,
        forward: Intent,
        callback: (resultCode: Int, data: Intent?) -> Unit
    ) {
        val extras = Bundle().apply {
            putParcelable(ShuttleCarrierActivity.EXTRA_FORWARD_INTENT, forward)
        }
        sendShuttle(callingActivity, targetUser, extras) { reply ->
            val code = reply?.getInt(
                ShuttleCarrierActivity.KEY_RESULT_CODE,
                Activity.RESULT_CANCELED
            ) ?: Activity.RESULT_CANCELED
            val data: Intent? = reply?.parcelable(ShuttleCarrierActivity.KEY_RESULT_DATA)
            callback(code, data)
        }
    }

    private fun sendShuttle(
        callingActivity: Activity,
        targetUser: UserHandle,
        intentExtras: Bundle,
        callback: (Bundle?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Pre-30 path would rely on cross-profile intent filters for the
            // shuttle actions; not attempted here. See integration report.
            Log.w(TAG, "Cross-profile shuttle requires API 30+ (CrossProfileApps)")
            callback(null)
            return
        }
        val requestId = UUID.randomUUID().toString()
        synchronized(pendingCallbacks) { pendingCallbacks[requestId] = callback }
        val cpa = callingActivity.getSystemService(CrossProfileApps::class.java)
        if (cpa == null) {
            Log.w(TAG, "CrossProfileApps not available")
            takeCallback(requestId)?.invoke(null)
            return
        }
        val intent = Intent(ShuttleCarrierActivity.ACTION_SHUTTLE).apply {
            component = ComponentName(callingActivity, ShuttleCarrierActivity::class.java)
            putExtra(ShuttleCarrierActivity.EXTRA_REQUEST_ID, requestId)
            putExtra(ShuttleCarrierActivity.EXTRA_ORIGIN_USER, Process.myUserHandle())
            putExtras(intentExtras)
        }
        try {
            cpa.startActivity(intent, targetUser, callingActivity, null)
        } catch (e: Exception) {
            Log.w(TAG, "Cross-profile carrier start failed", e)
            takeCallback(requestId)?.invoke(null)
        }
    }

    internal fun takeCallback(requestId: String): ((Bundle?) -> Unit)? =
        synchronized(pendingCallbacks) { pendingCallbacks.remove(requestId) }

    /**
     * Delivers a returned result to the waiting callback. Called by
     * [ShuttleCarrierActivity]. Returns false when no callback was waiting.
     */
    fun deliverResult(requestId: String?, result: Bundle?): Boolean {
        if (requestId == null) return false
        val callback = takeCallback(requestId) ?: return false
        callback(result)
        return true
    }
}
