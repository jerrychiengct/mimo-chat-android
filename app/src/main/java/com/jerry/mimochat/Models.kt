package com.jerry.mimochat

import org.json.JSONObject
import java.util.UUID

enum class ChatMode { COMPANION, ROLEPLAY }
enum class CharacterGender { FEMALE, MALE, UNSPECIFIED }
enum class CharacterSpecies { HUMAN, ALIEN, OTHER }
enum class PortraitStyle { REALISTIC, ANIME, OTHER }

data class CharacterCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New character",
    val tagline: String = "A new voice in your pocket",
    val personality: String = "Warm, curious and concise.",
    val scenario: String = "A casual, helpful conversation.",
    val greeting: String = "",
    val exampleDialogue: String = "",
    val primaryColour: Long = 0xFF7255F5,
    val matureTopics: Boolean = false,
    val portraitFile: String = "",
    val gender: CharacterGender = CharacterGender.UNSPECIFIED,
    val species: CharacterSpecies = CharacterSpecies.HUMAN,
    val portraitStyle: PortraitStyle = PortraitStyle.REALISTIC,
    val age: Int = 25,
    val mode: ChatMode = ChatMode.COMPANION
)

data class ChatMessage(
    val id: Long,
    val characterId: String,
    val fromUser: Boolean,
    val text: String,
    val mode: ChatMode = ChatMode.COMPANION
)

data class MemoryFact(
    val id: String = UUID.randomUUID().toString(),
    val characterId: String,
    val mode: ChatMode,
    val text: String,
    val sourceMessageId: Long? = null
)

data class AppSettings(
    val userName: String = "You",
    val darkMode: Boolean = false,
    val allowMildProfanity: Boolean = false,
    val apiKey: String = "",
    val model: String = "openrouter/free",
    val userPersona: String = ""
)

data class OpenRouterModel(
    val id: String,
    val name: String,
    val contextLength: Int,
    val promptPrice: String,
    val completionPrice: String
) {
    val isFree: Boolean
        get() = promptPrice.toDoubleOrNull() == 0.0 && completionPrice.toDoubleOrNull() == 0.0
}

inline fun <reified T : Enum<T>> enumValue(raw: String, fallback: T): T =
    runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)

fun CharacterCard.toJson(includeLocalPortrait: Boolean = false): JSONObject = JSONObject()
    .put("spec", "mimo-character-card-v2")
    .put("id", id).put("name", name).put("tagline", tagline)
    .put("personality", personality).put("scenario", scenario)
    .put("greeting", greeting).put("exampleDialogue", exampleDialogue)
    .put("primaryColour", primaryColour).put("matureTopics", matureTopics)
    .put("gender", gender.name).put("species", species.name)
    .put("portraitStyle", portraitStyle.name).put("age", age).put("mode", mode.name)
    .also { if (includeLocalPortrait) it.put("portraitFile", portraitFile) }

fun characterFromJson(raw: String, preserveId: Boolean = false): CharacterCard {
    val json = JSONObject(raw)
    return CharacterCard(
        id = if (preserveId) json.optString("id", UUID.randomUUID().toString()) else UUID.randomUUID().toString(),
        name = json.optString("name", "Imported character").take(40),
        tagline = json.optString("tagline", "Imported character").take(100),
        personality = json.optString("personality", "Warm and helpful.").take(1200),
        scenario = json.optString("scenario", "A casual conversation.").take(1000),
        greeting = json.optString("greeting", "").take(500),
        exampleDialogue = json.optString("exampleDialogue", "").take(1600),
        primaryColour = json.optLong("primaryColour", 0xFF7255F5),
        matureTopics = json.optBoolean("matureTopics", false),
        portraitFile = if (preserveId) json.optString("portraitFile", "").takeIf {
            it.matches(Regex("[a-f0-9-]+\\.jpg"))
        }.orEmpty() else "",
        gender = enumValue(json.optString("gender"), CharacterGender.UNSPECIFIED),
        species = enumValue(json.optString("species"), CharacterSpecies.HUMAN),
        portraitStyle = enumValue(json.optString("portraitStyle"), PortraitStyle.REALISTIC),
        age = json.optInt("age", 25).coerceIn(18, 999),
        mode = enumValue(json.optString("mode"), ChatMode.COMPANION)
    )
}
