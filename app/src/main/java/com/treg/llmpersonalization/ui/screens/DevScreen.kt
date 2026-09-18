package com.treg.llmpersonalization.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.treg.llmpersonalization.AppState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevScreen(appState: AppState, onBack: () -> Unit) {
    val trace = appState.lastTrace

    val debugText = buildString {
        if (trace == null) {
            append("(no generation yet)")
        } else {
            append("kind: ${trace.kind}\n")
            trace.mined?.let { append("MINED: ${it.concept} -> '${it.target.trim()}' x${it.count}\n") }
            append("concept: ${trace.conceptKey}\n")
            trace.conf?.let { append("conf: ${"%.3f".format(it)}\n") }
            trace.source?.let { append("source: $it\n") }
            trace.gate?.let { append("gate: $it\n") }
            trace.applied?.let { append("applied: '${it.trim()}'\n") }
            trace.gap?.let { append("gap: ${"%.2f".format(it)}\n") }
            trace.offset?.let { append("offset: ${"%.2f".format(it)}\n") }
            trace.skipped?.let { append("skipped: $it\n") }
        }
    }

    val minedText = appState.beliefs?.formatMined() ?: "(store not loaded)"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { appState.lastTrace = null }) {
                        Text("Clear trace")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("Last generation")
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    debugText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("Mined beliefs (raw)")
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    minedText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}