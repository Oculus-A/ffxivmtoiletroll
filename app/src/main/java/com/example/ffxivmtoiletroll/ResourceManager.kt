package com.example.ffxivmtoiletroll
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class ResourceManager(private val context: Context) {
    private val TAG = "ResourceManager"
    private val imagesDir = File(context.filesDir, "images")

    init {
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
    }

    fun importImage(sourceUri: Uri, prefix: String): String? {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val newFileName = "${prefix}user_${System.currentTimeMillis()}.png"
            val destinationFile = File(imagesDir, newFileName)

            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            Log.i(TAG, "Image imported successfully as: $newFileName")
            return newFileName

        } catch (e: Exception) {
            Log.e(TAG, "Failed to import image", e)
            return null
        }
    }

    fun listUserImages(prefix: String): List<String> {
        return imagesDir.listFiles { _, name -> name.startsWith(prefix) }?.map { it.name } ?: emptyList()
    }

    fun getImageFile(fileName: String): File? {
        val file = File(imagesDir, fileName)
        return if (file.exists()) file else null
    }
}
