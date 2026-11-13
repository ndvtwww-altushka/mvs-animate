package com.mvs.animate.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The client is exercised against a real socket. `com.sun.net.httpserver` cannot be used
 * here: Android unit tests compile against android.jar, which has no jdk.httpserver, so a
 * plain ServerSocket serves the requests instead.
 */
class OpenCodeClientTest {

    private class Request(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private class Reply(val code: Int, val body: String)

    private suspend fun withServer(handler: (Request) -> Reply, test: suspend (String) -> Unit) {
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket -> serve(socket, handler) }
                } catch (_: Exception) {
                    return@Thread
                }
            }
        }
        worker.isDaemon = true
        worker.start()
        try {
            test("http://127.0.0.1:${server.localPort}")
        } finally {
            server.close()
        }
    }

    private fun serve(socket: Socket, handler: (Request) -> Reply) {
        val input = socket.getInputStream().reader(Charsets.UTF_8)
        val requestLine = input.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = StringBuilder()
        val buffer = CharArray(1024)
        while (body.length < length) {
            val read = input.read(buffer, 0, minOf(buffer.size, length - body.length))
            if (read <= 0) break
            body.append(buffer, 0, read)
        }
        val reply = handler(Request(parts[0], parts[1], headers, body.toString()))
        val bytes = reply.body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 ${reply.code} X\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().let { out ->
            out.write(head.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }

    @Test fun detectsCurrentApiUsingApiInfoAndBasicAuth() = runBlocking {
        val expectedAuth = "Basic " + Base64.getEncoder().encodeToString("opencode:secret".toByteArray())
        val authSeen = AtomicBoolean(false)
        withServer({ request ->
            if (request.path == "/api/info") {
                authSeen.set(request.headers["authorization"] == expectedAuth)
                Reply(200, """{"version":"v2.0.32-android-termux.1","pid":1,"urls":[]}""")
            } else Reply(404, "{}")
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
        withServer({ request ->
            when {
                request.path == "/api/info" ->
                    Reply(200, """{"version":"v2.0.32-android-termux.1"}""")

                request.path == "/api/session" && request.method == "POST" -> {
                    sessionCreated.set(JSONObject(request.body).optString("title").contains("MVS Animate"))
                    Reply(200, """{"data":{"id":"ses_test"}}""")
                }

                request.path == "/api/session/ses_test/prompt" && request.method == "POST" -> {
                    val body = JSONObject(request.body)
                    val files = body.optJSONArray("files") ?: JSONArray()
                    promptSeen.set(
                        body.optString("text") == "Make an edit plan" &&
                            files.length() == 1 &&
                            files.getJSONObject(0).optString("uri").startsWith("data:image/png;base64,")
                    )
                    Reply(200, """{"data":{"id":"msg_user"}}""")
                }

                request.path == "/api/session/ses_test/message" ->
                    Reply(
                        200,
                        """{"data":[{"id":"msg_assistant","type":"assistant","time":{"created":1,"completed":2},"content":[{"type":"text","text":"AI motion plan"}],"finish":"stop"}],"cursor":{"previous":null,"next":null}}"""
                    )

                else -> Reply(404, """{"error":"not found"}""")
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
        withServer({ request ->
            when {
                request.path == "/api/info" ->
                    Reply(200, """{"version":"v2.0.32-android-termux.1"}""")

                request.path == "/api/session" && request.method == "POST" ->
                    Reply(200, """{"data":{"id":"ses_wrapped"}}""")

                request.path == "/api/session/ses_wrapped/prompt" ->
                    Reply(200, """{"data":{"id":"msg_user"}}""")

                request.path == "/api/session/ses_wrapped/message" ->
                    Reply(
                        200,
                        """{"data":[{"info":{"id":"msg_assistant","type":"assistant","time":{"created":1,"completed":2},"finish":"stop"},"parts":[{"type":"text","text":"Wrapped response"}]}],"cursor":{"next":null}}"""
                    )

                else -> Reply(404, """{"error":"not found"}""")
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
        withServer({ request ->
            when (request.path) {
                "/api/info" -> Reply(404, "{}")
                "/global/health" -> Reply(200, """{"healthy":true,"version":"1.9.0"}""")
                else -> Reply(404, "{}")
            }
        }) { base ->
            val status = OpenCodeClient(base).health()
            assertTrue(status.contains("1.9.0"))
            assertTrue(status.contains("legacy API"))
        }
    }
}
