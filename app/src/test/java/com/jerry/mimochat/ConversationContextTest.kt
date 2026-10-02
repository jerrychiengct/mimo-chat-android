package com.jerry.mimochat

import org.junit.Assert.*
import org.junit.Test

class ConversationContextTest {
    private val character = CharacterCard(id = "a", name = "Mimo")
    private fun msg(id: Long, text: String, user: Boolean = true, who: String = "a", mode: ChatMode = ChatMode.COMPANION) =
        ChatMessage(id, who, user, text, mode)

    @Test fun isolatesCharacterAndMode() {
        val result = ConversationContextBuilder.build(character, AppSettings(), listOf(
            msg(1, "private other character", who = "b"), msg(2, "fictional marriage", mode = ChatMode.ROLEPLAY), msg(3, "Hello")),
            listOf(MemoryFact(characterId = "b", mode = ChatMode.COMPANION, text = "secret"),
                MemoryFact(characterId = "a", mode = ChatMode.ROLEPLAY, text = "story secret")), 8192)
        assertEquals(listOf(3L), result.recent.map { it.id })
        assertFalse(result.system.contains("secret"))
        assertFalse(result.system.contains("fictional marriage"))
    }

    @Test fun retrievesOlderUserStatementWithoutAssistantFabrications() {
        val history = mutableListOf(msg(1, "My favourite instrument is a guitar."), msg(2, "Your guitar is blue.", user = false))
        repeat(40) { history += msg(3L + it, "Everyday conversation ${"other things ".repeat(20)}", user = it % 2 == 0) }
        history += msg(50, "Do you recall my favourite instrument?")
        val result = ConversationContextBuilder.build(character, AppSettings(), history, emptyList(), 8192)
        assertTrue(result.system.contains("My favourite instrument is a guitar."))
        assertFalse(result.system.contains("Your guitar is blue."))
        assertEquals(50L, result.recent.last().id)
    }

    @Test fun includesConfirmedMemoryWithProvenance() {
        val result = ConversationContextBuilder.build(character, AppSettings(), listOf(msg(3, "What do I enjoy?")),
            listOf(MemoryFact(characterId = "a", mode = ChatMode.COMPANION, text = "I enjoy hiking.", sourceMessageId = 1)), 8192)
        assertTrue(result.system.contains("I enjoy hiking. [source: 1]"))
        assertTrue(result.system.contains("corrections in recent messages win"))
    }

    @Test fun rejectsOversizeMessageRatherThanSilentlyLosingIt() {
        try {
            ConversationContextBuilder.build(character, AppSettings(), listOf(msg(1, "x".repeat(20000))), emptyList(), 4096)
            fail("Expected an actionable budget error")
        } catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("context budget")) }
    }

    @Test fun importsLegacyCardsPreservingLocalIdentity() {
        val old = """{"id":"old","name":"Nova","accessory":"GLASSES"}"""
        assertEquals("old", characterFromJson(old, preserveId = true).id)
        assertEquals(ChatMode.COMPANION, characterFromJson(old).mode)
        assertNotEquals("old", characterFromJson(old).id)
    }

    @Test fun portraitReferencesCannotEscapeLocalDirectoryOrBeImported() {
        val json = """{"name":"Nova","portraitFile":"../../secret.jpg"}"""
        assertEquals("", characterFromJson(json, preserveId = true).portraitFile)
        val local = character.copy(portraitFile = "1234abcd.jpg")
        assertFalse(local.toJson().has("portraitFile"))
        assertEquals("", characterFromJson(local.toJson(true).toString()).portraitFile)
    }
}
