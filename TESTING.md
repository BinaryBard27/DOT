# Taut phone test checklist

1. Install the debug APK from `app/build/outputs/apk/debug/app-debug.apk`. Confirm portrait orientation and black/white rendering.
2. On the home screen, open Free Play, Constellation Path, Daily Challenge, and Settings. Settings is a separate screen opened by the Settings button.
3. In Settings, move the input-delay slider to -100, 0, and +100 ms. Confirm the displayed value persists after leaving and reopening Settings. Toggle Sound and Vibration and confirm each setting persists.
4. Tap the Privacy Policy button and confirm Android opens the URL configured by `BuildConfig.PRIVACY_POLICY_URL`.
5. In Free Play, play levels 1–50 and verify the pulse never crosses the 8 dp screen margin. Verify PERFECT, GOOD, and MISS scoring/lives behavior manually.
6. In Constellation Path, play the 39 landmark tiers: each landmark has Easy, Medium, and Hard behavior. Easy is slower/wider with 3 frays; Medium is baseline with 3 frays; Hard is faster/narrower with 2 frays.
7. Confirm the daily challenge selects one landmark at Medium for the local date. Repeat on the same date to confirm the same landmark. Complete it and confirm the home streak increases; skip a day and confirm the next completion resets the streak to 1.
8. Complete a landmark and wait until the reveal finishes. Tap Share and confirm Android's share sheet opens with a PNG rendered from the revealed landmark. Tap Next separately.
9. Test in airplane mode. Gameplay must remain available if consent, ads, or network services fail.
10. On a 360x800 device/emulator, verify the 28 dp peg-spacing condition and 8 dp motion margin. The unit test is `LandmarkTest` and the motion tests are `MotionBoundsTest`.

Not currently covered by the shipped UI: a scrollable level-select screen, rewarded-ad offer flow, interstitial display flow, animation-scale detection, and real-device FPS measurement. These require separate implementation or device verification.

Release verification requires a user-supplied `keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; no keystore is included in this repository.
