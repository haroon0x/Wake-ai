package com.wake.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class AntigravityEngine(
    private val apiKeyProvider: () -> String?,
    private val model: String = "gemini-3.5-flash",
    override val name: String = "Antigravity Cloud"
) : LlmEngine {

    private var lastInteractionId: String? = null

    fun clearSession() {
        lastInteractionId = null
    }

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
                "https://generativelanguage.googleapis.com/v1beta/interactions?key=$key"
            ).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", key)
            }

            val body = JSONObject().apply {
                put("model", model)
                put("input", prompt)
                put("stream", true)
                put("tools", JSONArray().apply {
                    put(JSONObject().put("code_execution", JSONObject()))
                    put(JSONObject().put("google_search", JSONObject()))
                })
                lastInteractionId?.let { put("previous_interaction_id", it) }
            }

            val requestBody = body.toString()
            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.take(200)
                    .orEmpty()
                throw LlmException(apiError(responseCode, errorBody))
            }

            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue

                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") continue
                    val payload = runCatching { JSONObject(data) }.getOrElse {
                        continue
                    }

                    val eventType = payload.optString("event_type")
                    if (eventType == "interaction.created") {
                        val id = payload.optString("id").ifBlank {
                            payload.optJSONObject("interaction")?.optString("id")
                        }.orEmpty()
                        if (id.isNotBlank()) {
                            lastInteractionId = id
                        }
                    }

                    if (eventType == "step.delta") {
                        val delta = payload.optJSONObject("delta")
                        if (delta != null && delta.optString("type") == "text") {
                            val text = delta.optString("text")
                            if (!text.isNullOrEmpty()) {
                                emit(text)
                            }
                        }
                    }
                }
            }
        } catch (e: LlmException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw LlmException("The model request timed out. Check your connection and try again.", e)
        } catch (e: Exception) {
            throw LlmException("Could not reach the model. Check your connection and API key.", e)
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun apiError(code: Int, body: String): String = when (code) {
        400 -> "The model request was rejected. Check the selected model."
        401, 403 -> "The API key is invalid or does not have access to this model."
        404 -> "The selected model is unavailable."
        429 -> "The model rate limit was reached. Try again shortly."
        in 500..599 -> "The model service is temporarily unavailable."
        else -> body.takeIf { it.isNotBlank() } ?: "The model request failed with HTTP $code."
    }
}
