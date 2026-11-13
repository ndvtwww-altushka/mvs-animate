package com.mvs.animate.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

class OpenCodeClientTest {
    private suspend fun withServer(handler: (com.sun.net.httpserver.HttpExchange) -> Unit, test: suspend (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handler(exchange) }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test fun detectsCurrentApiUsingApiInfoAndBasicAuth() = runBlocking {
        val expectedAuth = "Basic " + Base64.getEncoder().encodeToString("opencode:secret".toByteArray())
        val authSeen = AtomicBoolean(false)
        withServer({ exchange ->
            if (exchange.requestURI.path == "/api/info") {
                authSeen.set(exchange.requestHeaders.getFirst("Authorization") == expectedAuth)
                respond(exchange, 200, """{"version":"v2.0.32-android-termux.1","pid":1,"urls":[]}""")
            } else respond(exchange, 404, "{}")
        }) { base ->
            val status = OpenCodeClient(base, "opencode", "secret").health()
            assertTrue(status.contains("v2.0.32-android-termux.1"))
            assertTrue(status.contains("current API"))
            assertTrue(authSeen.get())
        }
    }

    @Test fun currentApiCreatesSessionSubmitsDataUriAndReadsFinishedAssistantMessage() = runBlocking {
        val promptSeen = AtomicBoolean(false)
        val sessionCreated = AtomicBoolean(false)
        withServer({ exchange ->
            when {
                exchange.requestURI.path == "/api/info" ->
                    respond(exchange, 200, """{"version":"v2.0.32-android-termux.1"}""")
                exchange.requestURI.path == "/api/session" && exchange.requestMethod == "POST" -> {
                    val body = exchange.requestBody.bufferedReader().use { it.readText() }
                    sessionCreated.set(JSONObject(body).optString("title").contains("MVS Animate"))
                    respond(exchange, 200, """{"data":{"id":"ses_test"}}""")
                }
                exchange.requestURI.path == "/api/session/ses_test/prompt" && exchange.requestMethod == "POST" -> {
                    val body = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
                    val files = body.optJSONArray("files") ?: org.json.JSONArray()
                    promptSeen.set(body.optString("text") == "Make an edit plan" &&
                        files.length() == 1 && files.getJSONObject(0).optString("uri").startsWith("data:image/png;base64,"))
                    respond(exchange, 200, """{"data":{"id":"msg_user"}}""")
                }
                exchange.requestURI.path == "/api/session/ses_test/message" ->
                    respond(exchange, 200, """{"data":[{"id":"msg_assistant","type":"assistant","time":{"created":1,"completed":2},"content":[{"type":"text","text":"AI motion plan"}],"finish":"stop"}],"cursor":{"previous":null,"next":null}}""")
                else -> respond(exchange, 404, """{"error":"not found"}""")
            }
        }) { base ->
            val client = OpenCodeClient(base)
            client.health()
            val response = client.generateEditPlan(
                "Make an edit plan",
                images = listOf(OpenCodeClient.ImageAttachment("source.png", "image/png", byteArrayOf(1, 2, 3)))
            )
            assertEquals("ses_test", response.sessionId)
            assertEquals("AI motion plan", response.text)
            assertTrue(sessionCreated.get())
            assertTrue(promptSeen.get())
        }
    }


    @Test fun currentApiAcceptsInfoPartsMessageEnvelopeFromCompatibleBuilds() = runBlocking {
        withServer({ exchange ->
            when {
                exchange.requestURI.path == "/api/info" ->
                    respond(exchange, 200, """{"version":"v2.0.32-android-termux.1"}""")
                exchange.requestURI.path == "/api/session" && exchange.requestMethod == "POST" ->
                    respond(exchange, 200, """{"data":{"id":"ses_wrapped"}}""")
                exchange.requestURI.path == "/api/session/ses_wrapped/prompt" ->
                    respond(exchange, 200, """{"data":{"id":"msg_user"}}""")
                exchange.requestURI.path == "/api/session/ses_wrapped/message" ->
                    respond(exchange, 200, """{"data":[{"info":{"id":"msg_assistant","type":"assistant","time":{"created":1,"completed":2},"finish":"stop"},"parts":[{"type":"text","text":"Wrapped response"}]}],"cursor":{"next":null}}""")
                else -> respond(exchange, 404, """{"error":"not found"}""")
            }
        }) { base ->
            val client = OpenCodeClient(base)
            client.health()
            val response = client.generateEditPlan("Test wrapper")
            assertEquals("Wrapped response", response.text)
            assertEquals("ses_wrapped", response.sessionId)
        }
    }

    @Test fun fallsBackToLegacyHealthWhenApiInfoIsMissing() = runBlocking {
        withServer({ exchange ->
            when (exchange.requestURI.path) {
                "/api/info" -> respond(exchange, 404, "{}")
                "/global/health" -> respond(exchange, 200, """{"healthy":true,"version":"1.9.0"}""")
                else -> respond(exchange, 404, "{}")
            }
        }) { base ->
            val status = OpenCodeClient(base).health()
            assertTrue(status.contains("1.9.0"))
            assertTrue(status.contains("legacy API"))
        }
    }
}
