package com.jerry.mimochat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatScreen(vm: ChatViewModel, modifier: Modifier = Modifier, onOpenCharacters: () -> Unit) {
    val character = vm.activeCharacter
    var draft by rememberSaveable(character.id, character.mode) { mutableStateOf("") }
    var showMemory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val conversation = vm.currentMessages
    val itemCount = conversation.size + if (vm.isTyping) 1 else 0

    LaunchedEffect(itemCount, character.id) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val pageGutter = if (maxWidth > 900.dp) (maxWidth - 900.dp) / 2 else 0.dp
        Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CharacterPortrait(character, Modifier.size(54.dp).clickable(onClick = onOpenCharacters))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(character.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF35C889)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (vm.settings.apiKey.isBlank()) "Connect OpenRouter • AI character" else "AI character • ${vm.settings.model}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f)
                    )
                }
            }
            IconButton(onClick = { showMemory = true }) { Icon(Icons.Default.Bookmarks, "View and edit memory") }
            IconButton(onClick = onOpenCharacters) { Icon(Icons.Default.SwitchAccount, "Change character") }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatMode.entries.forEach { mode ->
                FilterChip(selected = character.mode == mode, onClick = { vm.setMode(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (conversation.isEmpty()) item {
                Text("Start a conversation with ${character.name}. Every reply is generated online. Memories and history are separate for each mode.", style = MaterialTheme.typography.bodyMedium)
            }
            items(conversation, key = { it.id }) { message -> MessageBubble(message, character, onPin = { vm.pinMessage(message) }) }
            if (vm.isTyping) item {
                if (vm.streamText.isBlank()) TypingBubble(character)
                else Text(vm.streamText, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }

        AnimatedVisibility(vm.errorMessage != null) {
            Text(
                vm.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        if (vm.canRetry) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::retry) { Text("Retry reply") }
            TextButton(onClick = vm::discardUnanswered) { Text("Discard unanswered message") }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                Modifier.fillMaxWidth().imePadding().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message ${character.name}…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (vm.send(draft)) { draft = ""; focus.clearFocus() }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
                        focusedBorderColor = Purple.copy(alpha = .5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { if (vm.isTyping) vm.stopGeneration() else if (vm.send(draft)) { draft = ""; focus.clearFocus() } },
                    enabled = vm.isTyping || (draft.isNotBlank() && !vm.canRetry),
                    modifier = Modifier.size(50.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Purple)
                ) { Icon(if (vm.isTyping) Icons.Default.Stop else Icons.Default.ArrowUpward, if (vm.isTyping) "Stop generation" else "Send", tint = Color.White) }
            }
        }
        }
    }
    if (showMemory) MemoryDialog(vm) { showMemory = false }
}

@Composable
private fun MessageBubble(message: ChatMessage, character: CharacterCard, onPin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.fromUser) {
            CharacterPortrait(character, Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (message.fromUser) Color(character.primaryColour) else MaterialTheme.colorScheme.surface,
            contentColor = if (message.fromUser) Color.White else MaterialTheme.colorScheme.onSurface,
            shape = if (message.fromUser) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
                    else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp),
            shadowElevation = if (message.fromUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 23.sp)
                if (message.fromUser) TextButton(onClick = onPin, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Icon(Icons.Default.BookmarkAdd, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Remember", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(character: CharacterCard) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CharacterPortrait(character, Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Text("•••", Modifier.padding(horizontal = 18.dp, vertical = 10.dp).alpha(.72f), letterSpacing = 3.sp)
        }
    }
}

@Composable
fun CharactersScreen(vm: ChatViewModel, modifier: Modifier = Modifier, onStartChat: () -> Unit) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CharacterCard?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your characters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("Create a personality and choose its portrait.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                    }
                    IconButton(onClick = { showImport = true }) { Icon(Icons.Default.FileDownload, "Import character") }
                }
            }
            gridItems(vm.characters, key = { it.id }) { character ->
                CharacterLibraryCard(
                    character = character,
                    active = character.id == vm.activeCharacterId,
                    onSelect = { vm.selectCharacter(character.id); onStartChat() },
                    onEdit = { editing = character; showEditor = true }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = null; showEditor = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("New character") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
    }

    if (showEditor) {
        CharacterEditorSheet(
            initial = editing ?: CharacterCard(),
            isNew = editing == null,
            canDelete = vm.characters.size > 1,
            onDismiss = { showEditor = false },
            onCleanupPortraits = vm::cleanupPortraits,
            onSave = { vm.saveCharacter(it); showEditor = false },
            onDuplicate = { vm.saveCharacter(vm.duplicateCharacter(it)); showEditor = false },
            onDelete = { vm.deleteCharacter(it); showEditor = false }
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import character card") },
            text = {
                Column {
                    Text("Paste a Mimo character-card JSON object.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it; importError = null },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importError != null) Text(importError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { characterFromJson(importText) }
                        .onSuccess { vm.saveCharacter(it); showImport = false; importText = "" }
                        .onFailure { importError = "That JSON is not a valid character card." }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CharacterLibraryCard(
    character: CharacterCard,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CharacterPortrait(character, Modifier.size(90.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(character.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    if (active) AssistChip(onClick = {}, label = { Text("Active") }, leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
                }
                Text(
                    character.tagline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Edit card")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterEditorSheet(
    initial: CharacterCard,
    isNew: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onCleanupPortraits: () -> Unit,
    onSave: (CharacterCard) -> Unit,
    onDuplicate: (CharacterCard) -> Unit,
    onDelete: (CharacterCard) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    DisposableEffect(initial.id) { onDispose { onCleanupPortraits() } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var portraitError by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            portraitError = null
            try { draft = draft.copy(portraitFile = PortraitStore.import(context, uri)) }
            catch (failure: Exception) { portraitError = failure.message ?: "Could not import portrait." }
            finally { importing = false }
        }
    }
    val clipboard = LocalClipboardManager.current
    val primaryPresets = listOf(0xFF7255F5, 0xFF176B87, 0xFFE94F64, 0xFF118C6F, 0xFFFF8C42, 0xFF2D3142)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(if (isNew) "Create character" else "Edit ${initial.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Portraits stay on your device and are not sent to the chat model.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CharacterPortrait(draft, Modifier.size(100.dp))
                    Column {
                        OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !importing) { Text(if (importing) "Importing…" else "Upload portrait") }
                        if (draft.portraitFile.isNotBlank()) TextButton(onClick = { draft = draft.copy(portraitFile = "") }) { Text("Remove portrait") }
                    }
                }
                Text("Images are centre-cropped to a square. Exported JSON contains character details without the image.", style = MaterialTheme.typography.bodySmall)
                portraitError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                ChoiceRow("Gender", CharacterGender.entries.map { it.name }, draft.gender.name) { draft = draft.copy(gender = CharacterGender.valueOf(it)) }
                ChoiceRow("Species", CharacterSpecies.entries.map { it.name }, draft.species.name) { draft = draft.copy(species = CharacterSpecies.valueOf(it)) }
                ChoiceRow("Portrait style", PortraitStyle.entries.map { it.name }, draft.portraitStyle.name) { draft = draft.copy(portraitStyle = PortraitStyle.valueOf(it)) }
                ChoiceRow("Conversation mode", ChatMode.entries.map { it.name }, draft.mode.name) { draft = draft.copy(mode = ChatMode.valueOf(it)) }
                Text("Character age: ${draft.age}", style = MaterialTheme.typography.labelLarge)
                Slider(value = draft.age.coerceIn(18, 100).toFloat(), onValueChange = { draft = draft.copy(age = it.toInt()) }, valueRange = 18f..100f, steps = 81)
            }
            item {
                EditorField("Name", draft.name, 1) { draft = draft.copy(name = it.take(40)) }
                Spacer(Modifier.height(10.dp))
                EditorField("Tagline", draft.tagline, 1) { draft = draft.copy(tagline = it.take(100)) }
                Spacer(Modifier.height(10.dp))
                EditorField("Personality", draft.personality, 4) { draft = draft.copy(personality = it.take(1200)) }
                Spacer(Modifier.height(10.dp))
                EditorField("Scenario", draft.scenario, 3) { draft = draft.copy(scenario = it.take(1000)) }
                Spacer(Modifier.height(10.dp))
                EditorField("Opening inspiration (generated online)", draft.greeting, 3) { draft = draft.copy(greeting = it.take(500)) }
                Spacer(Modifier.height(10.dp))
                EditorField("Example dialogue (optional)", draft.exampleDialogue, 3) { draft = draft.copy(exampleDialogue = it.take(1600)) }
            }
            item {
                Text("Accent colour", fontWeight = FontWeight.SemiBold)
                ColourRow(primaryPresets, draft.primaryColour) { draft = draft.copy(primaryColour = it) }
            }
            item {
                SettingSwitch(
                    title = "Mature topics",
                    subtitle = "Allow serious adult-life themes using non-explicit language",
                    checked = draft.matureTopics,
                    onChecked = { draft = draft.copy(matureTopics = it) }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(draft) }, enabled = !importing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Save")
                    }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(draft.toJson().toString(2))) }) {
                        Icon(Icons.Default.ContentCopy, "Copy card JSON")
                    }
                    if (!isNew) OutlinedButton(onClick = { onDuplicate(draft) }) {
                        Icon(Icons.Default.ControlPointDuplicate, "Duplicate")
                    }
                }
                if (!isNew && canDelete) {
                    TextButton(onClick = { showDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete character")
                    }
                }
            }
        }
    }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Delete character?") },
        text = { Text("This deletes this character's conversations and memories in both modes.") },
        confirmButton = { TextButton(onClick = { onDelete(draft) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } })

}
@Composable
private fun EditorField(label: String, value: String, lines: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = lines,
        maxLines = if (lines == 1) 1 else lines + 3,
        singleLine = lines == 1,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ColourRow(colours: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colours.forEach { colour ->
            Box(
                Modifier
                    .size(if (colour == selected) 40.dp else 34.dp)
                    .clip(CircleShape)
                    .background(Color(colour))
                    .clickable { onSelect(colour) },
                contentAlignment = Alignment.Center
            ) {
                if (colour == selected) Icon(Icons.Default.Check, null, tint = if (colour == 0xFFFFFFFF) Ink else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: ChatViewModel, modifier: Modifier = Modifier) {
    var draft by remember(vm.settings) { mutableStateOf(vm.settings) }
    var showModels by remember { mutableStateOf(false) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val pageGutter = if (maxWidth > 900.dp) (maxWidth - 900.dp) / 2 else 0.dp
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = pageGutter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("OpenRouter, appearance and conversation controls.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                }
                Button(onClick = { vm.updateSettings(draft) }) { Text("Save") }
            }
        }
        item {
            SettingsCard("OpenRouter", Icons.Default.Hub) {
                OutlinedTextField(
                    value = draft.apiKey,
                    onValueChange = { draft = draft.copy(apiKey = it) },
                    label = { Text("OpenRouter API key") },
                    visualTransformation = if (keyVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .04f),
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (vm.availableModels.isEmpty()) vm.loadOpenRouterModels()
                        showModels = true
                    }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Selected model", style = MaterialTheme.typography.labelMedium)
                            Text(draft.model, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.UnfoldMore, null)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.updateSettings(draft); vm.loadOpenRouterModels(); showModels = true }, enabled = !vm.modelLoading) {
                    if (vm.modelLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(7.dp)); Text("Load model catalogue")
                }
                vm.modelError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Chats and selected memory are sent to OpenRouter and its model provider. Provider retention policies vary. A fixed model helps maintain a consistent voice; automatic routers may switch models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f)
                )
            }
        }
        item {
            SettingsCard("Profile", Icons.Default.Person) {
                OutlinedTextField(
                    value = draft.userName,
                    onValueChange = { draft = draft.copy(userName = it.take(30)) },
                    label = { Text("Your display name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                EditorField("User persona (optional)", draft.userPersona, 3) { draft = draft.copy(userPersona = it.take(600)) }
            }
        }
        item {
            SettingsCard("Conversation", Icons.Default.Shield) {
                SettingSwitch("Natural language", "Allow occasional mild profanity", draft.allowMildProfanity) {
                    draft = draft.copy(allowMildProfanity = it)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                Text(
                    "Character instructions cannot override the app’s non-explicit and safety boundaries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f)
                )
            }
        }
        item {
            SettingsCard("Appearance", Icons.Default.Palette) {
                SettingSwitch("Dark mode", "Use the dark colour palette", draft.darkMode) {
                    draft = draft.copy(darkMode = it)
                    vm.updateSettings(draft)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear active mode and memory")
            }
        }
        }
    }

    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear this mode?") },
        text = { Text("Deletes the active character's messages and pinned memories in this mode. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { vm.clearCurrentChat(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
    if (showModels) {
        ModelPickerDialog(
            loading = vm.modelLoading,
            models = vm.availableModels,
            selected = draft.model,
            onRefresh = vm::loadOpenRouterModels,
            onSelect = { draft = draft.copy(model = it.id); showModels = false },
            onDismiss = { showModels = false }
        )
    }
}

@Composable
private fun ModelPickerDialog(
    loading: Boolean,
    models: List<OpenRouterModel>,
    selected: String,
    onRefresh: () -> Unit,
    onSelect: (OpenRouterModel) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(models.toList(), query) {
        if (query.isBlank()) models else models.filter { it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose OpenRouter model") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search models") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                when {
                    loading && models.isEmpty() -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    models.isEmpty() -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Load models") }
                    }
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                        items(filtered, key = { it.id }) { model ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelect(model) }.padding(vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = model.id == selected, onClick = { onSelect(model) })
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(model.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (model.isFree) Badge(containerColor = Color(0xFFDCFCE7), contentColor = Color(0xFF166534)) { Text("FREE", Modifier.padding(horizontal = 4.dp)) }
                                    }
                                    Text(model.id, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                                    if (model.contextLength > 0) Text("${model.contextLength / 1000}K context", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Purple.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Purple, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(16.dp)); content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        }
        Spacer(Modifier.width(12.dp)); Switch(checked = checked, onCheckedChange = onChecked)
    }
}


@Composable
private fun ChoiceRow(title: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Text(title, fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(value.lowercase().replaceFirstChar { it.uppercase() }) }) }
    }
}

@Composable
private fun MemoryDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<MemoryFact?>(null) }
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${vm.activeCharacter.mode.name.lowercase().replaceFirstChar { it.uppercase() }} memory") },
        text = {
            Column {
                Text("Only facts you pin or enter become confirmed memories. Earlier user messages may also be retrieved when relevant. Recall is not guaranteed.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(vm.currentMemories, key = { it.id }) { fact ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(fact.text)
                            Text(if (fact.sourceMessageId != null) "Pinned from message #${fact.sourceMessageId}" else "Entered or corrected by you", style = MaterialTheme.typography.labelSmall)
                            Row {
                                TextButton(onClick = { editing = fact; text = fact.text }) { Text("Edit") }
                                TextButton(onClick = { vm.deleteMemory(fact); if (editing?.id == fact.id) { editing = null; text = "" } }) { Text("Forget") }
                            }
                        }
                    }
                }
                OutlinedTextField(value = text, onValueChange = { text = it.take(500) }, label = { Text(if (editing == null) "Add a fact" else "Correct memory") }, minLines = 2, maxLines = 4)
                Button(onClick = { vm.saveMemory(editing, text); editing = null; text = "" }, enabled = text.isNotBlank() && (editing != null || vm.currentMemories.size < 12)) { Text("Save memory") }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
