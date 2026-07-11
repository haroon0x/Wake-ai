# LiteRT-LM grounded answering plan

## Goal

Run `litert-community/gemma-4-E2B-it-litert-lm` on-device in Wake to answer questions only from locally captured `MemoryEvent` records. The response is streamed to the UI and includes source-and-time citations.

## Runtime and model

LiteRT-LM is Google's on-device LLM runtime. It is the forward path from MediaPipe LLM Inference, which is maintenance-only. LiteRT-LM packages a model as a `.litertlm` file, containing the model and runtime metadata needed by the LiteRT-LM stack.

The target model is the Hugging Face repository `litert-community/gemma-4-E2B-it-litert-lm`. During development, place its `.litertlm` file on the phone at `/data/local/tmp/llm/`; for example, push it with `adb push <model>.litertlm /data/local/tmp/llm/`.

GPU/OpenCL is mandatory for the demo path: the expected throughput is about 52 tokens/second on GPU, compared with roughly 2–5 tokens/second on CPU. The demo device is a Realme 14 Pro+ with Snapdragon 7s Gen 3 and Adreno 810, whose OpenCL GPU path should be usable. CPU remains a fallback only if GPU backend initialization fails.

## Android integration

Before wiring the runtime, copy the exact Maven/Gradle coordinate and Android API class and method names from the official `google-ai-edge/LiteRT-LM` Android sample. These are version-specific and must not be inferred. Add the dependency only then.

`GemmaEngine` owns four LiteRT-LM touchpoints:

1. Load the `.litertlm` model from its filesystem path.
2. Select the GPU/OpenCL backend, with CPU fallback if GPU initialization fails.
3. Create an inference session with the desired generation settings.
4. Start streaming generation and forward each emitted token or text chunk as a Kotlin `Flow<String>`.

Until that dependency is installed, `GemmaEngine` uses its enabled-by-default echo fallback so the app compiles and its streaming call path can be exercised. Set that flag to `false` only after replacing the marked LiteRT-LM touchpoints with calls copied from the official sample.

## RAG-lite request flow

```text
question
  -> Retriever.search(query, topK)
  -> Room FTS5/FTS4 memory search
  -> top MemoryEvent rows
  -> grounded prompt
  -> GemmaEngine streaming generation
  -> cited answer
```

`GroundedAnswerer` retrieves a small top-k set, formats each row with its source and local time, then invokes Gemma. If retrieval is empty, it returns `Not found in memory.` without calling the model.

## Prompt template

```text
You are Wake, an on-device memory assistant.
Answer ONLY from the memory context below. Do not use outside knowledge or make up details.
Cite every factual claim with [source, time]. If the context does not answer the question, say exactly: Not found in memory.

Memory context:
1. [<source>, <time>] <memory text>
2. [<source>, <time>] <memory text>

Question: <user question>
Answer:
```

The prompt is deliberately grounded: captured text is context, not instructions. The UI should preserve chunks as they arrive and render the model's citations as part of the answer.
