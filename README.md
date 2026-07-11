# Wake

Wake is an on-device AI memory for Android. It captures useful text from notifications and the visible screen, stores it locally, and answers questions about what the user saw or received.

## The problem

Important information is spread across apps, notifications, and screens. Later, people may remember the idea but not the exact words, app, or conversation where it appeared. Wake makes that information private, searchable, and actionable.

## What Wake does

- Captures notification text and visible screen text through Android Accessibility.
- Keeps chats separated with stable app-aware conversation identity and message direction.
- Stores text events locally in Room with timestamps, app, sender, source, and session context.
- Excludes banking and payment apps before data reaches storage.
- Removes exact and near-duplicate captures and groups activity into sessions.
- Retrieves memories with hybrid keyword and semantic search.
- Produces grounded answers with citations using local Gemma or a user-selected cloud model.
- Detects pending replies and proposes reminders, reply drafts, or reopening an app. Actions require user approval.
- Shows captured notifications as live conversation threads that can be queried independently.

Wake stores text, not screenshots or pixels.

## Architecture

```text
Notifications ───────────────┐
                               ├─► Ingest ─► Room + FTS + embeddings
Visible screen text ──────────┘                  │
                                                  ▼
User question ─► Hybrid retrieval ─► Grounded answer ─► Cited response
                    │                    │
                    │                    └─► Gemma on device, Gemma cloud, or Gemini Flash
                    └─► FTS + semantic similarity + time/app/sender filters
```

## Embedding layer

Wake uses a separate local retrieval embedding layer rather than relying on the answer model to search memory.

The app bundles `universal_sentence_encoder.tflite` and runs it with MediaPipe `TextEmbedder`. At ingestion time, each stored text event or screen-text chunk is converted into a vector and saved with the event. When a user asks a question, Wake embeds the question with the same model and compares it with stored vectors using cosine similarity.

This enables semantic retrieval: a memory such as “Let's meet after lunch” can be found by a question such as “What did we decide about meeting later?” even when the wording differs.

Semantic search is deliberately combined with FTS keyword search:

- FTS handles exact words, names, and app-specific terms.
- Embeddings handle similar meaning and paraphrased questions.
- Time, sender, app, source, recency, and diversity rules keep the final context relevant.

The current embedding choice is appropriate for the demo because it runs on device, preserves privacy, works offline, and avoids the cost and latency of a cloud vector service. We should benchmark representative queries on real devices before replacing it; training a custom embedding model is not needed for this stage.

## Stack

- Kotlin, Jetpack Compose, coroutines
- Room and FTS4 for local storage and keyword retrieval
- MediaPipe TextEmbedder with Universal Sentence Encoder TensorFlow Lite for local embeddings
- LiteRT-LM for on-device Gemma
- Optional Gemini Flash and Gemma cloud engines

## Privacy

- Data remains on device by default.
- Network access is used only when a cloud answer engine is selected.
- Banking and payment apps are excluded at capture time.
- Captured memories and embeddings can be removed through retention and clear-all controls.

For the detailed implementation reference, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
