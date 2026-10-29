package com.example.callbot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && Prefs(c).enabled) {
            try { c.startForegroundService(Intent(c, AutoAnswerService::class.java)) } catch (_: Exception) {}
        }
    }
}
