# LiteRT-LM wiring

The echo fallback remains enabled because the Android GPU configuration required by the LiteRT-LM reference requires manifest changes outside this task. Apply the following after verifying the current Google AI Edge Gallery Android sample and adding its required native-library declarations.

## Gradle

Add this to `app/build.gradle.kts`:

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
```

## Classes

Use `com.google.ai.edge.litertlm.Engine`, `EngineConfig`, `Backend`, and `Conversation`.

## GemmaEngine

Replace the non-fallback branch of `load` with this CPU baseline, then replace `Backend.CPU()` with the Gallery sample's verified GPU configuration and retain a CPU retry if GPU initialization fails:

```kotlin
val loadedEngine = Engine(
    EngineConfig(
        modelPath = modelPath,
        backend = Backend.CPU()
    )
)
loadedEngine.initialize()
engine?.close()
engine = loadedEngine
ready = true
```

Store the engine as `private var engine: Engine? = null`. Replace the non-fallback branch of `generate` with:

```kotlin
val loadedEngine = engine ?: return@flow
loadedEngine.createConversation().use { conversation ->
    conversation.sendMessageAsync(prompt).collect { message ->
        emit(message.text)
    }
}
```

The official Kotlin API documents `Engine(EngineConfig(...))`, `engine.initialize()`, `engine.createConversation()`, and `conversation.sendMessageAsync(prompt): Flow<Message>`.
