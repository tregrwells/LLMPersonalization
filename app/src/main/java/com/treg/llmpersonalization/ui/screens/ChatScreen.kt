package com.treg.llmpersonalization.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.treg.llmpersonalization.AppState
import com.treg.llmpersonalization.logic.ChatOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    var softGate by remember { mutableStateOf(true) }
    var strictGate by remember { mutableStateOf(true) }
    var useBeliefs by remember { mutableStateOf(true) }
    var useMining by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Gate chips ---
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = softGate, onClick = { softGate = !softGate },
                label = { Text(if (softGate) "soft ON" else "soft OFF") })
            FilterChip(selected = strictGate, onClick = { strictGate = !strictGate },
                label = { Text(if (strictGate) "strict ON" else "strict OFF") })
            FilterChip(selected = useBeliefs, onClick = { useBeliefs = !useBeliefs },
                label = { Text(if (useBeliefs) "beliefs ON" else "beliefs OFF") })
            FilterChip(selected = useMining, onClick = { useMining = !useMining },
                label = { Text(if (useMining) "mine ON" else "mine OFF") })
        }

        // --- Response area ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                response.ifEmpty { "Enter text here." },
                style = MaterialTheme.typography.bodyLarge,
                color = if (response.isEmpty())
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onBackground
            )
        }

        // --- Input ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Enter text here") },
                modifier = Modifier.weight(1f),
                enabled = !busy
            )
            Button(
                onClick = {
                    val l = appState.llama ?: return@Button
                    val e = appState.extractor ?: return@Button
                    val b = appState.beliefs ?: return@Button
                    val prompt = input.trim()
                    if (prompt.isEmpty()) return@Button
                    busy = true
                    response = "(generating...)"
                    input = ""
                    scope.launch {
                        val trace = withContext(Dispatchers.IO) {
                            ChatOrchestrator(l, e, b).chat(
                                message = prompt,
                                user = appState.currentUser?.name ?: "default",
                                softGate = softGate,
                                strictGate = strictGate,
                                useBeliefs = useBeliefs,
                                useMining = useMining
                            )
                        }
                        response = trace.response
                        appState.lastTrace = trace
                        busy = false
                    }
                },
                enabled = !busy && input.isNotBlank(),
                modifier = Modifier.align(Alignment.CenterVertically)
            ) { Text(if (busy) "..." else "Send") }
        }
    }
}