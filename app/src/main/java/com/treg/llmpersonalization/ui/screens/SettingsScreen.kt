package com.treg.llmpersonalization.ui.screens

import androidx.compose.foundation.clickable
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
fun SettingsScreen(
    appState: AppState,
    onBack: () -> Unit,
    onOpenDev: () -> Unit,
    onOpenBeliefs: () -> Unit,
    onSwitchUser: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Profile")
            ListItem(
                headlineContent = { Text("Current user") },
                supportingContent = { Text(appState.currentUser?.name ?: "none") },
                modifier = Modifier.clickable { onSwitchUser() },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeader("Behavior")
            ListItem(
                headlineContent = { Text("Soft gate") },
                supportingContent = { Text("Correct contradicting assertions against reference") },
                trailingContent = {
                    Switch(
                        checked = appState.softGate,
                        onCheckedChange = { appState.softGate = it }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            )
            ListItem(
                headlineContent = { Text("Strict gate") },
                supportingContent = { Text("Reference overrides personal beliefs when contradicting") },
                trailingContent = {
                    Switch(
                        checked = appState.strictGate,
                        onCheckedChange = { appState.strictGate = it }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            )
            ListItem(
                headlineContent = { Text("Apply beliefs") },
                supportingContent = { Text("Use personal belief table when answering questions") },
                trailingContent = {
                    Switch(
                        checked = appState.useBeliefs,
                        onCheckedChange = { appState.useBeliefs = it }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            )
            ListItem(
                headlineContent = { Text("Mine beliefs") },
                supportingContent = { Text("Learn facts from user assertions") },
                trailingContent = {
                    Switch(
                        checked = appState.useMining,
                        onCheckedChange = { appState.useMining = it }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeader("Data")
            ListItem(
                headlineContent = { Text("Belief management") },
                supportingContent = { Text("View and reset mined beliefs") },
                modifier = Modifier.clickable { onOpenBeliefs() },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeader("Developer")
            ListItem(
                headlineContent = { Text("Developer panel") },
                supportingContent = { Text("Debug trace and raw mined state") },
                modifier = Modifier.clickable { onOpenDev() },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}