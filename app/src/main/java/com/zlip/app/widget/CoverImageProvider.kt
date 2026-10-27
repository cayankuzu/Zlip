package com.zlip.app.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the pre-rendered widget frames to whatever app draws the cover widget (System UI on Samsung).
 *
 * Exported on purpose: per-host URI grants are unreliable for widgets (the host package is not visible
 * to us under package visibility, and grants vanish on reboot). Exposure is kept minimal instead:
 * read-only, rendered frames only, file names are unguessable 128-bit hashes, no listing or queries.
 */
class CoverImageProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read-only")
        val name = uri.lastPathSegment.orEmpty()
        if (!FRAME_NAME.matches(name)) throw FileNotFoundException(name)
        val file = File(CoverFrames.dir(requireNotNull(context)), name)
        if (!file.isFile) throw FileNotFoundException(name)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private val FRAME_NAME = Regex("[0-9a-f]{32}\\.png")

        fun uri(context: Context, frame: File): Uri =
            Uri.Builder().scheme("content").authority("${context.packageName}.coverimages").appendPath(frame.name).build()
    }
}
