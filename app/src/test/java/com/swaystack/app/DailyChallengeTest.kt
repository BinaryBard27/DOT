package com.swaystack.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyChallengeTest {
    @Test fun `same day selects the same landmark at medium`() {
        assertEquals(DailyChallenge.mediumLevel(1234L, 13), DailyChallenge.mediumLevel(1234L, 13))
        assertEquals(2, DailyChallenge.mediumLevel(0L, 13))
        assertEquals(38, DailyChallenge.mediumLevel(12L, 13))
    }

    @Test fun `streak increments consecutively and resets after a missed day`() {
        assertEquals(1, DailyProgress.nextStreak(null, 0, 10))
        assertEquals(2, DailyProgress.nextStreak(10, 1, 11))
        assertEquals(2, DailyProgress.nextStreak(11, 2, 11))
        assertEquals(1, DailyProgress.nextStreak(11, 2, 13))
    }
}
