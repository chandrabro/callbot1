package com.example.callbot

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import android.os.Bundle
import android.text.InputType
import java.io.File

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var responder: Responder
    private lateinit var spinner: Spinner
    private lateinit var message: EditText
    private lateinit var delay: EditText
    private lateinit var rbTts: RadioButton
    private lateinit var rbAudio: RadioButton
    private lateinit var cbSpeaker: CheckBox
    private lateinit var cbHang: CheckBox
    private lateinit var sw: Switch
    private lateinit var outSpinner: Spinner
    private val PICK = 42

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        responder = Responder(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        fun label(t: String) = TextView(this).apply { text = t; textSize = 16f; setPadding(0, pad, 0, 4) }

        sw = Switch(this).apply { text = "Auto-answer calls"; textSize = 18f; isChecked = prefs.enabled }
        sw.setOnCheckedChangeListener { _, on -> if (on) askPerms() else toggleService(false) }
        root.addView(sw)

        root.addView(label("Reply mode"))
        val rg = RadioGroup(this)
        rbTts = RadioButton(this).apply { text = "Text-to-speech"; id = View.generateViewId() }
        rbAudio = RadioButton(this).apply { text = "Prerecorded audio"; id = View.generateViewId() }
        rg.addView(rbTts); rg.addView(rbAudio)
        (if (prefs.useAudio) rbAudio else rbTts).isChecked = true
        root.addView(rg)

        root.addView(label("TTS message"))
        message = EditText(this).apply { setText(prefs.message); minLines = 3 }
        root.addView(message)

        root.addView(label("Audio library"))
        spinner = Spinner(this)
        root.addView(spinner)
        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "Add audio"; setOnClickListener { pickAudio() } })
        row.addView(Button(this).apply { text = "Delete"; setOnClickListener { deleteSelected() } })
        root.addView(row)

        root.addView(label("Answer after (seconds)"))
        delay = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.delaySec.toString()) }
        root.addView(delay)

        root.addView(label("Call audio output (try another if the caller hears nothing)"))
        outSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Media (recommended)", "Alarm (loud)", "Voice call (often cancelled)"))
            setSelection(prefs.outputMode)
        }
        root.addView(outSpinner)

        cbSpeaker = CheckBox(this).apply { text = "Use speakerphone (caller hears via mic)"; isChecked = prefs.speaker }
        cbHang = CheckBox(this).apply { text = "Hang up after message ends"; isChecked = prefs.hangUp }
        root.addView(cbSpeaker); root.addView(cbHang)

        val row2 = LinearLayout(this)
        row2.addView(Button(this).apply { text = "Save"; setOnClickListener { save(); toast("Saved") } })
        row2.addView(Button(this).apply { text = "Test"; setOnClickListener { save(); responder.play(false) {} } })
        row2.addView(Button(this).apply { text = "Stop"; setOnClickListener { responder.stop() } })
        root.addView(row2)

        setContentView(ScrollView(this).apply { addView(root) })
        refreshAudio()
    }

    private fun save() {
        prefs.useAudio = rbAudio.isChecked
        prefs.message = message.text.toString()
        prefs.delaySec = delay.text.toString().toIntOrNull()?.coerceIn(0, 60) ?: 3
        prefs.outputMode = outSpinner.selectedItemPosition
        prefs.speaker = cbSpeaker.isChecked
        prefs.hangUp = cbHang.isChecked
        (spinner.selectedItem as? String)?.let { prefs.audioName = it }
    }

    private fun refreshAudio() {
        val files = prefs.audioFiles()
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, files)
        val idx = files.indexOf(prefs.audioName)
        if (idx >= 0) spinner.setSelection(idx)
    }

    private fun pickAudio() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"
        }, PICK)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri: Uri = data?.data ?: return
        if (req != PICK || res != RESULT_OK) return
        var name = "audio_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) name = c.getString(i)
            }
        }
        contentResolver.openInputStream(uri)?.use { input ->
            File(prefs.audioDir(), name).outputStream().use { input.copyTo(it) }
        }
        prefs.audioName = name
        refreshAudio()
        toast("Added $name")
    }

    private fun deleteSelected() {
        (spinner.selectedItem as? String)?.let {
            File(prefs.audioDir(), it).delete()
            if (prefs.audioName == it) prefs.audioName = ""
            refreshAudio()
        }
    }

    private fun askPerms() {
        val p = mutableListOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(p.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        val ok = r.isNotEmpty() && r.take(2).all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (ok) { save(); toggleService(true) } else { sw.isChecked = false; toast("Phone permissions are required") }
    }

    private fun toggleService(on: Boolean) {
        prefs.enabled = on
        val i = Intent(this, AutoAnswerService::class.java)
        if (on) startForegroundService(i) else stopService(i)
    }

    override fun onPause() { super.onPause(); save() }
    override fun onDestroy() { responder.shutdown(); super.onDestroy() }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
