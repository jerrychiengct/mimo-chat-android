package com.jerry.mimochat

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CharacterPersonalizationTest {
    @Test fun resolvesPerCharacterModelAndLegacyDefaultIndependently() {
        val settings = AppSettings(model = "provider/default:free")
        val custom = CharacterCard(modelId = " provider/other:free ")
        val legacy = characterFromJson("""{"id":"legacy","name":"Mimo"}""", preserveId = true)
        assertEquals("provider/other:free", effectiveModelId(custom, settings))
        assertEquals("provider/default:free", effectiveModelId(legacy, settings))
        assertEquals("provider/other:free", effectiveModelId(custom, settings.copy(model = "provider/new")))
        assertEquals("openrouter/free", effectiveModelId(legacy, settings.copy(model = " ")))
    }

    @Test fun personalizedCardRoundTripsAndImportsWithoutPrivatePortrait() {
        val card = CharacterCard(id = "saved", modelId = "provider/model:free", responseStyle = "Reply in relaxed Bahasa Malaysia.",
            portraitFile = "abcd1234.jpg", gender = CharacterGender.FEMALE)
        val local = characterFromJson(card.toJson(true).toString(), preserveId = true)
        assertEquals(card, local)
        val imported = characterFromJson(card.toJson().toString())
        assertNotEquals(card.id, imported.id)
        assertEquals(card.modelId, imported.modelId)
        assertEquals(card.responseStyle, imported.responseStyle)
        assertEquals("", imported.portraitFile)
    }

    @Test fun appliesPresetWithoutChangingIdentityOrChosenEngine() {
        val card = CharacterCard(id = "existing", name = "My character", portraitFile = "1234abcd.jpg",
            modelId = "provider/chosen:free", mode = ChatMode.ROLEPLAY, gender = CharacterGender.MALE)
        for (preset in CompanionPreset.entries) {
            val tuned = preset.applyTo(card)
            assertEquals(card.id, tuned.id)
            assertEquals(card.name, tuned.name)
            assertEquals(card.modelId, tuned.modelId)
            assertEquals(card.mode, tuned.mode)
            assertEquals(card.portraitFile, tuned.portraitFile)
            assertEquals(card.gender, tuned.gender)
            assertEquals(preset.responseStyle, tuned.responseStyle)
            assertEquals(preset.personality, tuned.personality)
        }
    }

    @Test fun engineSwitchRetainsScopedHistoryAndApprovedMemory() {
        val original = CharacterCard(id = "a", modelId = "provider/old", responseStyle = "Reply gently in Malay.")
        val history = listOf(ChatMessage(1, "a", true, "I enjoy coffee."), ChatMessage(2, "a", false, "How do you like it?"),
            ChatMessage(3, "a", true, "Do you remember what I enjoy?"), ChatMessage(4, "other", true, "private"))
        val facts = listOf(MemoryFact(characterId = "a", mode = ChatMode.COMPANION, text = "I enjoy coffee.", sourceMessageId = 1))
        val before = ConversationContextBuilder.build(original, AppSettings(), history, facts, 8192)
        val after = ConversationContextBuilder.build(original.copy(modelId = "provider/new"), AppSettings(), history, facts, 8192)
        assertEquals(before, after)
        assertEquals(listOf(1L, 2L, 3L), after.recent.map { it.id })
        assertTrue(after.system.contains("Response style: Reply gently in Malay."))
        assertTrue(after.system.contains("I enjoy coffee. [source: 1]"))
        assertFalse(after.system.contains("private"))
    }

    @Test fun selectedEngineUsesItsOwnCachedContextLimit() {
        val card = CharacterCard(modelId = "provider/small")
        val cache = JSONObject().put("provider/default", 64000).put("provider/small", 4096)
        assertEquals(4096, ModelContextLimits.resolve(effectiveModelId(card, AppSettings(model = "provider/default")), emptyList(), cache))
    }

    @Test fun oversizedPersonalizationFieldsAreBoundedOnImport() {
        val card = characterFromJson(JSONObject().put("modelId", "x".repeat(1000)).put("responseStyle", "y".repeat(3000)).toString())
        assertEquals(200, card.modelId.length)
        assertEquals(800, card.responseStyle.length)
    }
}
