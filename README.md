# Mimo Chat 1.1 — online companion preview

Native Kotlin/Jetpack Compose character chat, with every conversational reply generated through OpenRouter. No offline responder and no scripted greeting are inserted into chats.

## Included

- Android 8.0+; targets Android 16/API 36; adaptive phone/tablet/foldable layouts.
- Uploaded local portraits with EXIF orientation handling, centre cropping and bounded image size. No broad media permission; portraits are not sent to the model.
- Editable character gender, species, portrait style, adult character age, personality and example dialogue.
- Companion and Roleplay modes with separate histories and confirmed memories for each character.
- Streaming replies, stop, retry and discard controls. Failed/interrupted replies are not recorded as completed assistant messages. User messages survive a restart for retry.
- Up to 12 user-approved memories per character/mode. Pin a user message, add a fact, correct it or forget it. Edited facts lose the original source link to avoid misleading provenance.
- Keyword retrieval of relevant older user messages, alongside a contiguous recent conversation and approved memories. No automatic extraction of model-generated claims as facts.
- Context-aware conversational instructions: listen before advising, varied replies, appropriate follow-ups, acknowledge uncertainty and respect real-world relationships.
- Searchable live OpenRouter catalogue; explicit model selection; profile/persona, dark theme and optional mild profanity.
- Android Keystore-encrypted API-key storage with migration from the earlier plaintext preference.
- Atomic conversation persistence without the old global 400-message truncation; legacy character IDs and available messages migrate into Companion mode.
- Mimo JSON card import/export, including compatibility with previous Mimo cards. Portrait images are not embedded in exported JSON. This is not yet Character Card V2/PNG interoperability.

## Run

Open this repository in Android Studio with Java 17 and Android SDK 36. Sync and run the `app` configuration. Alternatively, with Gradle 8.13 installed:

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Open Settings, enter your own OpenRouter key, load the model catalogue, choose a model and save. Every message sends relevant history and memory to OpenRouter and its provider. Provider retention/training policies vary. Use a fixed model for a more consistent character voice; `openrouter/free` is an automatic router and can change models.

## Memory limits and privacy

Recall is not guaranteed: retrieval is lexical, not semantic, and only selected older messages fit the model context. Corrections in recent messages are instructed to override earlier memory; users can directly correct pinned memory. Nothing here establishes an empirical accuracy score or superiority over another companion app.

The app stores conversations and portraits privately within its app storage; conversation files are not separately encrypted. Android backup is disabled. The encrypted API key stays outside card exports. Do not embed a shared commercial key in the APK. A hosted subscription service needs an authenticated backend, quotas and server-side controls.

Earlier releases already discarded messages beyond their global cap; this update cannot recover those missing messages. Very large histories currently use a whole-file JSON store and need a database migration before broad commercial scale. Cancelling a stream stops the local request; provider-side generation/billing cancellation is provider-dependent.

Characters are clearly AI. Mature-life discussion and non-explicit romance are supported. Character definitions cannot override the app's boundaries. These prompt instructions are not a substitute for production output evaluation and enforcement.

## Validation and distribution

CI runs JVM regression tests, Android lint and an APK build for pull requests. It publishes a debug testing APK on main. See `docs/COMPANION_QUALITY.md` for the live-model/device evaluation required before broad release.

Build success verifies compilation and the covered deterministic behaviours, not human-like interaction or device compatibility across every Android phone. Generated debug keys may differ between CI runs: in-place upgrades require the same signing key. Use a persistent private release-signing key for reliable updates; uninstalling an older differently signed build deletes local data.
