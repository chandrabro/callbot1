# CallBot
Auto-answers incoming calls and plays a TTS message or a prerecorded audio file.

## Get the APK
1. Push this repo to GitHub.
2. Open the **Actions** tab, then **Build APK**, then **Run workflow** (it also runs on every push).
3. Download `CallBot-debug-apk` from the run's Artifacts and install it.
4. To publish a Release with the APK attached: `git tag v1.0 && git push --tags`.

## Notes
- Android doesn't allow apps to inject audio into the call uplink. CallBot enables speakerphone and plays audio loudly so the caller hears it through the mic.
- Grant the Phone permission. On some phones (Xiaomi, Oppo, Samsung) also allow auto-start and disable battery optimization for this app.
