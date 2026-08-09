package com.jerry.mimochat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

val Purple = Color(0xFF7255F5)
val Mint = Color(0xFF9AF5D0)
val Ink = Color(0xFF17151F)
val CanvasBackground = Color(0xFFF7F8FC)
val Night = Color(0xFF111116)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MimoApp() }
    }
}

@Composable
fun MimoApp(vm: ChatViewModel = viewModel()) {
    val colours = if (vm.settings.darkMode) {
        darkColorScheme(
            primary = Color(0xFFA997FF), secondary = Mint, background = Night,
            surface = Color(0xFF1B1A22), onBackground = Color(0xFFF2EFFA), onSurface = Color(0xFFF2EFFA)
        )
    } else {
        lightColorScheme(
            primary = Purple, secondary = Color(0xFF167C5A), background = CanvasBackground,
            surface = Color.White, onBackground = Ink, onSurface = Ink
        )
    }

    MaterialTheme(colorScheme = colours, typography = Typography()) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        BackHandler(enabled = tab != 0) { tab = 0 }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            if (useNavigationRail) {
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        Spacer(Modifier.weight(1f))
                        MimoRailItem(tab == 0, { tab = 0 }, Icons.Default.ChatBubble, "Chat")
                        MimoRailItem(tab == 1, { tab = 1 }, Icons.Default.Groups, "Characters")
                        MimoRailItem(tab == 2, { tab = 2 }, Icons.Default.Settings, "Settings")
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MimoDestination(tab, vm, { tab = 1 }, { tab = 0 })
                    }
                }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.ChatBubble, null) }, label = { Text("Chat") })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Groups, null) }, label = { Text("Characters") })
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                        }
                    }
                ) { padding ->
                    MimoDestination(tab, vm, { tab = 1 }, { tab = 0 }, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun MimoDestination(
    tab: Int,
    vm: ChatViewModel,
    openCharacters: () -> Unit,
    startChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (tab) {
        0 -> ChatScreen(vm, modifier, onOpenCharacters = openCharacters)
        1 -> CharactersScreen(vm, modifier, onStartChat = startChat)
        else -> SettingsScreen(vm, modifier)
    }
}

@Composable
private fun MimoRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null) },
        label = { Text(label) }
    )
}
