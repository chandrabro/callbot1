package com.example.callbot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File

/** Plays either the prerecorded audio file or the TTS message. */
class Responder(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var player: MediaPlayer? = null

    init {
        tts = TextToSpeech(ctx) { status -> ready = status == TextToSpeech.SUCCESS }
    }

    fun play(inCall: Boolean, onDone: () -> Unit) {
        stop()
        val p = Prefs(ctx)
        val file = File(p.audioDir(), p.audioName)
        if (p.useAudio && p.audioName.isNotEmpty() && file.exists()) playFile(file, inCall, onDone)
        else speak(p.message, inCall, onDone, 0)
    }

    private fun attrs(inCall: Boolean): AudioAttributes {
        val mode = if (inCall) Prefs(ctx).outputMode else 0
        val b = AudioAttributes.Builder()
        when (mode) {
            0 -> b.setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            1 -> b.setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            else -> b.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        }
        return b.build()
    }

    private fun playFile(f: File, inCall: Boolean, onDone: () -> Unit) {
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs(inCall))
                setDataSource(f.absolutePath)
                setOnCompletionListener { main.post(onDone) }
                setOnErrorListener { _, _, _ -> main.post(onDone); true }
                prepare()
                start()
            }
        } catch (e: Exception) { main.post(onDone) }
    }

    private fun speak(text: String, inCall: Boolean, onDone: () -> Unit, tries: Int) {
        val t = tts ?: return
        if (!ready) {
            if (tries < 20) main.postDelayed({ speak(text, inCall, onDone, tries + 1) }, 300)
            else onDone()
            return
        }
        t.setAudioAttributes(attrs(inCall))
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post(onDone) }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post(onDone) }
        })
        val b = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM,
                if (inCall) Prefs(ctx).streamType() else AudioManager.STREAM_MUSIC)
        }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, b, "callbot")
    }

    fun stop() {
        tts?.stop()
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
