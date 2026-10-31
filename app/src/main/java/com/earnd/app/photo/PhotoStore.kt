package com.earnd.app.photo

import android.content.Context
import java.io.File
import java.util.UUID

/** App-private photo storage under files/photos. Callers persist only the file name. */
class PhotoStore(context: Context) {
    val dir: File = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    fun newFileName(): String = "${UUID.randomUUID()}.jpg"

    /** Absolute file for a stored name; path components are stripped so it never leaves [dir]. */
    fun path(fileName: String): File = File(dir, File(fileName).name)

    fun exists(fileName: String): Boolean = path(fileName).isFile

    fun delete(fileName: String?): Boolean = !fileName.isNullOrBlank() && path(fileName).delete()

    companion object {
        const val DIR = "photos"
    }
}
