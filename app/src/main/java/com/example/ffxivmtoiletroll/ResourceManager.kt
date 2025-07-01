package com.example.ffxivmtoiletroll

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ResourceManager(private val context: Context) {

    private val TAG = "ResourceManager"
    private val imagesDir = File(context.filesDir, "images")
    private val soundsDir = File(context.filesDir, "sounds")

    // 应用启动时，确保存放用户资源的文件夹存在
    init {
        if (!imagesDir.exists()) imagesDir.mkdirs()
        if (!soundsDir.exists()) soundsDir.mkdirs()
    }

    /**
     * 从一个 URI 导入文件到应用的内部存储
     * @param sourceUri 来源文件的 URI
     * @param type 要导入的资源类型 (图片或音频)
     * @return 成功则返回新文件名，失败则返回 null
     */
    fun importResource(sourceUri: Uri, type: String): String? {
        val destinationDir = when (type) {
            "image" -> imagesDir
            "sound" -> soundsDir
            else -> return null
        }
        val extension = when (type) {
            "image" -> "png"
            "sound" -> "mp3" // 可根据需要支持更多格式
            else -> "dat"
        }
        val newFileName = "user_${System.currentTimeMillis()}.$extension"
        val destinationFile = File(destinationDir, newFileName)

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.i(TAG, "Resource imported successfully as: $newFileName")
            return newFileName
        } catch (e: IOException) {
            Log.e(TAG, "Failed to import resource from URI: $sourceUri", e)
            return null
        }
    }

    /**
     * 获取所有可用的图片资源标识符
     * @param prefix 内置图片资源的前缀 (例如 "match_" 或 "display_")
     * @return 一个包含所有内置和用户导入的图片资源的列表
     */
    fun getAvailableImages(prefix: String): List<ResourceIdentifier> {
        val resources = mutableListOf<ResourceIdentifier>()
        // 1. 添加内置资源
        R.drawable::class.java.fields
            .filter { it.name.startsWith(prefix) }
            .map { ResourceIdentifier(ResourceType.BUILT_IN, it.name) }
            .let { resources.addAll(it) }

        // 2. 添加用户导入的资源
        imagesDir.listFiles()
            ?.map { ResourceIdentifier(ResourceType.USER, it.name) }
            ?.let { resources.addAll(it) }

        return resources
    }

    /**
     * 获取所有可用的音效资源标识符
     * @return 一个包含所有内置和用户导入的音效资源的列表
     */
    fun getAvailableSounds(): List<ResourceIdentifier> {
        val resources = mutableListOf<ResourceIdentifier>()
        // 1. 添加内置资源
        R.raw::class.java.fields
            .map { ResourceIdentifier(ResourceType.BUILT_IN, it.name) }
            .let { resources.addAll(it) }

        // 2. 添加用户导入的资源
        soundsDir.listFiles()
            ?.map { ResourceIdentifier(ResourceType.USER, it.name) }
            ?.let { resources.addAll(it) }

        return resources
    }

    /**
     * 根据资源标识符加载一张 Bitmap 图片
     * @param identifier 资源的唯一标识
     * @return 加载成功返回 Bitmap，失败返回 null
     */
    fun loadBitmap(identifier: ResourceIdentifier): Bitmap? {
        return when (identifier.type) {
            ResourceType.BUILT_IN -> {
                val resId = context.resources.getIdentifier(identifier.path, "drawable", context.packageName)
                if (resId == 0) null else BitmapFactory.decodeResource(context.resources, resId)
            }
            ResourceType.USER -> {
                val file = File(imagesDir, identifier.path)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
        }
    }

    /**
     * 根据资源标识符获取音效文件的 File 对象
     * @param identifier 资源的唯一标识
     * @return 如果是用户导入的音效，返回其 File 对象，否则返回 null
     */
    fun getSoundFile(identifier: ResourceIdentifier): File? {
        if (identifier.type == ResourceType.USER) {
            val file = File(soundsDir, identifier.path)
            return if (file.exists()) file else null
        }
        return null // 内置音效通过 ID 直接播放，不返回 File
    }
}
