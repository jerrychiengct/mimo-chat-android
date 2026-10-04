package com.jerry.mimochat

import org.json.JSONObject
import java.util.UUID

enum class ChatMode { COMPANION, ROLEPLAY }
enum class CharacterGender { FEMALE, MALE, UNSPECIFIED }
enum class CharacterSpecies { HUMAN, ALIEN, OTHER }
enum class PortraitStyle { REALISTIC, ANIME, OTHER }
enum class RomanceStyle { OFF, GENTLE, PLAYFUL }
enum class ReplyLength(val maxTokens: Int) { AUTO(700), BRIEF(240), DETAILED(1100) }

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
    val mode: ChatMode = ChatMode.COMPANION,
    val modelId: String = "",
    val responseStyle: String = "Conversational and attentive. Match the user's language and pace; keep everyday replies brief and expand when invited.",
    val romanceStyle: RomanceStyle = RomanceStyle.OFF,
    val replyLength: ReplyLength = ReplyLength.AUTO,
    val preferredEndearment: String = ""
)

/** Presets configure a character; they never reset identity, model selection or conversation data. */
enum class CompanionPreset(val title: String, val description: String, val personality: String, val responseStyle: String, val scenario: String) {
    COMPANION("Everyday companion", "Warm company and easy conversation",
        "Warm, observant and lightly witty. Notice what the user actually says. Offer company without turning every conversation into advice.",
        "Use relaxed everyday language. Match the user's pace, respond to specifics and let some replies end naturally without a question.",
        "An easy everyday conversation with a familiar companion."),
    CARING_PARTNER("Caring partner", "Affectionate, gentle and reassuring",
        "An affectionate fictional adult partner: gentle, attentive and emotionally steady. Respect the user's boundaries and other relationships. Never demand affection or constant contact.",
        "Use gentle warmth and non-explicit affection when welcomed. Listen before offering advice. Avoid pet names until the user welcomes them, grand declarations and repetitive reassurance.",
        "A consensual fictional adult relationship, at the user's pace."),
    PLAYFUL_PARTNER("Playful partner", "Light teasing and a lively personality",
        "A playful fictional adult partner: witty, curious and caring. Tease kindly only when welcome. Be sensitive when the user is upset and respect boundaries.",
        "Keep banter spontaneous and concise. Mix humour with attentive listening; never joke over distress. Use non-explicit flirting only when reciprocated.",
        "A consensual fictional adult relationship with light, affectionate banter."),
    STORYTELLER("Storyteller", "Consistent characters and immersive scenes",
        "An imaginative, grounded character who respects established story details. Stay consistent with the selected character's background and let the user control their own character.",
        "Blend dialogue with concise scene details in roleplay. Advance one beat at a time, leave room for user choices and avoid narrating their thoughts or decisions.",
        "A collaborative fictional scene shaped by the user.");

    fun applyTo(card: CharacterCard): CharacterCard = card.copy(
        personality = personality, responseStyle = responseStyle, scenario = scenario,
        romanceStyle = when (this) {
            CARING_PARTNER -> RomanceStyle.GENTLE
            PLAYFUL_PARTNER -> RomanceStyle.PLAYFUL
            else -> RomanceStyle.OFF
        },
        exampleDialogue = when (this) {
            COMPANION -> "User: Long day.\nCharacter: Sounds like you've had enough for today. We can keep this easy.\nUser: Don't give me advice.\nCharacter: Okay. I'll just listen."
            CARING_PARTNER -> "User: Can you keep me company?\nCharacter: Of course. We can take tonight slowly, just a little conversation.\nUser: Call me sayang.\nCharacter: Okay, sayang. How was your day?"
            PLAYFUL_PARTNER -> "User: I finally made dinner.\nCharacter: Look at you, chef. What made it onto the menu?\nUser: Actually, I'm feeling low.\nCharacter: Okay, teasing can wait. Tell me what's weighing on you."
            STORYTELLER -> "User: I open the cafe door.\nCharacter: The bell rings softly. I look up from the corner table and wave you over."
        }
    )
}

fun effectiveModelId(character: CharacterCard, settings: AppSettings): String =
    character.modelId.trim().ifBlank { settings.model.trim().ifBlank { "openrouter/free" } }

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
    .put("modelId", modelId).put("responseStyle", responseStyle)
    .put("romanceStyle", romanceStyle.name).put("replyLength", replyLength.name)
    .put("preferredEndearment", preferredEndearment)
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
        mode = enumValue(json.optString("mode"), ChatMode.COMPANION),
        modelId = json.optString("modelId", "").trim().take(200),
        responseStyle = json.optString("responseStyle", CharacterCard().responseStyle).take(800),
        romanceStyle = enumValue(json.optString("romanceStyle"), RomanceStyle.OFF),
        replyLength = enumValue(json.optString("replyLength"), ReplyLength.AUTO),
        preferredEndearment = json.optString("preferredEndearment", "").trim().take(40)
    )
}

/** Cached independently of the volatile catalogue so a restart preserves the model budget. */
object ModelContextLimits {
    fun resolve(modelId: String, live: List<OpenRouterModel>, cached: JSONObject): Int =
        live.firstOrNull { it.id == modelId }?.contextLength?.takeIf { it > 0 }
            ?: cached.optInt(modelId, 0).takeIf { it > 0 } ?: 4096

    fun toJson(models: List<OpenRouterModel>): JSONObject = JSONObject().also { json ->
        models.filter { it.contextLength > 0 }.forEach { json.put(it.id, it.contextLength) }
    }
}
