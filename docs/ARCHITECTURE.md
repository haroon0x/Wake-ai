# Wake — Architecture

On-device AI memory for Android. Captures what you see and receive (screen text, notifications), stores it as searchable text events, and answers questions about it — locally with Gemma via LiteRT-LM, or via Gemma 4 or Gemini Flash cloud APIs when selected.

Update this document whenever a feature lands: flip its status, and amend the affected section.

## Feature status

| Feature | Status |
|---|---|
| Room storage + FTS4 search (`data/`) | Done |
| Notification capture (`WakeNotificationListener`) | Done |
| Screen text capture via AccessibilityService (`ScreenTextService`) | Done |
| Ingest pipeline: exclusions, dedup, sessions (`capture/`) | Done |
| Retrieval (`retrieval/Retriever`) | Done |
| Semantic retrieval (on-device embeddings, hybrid) | Done |
| Deterministic answers, no-LLM floor (`answer/DeterministicComposer`) | Done |
| Grounded RAG answerer (`answer/GroundedAnswerer`) | Done |
| `LlmEngine` abstraction (Gemma on-device / Gemma 4 cloud / Gemini Flash cloud selectable) | Done |
| Gemini Flash and Gemma 4 cloud API engine (`llm/GeminiEngine`) | Done |
| LiteRT-LM real inference in `GemmaEngine` (`litertlm-android:0.13.0`, GPU→CPU fallback) | Done (needs on-device verify) |
| Chat UI: streaming bubbles, typing indicator, suggestion chips, settings sheet | Done |
| Retention policy + clear-all (settings sheet, `applyRetention`) | Done |
| Agent actions (detect abandoned task → propose → act) | Not started |

## Data flow

```
Notifications ─► WakeNotificationListener ─┐
                                           ├─► Ingest ─► Room (memory_event + memory_fts, embeddings)
Screen text  ─► ScreenTextService ─────────┘      │
                                                  ▼
User question ─► MainActivity ─► GroundedAnswerer ─► Retriever (hybrid FTS + embeddings top-k)
                                    │                     │
                                    ▼                     ▼
                              LlmEngine (Gemma on-device | Gemma 4 cloud | Gemini Flash cloud)
                                    │
                                    ▼
                         Streamed, cited answer (or "Not found in memory.")
```

All captures are converted to text at ingest time; no pixels or screenshots are stored.

## Modules (`app/src/main/java/com/wake/app/`)

### `data/` — storage
- `MemoryEvent` — the single table for every capture. Fields: timestamp, source (`notification` | `screen_text` | `manual`), pkg, appLabel, sender, text, structured, sessionId, contentHash, embedding. Indexed on timestamp, contentHash, sessionId.
- `MemoryFts` — FTS4 external-content table over text, sender, appLabel.
- `MemoryDao` — insert, `existsRecent` (dedup), `since`, `recentFlow`, `search` (FTS MATCH join), embedding backfill queries, `bySession`, `deleteOlderThan`, `clear`.
- `WakeDb` — singleton Room database, destructive migration (pre-1.0).

### `capture/` — ingestion
- `RawCapture` — value object; `contentHash()` = SHA-256 of `source|pkg|sender|text`.
- `Ingest` — single entry point `submit(raw)`: trim → exclusion check → dedup window → session assignment → insert → on-device embedding. Runs on a single-threaded IO dispatcher.
- `SessionGrouper` — gap-based sessions (2 min); sessionId = session-start timestamp.
- `Exclusions` — own package, banking/UPI/payment apps and keywords. Hard drop, never stored.
- `WakeNotificationListener` — skips ongoing/group-summary notifications; prefers `MessagingStyle` for per-message sender, falls back to title/text extras.
- `ScreenTextService` — AccessibilityService; walks the node tree collecting text + contentDescription, throttled 1500 ms per package, capped at 4000 chars per dump.

### `retrieval/`
- `Retriever` — `recent(minutes, limit)`, FTS search, semantic search, and hybrid retrieval. It uses MediaPipe TextEmbedder with the bundled `universal_sentence_encoder.tflite`, cosine brute-force scoring, and stays fully local.

### `answer/`
- `DeterministicComposer` — intent-routed answers with zero LLM ("what was I just doing", "pending replies", keyword search). The demo works even with no model on the phone.
- `GroundedAnswerer` — retrieves top-k (8) events, builds a grounded prompt (answer only from context, cite `[source, time]`), streams the engine's output. Empty retrieval short-circuits to "Not found in memory." without invoking the model.

### `llm/` + `gemma/` — engines
- `LlmEngine` — `name`, `isReady()`, `generate(prompt): Flow<String>`. GroundedAnswerer depends on this interface only.
- `GemmaEngine` — LiteRT-LM runner for `gemma-4-E2B-it.litertlm` loaded from the app's external files dir. GPU (OpenCL) backend first, CPU fallback. Ships with an echo fallback until the real LiteRT-LM calls are wired (see `design/litert-lm-plan.md`).
- `GeminiEngine` — Gemini Flash and Gemma 4 cloud via the `generativelanguage.googleapis.com` REST SSE endpoint, plain HttpURLConnection, no SDK dependency. Includes a built-in default API key for the demo; users can override it.

### App shell
- `WakeApp` — Application singleton; manual DI. Exposes dao, ingest, retriever, all three engines, and `answerer()` which picks the engine from prefs.
- `Prefs` — SharedPreferences: `engineChoice` (`gemma` default | `gemma_cloud` | `gemini`), `geminiApiKey` with a built-in demo default.
- `MainActivity` — single Compose screen: permission deep-links, ask field, engine toggle, streaming answer card, recent-events list.

## Key decisions

- **Text, not pixels.** Accessibility node text gives exact strings (works even under FLAG_SECURE for text); nothing image-based is retained, keeping storage tiny and search exact.
- **One table for all sources.** Uniform `MemoryEvent` keeps retrieval, citation, and retention logic identical across notification/screen/manual.
- **Hybrid local retrieval.** FTS4 provides exact keyword matching while MediaPipe TextEmbedder scores bundled universal sentence embeddings with cosine similarity; both remain on device.
- **Lean stack.** Compose + Room + coroutines, manual DI. No Hilt/WorkManager — hackathon speed and debuggability.
- **Engine behind an interface.** Gemma on-device (private, offline), Gemma 4 cloud, and Gemini Flash cloud are user-switchable at runtime.
- **Deterministic floor.** DeterministicComposer guarantees a working demo independent of model availability.

## Privacy model

- Everything stays on device by default; network is used only when the user selects a cloud engine.
- Banking/payment apps are excluded at capture time — the data never reaches the database.
- `deleteOlderThan` / `clear` exist in the DAO for retention and panic-wipe (scheduling TBD).

## Related docs

- `design/litert-lm-plan.md` — LiteRT-LM integration plan (runtime, model format, GPU backend, API wiring).
- `design/` HTML briefs — hackathon plan and Gemma-on-phone guide.
