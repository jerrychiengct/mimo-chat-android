package com.jerry.mimochat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class StreamParserTest {
    @Test fun handlesHeartbeatMultilineUsageAndDone() = runBlocking {
        val input = """: OPENROUTER PROCESSING

data: {"choices":[{"delta":{"content":"Hi "}}]}

data: {"choices":[
data: {"delta":{"content":"there"}}]}

data: {"choices":[],"usage":{"total_tokens":4}}

data: [DONE]

"""
        val deltas = mutableListOf<String>()
        assertEquals("Hi there", StreamParser.read(input.reader().buffered()) { deltas += it })
        assertEquals(listOf("Hi ", "there"), deltas)
    }

    @Test fun rejectsMidstreamErrorAfterPartialText() = runBlocking {
        val input = "data: {\"choices\":[{\"delta\":{\"content\":\"Partial\"}}]}\n\ndata: {\"error\":{\"message\":\"Provider disconnected\"}}\n\n"
        try { StreamParser.read(input.reader().buffered()) {}; fail("Expected stream error") }
        catch (expected: IOException) { assertEquals("Provider disconnected", expected.message) }
    }

    @Test fun interruptedStreamIsNotACompleteAssistantMessage() = runBlocking {
        val input = "data: {\"choices\":[{\"delta\":{\"content\":\"Partial\"}}]}\n\n"
        try { StreamParser.read(input.reader().buffered()) {}; fail("Expected interrupted stream") }
        catch (expected: IOException) { assertTrue(expected.message!!.contains("interrupted")) }
    }

    @Test fun emptyResponseIsAnError() = runBlocking {
        try { StreamParser.read("data: [DONE]\n\n".reader().buffered()) {}; fail("Expected empty response error") }
        catch (expected: IOException) { assertTrue(expected.message!!.contains("no text")) }
    }
}
