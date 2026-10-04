package com.jerry.mimochat

import org.junit.Assert.*
import org.junit.Test

class CompanionPreferencesTest {
    private fun history(id: String) = listOf(ChatMessage(1, id, true, "Can we talk?"))

    @Test fun oldAndInvalidCardsRemainFriendlyWithAutomaticLength() {
        for (raw in listOf("""{"name":"Old"}""",
            """{"romanceStyle":"UNKNOWN","replyLength":"UNKNOWN"}""")) {
            val card = characterFromJson(raw)
            assertEquals(RomanceStyle.OFF, card.romanceStyle)
            assertEquals(ReplyLength.AUTO, card.replyLength)
            assertEquals("", card.preferredEndearment)
        }
    }

    @Test fun preferencesRoundTripWithIdentityAndHistoryScope() {
        val card = CharacterCard(id = "a", romanceStyle = RomanceStyle.PLAYFUL,
            replyLength = ReplyLength.BRIEF, preferredEndearment = "sayang", mode = ChatMode.ROLEPLAY)
        assertEquals(card, characterFromJson(card.toJson(true).toString(), preserveId = true))
        val imported = characterFromJson(card.toJson().toString())
        assertNotEquals(card.id, imported.id)
        assertEquals(card.romanceStyle, imported.romanceStyle)
        assertEquals(card.replyLength, imported.replyLength)
        assertEquals(card.preferredEndearment, imported.preferredEndearment)
        assertEquals(card.mode, imported.mode)
    }

    @Test fun turningOffRomanceKeepsMemoryButRemovesEndearmentInstructions() {
        val card = CharacterCard(id = "a", romanceStyle = RomanceStyle.GENTLE, preferredEndearment = "sayang")
        val facts = listOf(MemoryFact(characterId = "a", mode = ChatMode.COMPANION, text = "I like tea."))
        val on = ConversationContextBuilder.build(card, AppSettings(), history("a"), facts, 8192)
        val off = ConversationContextBuilder.build(card.copy(romanceStyle = RomanceStyle.OFF), AppSettings(), history("a"), facts, 8192)
        assertEquals(on.recent, off.recent)
        assertTrue(on.system.contains("sayang"))
        assertFalse(off.system.contains("sayang"))
        assertTrue(off.system.contains("ROMANCE OFF"))
        assertTrue(off.system.contains("I like tea."))
    }

    @Test fun longerRepliesReserveMoreContextInsteadOfOverfillingModel() {
        val brief = CharacterCard(id = "a", replyLength = ReplyLength.BRIEF)
        val baseline = ConversationContextBuilder.build(brief, AppSettings(), history("a"), emptyList(), 8192)
        val required = baseline.system.toByteArray(Charsets.UTF_8).size + 24 + "Can we talk?".length + 24
        val tightContext = required + 240 + 200 + 100
        assertEquals(240, ConversationContextBuilder.build(brief, AppSettings(), history("a"), emptyList(), tightContext).maxTokens)
        try {
            ConversationContextBuilder.build(brief.copy(replyLength = ReplyLength.DETAILED), AppSettings(), history("a"), emptyList(), tightContext)
            fail("Detailed replies need more reserved output space")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("context budget"))
        }
    }

    @Test fun defaultsStillFitSmallModelAndPartnerPresetsEnableTheirStyle() {
        val card = CharacterCard(id = "a")
        assertEquals(700, ConversationContextBuilder.build(card, AppSettings(), history("a"), emptyList(), 4096).maxTokens)
        val gentle = CompanionPreset.CARING_PARTNER.applyTo(card)
        val playful = CompanionPreset.PLAYFUL_PARTNER.applyTo(card)
        assertEquals(RomanceStyle.GENTLE, gentle.romanceStyle)
        assertEquals(RomanceStyle.PLAYFUL, playful.romanceStyle)
        assertEquals(card.id, gentle.id)
        assertTrue(gentle.exampleDialogue.isNotBlank())
    }
}
