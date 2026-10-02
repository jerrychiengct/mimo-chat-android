# Mimo companion experience — v1.2 preview

Mimo is a private, fully online character-chat app. The user chooses a ready-made personality or creates their own, then assigns an OpenRouter model. A character carries its own history and editable memories; the engine can change without replacing that identity.

## Research and product decisions

Reviewed official public pages on 2 October 2026; this is a product-reference review, not a hands-on audit of Emochi+ subscriptions or proprietary code.

| Reference | Publicly documented pattern | Mimo decision |
| --- | --- | --- |
| [Emochi creation guide](https://guide.flowgpt.com/howto/emochi) | Quick and Expert character creation | Start with a personality preset, then edit details |
| [Emochi quick mode](https://guide.flowgpt.com/howto/emochi/quick-mode) | Refine name, personality and conversation style | Keep character identity and speaking style separately editable |
| [FlowGPT Emochi guide](https://flowgpt.com/guide/emochi) | Character catalogues, roleplay and custom personalities | Focus first on a small personal companion library |
| [OpenRouter limits](https://openrouter.ai/docs/api_reference/limits) | Free models have request caps | Free-only catalogue filter; never promise unlimited replies |
| [OpenRouter models](https://openrouter.ai/docs/guides/overview/models) | Model identities, context lengths and pricing | Display actual model metadata rather than invented capability scores |

Opportunity hypothesis: a focused companion experience with transparent, correctable memory can make Mimo distinctive. The reviewed sources do not prove that competitors have worse recall or dialogue.

## Visual system implemented

- Muted violet accent, warm neutral canvas, restrained surfaces and readable native typography.
- Chat-first bottom navigation on phones; navigation rail on larger screens.
- Companion library with visible selection and per-bot model identity.
- A personal introduction and optional draft starters on empty conversations. Tapping a starter fills the composer and does not send a message.
- AI identity appears in the chat header. No green online indicator suggesting a human presence or a verified API connection.
- Adaptive launcher icon: a white conversation bubble with a rounded lower-case m; monochrome variant for themed Android icons. Original vector artwork, no copied competitor assets.

## Bot tuning implemented

Four editable presets: attentive companion, caring partner, playful partner and storyteller. A preset changes personality, speaking style and scenario after confirmation; identity, portrait, mode and model assignment remain intact.

Each bot can follow the global default model or hold a fixed OpenRouter model. Existing cards inherit the old default automatically. New fields survive local persistence and Mimo JSON import/export. The request uses the chosen bot model and that model's context limit.

Conversation instructions prioritise emotional specifics, proportionate responses, matching language and pace, varied follow-ups and honest uncertainty. They discourage repetitive AI disclaimers while requiring honest AI identity when asked. Fictional partner roleplay is permitted within the existing non-explicit scope; real-world activities, shared memories and offline presence must not be invented as facts.

Memory is user-confirmed, editable and scoped to character/mode. This preview does not silently learn durable facts, train model weights, offer perfect recall or claim to pass a Turing test.

## Acceptance and next priorities

CI must pass JVM tests, lint and APK compilation. Manual device review remains necessary for keyboard insets, small screens, large font, TalkBack, night mode, adaptive icon masks, image imports and long conversations. Live OpenRouter evaluation is required before making dialogue-quality claims.

Next technical priorities: user-confirmed memory suggestions, temporal facts and correction conflicts; structured conversation summaries with source references; conversation export and durable database storage. Never promote assistant guesses into memories.

Evaluate candidate free models with identical scenarios: Malay/English switching, loneliness, silence, a corrected name, a remembered preference after 50 turns, relationship boundaries, fictional scene continuity, model switching and API rate limits. Record correctness, character consistency, repetition, latency and cost. Human raters should know they are evaluating AI; do not fabricate radar scores.
