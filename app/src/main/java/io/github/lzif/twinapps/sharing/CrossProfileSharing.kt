package io.github.lzif.twinapps.sharing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * App-side sharing between the personal profile and the work profile.
 *
 * Outbound shares are plain ACTION_SEND intents: the cross-profile intent
 * filters registered at provisioning (M1, spec isl.share.intent-filters)
 * make the other profile's apps appear in the system chooser, so no special
 * addressing is needed for the common case. Files are staged through a
 * FileProvider so the receiving profile can read them.
 *
 * Manifest must declare the FileProvider (authorities
 * "<package>.fileprovider", paths in res/xml/file_paths.xml); see the
 * integration report.
 *
 * Functional behavior is defined by the project spec,
 * section 5 (Cross-profile sharing & RPC).
 */
class CrossProfileSharing(private val context: Context) {

    /** Shares a file with the work profile (original entry point). */
    fun shareFileToProfile(filePath: String) {
        shareFileToProfile(File(filePath), null)
    }

    /** Shares [file] with the work profile via the system chooser. */
    fun shareFileToProfile(file: File, mimeType: String?) {
        val uri = stageForSharing(file) ?: run {
            Log.w(TAG, "Cannot stage ${file.absolutePath} for sharing")
            return
        }
        val type = mimeType ?: context.contentResolver.getType(uri) ?: "*/*"
        val send = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(send)
    }

    /** Shares plain text with the work profile via the system chooser. */
    fun shareTextToProfile(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launchChooser(send)
    }

    private fun launchChooser(send: Intent) {
        val chooser = Intent.createChooser(send, "Share to profile")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Copies [file] into the app's share staging area and returns a content
     * URI for it. Returns null when the file cannot be staged.
     */
    fun stageForSharing(file: File): Uri? {
        if (!file.isFile || !file.canRead()) return null
        return try {
            val dir = File(context.cacheDir, STAGING_DIR).apply { mkdirs() }
            val staged = File(dir, file.name)
            if (staged.absolutePath != file.absolutePath) {
                file.copyTo(staged, overwrite = true)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                staged
            )
        } catch (e: Exception) {
            Log.w(TAG, "Staging failed for ${file.absolutePath}", e)
            null
        }
    }

    companion object {
        private const val TAG = "CrossProfileSharing"
        private const val STAGING_DIR = "shared"
    }
}
