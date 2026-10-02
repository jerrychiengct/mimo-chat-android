package com.jerry.mimochat

import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException

/** SSE framing, including comments, multi-line events and errors after HTTP 200. */
object StreamParser {
    suspend fun read(reader: BufferedReader, onDelta: suspend (String) -> Unit): String {
        val reply = StringBuilder()
        val event = mutableListOf<String>()
        var completed = false
        suspend fun dispatch() {
            if (event.isEmpty()) return
            val data = event.joinToString("\n")
            event.clear()
            if (data == "[DONE]") { completed = true; return }
            val chunk = JSONObject(data)
            chunk.optJSONObject("error")?.let { throw IOException(it.optString("message", "The model stopped unexpectedly.")) }
            val choice = chunk.optJSONArray("choices")?.optJSONObject(0)
            if (choice?.optString("finish_reason") == "error") throw IOException("The model stopped unexpectedly.")
            val delta = choice?.optJSONObject("delta")?.optString("content", "").orEmpty()
            if (delta.isNotEmpty()) { reply.append(delta); onDelta(delta) }
        }
        while (!completed) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> dispatch()
                line.startsWith("data:") -> event += line.substring(5).removePrefix(" ")
                // Comments, event names and heartbeats carry no text.
            }
        }
        if (!completed) dispatch()
        if (!completed) throw IOException("The response was interrupted. Retry to request a complete reply.")
        if (reply.isBlank()) throw IOException("The model returned no text. Try another model.")
        return reply.toString().trim()
    }
}
