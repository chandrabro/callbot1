package com.example.callbot

import android.content.Context
import java.io.File

class Prefs(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()
    var useAudio: Boolean
        get() = sp.getBoolean("useAudio", false)
        set(v) = sp.edit().putBoolean("useAudio", v).apply()
    var message: String
        get() = sp.getString("message", "Hello, I am unable to take your call right now. Please leave a message or try again later.")!!
        set(v) = sp.edit().putString("message", v).apply()
    var audioName: String
        get() = sp.getString("audioName", "")!!
        set(v) = sp.edit().putString("audioName", v).apply()
    var delaySec: Int
        get() = sp.getInt("delaySec", 3)
        set(v) = sp.edit().putInt("delaySec", v).apply()
    var speaker: Boolean
        get() = sp.getBoolean("speaker", true)
        set(v) = sp.edit().putBoolean("speaker", v).apply()
    var outputMode: Int
        get() = sp.getInt("outputMode", 0)
        set(v) = sp.edit().putInt("outputMode", v).apply()
    fun streamType(): Int = when (outputMode) {
        0 -> android.media.AudioManager.STREAM_MUSIC
        1 -> android.media.AudioManager.STREAM_ALARM
        else -> android.media.AudioManager.STREAM_VOICE_CALL
    }
    var hangUp: Boolean
        get() = sp.getBoolean("hangUp", false)
        set(v) = sp.edit().putBoolean("hangUp", v).apply()

    fun audioDir(): File = File(ctx.filesDir, "audio").apply { mkdirs() }
    fun audioFiles(): List<String> = audioDir().list()?.sorted() ?: emptyList()
}
