# Taut phone test checklist

1. Install `app/build/outputs/apk/debug/app-debug.apk`, launch in portrait, and confirm the black/white home screen shows Free Play, Constellation Path, and the daily streak.
2. Start Free Play. Tap at the pulse/peg crossing and verify PERFECT, GOOD, and MISS each preserve the existing score/combo/life rules. Confirm the pulse remains at least 8 dp from either edge.
3. Finish a landmark at Easy, then verify its Medium button unlocks; finish Medium and verify Hard unlocks. Confirm the next landmark's Easy remains locked until the prior Easy is complete. Repeat all three difficulties: Easy has a wider window and 3 frays, Medium is baseline and 3 frays, Hard is faster, narrower, and has 2 frays.
4. Open Settings. Move input delay through -100, 0, and +100 ms and use the test area. Confirm the timing changes are bounded and scoring is unchanged. Toggle vibration and sound independently.
5. On Easy, verify the next peg's swing-range guide is faint and does not identify the tap instant. Trigger PERFECT, GOOD, and miss feedback and confirm flash/shake/haptic behavior is under 250 ms.
6. Complete a landmark. Wait for the full reveal, hold for one second, then use Share and confirm Android's share sheet opens with the rendered picture. Use Next to return to level select.
7. Enable airplane mode and repeat Free Play and a previously available landmark. The game must remain playable; ads may be unavailable without blocking play.
8. On a fresh install, verify consent is requested before ads initialize. In test mode, after a miss confirm the rewarded offer appears at most once per Free Play run and once per landmark stroke. Complete landmarks and verify interstitials never appear during a stroke and are rate-limited.
9. Change Android animation scale to 0 and repeat navigation and gameplay; the game must remain responsive with animations disabled.
10. Verify the daily landmark is stable for the same local date and the streak increments only after completing that day's Medium landmark.
11. Repeat the test on a 360x800 device/emulator. Check every landmark peg pair visually for at least 28 dp separation and confirm no dot leaves the 8 dp margin.

Release verification requires a user-supplied `keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; no keystore is included in this repository.
