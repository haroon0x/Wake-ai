package com.wake.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiEngine(
    private val apiKeyProvider: () -> String?,
    private val model: String = MODEL
) : LlmEngine {
    override val name = "Gemini"

    override fun isReady(): Boolean = !apiKeyProvider().isNullOrBlank()

    override fun generate(prompt: String): Flow<String> = flow {
        val key = apiKeyProvider()
        if (key.isNullOrBlank()) {
            emit("No Gemini API key set.")
            return@flow
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$key"
            ).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject()
                .put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                ))
                .toString()
            connection.outputStream.bufferedWriter().use { it.write(body) }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.take(200)
                    .orEmpty()
                emit("Gemini error: $responseCode $errorBody")
                return@flow
            }

            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue

                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") continue
                    val text = JSONObject(data)
                        .optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", null)
                    if (text != null) emit(text)
                }
            }
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val MODEL = "gemini-flash-latest"
    }
}
