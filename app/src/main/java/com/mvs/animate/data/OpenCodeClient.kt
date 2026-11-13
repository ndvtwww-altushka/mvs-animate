package com.mvs.animate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * OpenCode HTTP client with support for the current API-prefixed surface and the
 * legacy un-prefixed server API. The current API is detected via GET /api/info.
 */
class OpenCodeClient(
    private val baseUrl: String,
    private val username: String = "",
    private val password: String = "",
) {
    data class ImageAttachment(val fileName: String, val mime: String, val bytes: ByteArray)
    data class AiResponse(val text: String, val sessionId: String, val httpCode: Int)
    data class ServerStatus(val version: String, val api: String)
    data class ModelInfo(
        val id: String,
        val modelID: String,
        val providerID: String,
        val name: String,
        val outputLimit: Long,
        val contextLimit: Long,
        val supportsImages: Boolean,
        val enabled: Boolean = true,
    ) {
        val key: String get() = "$providerID/$id"
        val label: String get() = "$name · $providerID"
    }
    data class ModelCatalog(val models: List<ModelInfo>, val defaultKey: String?)

    private enum class ApiMode { CURRENT, LEGACY }
    @Volatile private var apiMode: ApiMode? = null

    private fun normalizedBaseUrl(): String {
        var normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "OpenCode URL must start with http:// or https://"
        }
        // Accept either the server root or a URL accidentally ending in /api.
        if (normalized.endsWith("/api", ignoreCase = true)) normalized = normalized.dropLast(4)
        return normalized.trimEnd('/')
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): HttpResult {
        val conn = (URL(normalizedBaseUrl() + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MVS-Animate/0.2.3")
            if (body != null) setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (password.isNotBlank()) {
                // OpenCode serve defaults its Basic-auth username to "opencode".
                // Keep that default even when the optional username field is cleared.
                val authUsername = username.ifBlank { "opencode" }
                val token = Base64.getEncoder().encodeToString("$authUsername:$password".toByteArray(Charsets.UTF_8))
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
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun requireSuccess(result: HttpResult, operation: String): String {
        if (result.code !in 200..299) {
            val hint = when (result.code) {
                401 -> "Check Basic-auth username/password in MVS Animate."
                404 -> "The server does not expose this OpenCode API route. Check that Base URL points to the server root."
                502, 503, 504 -> "OpenCode is unavailable or still starting."
                else -> ""
            }
            error(buildString {
                append("$operation failed (HTTP ${result.code})")
                if (result.body.isNotBlank()) append(": ${result.body.take(500)}")
                if (hint.isNotBlank()) append(". $hint")
            })
        }
        return result.body
    }

    /** Detects the server API instead of assuming the pre-v2 /global/health route. */
    suspend fun health(): String = withContext(Dispatchers.IO) {
        val info = request("/api/info")
        if (info.code in 200..299) {
            val json = runCatching { JSONObject(info.body) }.getOrElse {
                error("OpenCode /api/info returned invalid JSON: ${info.body.take(300)}")
            }
            apiMode = ApiMode.CURRENT
            val version = json.optString("version").ifBlank { "unknown version" }
            "OpenCode $version · current API"
        } else if (info.code == 404 || info.code == 405) {
            val legacy = request("/global/health")
            if (legacy.code in 200..299) {
                val json = JSONObject(legacy.body)
                apiMode = ApiMode.LEGACY
                "OpenCode ${json.optString("version", "connected")} · legacy API"
            } else {
                requireSuccess(info, "OpenCode API discovery")
                error("OpenCode API discovery failed: HTTP ${legacy.code} ${legacy.body.take(300)}")
            }
        } else {
            requireSuccess(info, "OpenCode authentication / health check")
            error("Unexpected empty response from OpenCode /api/info")
        }
    }

    /** Load enabled chat models exposed by the connected OpenCode instance. */
    suspend fun listModels(): ModelCatalog = withContext(Dispatchers.IO) {
        if (apiMode == null) health()
        if (apiMode != ApiMode.CURRENT) {
            return@withContext ModelCatalog(emptyList(), null)
        }
        val response = requireSuccess(request("/api/model"), "Load OpenCode models")
        val root = runCatching { JSONObject(response) }.getOrNull()
        val array = root?.optJSONArray("data")
            ?: root?.optJSONArray("models")
            ?: runCatching { JSONArray(response) }.getOrNull()
            ?: JSONArray()
        val models = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (!item.optBoolean("enabled", true)) continue
                val type = item.optString("type")
                if (type.isNotBlank() && type != "chat") continue
                val id = item.optString("id").ifBlank { item.optString("modelID") }
                val modelId = item.optString("modelID").ifBlank { id }
                val providerId = item.optString("providerID")
                if (id.isBlank() || providerId.isBlank()) continue
                val limits = item.optJSONObject("limit") ?: JSONObject()
                val caps = item.optJSONObject("capabilities") ?: JSONObject()
                val inputTypes = caps.optJSONArray("input") ?: JSONArray()
                val supportsImages = (0 until inputTypes.length()).any {
                    inputTypes.optString(it).equals("image", ignoreCase = true) ||
                        inputTypes.optString(it).equals("images", ignoreCase = true)
                }
                add(
                    ModelInfo(
                        id = id,
                        modelID = modelId,
                        providerID = providerId,
                        name = item.optString("name").ifBlank { id },
                        outputLimit = limits.optLong("output", 0L),
                        contextLimit = limits.optLong("context", 0L),
                        supportsImages = supportsImages,
                        enabled = true,
                    )
                )
            }
        }.sortedWith(compareBy<ModelInfo> { it.providerID }.thenBy { it.name.lowercase() })

        val defaultKey = runCatching {
            val defaultBody = requireSuccess(request("/api/model/default"), "Load default OpenCode model")
            val defaultRoot = JSONObject(defaultBody)
            val default = defaultRoot.optJSONObject("data") ?: defaultRoot
            val id = default.optString("id")
            val provider = default.optString("providerID")
            if (id.isNotBlank() && provider.isNotBlank()) "$provider/$id" else null
        }.getOrNull()
        ModelCatalog(models, defaultKey)
    }

    suspend fun generateEditPlan(
        prompt: String,
        agent: String = "",
        images: List<ImageAttachment> = emptyList(),
        selectedModel: ModelInfo? = null,
    ): AiResponse = withContext(Dispatchers.IO) {
        if (apiMode == null) health()
        when (apiMode) {
            ApiMode.CURRENT -> generateCurrentApi(prompt, agent, images, selectedModel)
            ApiMode.LEGACY -> generateLegacyApi(prompt, agent, images)
            null -> error("OpenCode API version was not detected")
        }
    }

    /** OpenCode 2.x /api routes: prompt admission is async, so read messages until idle. */
    private suspend fun generateCurrentApi(
        prompt: String,
        agent: String,
        images: List<ImageAttachment>,
        selectedModel: ModelInfo?,
    ): AiResponse {
        val createBody = JSONObject().put("title", "MVS Animate edit plan")
        if (agent.isNotBlank()) createBody.put("agent", agent)
        val created = requireSuccess(request("/api/session", "POST", createBody), "Create OpenCode session")
        val createJson = JSONObject(created)
        val sessionObject = createJson.optJSONObject("data") ?: createJson
        val sessionId = sessionObject.optString("id").ifBlank { sessionObject.optString("sessionID") }
        require(sessionId.isNotBlank()) { "OpenCode did not return a session id: ${created.take(400)}" }

        if (selectedModel != null) {
            val modelBody = JSONObject().put(
                "model",
                JSONObject().put("id", selectedModel.id).put("providerID", selectedModel.providerID)
            )
            requireSuccess(
                request("/api/session/$sessionId/model", "POST", modelBody),
                "Select OpenCode model ${selectedModel.key}"
            )
        }

        val files = JSONArray()
        images.take(4).forEach { image ->
            require(image.bytes.size <= 5 * 1024 * 1024) { "Image ${image.fileName} is larger than 5 MB" }
            val encoded = Base64.getEncoder().encodeToString(image.bytes)
            // The current HTTP API accepts PromptInput.FileAttachment objects by URI.
            // A data URI keeps the image on-device; raw audio is never attached here.
            files.put(
                JSONObject()
                    .put("uri", "data:${image.mime};base64,$encoded")
                    .put("name", image.fileName)
                    .put("description", "Visual source image for the edit plan")
            )
        }
        val promptBody = JSONObject().put("text", prompt)
        if (files.length() > 0) promptBody.put("files", files)
        val promptResult = request("/api/session/$sessionId/prompt", "POST", promptBody)
        requireSuccess(promptResult, "Queue OpenCode prompt")

        val deadline = System.currentTimeMillis() + 5 * 60_000L
        var lastAssistantText = ""
        var lastAssistantCompleted = false
        var lastIdleOutcome = ""
        var lastHttpCode = promptResult.code
        while (System.currentTimeMillis() < deadline) {
            delay(1_200)
            val messagesResult = request("/api/session/$sessionId/message?limit=100&order=asc")
            val messagesBody = requireSuccess(messagesResult, "Read OpenCode session messages")
            lastHttpCode = messagesResult.code
            val messagesRoot = JSONObject(messagesBody)
            val messageArray = messagesRoot.optJSONArray("data") ?: messagesRoot.optJSONArray("messages") ?: JSONArray()
            val assistantTexts = mutableListOf<String>()
            lastAssistantCompleted = false
            lastIdleOutcome = ""
            for (index in 0 until messageArray.length()) {
                val envelope = messageArray.optJSONObject(index) ?: continue
                // Current projected API returns the message directly. A few compatible builds
                // wrap it as { info, parts }; accept both representations.
                val message = envelope.optJSONObject("info") ?: envelope
                when (message.optString("type")) {
                    "assistant" -> {
                        val content = message.optJSONArray("content") ?: envelope.optJSONArray("parts") ?: JSONArray()
                        val text = buildString {
                            for (partIndex in 0 until content.length()) {
                                val part = content.optJSONObject(partIndex) ?: continue
                                if (part.optString("type") == "text") append(part.optString("text"))
                            }
                        }
                        if (text.isNotBlank()) assistantTexts += text
                        val time = message.optJSONObject("time")
                        // A completed assistant message with finish=tool-calls is not the
                        // final answer: the agent still needs to execute the requested tools.
                        lastAssistantCompleted = time != null && time.has("completed") && !time.isNull("completed") &&
                            message.optString("finish") != "tool-calls"
                        if (message.optString("finish") == "error") {
                            val errorText = message.optJSONObject("error")?.toString().orEmpty()
                            error(formatModelError(errorText.ifBlank { "assistant returned finish=error" }))
                        }
                    }
                    "idle" -> lastIdleOutcome = message.optString("outcome")
                }
            }
            lastAssistantText = assistantTexts.joinToString("\n\n").trim()

            if (lastIdleOutcome == "failed") {
                val idleError = messageArray.run {
                    (0 until length()).asSequence().mapNotNull { optJSONObject(it) }
                        .map { it.optJSONObject("info") ?: it }
                        .firstOrNull { it.optString("type") == "idle" }
                        ?.optJSONObject("error")?.toString()
                }.orEmpty()
                error(if (idleError.isNotBlank()) formatModelError(idleError)
                    else "OpenCode agent run failed. Check the OpenCode server log / model credentials.")
            }
            if (lastIdleOutcome == "interrupted") {
                error("OpenCode run was interrupted before it returned a complete edit plan.")
            }
            // A successful idle transition is authoritative even if this build omits the
            // assistant time.completed field. Never wait five minutes after an empty success.
            if (lastIdleOutcome == "succeeded") {
                if (lastAssistantText.isBlank()) {
                    error("OpenCode finished the run but returned no text. Check the selected model and server log.")
                }
                return AiResponse(lastAssistantText, sessionId, lastHttpCode)
            }
            if (lastAssistantCompleted && lastAssistantText.isNotBlank()) {
                return AiResponse(lastAssistantText, sessionId, lastHttpCode)
            }
        }
        if (lastAssistantText.isNotBlank()) {
            return AiResponse(lastAssistantText, sessionId, lastHttpCode)
        }
        error("Timed out waiting for OpenCode to finish after 5 minutes. Session: $sessionId")
    }

    private fun formatModelError(raw: String): String {
        val flat = raw.replace("\\\"", "\"").replace("\\\\", "\\")
        val quota = Regex("(?i)(?:requested up to )([0-9,]+)(?: tokens)?.{0,120}?(?:can only afford|afford)\\s+([0-9,]+)").find(flat)
        if (flat.contains("provider.quota", ignoreCase = true) || flat.contains("requires more credits", ignoreCase = true)) {
            val requested = quota?.groupValues?.getOrNull(1)?.replace(",", "")
            val affordable = quota?.groupValues?.getOrNull(2)?.replace(",", "")
            val numbers = if (requested != null && affordable != null) " Запросил max_tokens=$requested, доступно около $affordable." else ""
            return "OpenCode подключён, но провайдер отклонил запрос из-за лимита кредитов.$numbers Выбери в Connections модель с меньшим output limit (ориентир — до 4096 токенов) или уменьши limit.output для этой модели в конфиге OpenCode. Это ограничение провайдера, не ошибка подключения."
        }
        return "OpenCode model failed: ${raw.take(1200)}"
    }

    /** Compatibility with older OpenCode servers that expose /global/health and /session routes. */
    private fun generateLegacyApi(
        prompt: String,
        agent: String,
        images: List<ImageAttachment>,
    ): AiResponse {
        val sessionBody = requireSuccess(
            request("/session", "POST", JSONObject().put("title", "MVS Animate edit plan")),
            "Create legacy OpenCode session"
        )
        val session = JSONObject(sessionBody)
        val sessionId = session.optString("id").ifBlank { session.optString("sessionID") }
        require(sessionId.isNotBlank()) { "OpenCode did not return a session id: ${sessionBody.take(300)}" }

        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
        images.take(4).forEach { image ->
            require(image.bytes.size <= 5 * 1024 * 1024) { "Image ${image.fileName} is larger than 5 MB" }
            val encoded = Base64.getEncoder().encodeToString(image.bytes)
            parts.put(JSONObject().put("type", "file").put("mime", image.mime).put("filename", image.fileName)
                .put("url", "data:${image.mime};base64,$encoded"))
        }
        val message = JSONObject().put("parts", parts)
        if (agent.isNotBlank()) message.put("agent", agent)
        val result = request("/session/$sessionId/message", "POST", message)
        val responseBody = requireSuccess(result, "Send legacy OpenCode prompt")
        val root = JSONObject(responseBody)
        val responseParts = root.optJSONArray("parts") ?: JSONArray()
        val output = buildString {
            for (i in 0 until responseParts.length()) {
                val part = responseParts.optJSONObject(i) ?: continue
                if (part.optString("type") == "text") append(part.optString("text"))
            }
        }.ifBlank { root.optString("text", responseBody) }
        return AiResponse(output, sessionId, result.code)
    }
}
