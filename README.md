# Mimo Chat

A customisable native Android character-chat app with OpenRouter model selection and procedural pixel avatars.

## Platform support

- Android 8.0 (API 26) and newer
- Targets Android 16 (API 36)
- Edge-to-edge system UI
- Responsive phone layouts in portrait and landscape
- Navigation rail and centred content on tablets, foldables and wide windows
- Adaptive multi-column character library
- Resizable and multi-window compatible

## Included

- Kotlin + Jetpack Compose UI
- Multiple editable character cards with separate conversations
- Character name, tagline, personality, scenario, greeting and example dialogue
- Portable JSON character-card import and export
- Procedural 11×16-inspired pixel character rig
- Idle, talking, walking, waving, jumping, happy, sad, confused and sleepy animations
- Custom outfit, face and accent colours
- Cap, cat-ear, antenna and glasses accessories
- Optional mature-topic tone for non-explicit adult-life discussions
- Optional mild profanity tolerance
- Dark mode
- Persistent local characters, settings and conversations
- Offline demo responses
- OpenRouter chat-completions integration
- Live searchable OpenRouter model catalogue with free-model badges
- HTTPS-only network policy and no hard-coded credentials

## Open in Android Studio

1. Open the `MimoChat` folder.
2. Let Android Studio install/sync the requested Android SDK and Gradle components.
3. Run the `app` configuration on an Android 8.0+ emulator or device.

The repository also includes a GitHub Actions build. Every push to `main` runs Android lint, builds an installable APK, generates a SHA-256 checksum and attaches both files to the `v1.0.0` GitHub release.

## Connect OpenRouter

The project deliberately contains no API key. Open **Settings**, then:

1. Enter an OpenRouter development/testing API key.
2. Tap **Load model catalogue**.
3. Search and select any currently available model.
4. Save the settings.

Without a key, the app remains usable in offline demo mode.

## Character cards

Open **Characters → New character**. The editor includes a live animation preview for every supported state. The copy button exports the current card as `mimo-character-card-v1` JSON; the import button in the character library accepts that JSON and assigns it a new local ID.

## Production notes

Do not distribute an APK that sends a permanent provider key directly from the phone. For production, put the OpenRouter call behind your own authenticated server, then add per-user rate limits, moderation, privacy terms and account deletion.

Character instructions cannot override the local master safety instruction. Server-side safety controls should remain authoritative.
