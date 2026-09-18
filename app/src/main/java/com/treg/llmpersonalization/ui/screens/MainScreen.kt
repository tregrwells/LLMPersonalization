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

    ChatOptionsDialog(
        chat = menuChat,
        onDismiss = { menuChat = null },
        onRename = { c ->
            renameText = c.title
            renameChat = c
            menuChat = null
        },
        onDelete = { c ->
            deleteChat = c
            menuChat = null
        }
    )

    RenameDialog(
        chat = renameChat,
        initialText = renameText,
        onTextChange = { renameText = it },
        onDismiss = { renameChat = null },
        onConfirm = {
            val c = renameChat ?: return@RenameDialog
            val t = renameText.trim()
            if (t.isNotEmpty()) {
                appState.chatStore?.rename(c, t)
                appState.notifyChatUpdated()
            }
            renameChat = null
        }
    )

    DeleteDialog(
        chat = deleteChat,
        onDismiss = { deleteChat = null },
        onConfirm = {
            val c = deleteChat ?: return@DeleteDialog
            appState.chatStore?.delete(c.id)
            if (appState.currentChat?.id == c.id) {
                appState.clearCurrentChat()
            }
            appState.notifyChatUpdated()
            deleteChat = null
        }
    )
}

@Composable
private fun ChatOptionsDialog(
    chat: Chat?,
    onDismiss: () -> Unit,
    onRename: (Chat) -> Unit,
    onDelete: (Chat) -> Unit
) {
    if (chat == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chat.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { Text("Manage this chat") },
        confirmButton = {
            TextButton(onClick = { onRename(chat) }) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = { onDelete(chat) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
private fun RenameDialog(
    chat: Chat?,
    initialText: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (chat == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = initialText,
                onValueChange = onTextChange,
                label = { Text("Title") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteDialog(
    chat: Chat?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (chat == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete chat?") },
        text = { Text("This chat will be permanently removed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
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