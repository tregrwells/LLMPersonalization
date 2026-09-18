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
import com.treg.llmpersonalization.engine.GenerationConfig
import com.treg.llmpersonalization.engine.LlamaBridge
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
    var status by remember { mutableStateOf("Preparing assets...") }
    var input by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val modelFile = withContext(Dispatchers.IO) {
            copyAssetIfNeeded(context, "qwen3.5-0.8b-q8_0.gguf")
        }
        status = "Loading model..."
        val result = withContext(Dispatchers.IO) {
            LlamaBridge.load(modelFile.absolutePath)
        }
        loaded = result
        status = if (result != null)
            "Ready (vocab=${result.vocabSize}, n_ctx=${result.contextSize})"
        else "LOAD FAILED — check logcat"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(status, style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            enabled = loaded != null && !busy
        )

        Button(
            onClick = {
                val h = loaded ?: return@Button
                val prompt = input.trim()
                if (prompt.isEmpty()) return@Button
                busy = true
                response = "(generating...)"
                scope.launch {
                    val route = GenerationConfig.route(hasBelief = false)
                    val out = withContext(Dispatchers.IO) {
                        LlamaBridge.generate(
                            h, prompt,
                            targetToken = null, offset = null,
                            maxTokens = route.maxTokens,
                            temperature = route.temperature
                        )
                    }
                    response = out
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