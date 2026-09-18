package com.treg.llmpersonalization

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.treg.llmpersonalization.engine.ExtractorBridge
import com.treg.llmpersonalization.engine.LlamaBridge
import com.treg.llmpersonalization.logic.BeliefStore
import com.treg.llmpersonalization.logic.ChatOrchestrator
import com.treg.llmpersonalization.ui.theme.LLMPersonalizationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LLMPersonalizationTheme {
                Surface(modifier = Modifier.fillMaxSize()) { ChatScreen() }
            }
        }
    }
}

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<LlamaBridge.Loaded?>(null) }
    var extractor by remember { mutableStateOf<ExtractorBridge?>(null) }
    var beliefs by remember { mutableStateOf<BeliefStore?>(null) }
    var status by remember { mutableStateOf("Preparing...") }

    var user by remember { mutableStateOf("treg") }
    var softGate by remember { mutableStateOf(true) }
    var strictGate by remember { mutableStateOf(true) }
    var useBeliefs by remember { mutableStateOf(true) }
    var useMining by remember { mutableStateOf(true) }

    var input by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var debug by remember { mutableStateOf("") }
    var minedPanel by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun refreshMined() {
        minedPanel = beliefs?.formatMined() ?: "(store not loaded)"
    }

    LaunchedEffect(Unit) {
        val modelFile = withContext(Dispatchers.IO) {
            copyAssetIfNeeded(context, "qwen3.5-0.8b-q8_0.gguf")
        }
        val onnxFile = withContext(Dispatchers.IO) {
            copyAssetIfNeeded(context, "extractor_int8.onnx")
        }
        val schemaJson = withContext(Dispatchers.IO) {
            context.assets.open("rebel_schema.json").bufferedReader().readText()
        }
        val beliefJson = withContext(Dispatchers.IO) {
            context.assets.open("belief_tables_v1_8.json").bufferedReader().readText()
        }

        status = "Loading base model..."
        val l = withContext(Dispatchers.IO) { LlamaBridge.load(modelFile.absolutePath) }
        if (l == null) { status = "Model load FAILED"; return@LaunchedEffect }

        status = "Loading extractor..."
        val e = withContext(Dispatchers.IO) {
            ExtractorBridge.load(context, onnxFile, schemaJson)
        }

        val b = BeliefStore.load(context, beliefJson, "treg")

        loaded = l
        extractor = e
        beliefs = b
        status = "Ready (vocab=${l.vocabSize}, user=$user)"

        refreshMined()
    }

    LaunchedEffect(user) { refreshMined() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(status, style = MaterialTheme.typography.bodySmall)

        // --- User dropdown ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = beliefs != null
                ) { Text("User: $user") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    (beliefs?.users() ?: emptyList()).forEach { u ->
                        DropdownMenuItem(
                            text = { Text(u) },
                            onClick = { user = u; expanded = false }
                        )
                    }
                }
            }
        }

        // --- Toggles ---
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = softGate, onClick = { softGate = !softGate },
                label = { Text("soft ${if (softGate) "ON" else "OFF"}", style = MaterialTheme.typography.labelSmall) })
            FilterChip(selected = strictGate, onClick = { strictGate = !strictGate },
                label = { Text("strict ${if (strictGate) "ON" else "OFF"}", style = MaterialTheme.typography.labelSmall) })
            FilterChip(selected = useBeliefs, onClick = { useBeliefs = !useBeliefs },
                label = { Text("beliefs ${if (useBeliefs) "ON" else "OFF"}", style = MaterialTheme.typography.labelSmall) })
            FilterChip(selected = useMining, onClick = { useMining = !useMining },
                label = { Text("mine ${if (useMining) "ON" else "OFF"}", style = MaterialTheme.typography.labelSmall) })
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            enabled = loaded != null && !busy
        )

        Button(
            onClick = {
                val l = loaded ?: return@Button
                val e = extractor ?: return@Button
                val b = beliefs ?: return@Button
                val prompt = input.trim()
                if (prompt.isEmpty()) return@Button
                busy = true
                response = "(generating...)"
                debug = ""

                scope.launch {
                    val trace = withContext(Dispatchers.IO) {
                        ChatOrchestrator(l, e, b).chat(
                            message = prompt, user = user,
                            softGate = softGate, strictGate = strictGate,
                            useBeliefs = useBeliefs, useMining = useMining
                        )
                    }
                    response = trace.response
                    debug = buildString {
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
                    refreshMined()
                    busy = false
                }
            },
            enabled = loaded != null && !busy && input.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Generating..." else "Send") }

        Text("Response", style = MaterialTheme.typography.titleSmall)
        Text(
            response.ifEmpty { "(nothing yet)" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        )

        Divider()
        Text("Debug", style = MaterialTheme.typography.titleSmall)
        Text(
            debug.ifEmpty { "(empty)" },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().height(120.dp).verticalScroll(rememberScrollState())
        )

        Text("Mined for $user", style = MaterialTheme.typography.titleSmall)
        Text(
            minedPanel,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().height(100.dp).verticalScroll(rememberScrollState())
        )
    }
}

private fun copyAssetIfNeeded(
    context: android.content.Context,
    assetName: String,
    destName: String = assetName
): File {
    val dest = File(context.filesDir, destName)
    if (dest.exists() && dest.length() > 0) return dest
    val tmp = File(context.filesDir, "$destName.tmp")
    if (tmp.exists()) tmp.delete()
    context.assets.open(assetName).use { input ->
        FileOutputStream(tmp).use { output ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                output.write(buf, 0, n)
            }
            output.flush()
            output.fd.sync()
        }
    }
    tmp.renameTo(dest)
    return dest
}