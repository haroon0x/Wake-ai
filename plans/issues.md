# Wake — End-to-End Issues

Reviewed against the current working tree on 2026-07-11. These are the remaining issues preventing Wake from being a verified end-to-end application.

## 1. Establish a verified Android build

- **Status:** Not verified
- **Impact:** The resource-linking error has been corrected, but the complete application has not been built successfully from the current working tree.
- **Evidence:** `app/src/main/res/xml/accessibility_service_config.xml` now references `@string/accessibility_service_description`, which exists in `app/src/main/res/values/strings.xml`.
- **Required work:** Run a clean debug build in Android Studio or with `./gradlew assembleDebug` using JDK 17 and resolve any remaining compilation or resource errors.
- **Done when:** The debug APK builds and installs on the target phone.

## 2. Correct the Gemma Cloud model identifier

- **Status:** Incorrect configuration
- **Impact:** Selecting Gemma Cloud will likely return a model-not-found API error.
- **Evidence:** `app/src/main/java/com/wake/app/llm/GeminiEngine.kt` uses `gemma-4-e2b-it`. The hosted Gemma 4 API supports model identifiers such as `gemma-4-26b-a4b-it` and `gemma-4-31b-it`.
- **Required work:** Replace the invalid model identifier with a supported hosted Gemma model and confirm that the selected endpoint supports the existing streaming request.
- **Done when:** Gemma Cloud returns and streams a valid answer on the physical device.

## 3. Remove and rotate the committed cloud API key

- **Status:** Security issue
- **Impact:** A key embedded in source code and an APK cannot be kept secret and could be abused by anyone who obtains it.
- **Evidence:** `app/src/main/java/com/wake/app/Prefs.kt` contains a default API credential.
- **Required work:** Remove the default key, rotate the exposed key, require user-provided credentials for the current demo, and ensure documentation does not claim a built-in key exists.
- **Done when:** No API credential is stored in tracked source code and the old key has been revoked.

## 4. Handle model and network failures in the UI

- **Status:** Incomplete
- **Impact:** Timeouts, connection failures, and malformed API responses can terminate the request without showing a useful message.
- **Evidence:** `app/src/main/java/com/wake/app/MainActivity.kt` resets the busy state in `finally` but does not catch generation failures. `GeminiEngine.kt` only converts non-2xx HTTP responses into output text.
- **Required work:** Represent loading, streamed output, success, and failure explicitly. Catch request failures and display a concise retryable error.
- **Done when:** Offline mode, an invalid key, an invalid model, timeout, and successful generation all produce understandable UI states.

## 5. Verify capture-to-answer flow on a physical device

- **Status:** Not demonstrated
- **Impact:** Individual pipeline components exist, but the product has not been proven as one working flow.
- **Required work:** Enable notification and accessibility access, capture one known notification and one known screen, confirm both are stored, ask a question answered by those captures, and verify the model response includes the expected source and time.
- **Done when:** The following flow succeeds without manually modifying the database:

```text
notification or screen
→ ingestion
→ Room and FTS
→ retrieval
→ grounded prompt
→ cloud model
→ streamed cited answer
```

## 6. Improve permission and readiness states

- **Status:** Incomplete
- **Impact:** The UI links to Android settings but does not tell the user whether notification or accessibility access is currently enabled. Cloud engines are displayed as ready based on selection rather than a validated key or successful request.
- **Evidence:** `app/src/main/java/com/wake/app/MainActivity.kt` shows settings buttons and derives cloud readiness directly from the selected engine.
- **Required work:** Detect permission state when the activity resumes, show enabled or disabled status, validate that a cloud key is present, and prevent empty or impossible requests.
- **Done when:** Users can see exactly what must be enabled before asking a question.

## 7. Tighten retrieval fallback behavior

- **Status:** Functionally weak
- **Impact:** When keyword retrieval finds nothing, the latest two hours of events are sent to the model even when unrelated to the question. This can produce irrelevant answers and wastes model context.
- **Evidence:** `app/src/main/java/com/wake/app/answer/GroundedAnswerer.kt` falls back from search results to recent events unconditionally.
- **Required work:** Preserve recent-memory handling for temporal questions while returning `Not found in memory.` for unrelated failed searches. Keep FTS retrieval for the first E2E milestone; semantic retrieval can follow later.
- **Done when:** Known memories are retrieved, temporal questions use recent context, and unrelated questions do not receive arbitrary recent events.

## 8. Replace the on-device Gemma echo scaffold with real inference

- **Status:** Not implemented
- **Impact:** The app labels the engine as on-device Gemma, but it only echoes the grounded prompt and performs no model inference.
- **Evidence:** `app/src/main/java/com/wake/app/gemma/GemmaEngine.kt` enables its fallback by default and emits `LiteRT-LM fallback` text.
- **Required work:** Verify the current official LiteRT-LM Android sample and dependency, add required Android/native configuration, load the model, establish CPU generation, add supported GPU acceleration with CPU fallback, stream generated text, and release model resources safely.
- **Done when:** Airplane mode is enabled and the physical phone produces a grounded answer generated by the local model rather than echoed input.

## 9. Add retention and clear-memory controls

- **Status:** Backend capability exists; product flow missing
- **Impact:** Captured personal text accumulates indefinitely and users cannot erase it from the UI.
- **Evidence:** `MemoryDao.deleteOlderThan` and `MemoryDao.clear` exist, but neither is scheduled or exposed in the application UI.
- **Required work:** Add a clear-memory action and one simple retention preference. Avoid adding background scheduling until the selected retention behavior requires it.
- **Done when:** Users can erase all memory and the selected retention rule is enforced.

## Recommended execution order

1. Verify the Android build.
2. Correct the cloud model identifier.
3. Remove and rotate the exposed key.
4. Add cloud error and readiness handling.
5. Complete the physical-device cloud E2E run.
6. Improve permission state and retrieval behavior.
7. Integrate real on-device LiteRT-LM inference.
8. Add retention and clear-memory controls.

The cloud E2E milestone should be completed before LiteRT-LM integration. It verifies capture, storage, retrieval, grounding, model orchestration, and UI streaming independently of the more difficult on-device runtime work.
