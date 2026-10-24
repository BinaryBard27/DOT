package com.swaystack.app

import com.swaystack.app.game.GameConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test fun `input delay is bounded to plus or minus one hundred milliseconds`() {
        assertEquals(GameConstants.INPUT_OFFSET_MIN_MS, (-500).coerceIn(-100, 100))
        assertEquals(GameConstants.INPUT_OFFSET_MAX_MS, 500.coerceIn(-100, 100))
        assertTrue(BuildConfig.PRIVACY_POLICY_URL.isNotBlank())
    }
}
