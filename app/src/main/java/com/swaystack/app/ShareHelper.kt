package com.swaystack.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {
    fun createShareIntent(context: Context, bitmap: Bitmap, name: String): Intent {
        val directory = File(context.cacheDir, "shared_landmarks").apply { mkdirs() }
        val file = File(directory, "${name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
