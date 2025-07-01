package com.example.ffxivmtoiletroll
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.File

class SoundPlayer(private val context: Context) {

    private val soundPool: SoundPool
    // (已修改) 使用 ResourceIdentifier 作为 Key，来唯一标识一个音效资源
    private val soundMap = mutableMapOf<ResourceIdentifier, Int>()

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    /**
     * 加载一个音效到 SoundPool 中
     * @param identifier 资源的唯一标识
     */
    fun loadSound(identifier: ResourceIdentifier) {
        if (!soundMap.containsKey(identifier)) {
            val soundId = when(identifier.type) {
                // 如果是内置资源，通过资源名获取ID并加载
                ResourceType.BUILT_IN -> {
                    val resId = context.resources.getIdentifier(identifier.path, "raw", context.packageName)
                    if (resId != 0) soundPool.load(context, resId, 1) else 0
                }
                // 如果是用户导入的资源，通过文件路径加载
                ResourceType.USER -> {
                    val file = File(File(context.filesDir, "sounds"), identifier.path)
                    if (file.exists()) soundPool.load(file.absolutePath, 1) else 0
                }
            }
            if (soundId != 0) {
                soundMap[identifier] = soundId
            }
        }
    }

    /**
     * 播放一个音效
     * @param identifier 资源的唯一标识
     */
    fun playSound(identifier: ResourceIdentifier) {
        val soundId = soundMap[identifier]
        if (soundId != null && soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        } else {
            Log.w("SoundPlayer", "Sound for identifier $identifier not loaded, attempting to load and play.")
            // 如果音效未加载，尝试即时加载并播放
            loadSound(identifier)
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                // 需要再次检查映射，确保播放的是刚刚加载完成的正确音效
                if (status == 0 && soundMap.containsValue(sampleId)) {
                    soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
                }
            }
        }
    }

    fun release() {
        soundPool.release()
    }
}
