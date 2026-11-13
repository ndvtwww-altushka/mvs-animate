package com.mvs.animate.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class OpenCodeClient(
    private val baseUrl: String,
    private val username: String = "",
    private val password: String = "",
) {
    data class ImageAttachment(val fileName: String, val mime: String, val bytes: ByteArray)
    data class AiResponse(val text: String, val sessionId: String, val httpCode: Int)

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): Pair<Int, String> {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "OpenCode URL must start with http:// or https://"
        }
        val conn = (URL(normalized + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 120_000
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (username.isNotBlank() || password.isNotBlank()) {
                val token = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $token")
            }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }

    suspend fun health(): String = withContext(Dispatchers.IO) {
        val (code, body) = request("/global/health")
        if (code !in 200..299) error("OpenCode health failed ($code): ${body.take(300)}")
        val json = JSONObject(body)
        "OpenCode ${json.optString("version", "connected")}" 
    }

    suspend fun generateEditPlan(
        prompt: String,
        agent: String = "",
        images: List<ImageAttachment> = emptyList(),
    ): AiResponse = withContext(Dispatchers.IO) {
        val (_, sessionBody) = request("/session", "POST", JSONObject().put("title", "MVS Animate edit plan"))
        val session = JSONObject(sessionBody)
        val sessionId = session.optString("id").ifBlank { session.optString("sessionID") }
        require(sessionId.isNotBlank()) { "OpenCode did not return a session id: ${sessionBody.take(300)}" }

        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
        images.take(4).forEach { image ->
            require(image.bytes.size <= 5 * 1024 * 1024) { "Image ${image.fileName} is larger than 5 MB" }
            val encoded = Base64.encodeToString(image.bytes, Base64.NO_WRAP)
            parts.put(
                JSONObject()
                    .put("type", "file")
                    .put("mime", image.mime)
                    .put("filename", image.fileName)
                    .put("url", "data:${image.mime};base64,$encoded")
            )
        }
        val message = JSONObject().put("parts", parts)
        if (agent.isNotBlank()) message.put("agent", agent)
        val (code, responseBody) = request("/session/$sessionId/message", "POST", message)
        if (code !in 200..299) error("OpenCode request failed ($code): ${responseBody.take(600)}")
        val root = JSONObject(responseBody)
        val responseParts = root.optJSONArray("parts") ?: JSONArray()
        val output = buildString {
            for (i in 0 until responseParts.length()) {
                val part = responseParts.optJSONObject(i) ?: continue
                if (part.optString("type") == "text") append(part.optString("text"))
            }
        }.ifBlank { root.optString("text", responseBody) }
        AiResponse(output, sessionId, code)
    }
}
