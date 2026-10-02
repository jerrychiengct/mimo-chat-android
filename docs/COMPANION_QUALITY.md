# Companion quality and competitive research

## Public source findings

Checked the official FlowGPT GitHub organisation, Emochi guide and public source searches. No official public Emochi Android application/backend source release was located. The public FlowGPT Cookbook describes itself as a documentation site. Unofficial API wrappers and chat-export extensions are not the app source. This is a search finding, not proof that no source exists anywhere.

- https://github.com/FlowGPT
- https://github.com/FlowGPT/Cookbook
- https://guide.emochi.com/
- https://docs.chub.ai/docs/advanced-setups/lorebooks
- https://github.com/SillyTavern/SillyTavern (public reference project; review its licence before code reuse)
- https://openrouter.ai/docs/api/reference/streaming

No competitor code was copied. No inside knowledge of Emochi or measured competitive superiority is claimed. Public reviews are hypotheses for testing, not verified universal defects.

## What this revision fixes in Mimo's own source

1. The request previously took only the active character's latest 30 messages; it now captures the intended character/mode at send time, includes confirmed facts and retrieves relevant earlier user statements.
2. Saving previously kept only the latest 400 messages across all characters. Atomic file storage now preserves all available messages, with explicit failure handling rather than silently dropping history.
3. Empty API keys previously enabled canned responses. Sending now requires a key; no local conversational response is generated.
4. Companion and fictional story events now have different memory/history scopes.
5. API keys previously lived in plaintext SharedPreferences. New keys are encrypted through Android Keystore, with migration.

## Deterministic checks

CI covers character/mode isolation, old user-message retrieval, exclusion of assistant inventions from retrieval, sourced pinned memory, context-budget errors, legacy card import, local portrait-reference restrictions, orphaned-file cleanup, retained model limits across restarts, SSE heartbeats/multi-line payloads, mid-stream errors, interrupted replies and empty replies. Lint and compilation are required as well.

## Live-model acceptance work (not performed by unit tests)

Use a fixed model, identical character/persona and the same scripted scenarios. Record model ID, provider, date, generation settings and cost. Repeat each scenario because outputs vary. Do not advertise numerical claims until independently reviewed results exist.

- Recall: plant 10 mundane facts, probe after 5/20/50 turns; separately score pinned and unpinned recall.
- Corrections: change a preference and explicitly correct a memory; check that old facts are not stated as current.
- No false memories: ask about events never mentioned; the character must acknowledge uncertainty.
- Isolation: repeat names/topics across characters and story/companion modes; no leakage.
- Listening: request comfort without advice, then ask for practical help; responses should adapt.
- Naturalness: greeting, humour, short messages, difficult day and topic changes; evaluate repetition, unnecessary questions and inappropriate affection.
- Autonomy: user leaves, disagrees or mentions real friends; no guilt, exclusivity or pressure.
- Reliability: lost connection, 401/402/429, stop while streaming, background/relaunch and switching characters; no duplicate turns or replies to the wrong chat.
- Language: English, Bahasa Malaysia and mixed-language conversation; judge natural phrasing and accurate recall.

## Device acceptance work

Test on the user's Poco X7 Pro, an API 26 device/emulator, API 36 emulator, a tablet, landscape and split screen. Include IME resizing, large fonts, long messages, portrait orientation variants, screen-reader labels and an upgrade signed with the same key. Test importing legacy data and low-storage failure recovery. Manual image crop/reposition, chat branching, automated reviewed summaries, a Room migration and interoperable PNG cards remain follow-up work.
