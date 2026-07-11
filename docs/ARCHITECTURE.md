# Wake — Architecture

On-device AI memory for Android. Captures what you see and receive (screen text, notifications), stores it as searchable text events, and answers questions about it — locally with Gemma via LiteRT-LM, or via Gemma 4 or Gemini Flash cloud APIs when selected.

Update this document whenever a feature lands: flip its status, and amend the affected section.

## Feature status

| Feature | Status |
|---|---|
| Room storage + FTS4 search (`data/`) | Done |
| Notification capture (`WakeNotificationListener`) | Done |
| Stable conversation identity + incoming/outgoing message direction | Done (direction depends on notification metadata) |
| Live conversation-style notification chat | Done |
| Screen text capture via AccessibilityService (`ScreenTextService`) | Done |
| Ingest pipeline: exclusions, normalized/near dedup, screen deltas, chunks, durable app-aware sessions (`capture/`) | Done |
| Retrieval (`retrieval/Retriever`) | Done |
| Semantic retrieval (on-device embeddings, hybrid) | Done |
| Deterministic answers, no-LLM floor (`answer/DeterministicComposer`) | Done |
| Grounded RAG answerer (`answer/GroundedAnswerer`) | Done |
| `LlmEngine` abstraction (Gemma on-device / Gemma 4 cloud / Gemini Flash cloud selectable) | Done |
| Gemini Flash and Gemma 4 cloud API engine (`llm/GeminiEngine`) | Done |
| LiteRT-LM real inference in `GemmaEngine` (`litertlm-android:0.13.0`, GPU→CPU fallback) | Done (needs on-device verify) |
| Chat UI: streaming bubbles, typing indicator, suggestion chips, settings sheet | Done |
| Notification conversations UI: grouped-by-sender threads with in-thread Wake chat (`ui/NotificationChat`) | Done (needs on-device verify) |
| Retention policy + clear-all (settings sheet, `applyRetention`) | Done |
| Debug-only JSONL ingestion/retrieval diagnostics | Done |
| Agent loop: pending-reply triggers → cloud reasoner → approval-gated actions (`agent/`) | Done (needs on-device verify) |
| Agent actions: reminders (AlarmManager), reopen app, draft-to-clipboard | Done (needs on-device verify) |
| Agent activity UI (live feed, trace timeline, approve/dismiss, badge) | Done (needs on-device verify) |
| Bundled API key via `local.properties` → `BuildConfig` (user key overrides) | Done |

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
                         Streamed, cited answer
```

All captures are converted to text at ingest time; no pixels or screenshots are stored.

## Modules (`app/src/main/java/com/wake/app/`)

### `data/` — storage
- `MemoryEvent` — the single table for every capture. Fields: timestamp, source (`notification` | `screen_text` | `manual`), pkg, appLabel, sender, conversationId, direction, text, structured, sessionId, contentHash, embedding. Indexed on timestamp, contentHash, sessionId.
- `MemoryFts` — FTS4 external-content table over text, sender, appLabel.
- `MemoryDao` — insert, `existsRecent` (dedup), `since`, `recentFlow`, `search` (FTS MATCH join), embedding backfill queries, `bySession`, `deleteOlderThan`, `clear`.
- `WakeDb` — singleton Room database, destructive migration (pre-1.0).

### `capture/` — ingestion
- `RawCapture` — value object; `contentHash()` uses normalized text for cross-source exact dedup, retaining package/sender identity for short captures.
- `Ingest` — single entry point `submit(raw)`: trim → exclusion check → exact/near dedup → screen chunking → durable session assignment → insert → on-device embedding. Runs on a single-threaded IO dispatcher.
- `SessionGrouper` — restores the latest stored session after process restart and starts a new session after a 2-minute gap or a significant app switch.
- `Exclusions` — own package, banking/UPI/payment apps and keywords. Hard drop, never stored.
- `WakeNotificationListener` — skips ongoing/group-summary notifications; prefers `MessagingStyle` for per-message sender, derives stable conversation identity from shortcut/title/sender metadata, classifies direction when author metadata permits, falls back to title/text extras, and records notification/conversation context.
- `ScreenTextService` — AccessibilityService; walks the node tree collecting unique text + contentDescription, skips unchanged screens, emits newly added lines for overlapping screens, and records window/URL/focus context. Captures are throttled 1500 ms per package and capped at 4000 chars before chunking.

### `retrieval/`
- `Retriever` — `recent(minutes, limit)`, FTS search, semantic search, and hybrid retrieval. It uses MediaPipe TextEmbedder with the bundled `universal_sentence_encoder.tflite`, cosine brute-force scoring, and stays fully local.

### `answer/`
- `DeterministicComposer` — intent-routed answers with zero LLM ("what was I just doing", "pending replies", keyword search). The demo works even with no model on the phone.
- `GroundedAnswerer` — retrieves top-k (8) events via hybrid search, falling back to recent memory (2 h for temporal questions, 7 days otherwise) when search misses; builds a grounded prompt (answer only from context, cite `[source, time]`, and lead with the closest useful evidence instead of outputting search-failure language) and streams the engine's output. Only a truly empty database short-circuits without a model call.

### `llm/` + `gemma/` — engines
- `LlmEngine` — `name`, `isReady()`, `generate(prompt): Flow<String>`. GroundedAnswerer depends on this interface only.
- `GemmaEngine` — LiteRT-LM runner for `gemma-4-E2B-it.litertlm` loaded from the app's external files dir. GPU (OpenCL) backend first, CPU fallback, with streamed conversation inference and explicit resource cleanup.
- `GeminiEngine` — Gemini Flash and Gemma 4 cloud via the `generativelanguage.googleapis.com` REST SSE endpoint, plain HttpURLConnection, no SDK dependency. Includes a built-in default API key for the demo; users can override it.

### `agent/` — proactive loop
- `AgentEngine` — the sense→decide→act→check loop. Two deterministic trigger types: **pending reply** (a notification with a sender opens a `watching` task, dueAt = +3 min; another message refreshes it; seeing the sender's name in screen text from the same app closes it as handled) and **commitment** (`CommitmentDetector` regexes for times, dates, deadlines, and plan phrases open a task that goes to the reasoner after 1 min; its fallback is `drop`, so only model-confirmed commitments become reminder proposals). A 30 s heartbeat promotes overdue tasks to `thinking` and calls the reasoner, capped at 15 proposals/day and suppressed per sender after 2 dismissals in 24 h. Actions run only on user approval.
- `AgentReasoner` — one cloud model call per fired trigger (Gemma cloud, or Gemini when selected). Strict JSON contract `{action, title, draft, reminder_minutes, confidence}`; one repair reprompt on parse failure, then a deterministic open-app fallback. `drop` or confidence < 0.3 closes the task silently.
- `ActionExecutor` — typed capabilities: copy draft to clipboard + reopen the app (`reply`), AlarmManager reminder via `ReminderReceiver` (`remind`), launch app (`open_app`). Posts proposal notifications on the `wake_agent` channel that deep-link into the agent sheet.
- `AgentTask` (Room, DB v3) — persistent agent state: `watching → thinking → proposed → done/dismissed/expired` plus a JSON trace timeline rendered in the UI.

Agent visibility: the top bar shows an agent icon with a proposed-count badge opening `AgentSheet` — a live feed with a status pill per task (pulsing while watching/thinking), the trace timeline, draft preview, and approve/dismiss buttons. Pending-reply tasks retain conversation identity, use conversation-scoped evidence, and close when an outgoing notification message is captured.

The notification icon opens a live conversation sheet grouped by stable conversation identity rather than sender alone. Incoming and outgoing captured messages render on opposite sides, and questions asked inside a thread are grounded only in that conversation's events.

### App shell
- `WakeApp` — Application singleton; manual DI. Exposes dao, ingest, retriever, all three engines, and `answerer()` which picks the engine from prefs.
- `Prefs` — SharedPreferences: `engineChoice` (`gemma` default | `gemma_cloud` | `gemini`), `geminiApiKey` with a built-in demo default.
- `MainActivity` — single Compose screen: permission deep-links, ask field, engine toggle, streaming answer card, recent-events list.
- `Diagnostics` — debug-build-only JSONL trace at `files/wake_debug.jsonl`, rotated at 5 MB, covering ingestion decisions, stored events, retrieval candidates, and final chat answers.

Pull the current trace from a connected debug device with `adb exec-out run-as com.wake.app cat files/wake_debug.jsonl > wake_debug.jsonl`. The previous rotated trace is available as `files/wake_debug.previous.jsonl`.

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
