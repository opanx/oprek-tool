package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntropyAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var results by remember { mutableStateOf(listOf<Pair<String, Double>>()) }
    var loaded by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try { val b = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf(); withContext(Dispatchers.Main) { fileBytes = b; loaded = true } } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Entropy Analyzer", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) { Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open Binary") } }
            if (loaded) {
                Button(onClick = {
                    val blockSize = maxOf(fileBytes.size / 64, 256)
                    val r = mutableListOf<Pair<String, Double>>()
                    for (i in fileBytes.indices step blockSize) {
                        val end = minOf(i + blockSize, fileBytes.size)
                        val chunk = fileBytes.copyOfRange(i, end)
                        r.add("0x${"%06X".format(i)}-${"%06X".format(end)}" to calcEntropy(chunk))
                    }
                    results = r
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("Analyze Entropy") }
                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(results) { _, (range, ent) ->
                        val bar = "█".repeat((ent * 3).toInt())
                        val color = when { ent > 7.0 -> AccentRed; ent > 6.0 -> AccentOrange; ent > 5.0 -> AccentCyan; else -> AccentGreen }
                        Card(Modifier.fillMaxWidth().padding(vertical = 1.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(4.dp)) {
                            Row(Modifier.padding(6.dp)) {
                                Text("${range.padEnd(20)} ", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("${String.format("%.4f", ent)} ", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(bar, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "${results.size} entropy blocks analyzed" },
                filename = "entropy.txt",
                subfolder = "analysis"
            )

    }
}

private fun calcEntropy(data: ByteArray): Double {
    if (data.isEmpty()) return 0.0; val freq = IntArray(256); for (b in data) freq[b.toInt() and 0xFF]++
    var e = 0.0; for (f in freq) if (f > 0) { val p = f.toDouble() / data.size; e -= p * ln(p) / ln(2.0) }; return e
}
