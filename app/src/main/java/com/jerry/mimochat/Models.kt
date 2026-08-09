package com.jerry.mimochat

import org.json.JSONObject
import java.util.UUID

enum class AvatarAccessory { NONE, CAP, CAT_EARS, ANTENNA, GLASSES }

enum class AvatarMotion { IDLE, TALK, WALK, WAVE, JUMP, HAPPY, SAD, CONFUSED, SLEEPY }

data class CharacterCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New character",
    val tagline: String = "A new voice in your pocket",
    val personality: String = "Warm, curious and concise.",
    val scenario: String = "A casual, helpful conversation.",
    val greeting: String = "Hello! What shall we talk about?",
    val exampleDialogue: String = "",
    val primaryColour: Long = 0xFF7255F5,
    val skinColour: Long = 0xFFFFD3B6,
    val accentColour: Long = 0xFF9AF5D0,
    val accessory: AvatarAccessory = AvatarAccessory.NONE,
    val matureTopics: Boolean = false
)

data class ChatMessage(
    val id: Long,
    val characterId: String,
    val fromUser: Boolean,
    val text: String
)

data class AppSettings(
    val userName: String = "You",
    val darkMode: Boolean = false,
    val allowMildProfanity: Boolean = false,
    val apiKey: String = "",
    val model: String = "openrouter/free"
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

fun CharacterCard.toJson(): JSONObject = JSONObject()
    .put("spec", "mimo-character-card-v1")
    .put("id", id)
    .put("name", name)
    .put("tagline", tagline)
    .put("personality", personality)
    .put("scenario", scenario)
    .put("greeting", greeting)
    .put("exampleDialogue", exampleDialogue)
    .put("primaryColour", primaryColour)
    .put("skinColour", skinColour)
    .put("accentColour", accentColour)
    .put("accessory", accessory.name)
    .put("matureTopics", matureTopics)

fun characterFromJson(raw: String): CharacterCard {
    val json = JSONObject(raw)
    return CharacterCard(
        id = UUID.randomUUID().toString(),
        name = json.optString("name", "Imported character").take(40),
        tagline = json.optString("tagline", "Imported character").take(100),
        personality = json.optString("personality", "Warm and helpful.").take(1200),
        scenario = json.optString("scenario", "A casual conversation.").take(1000),
        greeting = json.optString("greeting", "Hello!").take(500),
        exampleDialogue = json.optString("exampleDialogue", "").take(1600),
        primaryColour = json.optLong("primaryColour", 0xFF7255F5),
        skinColour = json.optLong("skinColour", 0xFFFFD3B6),
        accentColour = json.optLong("accentColour", 0xFF9AF5D0),
        accessory = runCatching {
            AvatarAccessory.valueOf(json.optString("accessory", AvatarAccessory.NONE.name))
        }.getOrDefault(AvatarAccessory.NONE),
        matureTopics = json.optBoolean("matureTopics", false)
    )
}
