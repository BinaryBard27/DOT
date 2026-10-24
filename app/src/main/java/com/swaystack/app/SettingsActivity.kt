package com.swaystack.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(TextView(this).apply { text = "TAUT SETTINGS"; textSize = 28f })
        val delayLabel = TextView(this)
        val slider = SeekBar(this).apply { max = 200; progress = store.inputDelayOffsetMs + 100 }
        fun updateDelay(progress: Int) { val value = progress - 100; store.inputDelayOffsetMs = value; delayLabel.text = "Input delay: ${value} ms" }
        updateDelay(slider.progress)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = updateDelay(progress)
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        root.addView(delayLabel); root.addView(slider)
        root.addView(TextView(this).apply { text = "Tap the slider, then tap in a run to test timing offset."; textSize = 16f })
        root.addView(Switch(this).apply { text = "Vibration"; isChecked = store.vibrationEnabled; setOnCheckedChangeListener { _, checked -> store.vibrationEnabled = checked } })
        root.addView(Switch(this).apply { text = "Sound"; isChecked = store.soundEnabled; setOnCheckedChangeListener { _, checked -> store.soundEnabled = checked } })
        root.addView(Button(this).apply { text = "Privacy Policy"; setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL))) } })
        setContentView(root)
    }
}
