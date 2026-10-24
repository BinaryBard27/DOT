package com.swaystack.app

import android.content.Context
import com.swaystack.app.game.GameConstants

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("TautPrefs", Context.MODE_PRIVATE)

    var inputDelayOffsetMs: Int
        get() = prefs.getInt("input_delay_offset_ms", 0).coerceIn(GameConstants.INPUT_OFFSET_MIN_MS, GameConstants.INPUT_OFFSET_MAX_MS)
        set(value) = prefs.edit().putInt("input_delay_offset_ms", value.coerceIn(GameConstants.INPUT_OFFSET_MIN_MS, GameConstants.INPUT_OFFSET_MAX_MS)).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(value) = prefs.edit().putBoolean("vibration_enabled", value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_enabled", value).apply()
}
