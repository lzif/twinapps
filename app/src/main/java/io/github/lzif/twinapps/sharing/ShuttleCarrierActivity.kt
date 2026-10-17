package io.github.lzif.twinapps.sharing

import android.content.ComponentName
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Ferries work across profiles and returns the result to the originator.
 *
 * Two modes, selected by the incoming extras:
 *
 * - Closure mode ([EXTRA_CLOSURE]): executes the [ProfileClosure] in this
 *   profile's process via [ShuttleClient.callLocal] and sends the reply back.
 * - Forward mode ([EXTRA_FORWARD_INTENT]): starts the enclosed intent here
 *   (e.g. a file picker) and sends its activity result back.
 *
 * The result travels back as an [ACTION_SHUTTLE_RESULT] intent addressed to
 * this same activity class in the origin profile (API 30+ via
 * [CrossProfileApps]). Manifest must declare this activity as exported; see
 * the integration report.
 *
 * Spec: isl.share.carrier-activity (required: no; implemented as documented,
 * payload contract kept minimal and explicit).
 */
class ShuttleCarrierActivity : AppCompatActivity() {

    private val forwardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val reply = Bundle().apply {
                putInt(KEY_RESULT_CODE, result.resultCode)
                putParcelable(KEY_RESULT_DATA, result.data)
            }
            sendResultBack(reply)
        }

    private var pendingRequestId: String? = null
    private var originUser: UserHandle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            ACTION_SHUTTLE_RESULT -> {
                val requestId: String? = intent.getStringExtra(EXTRA_REQUEST_ID)
                val result: Bundle? = intent.getBundleExtra(EXTRA_RESULT)
                ShuttleClient.deliverResult(requestId, result)
                finish()
            }
            ACTION_SHUTTLE -> {
                pendingRequestId = intent.getStringExtra(EXTRA_REQUEST_ID)
                originUser = intent.parcelableExtra(EXTRA_ORIGIN_USER)
                val forward: Intent? = intent.parcelableExtra(EXTRA_FORWARD_INTENT)
                if (forward != null) {
                    try {
                        forwardLauncher.launch(forward)
                    } catch (e: Exception) {
                        Log.w(TAG, "Forward launch failed", e)
                        sendResultBack(null)
                    }
                } else {
                    val closure: ProfileClosure? = intent.parcelableExtra(EXTRA_CLOSURE)
                    if (closure == null) {
                        sendResultBack(null)
                    } else {
                        // Closures are expected to be fast; still keep them
                        // off the main thread.
                        Thread {
                            val reply = try {
                                ShuttleClient.callLocal(this, closure)
                            } catch (e: Exception) {
                                Log.w(TAG, "Closure execution failed", e)
                                null
                            }
                            runOnUiThread { sendResultBack(reply) }
                        }.start()
                    }
                }
            }
            else -> finish()
        }
    }

    private fun sendResultBack(result: Bundle?) {
        val requestId = pendingRequestId
        val target = originUser
        var delivered = false
        if (requestId != null && target != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            val cpa = getSystemService(CrossProfileApps::class.java)
            if (cpa != null) {
                val back = Intent(ACTION_SHUTTLE_RESULT).apply {
                    component = ComponentName(
                        this@ShuttleCarrierActivity,
                        ShuttleCarrierActivity::class.java
                    )
                    putExtra(EXTRA_REQUEST_ID, requestId)
                    putExtra(EXTRA_RESULT, result)
                }
                try {
                    cpa.startActivity(back.component!!, target, this, null)
                    delivered = true
                } catch (e: Exception) {
                    Log.w(TAG, "Return trip to origin profile failed", e)
                }
            }
        }
        if (!delivered) {
            // Fallback for in-profile round trips (tests, single-profile dev).
            ShuttleClient.deliverResult(requestId, result)
        }
        finish()
    }

    companion object {
        const val ACTION_SHUTTLE = "io.github.lzif.twinapps.action.SHUTTLE"
        const val ACTION_SHUTTLE_RESULT = "io.github.lzif.twinapps.action.SHUTTLE_RESULT"

        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_CLOSURE = "closure"
        const val EXTRA_ORIGIN_USER = "origin_user"
        const val EXTRA_FORWARD_INTENT = "forward_intent"
        const val EXTRA_RESULT = "result"

        const val KEY_RESULT_CODE = "result_code"
        const val KEY_RESULT_DATA = "result_data"

        private const val TAG = "ShuttleCarrier"
    }
}
