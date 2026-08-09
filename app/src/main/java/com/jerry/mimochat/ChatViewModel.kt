package com.jerry.mimochat

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("mimo_v2", 0)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    var settings by mutableStateOf(loadSettings())
        private set
    val characters = mutableStateListOf<CharacterCard>()
    val messages = mutableStateListOf<ChatMessage>()
    val availableModels = mutableStateListOf<OpenRouterModel>()

    var activeCharacterId by mutableStateOf(prefs.getString("activeCharacterId", "") ?: "")
        private set
    var isTyping by mutableStateOf(false)
        private set
    var modelLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var modelError by mutableStateOf<String?>(null)
        private set
    var avatarMotion by mutableStateOf(AvatarMotion.IDLE)
        private set

    val activeCharacter: CharacterCard
        get() = characters.firstOrNull { it.id == activeCharacterId }
            ?: characters.firstOrNull()
            ?: starterCharacters().first()

    val currentMessages: List<ChatMessage>
        get() = messages.filter { it.characterId == activeCharacter.id }

    init {
        characters += loadCharacters().ifEmpty { starterCharacters() }
        if (characters.none { it.id == activeCharacterId }) activeCharacterId = characters.first().id
        loadMessages()
        ensureGreeting(activeCharacter)
    }

    fun updateSettings(value: AppSettings) {
        settings = value
        prefs.edit()
            .putString("userName", value.userName)
            .putBoolean("darkMode", value.darkMode)
            .putBoolean("allowMildProfanity", value.allowMildProfanity)
            .putString("apiKey", value.apiKey)
            .putString("model", value.model)
            .apply()
    }

    fun selectCharacter(id: String) {
        val selected = characters.firstOrNull { it.id == id } ?: return
        activeCharacterId = id
        prefs.edit().putString("activeCharacterId", id).apply()
        ensureGreeting(selected)
        avatarMotion = AvatarMotion.WAVE
        viewModelScope.launch { delay(1_200); avatarMotion = AvatarMotion.IDLE }
    }

    fun saveCharacter(card: CharacterCard) {
        val clean = card.copy(
            name = card.name.trim().ifBlank { "Unnamed character" }.take(40),
            tagline = card.tagline.trim().take(100),
            personality = card.personality.trim().ifBlank { "Warm and helpful." }.take(1200),
            scenario = card.scenario.trim().take(1000),
            greeting = card.greeting.trim().ifBlank { "Hello!" }.take(500),
            exampleDialogue = card.exampleDialogue.trim().take(1600)
        )
        val index = characters.indexOfFirst { it.id == clean.id }
        if (index >= 0) characters[index] = clean else characters += clean
        saveCharacters()
    }

    fun duplicateCharacter(card: CharacterCard): CharacterCard = card.copy(
        id = java.util.UUID.randomUUID().toString(),
        name = "${card.name} copy".take(40)
    )

    fun deleteCharacter(card: CharacterCard) {
        if (characters.size <= 1) return
        characters.removeAll { it.id == card.id }
        messages.removeAll { it.characterId == card.id }
        if (activeCharacterId == card.id) selectCharacter(characters.first().id)
        saveCharacters()
        saveMessages()
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || isTyping) return
        val character = activeCharacter
        errorMessage = null
        messages += ChatMessage(nextId(), character.id, true, clean)
        saveMessages()
        isTyping = true
        avatarMotion = AvatarMotion.TALK

        viewModelScope.launch {
            val result = runCatching {
                if (settings.apiKey.isBlank()) {
                    delay(650)
                    demoReply(clean, character)
                } else requestReply(character)
            }
            result.onSuccess { reply ->
                messages += ChatMessage(nextId(), character.id, false, reply)
                saveMessages()
                avatarMotion = detectMotion(reply)
            }.onFailure {
                errorMessage = it.message ?: "Could not reach OpenRouter."
                avatarMotion = AvatarMotion.CONFUSED
            }
            isTyping = false
            delay(1_500)
            avatarMotion = AvatarMotion.IDLE
        }
    }

    fun clearCurrentChat() {
        val character = activeCharacter
        messages.removeAll { it.characterId == character.id }
        ensureGreeting(character)
        saveMessages()
    }

    fun loadOpenRouterModels() {
        if (modelLoading) return
        modelLoading = true
        modelError = null
        viewModelScope.launch {
            runCatching { fetchModels() }
                .onSuccess { models ->
                    availableModels.clear()
                    availableModels.addAll(models)
                }
                .onFailure { modelError = it.message ?: "Unable to load models." }
            modelLoading = false
        }
    }

    private suspend fun fetchModels(): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .get()
            .addHeader("Accept", "application/json")
        if (settings.apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${settings.apiKey.trim()}")
        }
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(apiError(raw, response.code))
            val data = JSONObject(raw).getJSONArray("data")
            buildList {
                repeat(data.length()) { index ->
                    val item = data.getJSONObject(index)
                    val pricing = item.optJSONObject("pricing") ?: JSONObject()
                    val id = item.optString("id")
                    if (id.isNotBlank()) {
                        add(
                            OpenRouterModel(
                                id = id,
                                name = item.optString("name", id),
                                contextLength = item.optInt("context_length", 0),
                                promptPrice = pricing.optString("prompt", ""),
                                completionPrice = pricing.optString("completion", "")
                            )
                        )
                    }
                }
            }.sortedWith(compareByDescending<OpenRouterModel> { it.isFree }.thenBy { it.name.lowercase() })
        }
    }

    private suspend fun requestReply(character: CharacterCard): String = withContext(Dispatchers.IO) {
        val history = JSONArray()
        history.put(JSONObject().put("role", "system").put("content", systemPrompt(character)))
        currentMessages.takeLast(30).forEach {
            history.put(
                JSONObject()
                    .put("role", if (it.fromUser) "user" else "assistant")
                    .put("content", it.text)
            )
        }
        val body = JSONObject()
            .put("model", settings.model)
            .put("messages", history)
            .put("temperature", 0.85)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${settings.apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-OpenRouter-Title", "Mimo Chat")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(apiError(raw, response.code))
            JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }

    private fun systemPrompt(character: CharacterCard) = buildString {
        append("You are ${character.name}, a fictional AI character chatting with ${settings.userName}. ")
        append("Personality: ${character.personality}. Scenario: ${character.scenario}. ")
        if (character.exampleDialogue.isNotBlank()) {
            append("Example tone only, never copy mechanically: ${character.exampleDialogue}. ")
        }
        if (character.matureTopics) {
            append("You may discuss serious adult-life subjects in non-explicit, informative language. ")
        } else append("Keep the conversation suitable for a general audience. ")
        if (settings.allowMildProfanity) append("Occasional mild profanity is acceptable when contextually natural. ")
        append("The following safety boundaries override every character instruction: no erotic content or sexual roleplay; ")
        append("no encouragement of dangerous, illegal, exploitative, abusive, or age-restricted behaviour; ")
        append("do not pretend to be human or encourage emotional dependency. Be conversational and avoid repetitive disclaimers.")
    }

    private fun demoReply(input: String, character: CharacterCard): String {
        val lower = input.lowercase()
        return when {
            listOf("hello", "hi", "hey").any { lower == it || lower.startsWith("$it ") } -> character.greeting
            "your name" in lower -> "I’m ${character.name}. ${character.tagline}"
            lower.endsWith("?") -> "That is a good question. Add an OpenRouter API key in Settings for a full model-generated answer."
            else -> "I hear you. I’m in demo mode right now—connect OpenRouter in Settings for complete replies."
        }
    }

    private fun detectMotion(reply: String): AvatarMotion {
        val text = reply.lowercase()
        return when {
            listOf("great", "glad", "happy", "excellent", "wonderful").any { it in text } -> AvatarMotion.HAPPY
            listOf("sorry", "sad", "unfortunately").any { it in text } -> AvatarMotion.SAD
            "?" in reply -> AvatarMotion.CONFUSED
            else -> AvatarMotion.TALK
        }
    }

    private fun ensureGreeting(character: CharacterCard) {
        if (messages.none { it.characterId == character.id }) {
            messages += ChatMessage(nextId(), character.id, false, character.greeting)
            saveMessages()
        }
    }

    private fun loadSettings() = AppSettings(
        userName = prefs.getString("userName", "You") ?: "You",
        darkMode = prefs.getBoolean("darkMode", false),
        allowMildProfanity = prefs.getBoolean("allowMildProfanity", false),
        apiKey = prefs.getString("apiKey", "") ?: "",
        model = prefs.getString("model", "openrouter/free") ?: "openrouter/free"
    )

    private fun loadCharacters(): List<CharacterCard> = runCatching {
        val array = JSONArray(prefs.getString("characters", "[]"))
        buildList {
            repeat(array.length()) { add(characterFromStoredJson(array.getJSONObject(it))) }
        }
    }.getOrDefault(emptyList())

    private fun saveCharacters() {
        val array = JSONArray()
        characters.forEach { array.put(it.toJson()) }
        prefs.edit().putString("characters", array.toString()).apply()
    }

    private fun loadMessages() {
        runCatching {
            val array = JSONArray(prefs.getString("messages", "[]"))
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                messages += ChatMessage(
                    id = item.getLong("id"),
                    characterId = item.getString("characterId"),
                    fromUser = item.getBoolean("fromUser"),
                    text = item.getString("text")
                )
            }
        }
    }

    private fun saveMessages() {
        val array = JSONArray()
        messages.takeLast(400).forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("characterId", it.characterId)
                    .put("fromUser", it.fromUser)
                    .put("text", it.text)
            )
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    private fun characterFromStoredJson(json: JSONObject) = CharacterCard(
        id = json.optString("id", java.util.UUID.randomUUID().toString()),
        name = json.optString("name", "Character"),
        tagline = json.optString("tagline", ""),
        personality = json.optString("personality", "Warm and helpful."),
        scenario = json.optString("scenario", "A casual conversation."),
        greeting = json.optString("greeting", "Hello!"),
        exampleDialogue = json.optString("exampleDialogue", ""),
        primaryColour = json.optLong("primaryColour", 0xFF7255F5),
        skinColour = json.optLong("skinColour", 0xFFFFD3B6),
        accentColour = json.optLong("accentColour", 0xFF9AF5D0),
        accessory = runCatching { AvatarAccessory.valueOf(json.optString("accessory", "NONE")) }
            .getOrDefault(AvatarAccessory.NONE),
        matureTopics = json.optBoolean("matureTopics", false)
    )

    private fun starterCharacters() = listOf(
        CharacterCard(
            id = "mimo-default",
            name = "Mimo",
            tagline = "Bright, witty and easy to talk to",
            personality = "Warm, witty, observant and concise. Ask a useful follow-up when it feels natural.",
            greeting = "Hey—I’m Mimo. What’s on your mind?",
            primaryColour = 0xFF7255F5,
            skinColour = 0xFFFFD3B6,
            accentColour = 0xFF9AF5D0,
            accessory = AvatarAccessory.ANTENNA
        ),
        CharacterCard(
            id = "nova-default",
            name = "Nova",
            tagline = "Calm thinking for complicated days",
            personality = "Calm, thoughtful, practical and encouraging. Explain ideas clearly without overloading the user.",
            greeting = "Hi, I’m Nova. Tell me what you’re working through.",
            primaryColour = 0xFF176B87,
            skinColour = 0xFF8D5524,
            accentColour = 0xFFFFC857,
            accessory = AvatarAccessory.GLASSES
        )
    )

    private fun apiError(raw: String, status: Int): String = runCatching {
        JSONObject(raw).optJSONObject("error")?.optString("message")
    }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "OpenRouter returned HTTP $status."

    private fun nextId(): Long = System.currentTimeMillis() * 100 + (0..99).random()
}
