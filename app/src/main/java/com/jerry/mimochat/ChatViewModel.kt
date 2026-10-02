package com.jerry.mimochat

import android.app.Application
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("mimo_v2", 0)
    private val stateFile = AtomicFile(File(application.filesDir, "conversation-state-v3.json"))
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(150, TimeUnit.SECONDS).build()
    private var generation: Job? = null
    private var requestSequence = 0
    @Volatile private var activeCall: Call? = null
    private var lastId = System.currentTimeMillis() * 100
    private var storageHealthy = true

    var errorMessage by mutableStateOf<String?>(null)
        private set
    var settings by mutableStateOf(loadSettings())
        private set
    val characters = mutableStateListOf<CharacterCard>()
    val messages = mutableStateListOf<ChatMessage>()
    val memories = mutableStateListOf<MemoryFact>()
    val availableModels = mutableStateListOf<OpenRouterModel>()
    var activeCharacterId by mutableStateOf(prefs.getString("activeCharacterId", "") ?: "")
        private set
    var isTyping by mutableStateOf(false)
        private set
    var streamText by mutableStateOf("")
        private set
    var modelLoading by mutableStateOf(false)
        private set
    var modelError by mutableStateOf<String?>(null)
        private set

    val activeCharacter: CharacterCard
        get() = characters.firstOrNull { it.id == activeCharacterId } ?: characters.first()
    val currentMessages: List<ChatMessage>
        get() = messages.filter { it.characterId == activeCharacter.id && it.mode == activeCharacter.mode }
    val currentMemories: List<MemoryFact>
        get() = memories.filter { it.characterId == activeCharacter.id && it.mode == activeCharacter.mode }
    val canRetry: Boolean get() = !isTyping && currentMessages.lastOrNull()?.fromUser == true

    init {
        characters += loadCharacters().ifEmpty { starterCharacters() }
        if (characters.none { it.id == activeCharacterId }) activeCharacterId = characters.first().id
        loadConversationState()
        lastId = maxOf(lastId, messages.maxOfOrNull { it.id } ?: 0L)
    }

    fun updateSettings(value: AppSettings) {
        runCatching {
            val encrypted = KeyStoreSecret.encrypt(value.apiKey.trim())
            prefs.edit().putString("userName", value.userName.take(30))
                .putString("userPersona", value.userPersona.take(600))
                .putBoolean("darkMode", value.darkMode).putBoolean("allowMildProfanity", value.allowMildProfanity)
                .putString("apiKeyEncrypted", encrypted).remove("apiKey").putString("model", value.model.trim()).apply()
            settings = value.copy(apiKey = value.apiKey.trim(), userName = value.userName.take(30), userPersona = value.userPersona.take(600))
            errorMessage = null
        }.onFailure { errorMessage = "Could not securely save settings. Please try again." }
    }

    fun selectCharacter(id: String) {
        if (characters.none { it.id == id }) return
        stopGeneration()
        activeCharacterId = id
        errorMessage = null
        prefs.edit().putString("activeCharacterId", id).apply()
    }

    fun setMode(mode: ChatMode) {
        stopGeneration()
        saveCharacter(activeCharacter.copy(mode = mode))
        errorMessage = null
    }

    fun saveCharacter(card: CharacterCard) {
        if (!storageHealthy) return
        if (card.id == activeCharacterId && isTyping) stopGeneration()
        val clean = card.copy(name = card.name.trim().ifBlank { "Unnamed character" }.take(40),
            tagline = card.tagline.trim().take(100), personality = card.personality.trim().take(1200),
            scenario = card.scenario.trim().take(1000), greeting = card.greeting.trim().take(500),
            exampleDialogue = card.exampleDialogue.trim().take(1600), age = card.age.coerceIn(18, 999))
        val index = characters.indexOfFirst { it.id == card.id }
        if (index >= 0) characters[index] = clean else characters += clean
        saveCharacters()
    }

    fun duplicateCharacter(card: CharacterCard) = card.copy(id = UUID.randomUUID().toString(), name = "${card.name} copy".take(40))

    fun deleteCharacter(card: CharacterCard) {
        if (!storageHealthy || characters.size <= 1) return
        stopGeneration()
        characters.removeAll { it.id == card.id }
        messages.removeAll { it.characterId == card.id }
        memories.removeAll { it.characterId == card.id }
        if (activeCharacterId == card.id) selectCharacter(characters.first().id)
        saveCharacters()
        saveConversationState()
    }

    /** No local bot messages, including greetings, are inserted into online conversations. */
    fun send(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank() || isTyping || !storageHealthy) return false
        if (settings.apiKey.isBlank()) { errorMessage = "Add your OpenRouter API key in Settings to chat online."; return false }
        if (canRetry) { errorMessage = "Retry or discard the unanswered message first."; return false }
        val character = activeCharacter
        messages += ChatMessage(nextId(), character.id, true, clean, character.mode)
        if (!saveConversationState()) return false
        startReply()
        return true
    }

    fun retry() {
        if (!canRetry || !storageHealthy) return
        if (settings.apiKey.isBlank()) { errorMessage = "Add an OpenRouter API key in Settings."; return }
        startReply()
    }

    fun discardUnanswered() {
        if (!canRetry || !storageHealthy) return
        val pending = currentMessages.last()
        messages.removeAll { it.id == pending.id }
        // A discarded source must not leave a dangling pinned memory.
        memories.removeAll { it.sourceMessageId == pending.id }
        errorMessage = null
        saveConversationState()
    }

    private fun startReply() {
        val character = activeCharacter
        val requestSettings = settings
        val history = messages.toList()
        val facts = memories.toList()
        val contextLength = availableModels.firstOrNull { it.id == requestSettings.model }?.contextLength
            ?.takeIf { it > 0 } ?: 4096
        val sequence = ++requestSequence
        errorMessage = null
        streamText = ""
        isTyping = true
        generation = viewModelScope.launch {
            try {
                val context = ConversationContextBuilder.build(character, requestSettings, history, facts, contextLength)
                val reply = requestReply(context, requestSettings)
                messages += ChatMessage(nextId(), character.id, false, reply, character.mode)
                saveConversationState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (sequence == requestSequence) errorMessage = failure.message ?: "Could not reach OpenRouter. Retry your message."
            } finally {
                if (sequence == requestSequence) {
                    isTyping = false
                    streamText = ""
                    activeCall = null
                }
            }
        }
    }

    fun stopGeneration() {
        ++requestSequence
        activeCall?.cancel()
        generation?.cancel()
        generation = null
        isTyping = false
        streamText = ""
    }

    fun pinMessage(message: ChatMessage) {
        if (!storageHealthy || !message.fromUser || message.characterId != activeCharacter.id || message.mode != activeCharacter.mode) return
        if (memories.any { it.sourceMessageId == message.id }) return
        if (currentMemories.size >= 12) { errorMessage = "Keep up to 12 pinned memories per mode. Remove one first."; return }
        memories += MemoryFact(characterId = message.characterId, mode = message.mode, text = message.text.take(500), sourceMessageId = message.id)
        saveConversationState()
    }

    fun saveMemory(existing: MemoryFact?, text: String) {
        if (!storageHealthy || text.isBlank()) return
        if (existing == null && currentMemories.size >= 12) { errorMessage = "Remove a memory before adding another."; return }
        val index = memories.indexOfFirst { it.id == existing?.id }
        // Edited facts are explicitly user-entered; do not retain a misleading source link.
        val value = MemoryFact(id = existing?.id ?: UUID.randomUUID().toString(), characterId = activeCharacter.id,
            mode = activeCharacter.mode, text = text.trim().take(500))
        if (index >= 0) memories[index] = value else memories += value
        saveConversationState()
    }

    fun deleteMemory(fact: MemoryFact) { if (storageHealthy) { memories.remove(fact); saveConversationState() } }

    fun clearCurrentChat() {
        if (!storageHealthy) return
        stopGeneration()
        val character = activeCharacter
        messages.removeAll { it.characterId == character.id && it.mode == character.mode }
        memories.removeAll { it.characterId == character.id && it.mode == character.mode }
        errorMessage = null
        saveConversationState()
    }

    fun loadOpenRouterModels() {
        if (modelLoading) return
        modelLoading = true
        modelError = null
        val key = settings.apiKey
        viewModelScope.launch {
            try {
                val models = withContext(Dispatchers.IO) {
                    val builder = Request.Builder().url("https://openrouter.ai/api/v1/models").get()
                    if (key.isNotBlank()) builder.addHeader("Authorization", "Bearer $key")
                    client.newCall(builder.build()).execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException(apiError(raw, response.code))
                        val data = JSONObject(raw).getJSONArray("data")
                        buildList {
                            repeat(data.length()) {
                                val item = data.getJSONObject(it)
                                val pricing = item.optJSONObject("pricing") ?: JSONObject()
                                val id = item.optString("id")
                                if (id.isNotBlank()) add(OpenRouterModel(id, item.optString("name", id), item.optInt("context_length", 0),
                                    pricing.optString("prompt", ""), pricing.optString("completion", "")))
                            }
                        }.sortedWith(compareByDescending<OpenRouterModel> { it.isFree }.thenBy { it.name.lowercase() })
                    }
                }
                availableModels.clear(); availableModels.addAll(models)
            } catch (failure: Exception) { modelError = failure.message ?: "Unable to load models." }
            finally { modelLoading = false }
        }
    }

    private suspend fun requestReply(context: ConversationContext, requestSettings: AppSettings): String = withContext(Dispatchers.IO) {
        val history = JSONArray().put(JSONObject().put("role", "system").put("content", context.system))
        context.recent.forEach { history.put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text)) }
        val body = JSONObject().put("model", requestSettings.model).put("messages", history)
            .put("temperature", 0.75).put("max_tokens", 700).put("stream", true).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${requestSettings.apiKey}")
            .addHeader("Accept", "text/event-stream").addHeader("X-OpenRouter-Title", "Mimo Chat").post(body).build()
        val call = client.newCall(request)
        withContext(Dispatchers.Main) {
            currentCoroutineContext().ensureActive()
            activeCall = call
        }
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException(apiError(response.body?.string().orEmpty(), response.code))
            val reader = response.body?.charStream()?.buffered() ?: throw IOException("OpenRouter returned no response.")
            StreamParser.read(reader) { delta ->
                // Backpressure keeps updates ordered and prevents a finished/cancelled request updating a later chat.
                withContext(Dispatchers.Main) { currentCoroutineContext().ensureActive(); streamText += delta }
            }
        }
    }

    private fun loadSettings(): AppSettings {
        val key = runCatching {
            if (prefs.contains("apiKeyEncrypted")) KeyStoreSecret.decrypt(prefs.getString("apiKeyEncrypted", "").orEmpty())
            else prefs.getString("apiKey", "").orEmpty().also {
                if (it.isNotBlank()) prefs.edit().putString("apiKeyEncrypted", KeyStoreSecret.encrypt(it)).remove("apiKey").apply()
            }
        }.getOrElse { errorMessage = "Your saved API key could not be opened. Re-enter it in Settings."; "" }
        return AppSettings(userName = prefs.getString("userName", "You") ?: "You", darkMode = prefs.getBoolean("darkMode", false),
            allowMildProfanity = prefs.getBoolean("allowMildProfanity", false), apiKey = key,
            model = prefs.getString("model", "openrouter/free") ?: "openrouter/free",
            userPersona = prefs.getString("userPersona", "").orEmpty())
    }

    private fun loadCharacters(): List<CharacterCard> = runCatching {
        val array = JSONArray(prefs.getString("characters", "[]"))
        List(array.length()) { characterFromJson(array.getJSONObject(it).toString(), preserveId = true) }
    }.getOrElse { storageHealthy = false; errorMessage = "Character data could not be read. Existing data has been preserved."; emptyList() }

    private fun saveCharacters() {
        val array = JSONArray(); characters.forEach { array.put(it.toJson(includeLocalPortrait = true)) }
        prefs.edit().putString("characters", array.toString()).apply()
    }

    private fun loadConversationState() {
        runCatching {
            val saved = if (stateFile.baseFile.exists() || File(stateFile.baseFile.path + ".bak").exists()) {
                JSONObject(String(stateFile.readFully(), Charsets.UTF_8))
            } else JSONObject().put("messages", JSONArray(prefs.getString("messages", "[]")))
            val array = saved.getJSONArray("messages")
            val loaded = List(array.length()) {
                val item = array.getJSONObject(it)
                ChatMessage(item.getLong("id"), item.getString("characterId"), item.getBoolean("fromUser"), item.getString("text"),
                    enumValue(item.optString("mode"), ChatMode.COMPANION))
            }
            val facts = saved.optJSONArray("memories") ?: JSONArray()
            val loadedFacts = List(facts.length()) {
                val item = facts.getJSONObject(it)
                MemoryFact(item.getString("id"), item.getString("characterId"),
                    enumValue(item.optString("mode"), ChatMode.COMPANION), item.getString("text"),
                    if (item.has("sourceMessageId")) item.getLong("sourceMessageId") else null)
            }
            messages.addAll(loaded); memories.addAll(loadedFacts)
        }.onFailure {
            storageHealthy = false
            errorMessage = "Conversation data could not be read. Existing files are preserved; changes are disabled."
        }
    }

    private fun saveConversationState(): Boolean {
        if (!storageHealthy) return false
        val array = JSONArray(); val facts = JSONArray()
        messages.forEach { array.put(JSONObject().put("id", it.id).put("characterId", it.characterId)
            .put("fromUser", it.fromUser).put("text", it.text).put("mode", it.mode.name)) }
        memories.forEach { facts.put(JSONObject().put("id", it.id).put("characterId", it.characterId)
            .put("mode", it.mode.name).put("text", it.text).also { json -> it.sourceMessageId?.let { source -> json.put("sourceMessageId", source) } }) }
        var output: java.io.FileOutputStream? = null
        return try {
            output = stateFile.startWrite()
            output.write(JSONObject().put("schema", 3).put("messages", array).put("memories", facts).toString().toByteArray(Charsets.UTF_8))
            stateFile.finishWrite(output)
            prefs.edit().remove("messages").apply()
            true
        } catch (failure: Exception) {
            stateFile.failWrite(output)
            storageHealthy = false
            errorMessage = "Could not save conversation data. Free device storage before continuing."
            false
        }
    }

    private fun starterCharacters() = listOf(
        CharacterCard(id = "mimo-default", name = "Mimo", tagline = "Warm company, at your pace",
            personality = "Warm, witty and observant. Listen closely, use light humour when welcome, and keep everyday replies concise."),
        CharacterCard(id = "nova-default", name = "Nova", tagline = "A thoughtful voice for complicated days",
            personality = "Calm, thoughtful and practical. Offer comfort before advice, and respect when the user simply wants company.", primaryColour = 0xFF176B87)
    )

    private fun apiError(raw: String, status: Int): String = when (status) {
        401 -> "OpenRouter rejected the API key. Check Settings."
        402 -> "OpenRouter credits are insufficient. Add credits or choose a free model."
        429 -> "This model is rate-limited. Wait and retry, or choose another model."
        else -> runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }
            .getOrNull().takeUnless { it.isNullOrBlank() } ?: "OpenRouter returned HTTP $status. Retry your message."
    }
    private fun nextId(): Long = ++lastId
    override fun onCleared() { activeCall?.cancel(); super.onCleared() }
}
