package com.swaystack.app

import android.content.SharedPreferences
import java.util.Calendar
import java.util.TimeZone

object DailyChallenge {
    fun localDayIndex(nowMillis: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMillis
        val offset = calendar.timeZone.getOffset(nowMillis)
        return Math.floorDiv(nowMillis + offset, 86_400_000L)
    }

    fun landmarkIndex(dayIndex: Long, landmarkCount: Int): Int = Math.floorMod(dayIndex, landmarkCount.toLong()).toInt()
    fun mediumLevel(dayIndex: Long, landmarkCount: Int): Int = landmarkIndex(dayIndex, landmarkCount) * 3 + 2
}

object DailyProgress {
    fun nextStreak(previousDay: Long?, previousStreak: Int, completedDay: Long): Int = when {
        previousDay == completedDay -> previousStreak
        previousDay != null && completedDay == previousDay + 1L -> previousStreak + 1
        else -> 1
    }

    fun currentStreak(prefs: SharedPreferences): Int = prefs.getInt("daily_streak", 0)

    fun recordCompletion(prefs: SharedPreferences, completedDay: Long) {
        val previousDay = if (prefs.contains("daily_last_completed_day")) prefs.getLong("daily_last_completed_day", completedDay) else null
        val next = nextStreak(previousDay, currentStreak(prefs), completedDay)
        prefs.edit().putLong("daily_last_completed_day", completedDay).putInt("daily_streak", next).apply()
    }
}
