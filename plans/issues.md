# Wake — Remaining End-to-End Issues

Reviewed against the current working tree on 2026-07-11. Code-backed issues from the previous review have been addressed. The remaining items require external or physical-device verification.

## 1. Verify the Android build and installation

- **Status:** Not verified
- **Required work:** Build the debug APK with the configured Android toolchain, install it on the target phone, and resolve any compilation, resource, packaging, or installation errors.
- **Done when:** The current working tree builds and installs successfully on the target phone.

## 2. Complete the physical-device end-to-end run

- **Status:** Not verified
- **Required work:** Enable notification and accessibility access, capture a known notification and screen, confirm ingestion and embeddings in `wake_debug.jsonl`, ask representative lexical, semantic, sender, app, and temporal questions, and inspect the retrieval trace and cited answers.
- **Done when:** Capture, storage, embeddings, constrained hybrid retrieval, cloud generation, local Gemma generation, citations, and human-readable source labels work without manually modifying the database.

## 3. Confirm cloud credentials and model access

- **Status:** Partially complete
- **Completed:** No default API credential remains in tracked source, and Gemma Cloud uses `gemma-4-26b-a4b-it`.
- **Required work:** Confirm any previously exposed credential has been revoked outside the repository, provide a valid user key, and verify that both configured cloud models accept and stream requests on the physical device.
- **Done when:** The old credential is confirmed revoked and both cloud selections return a grounded streamed answer using a user-provided key.

## 4. Verify local Gemma on the target phone

- **Status:** Implemented but not device-verified
- **Completed:** LiteRT-LM loads the local model, attempts GPU first with CPU fallback, creates a conversation, streams generated output, and releases resources.
- **Required work:** Load `gemma-4-E2B-it.litertlm` on the target phone and verify startup time, backend selection, generation quality, memory use, and airplane-mode answering.
- **Done when:** The physical phone produces a grounded answer in airplane mode without echoing the prompt or using a cloud engine.
