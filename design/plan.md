# Wake — Hackathon Plan

Google DeepMind Bangalore Hackathon. Target: **Special Prize — Best Use of Gemma 4 (Local-First Agents on Gemma, $2000)**, with Best Overall as secondary shot.

One line: **Wake is a fully local AI memory-and-recovery agent for Android — it remembers what you saw, notices what you abandoned, and acts to get you back on track, entirely on-device.**

---

## 1. Why this framing wins

Judging: Creativity 35% / Live Demo 25% / India Impact 25% / Technical Depth 15%.

- **Creativity**: Recall/Rewind exist on desktop; nothing like this exists on Android phones. And Wake doesn't just *search* memory — it *acts* on it. That's the never-seen-before angle.
- **Gemma prize bar**: "If you can draw your agent as a single straight arrow from input to output, it isn't an agent yet." Pure capture→retrieve→answer fails this. Wake ships the full loop: **sense** (screen/notification capture) → **decide** (Gemma detects abandoned task / distraction) → **act** (restores context, drafts follow-up) → **check** (shows plan before acting, admits capture gaps, defers to human when unsure).
- **India impact**: spotty connectivity, high data costs, privacy-sensitive users — on-device is not a gimmick here, it's the only viable architecture. Airplane-mode demo makes this visceral.
- **Live demo**: phone on stage, airplane mode ON, real WhatsApp notifications, real browsing, then "what was I just doing?" answered with exact strings. Deterministic composer guarantees the demo works even if the LLM misbehaves.

## 2. Hackathon rules — compliance

- **New Work Only / repo public**: create a FRESH public repo at the event. Do not copy code from the existing `wake` repo. The spec document is thinking, not code — reusing ideas is fine; reusing pre-written code is disqualification territory. Every commit timestamped during the event; commit every 30–45 minutes so the git history itself proves day-of work. Do not open the old `wake` repo during the event.
- **Demo must show only event-built work**: everything in the demo (capture, retrieval, Gemma answering, the act step) is built day-of. Pre-event work is limited to *learning* (dry-running Gemma on the phone in a throwaway app that is never presented) and downloading model weights.
- **Anti-project list**: Wake is not a chatbot, not RAG-over-documents, not a dashboard. Keep the timeline screen secondary — the primary surface is ask → grounded answer → agent action. Never describe it as "RAG" in the pitch.

## 3. What Wake is NOT building at the hackathon

Cut everything the full spec needs for production but a sideloaded demo doesn't:

- Encryption at rest, Play disclosure screens, redaction, retention settings, excluded-apps UI (hardcode a small exclusion list)
- Event-triggered screenshots + OCR
- Markdown summary layer, embeddings, daily recap
- OEM background-kill hardening
- Manual save / share sheet

## 4. Architecture (event build)

```
NotificationListenerService ──┐
AccessibilityService          ├─→ throttle/dedup → Room (SQLite + FTS5) → sessions
(node-tree screen text)     ──┘                                   │
UsageStatsManager (live query at answer time) ────────────────────┤
                                                                  ▼
                                    query router (heuristics)
                                    ├─ structured → deterministic composer (SQL + templates)
                                    └─ open       → FTS5 retrieval → Gemma (LiteRT-LM,
                                                    on-device, GPU) → grounded answer
                                                                  │
                                    agent loop (the prize-winner) ▼
                                    periodic: Gemma scans recent sessions →
                                    detects abandoned task / unanswered message →
                                    proposes action → user confirms → acts
                                    (reopen app+link via intent, prefill reply draft)
```

Stack: Kotlin, Jetpack Compose, Room + FTS5, NotificationListenerService, AccessibilityService, UsageStatsManager (AppOpsManager for the permission check — `queryUsageStats()` fails silently), **LiteRT-LM** running **Gemma 4 E2B** on the **GPU/OpenCL backend**.

> Verified constraints (research 2026-07-10):
> - Use **LiteRT-LM**, not MediaPipe LLM Inference — Google now recommends it for on-device LLM. `litert-community/gemma-4-E2B-it-litert-lm` is published on HuggingFace (the exact model the hackathon names). Download before the event.
> - **GPU backend is mandatory**: E2B ≈52 tok/s on GPU vs only 2–5 tok/s on CPU (mid-range). CPU inference is demo-death. Flagship gives 10–25 tok/s comfortably.
> - MessagingStyle: use `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification()` — hand-parsing `android.text` yields collapsed "5 new messages".
> - Node tree is rich for Chrome (URL bar + page text via WebContentsAccessibility), WhatsApp (named IDs like `conversation_contact_name`), Gmail. **Flutter/canvas apps expose only sparse Semantics** — pick demo apps that expose well; use Instagram only as the distraction (detect app-switch, don't read it).
> - FLAG_SECURE does NOT block accessibility text reading (only screenshots). Banking-app text is technically readable — but keep those apps EXCLUDED by design; that is the trust story, not a limitation.
> - `ACTION_SET_TEXT` works on standard fields; custom inputs reject it → fallback clipboard + `ACTION_PASTE`.
> - Prior art: desktop is crowded (Screenpipe YC/18k stars; ScreenMind = Gemma 4 E2B local clone). No direct Android consumer equivalent found → Android is the open lane. Capture itself is commodity (`fogies/android-accessibility-capture`); differentiation must be the agent act + on-device answering.

Key spec carry-overs (ideas, rewritten fresh):
- Sender extraction from `Notification.MessagingStyle` extras — biggest answer-quality lever for "what did X send me?"
- Skip group summaries and `FLAG_ONGOING_EVENT`; merge repeated notifications
- Screen-text capture on window/content-change events, throttled, hash-deduped
- Session grouping: gap-based (new session after idle gap or significant app switch)
- Grounded answers only; cite source + time; say "not found" plainly

## 5. Team split (3 people)

- **Person A — Capture**: Room schema + FTS5 (first 45 min, schema agreed by all three before splitting), NotificationListenerService with MessagingStyle sender extraction, AccessibilityService capture with throttle/dedup, session grouping. Strongest Android-internals dev takes this.
- **Person B — AI**: deterministic answer composer first (demo floor by ~1pm with zero LLM), then Gemma via LiteRT-LM on GPU (must be the person who did the pre-event dry run), query router heuristics, grounded answering.
- **Person C — UI + agent + demo**: Compose ask screen, session timeline (kept secondary), agent loop with B (detect abandoned task → propose → act via intents), demo script, 1-minute video, submission form, rehearsal. Floater when others are blocked.

Sync points: schema freeze 11:15 · capture→composer integration 1:45 · full-pipeline test 3:30 · feature freeze 4:15 · two full rehearsals before 5:00.

## 6. Hour-by-hour (10:30 start, 5:00 submit)

| Time | Work | Ships |
|------|------|-------|
| 10:30–11:15 | Fresh repo, project skeleton, Room schema (raw_events + sender + session_id), FTS5 table, permissions plumbing | Foundation |
| 11:15–12:00 | NotificationListenerService: MessagingStyle sender extraction, dedup, ongoing-filter | Notification memory |
| 12:00–1:15 | AccessibilityService node-tree capture: throttle, dedup, hardcoded exclusions; session grouping | The moat |
| 1:15–1:45 | Lunch — while eating: write demo script, test capture on real usage | |
| 1:45–2:30 | Deterministic answer composer: "what was I just doing", "what did [sender] send", "before I opened [app]" | **Demo floor secured — works with zero LLM** |
| 2:30–3:45 | Gemma via LiteRT-LM on GPU (pre-learned, model already on device): retrieval → grounded answering, query router heuristics | Open questions |
| 3:45–4:30 | Agent loop: scan recent sessions, detect abandoned task / unanswered message, propose + act (intent to reopen app/URL, draft reply text) | Prize-winning loop |
| 4:30–5:00 | Minimal ask UI polish, record 1-min video, submit, rehearse | Submission |

Triage rule: if running behind at 3:45, cut the agent loop to ONE hardcoded-flow action ("resume what I abandoned" → reopen last non-distraction session's app + URL) rather than general detection. One reliable act beats three flaky ones.

## 7. Demo script (~3 min)

1. Airplane mode ON, shown to judges. "No servers exist. Watch the status bar the whole demo."
2. Live: receive WhatsApp message (from teammate's phone — works offline? NO — do this BEFORE airplane mode, capture is already stored). Browse Chrome to a page with a visible code/ID. Open Gmail. Get "distracted" into Instagram.
3. Ask: "what was I just doing?" → timeline answer, exact.
4. Ask: "what was the registration code I saw?" → character-perfect string. Punchline: "node tree, not OCR — it can't be wrong."
5. Wake proactively: "You opened Instagram mid-task. Resume?" → tap → Chrome reopens on the exact page, pending WhatsApp reply surfaced with a draft.
6. Close: "Everything you saw ran on this phone in airplane mode. 750M Indians on spotty networks don't need a data center to have memory."

Q&A prep: FLAG_SECURE limitation (banking apps — honest answer: excluded by design), battery (node tree ≪ screenshots), why not iOS (no equivalent APIs — Android-native is the moat).

## 8. Risks

1. **Gemma integration eats the day** → killed by pre-event dry run (see prep). Deterministic composer is the in-demo fallback.
2. **CPU inference by mistake** → E2B on CPU is 2–5 tok/s (unusable). Confirm GPU/OpenCL backend is active in the dry run.
3. **Accessibility event flood** → throttle window + hash dedup budgeted 75 min, known patterns in advance.
4. **Venue wifi** → model weights cached on phone before event; zero downloads needed day-of.
5. **LLM hallucination on stage** → structured demo questions route to deterministic composer; only one open question in script.
6. **Phone dies / breaks** → backup phone if available; APK also on teammate device.

## 9. Unvalidated assumptions — test BEFORE the event, in priority order

Research (2026-07-10) resolved several — remaining live tests, priority order:

1. **Gemma 4 E2B on the actual demo phone, GPU backend, via LiteRT-LM** — measure tok/s and load time. This is now the #1 risk (CPU fallback is unusable). Stage 1/2 dry runs.
2. **Node-tree text quality per app** — `adb shell uiautomator dump` on Chrome / WhatsApp / Gmail confirms rich text (research says yes; verify on your device build). Skip Instagram as a *read* target — use it only as the distraction.
3. **WhatsApp MessagingStyle via `extractMessagingStyleFromNotification()`** — confirm real per-message sender/text on your WhatsApp version.
4. **`ACTION_SET_TEXT` into WhatsApp input** — confirm; fallback clipboard + `ACTION_PASTE`.

Resolved by research (no test needed):
- FLAG_SECURE does NOT block accessibility text → moat not blocked (but keep banking excluded by design).
- Node tree carries resource-id + text + bounds; Chrome/WhatsApp/Gmail expose well.

## 10. Pre-event prep checklist (learning + assets only — no project code)

- [ ] Throwaway test app: **LiteRT-LM** running **Gemma 4 E2B on the GPU backend** on the actual demo phone. Measure tokens/sec, load time, RAM headroom. NEVER shown or reused at the event.
- [ ] `litert-community/gemma-4-E2B-it-litert-lm` (and E4B) model files downloaded to the phone (HuggingFace Gemma license accepted).
- [ ] Confirm phone: ≥8GB RAM, GPU/OpenCL inference works, developer mode + USB debugging on, cable, laptop adb working.
- [ ] `adb shell uiautomator dump` on Chrome / WhatsApp / Gmail — eyeball that text nodes are rich on your device build.
- [ ] Skim once more: `extractMessagingStyleFromNotification()`, AccessibilityService event types, FTS5-in-Room setup.
- [ ] Team roles decided (capture / AI / UI+demo).
