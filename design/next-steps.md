# Wake — Next Steps

Where we are and what to do before the event. Data pipeline decided; two big risks already killed.

## Validated so far

- [x] Gemma 4 E2B runs offline on the Realme 14 Pro+ (Snapdragon 7s Gen 3 / Adreno 810 — the Qualcomm chip, best OpenCL/GPU story of the three phones).
- [x] Accessibility node-tree dump gives rich text on Chrome, WhatsApp, Gmail. The moat holds.

Two biggest unknowns are gone. Everything left is smaller.

---

## The data pipeline (decided)

Do NOT store raw screenshots as the thing you search. Convert every capture into a small structured text "memory event" at capture time, index the text, discard the pixels (keep a thumbnail only if the image itself matters).

```
capture ─┬─ screenshot         ─→ OCR (ML Kit, on-device) [or Gemma-vision caption] ─→ text
         ├─ accessibility text ─→ already text (node tree)   ← PRIMARY source
         └─ notification        ─→ already text (title/body/sender)
                                       │
                       normalize into ONE event schema
                                       │
                       store in SQLite  +  index in FTS5
                                       │
                       group into sessions (gap-based)
                                       │
   retrieve: FTS5 keyword + time/app/sender filter → top-k events → Gemma → grounded answer
```

One row for everything, so retrieval doesn't care where a memory came from:

```
memory_event(
  id, timestamp,
  source,            -- screenshot | screen_text | notification
  app, sender,
  text,              -- the searchable content
  structured,        -- json: url / code / etc
  thumbnail_path,    -- nullable, only if image kept
  session_id
)
+ FTS5 virtual table over (text, sender, app)
```

Rules:
- **Dedup + throttle at capture, before storage.** Hash each text blob, drop consecutive near-duplicates. Screen content fires constantly.
- **Retrieve on text, never on images.** FTS5 keyword + time/app filters → feed top-k into Gemma. No embeddings for the hackathon.
- **Screenshots are an input you convert to text and mostly throw away**, not the thing you store and search. Node-tree text is primary; screenshots are the event-triggered supplement for what the tree can't see (images, canvas apps).

---

## Remaining pre-event work, in order

### 1. Stage 2 — get Gemma answering from YOUR code (biggest remaining de-risk)

In a throwaway app that is never presented:

- [ ] `adb push` the `.litertlm` model to the Realme.
- [ ] Build the official LiteRT-LM Android sample, run it on the Realme.
- [ ] Learn the 4 calls you will reuse: **load model → select GPU backend (with CPU fallback) → create session → streaming generate.**
- [ ] Feed it a hardcoded string like: `Events: opened Chrome, read about X, WhatsApp from Haneen "send the pdf". Question: what was I just doing?` and confirm it answers grounded in that text.

When this works, the whole pipeline (node-tree text → SQLite → Gemma → grounded answer) is proven except the plumbing, which is the day-of build.

### 2. Two small API tests (~30 min, teammates in parallel)

- [ ] **MessagingStyle:** confirm a real WhatsApp notification yields per-message sender via `extractMessagingStyleFromNotification`. This is "what did X send me" quality.
- [ ] **ACTION_SET_TEXT** into WhatsApp's input box — ONLY if the draft-reply act stays in the demo. If it rejects, note the clipboard + `ACTION_PASTE` fallback.

### 3. Then stop

- [ ] Model files cached on the Realme, Gemma HuggingFace license accepted.
- [ ] One full offline build at home so Gradle has every dependency cached (venue wifi = unusable).
- [ ] **Write zero product code until 10:30 on event day.** Rebuild the integrated app fresh from muscle memory at the event — that is the compliance win. Keep every dry-run app isolated and throwaway.

---

## Day-of build order (recap)

Foundation (repo + Room + FTS5) → notification capture → accessibility capture → **deterministic composer (demo floor, no LLM)** → Gemma grounded answers → agent act → rehearse. Full detail in `plan.md`.
