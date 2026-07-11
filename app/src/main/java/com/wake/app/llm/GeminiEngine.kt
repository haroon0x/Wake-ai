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

class GeminiEngine(
    private val apiKeyProvider: () -> String?,
    private val model: String = MODEL,
    override val name: String = "Gemini"
) : LlmEngine {
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
                throw LlmException(apiError(responseCode, errorBody))
            }

            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue

                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") continue
                    val payload = runCatching { JSONObject(data) }.getOrElse {
                        throw LlmException("The model returned an unreadable response.", it)
                    }
                    val text = payload
                        .optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", null)
                    if (text != null) emit(text)
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

    companion object {
        const val MODEL = "gemini-flash-latest"
        const val GEMMA_CLOUD_MODEL = "gemma-4-26b-a4b-it"

        private fun apiError(code: Int, body: String): String = when (code) {
            400 -> "The model request was rejected. Check the selected model."
            401, 403 -> "The API key is invalid or does not have access to this model."
            404 -> "The selected model is unavailable."
            429 -> "The model rate limit was reached. Try again shortly."
            in 500..599 -> "The model service is temporarily unavailable."
            else -> body.takeIf { it.isNotBlank() } ?: "The model request failed with HTTP $code."
        }
    }
}
