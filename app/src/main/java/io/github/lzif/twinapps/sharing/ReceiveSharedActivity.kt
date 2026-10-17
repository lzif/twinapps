package io.github.lzif.twinapps.sharing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Receives files and text shared from the other profile and stores them in
 * this profile's private storage.
 *
 * The cross-profile intent filters registered at provisioning (M1, spec
 * isl.share.intent-filters) make this activity appear in the other
 * profile's share sheets for SEND / SEND_MULTIPLE / VIEW flows.
 *
 * Manifest must declare the SEND / SEND_MULTIPLE / VIEW intent filters;
 * see the integration report.
 */
class ReceiveSharedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = handleIncoming(intent)
        val message = if (saved.isEmpty()) {
            "Nothing to save"
        } else {
            "Saved ${saved.size} file(s) to TwinApps"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun handleIncoming(intent: Intent?): List<File> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) {
                    listOfNotNull(saveText(text))
                } else {
                    val uri: Uri? = intent.parcelableExtra(Intent.EXTRA_STREAM)
                    listOfNotNull(uri?.let { copyToReceived(it) })
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris: ArrayList<Uri>? =
                    intent.parcelableArrayListExtra(Intent.EXTRA_STREAM)
                uris.orEmpty().mapNotNull { copyToReceived(it) }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                listOfNotNull(uri?.let { copyToReceived(it) })
            }
            else -> emptyList()
        }
    }

    private fun receivedDir(): File = File(filesDir, RECEIVED_DIR).apply { mkdirs() }

    private fun copyToReceived(uri: Uri): File? {
        return try {
            val extension = contentResolver.getType(uri)?.let { type ->
                MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
            }
            val name = buildString {
                append("shared_")
                append(System.currentTimeMillis())
                if (extension != null) append('.').append(extension)
            }
            val dest = File(receivedDir(), name)
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save shared content: $uri", e)
            null
        }
    }

    private fun saveText(text: String): File? {
        return try {
            val dest = File(receivedDir(), "shared_${System.currentTimeMillis()}.txt")
            dest.writeText(text)
            dest
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save shared text", e)
            null
        }
    }

    companion object {
        private const val TAG = "ReceiveShared"
        private const val RECEIVED_DIR = "received"
    }
}
