package com.example.ffxivmtoiletroll
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

class SoundPlayer(private val context: Context) {

    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<Int, Int>() // Map<资源ID, SoundPool加载后的ID>

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

    fun loadSound(resId: Int) {
        if (!soundMap.containsKey(resId)) {
            val soundId = soundPool.load(context, resId, 1)
            soundMap[resId] = soundId
        }
    }

    fun playSound(resId: Int) {
        val soundId = soundMap[resId]
        if (soundId != null) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        } else {
            Log.w("SoundPlayer", "Sound for resId $resId not loaded!")
            // 可以选择在这里即时加载并播放
            loadSound(resId)
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
                }
            }
        }
    }

    fun release() {
        soundPool.release()
    }
}