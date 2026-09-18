package com.treg.llmpersonalization.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.treg.llmpersonalization.AppState
import com.treg.llmpersonalization.data.Message
import com.treg.llmpersonalization.data.MessageRole
import com.treg.llmpersonalization.logic.ChatOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val chat = appState.currentChat
    val chatStore = appState.chatStore

    var messages by remember(chat?.id) {
        mutableStateOf(chat?.messages?.toList() ?: emptyList())
    }
    var input by remember(chat?.id) { mutableStateOf("") }
    var busy by remember(chat?.id) { mutableStateOf(false) }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + if (busy) 1 else 0)
        }
    }

    if (chat == null) {
        EmptyState()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Messages ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { msg -> MessageBubble(msg) }
            if (busy) {
                item { GeneratingBubble() }
            }
        }

        // --- Input ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Enter text here") },
                modifier = Modifier.weight(1f),
                enabled = !busy,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    send(appState, chat, input, { input = it }, { busy = it }, { messages = it }, scope)
                }),
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = {
                    send(appState, chat, input, { input = it }, { busy = it }, { messages = it }, scope)
                },
                enabled = !busy && input.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (busy) "..." else "Send") }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Open a chat from the menu, or create a new one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val isUser = msg.role == MessageRole.USER
    val bg = if (isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                msg.think?.let { ThinkBlock(it, fg) }
                Text(
                    msg.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = fg
                )
            }
        }
    }
}

@Composable
private fun ThinkBlock(think: String, color: androidx.compose.ui.graphics.Color) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clickable { expanded = !expanded }
            .background(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            if (expanded) "▾ Reasoning" else "▸ Reasoning",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
        if (expanded) {
            Text(
                think,
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun GeneratingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp,
                bottomStart = 4.dp, bottomEnd = 16.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                "Thinking...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/** Executes one turn: persist user msg, generate, persist assistant msg. */
private fun send(
    appState: AppState,
    chat: com.treg.llmpersonalization.data.Chat,
    rawInput: String,
    setInput: (String) -> Unit,
    setBusy: (Boolean) -> Unit,
    setMessages: (List<Message>) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val l = appState.llama ?: return
    val e = appState.extractor ?: return
    val b = appState.beliefs ?: return
    val store = appState.chatStore ?: return
    val prompt = rawInput.trim()
    if (prompt.isEmpty()) return

    val userMsg = Message(MessageRole.USER, prompt, null, System.currentTimeMillis())

    // Auto-title on first message
    if (chat.messages.isEmpty() && chat.title == "New Chat") {
        chat.title = prompt.take(40).let { if (prompt.length > 40) "$it…" else it }
    }
    chat.messages.add(userMsg)
    store.save(chat)
    appState.notifyChatUpdated()

    setMessages(chat.messages.toList())
    setInput("")
    setBusy(true)

    scope.launch {
        val trace = withContext(Dispatchers.IO) {
            ChatOrchestrator(l, e, b).chat(
                message = prompt,
                user = appState.currentUser?.name ?: "default",
                softGate = appState.softGate,
                strictGate = appState.strictGate,
                useBeliefs = appState.useBeliefs,
                useMining = appState.useMining
            )
        }
        appState.lastTrace = trace

        val assistantMsg = Message(
            MessageRole.ASSISTANT,
            trace.response,
            trace.think,
            System.currentTimeMillis()
        )
        chat.messages.add(assistantMsg)
        store.save(chat)
        appState.notifyChatUpdated()

        setMessages(chat.messages.toList())
        setBusy(false)
    }
}