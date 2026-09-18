package com.treg.llmpersonalization.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.treg.llmpersonalization.AppState
import com.treg.llmpersonalization.data.Chat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    appState: AppState,
    onOpenSettings: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val user = appState.currentUser
    val chats = remember(user, appState.chatsVersion) { appState.listChats() }
    val activeId = appState.currentChat?.id

    // Dialog state
    var menuChat by remember { mutableStateOf<Chat?>(null) }
    var renameChat by remember { mutableStateOf<Chat?>(null) }
    var deleteChat by remember { mutableStateOf<Chat?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Chats",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("New chat") },
                    selected = false,
                    onClick = {
                        appState.createChat()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                if (chats.isEmpty()) {
                    Text(
                        "No chats yet",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(chats, key = { it.id }) { chat ->
                            ChatDrawerItem(
                                chat = chat,
                                active = chat.id == activeId,
                                onOpen = {
                                    appState.openChat(chat)
                                    scope.launch { drawerState.close() }
                                },
                                onLongPress = { menuChat = chat }
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            appState.currentChat?.title ?: "Treg's LLM Model",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Chats")
                        }
                    },
                    actions = {
                        if (user != null) {
                            Text(
                                user.name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                ChatScreen(appState)
            }
        }
    }

    // --- Chat options menu ---
    menuChat?.let { chat ->
        AlertDialog(
            onDismissRequest = { menuChat = null },
            title = { Text(chat.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text("Manage this chat") },
            confirmButton = {
                TextButton(onClick = {
                    renameText = chat.title
                    renameChat = chat
                    menuChat = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteChat = chat
                    menuChat = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    // --- Rename dialog ---
    renameChat?.let { chat ->
        AlertDialog(
            onDismissRequest = { renameChat = null },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = renameText.trim()
                        if (t.isNotEmpty()) {
                            appState.chatStore?.rename(chat, t)
                            if (appState.currentChat?.id == chat.id) {
                                // refresh currently open chat title by re-reading from store
                                appState.openChat(chat)
                            }
                            appState.notifyChatUpdated()
                        }
                        renameChat = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameChat = null }) { Text("Cancel") }
            }
        )
    }

    // --- Delete confirm ---
    deleteChat?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleteChat = null },
            title = { Text("Delete chat?") },
            text = { Text("\"${chat.title}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    appState.chatStore?.delete(chat.id)
                    if (appState.currentChat?.id == chat.id) {
                        appState.openChat(
                            // null it out — no current chat
                            Chat(
                                id = "",
                                userId = "",
                                title = "",
                                createdAt = 0L,
                                updatedAt = 0L
                            ).also { /* placeholder */ }
                        )
                    }
                    appState.notifyChatUpdated()
                    deleteChat = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteChat = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatDrawerItem(
    chat: Chat,
    active: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val bg = if (active)
        MaterialTheme.colorScheme.secondaryContainer
    else
        Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            chat.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active)
                MaterialTheme.colorScheme.onSecondaryContainer
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}