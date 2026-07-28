package com.geoiq.geoiq_android_lk_vision_bot_sdk

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

fun Context.copyUriToCache(uri: Uri): File? {
    var fileName = "temp_file"
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1) cursor.getString(idx)?.let { fileName = it }
        }
    }
    val tempFile = File(cacheDir, fileName)
    return try {
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        tempFile
    } catch (e: IOException) {
        if (tempFile.exists()) tempFile.delete()
        null
    }
}
