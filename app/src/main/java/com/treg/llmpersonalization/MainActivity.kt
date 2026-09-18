package com.treg.llmpersonalization

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.treg.llmpersonalization.engine.LlamaBridge
import com.treg.llmpersonalization.ui.theme.LLMPersonalizationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LLMPersonalizationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoadScreen()
                }
            }
        }
    }
}

@Composable
fun LoadScreen() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Idle") }
    var loaded by remember { mutableStateOf<LlamaBridge.Loaded?>(null) }

    LaunchedEffect(Unit) {
        status = "Preparing assets..."
        val modelFile = withContext(Dispatchers.IO) {
            copyAssetIfNeeded(context, "qwen3.5-0.8b-q8_0.gguf")
        }
        status = "Loading model (${modelFile.length() / 1_000_000} MB)..."
        val result = withContext(Dispatchers.IO) {
            LlamaBridge.load(modelFile.absolutePath)
        }
        loaded = result
        status = if (result != null) {
            "Model loaded\nvocab = ${result.vocabSize}\nn_ctx = ${result.contextSize}"
        } else {
            "Model load FAILED - check logcat for LlamaJNI"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "LLM Personalization - Phase C Session 1",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (loaded != null) {
            Text(
                text = "Ready for Session 2 (generation).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Copy an asset to filesDir on first launch. Streams in 1 MB chunks to
 * avoid loading 812 MB into memory.
 *
 * Android's AssetManager caps single-file reads at ~1 MB, so a bulk read
 * is not possible. Chunked stream is mandatory for files this size.
 */
private fun copyAssetIfNeeded(
    context: android.content.Context,
    assetName: String,
    destName: String = assetName,
): File {
    val dest = File(context.filesDir, destName)
    if (dest.exists() && dest.length() > 0) return dest

    val tmp = File(context.filesDir, "$destName.tmp")
    if (tmp.exists()) tmp.delete()

    context.assets.open(assetName).use { input ->
        FileOutputStream(tmp).use { output ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                output.write(buffer, 0, n)
            }
            output.flush()
            output.fd.sync()
        }
    }
    tmp.renameTo(dest)
    return dest
}