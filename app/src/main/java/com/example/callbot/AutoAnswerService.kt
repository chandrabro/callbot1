package com.example.callbot

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

class AutoAnswerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var responder: Responder
    private lateinit var audio: AudioManager
    private var answeredByUs = false
    private var lastState = TelephonyManager.EXTRA_STATE_IDLE
    private var registered = false
    private var savedVol = -1
    private var savedStream = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val state = i.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            if (state == lastState) return // PHONE_STATE can fire twice
            lastState = state
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> scheduleAnswer()
                TelephonyManager.EXTRA_STATE_OFFHOOK -> if (answeredByUs) {
                    handler.postDelayed({ startPlayback() }, 1500)
                }
                TelephonyManager.EXTRA_STATE_IDLE -> cleanup()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        responder = Responder(this)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("cb", "CallBot", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cb")
            .setContentTitle("CallBot is active")
            .setContentText("Incoming calls will be answered automatically")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setOngoing(true)
            .build()
        startForeground(1, n)
        if (!registered) {
            registerReceiver(receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
            registered = true
        }
        return START_STICKY
    }

    private fun scheduleAnswer() {
        handler.postDelayed({
            if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                answeredByUs = true
                (getSystemService(TELECOM_SERVICE) as TelecomManager).acceptRingingCall()
            }
        }, Prefs(this).delaySec * 1000L)
    }

    private fun startPlayback() {
        val p = Prefs(this)
        if (p.speaker) {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = true
        }
        val st = p.streamType()
        savedStream = st
        savedVol = audio.getStreamVolume(st)
        audio.setStreamVolume(st, audio.getStreamMaxVolume(st), 0)
        responder.play(inCall = true) {
            if (p.hangUp &&
                checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            ) {
                (getSystemService(TELECOM_SERVICE) as TelecomManager).endCall()
            }
        }
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        responder.stop()
        @Suppress("DEPRECATION")
        audio.isSpeakerphoneOn = false
        if (savedVol >= 0) { try { audio.setStreamVolume(savedStream, savedVol, 0) } catch (_: Exception) {}; savedVol = -1 }
        answeredByUs = false
    }

    override fun onDestroy() {
        if (registered) unregisterReceiver(receiver)
        cleanup()
        responder.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
