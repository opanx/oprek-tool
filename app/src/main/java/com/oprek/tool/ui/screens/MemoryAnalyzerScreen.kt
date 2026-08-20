package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.core.NativeLib
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryAnalyzerScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var entropyVal by remember { mutableDoubleStateOf(0.0) }
    var packerName by remember { mutableStateOf("") }
    var byteFreq by remember { mutableStateOf<List<Pair<Int, Long>>>(emptyList()) }
    var hasNative by remember { mutableStateOf(false) }
    var hasFile by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { NativeLib.entropy(byteArrayOf(1, 2, 3)); hasNative = true } catch (_: Exception) {}
        val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull()
        hasFile = file != null
        if (file != null && hasNative) {
            val data = withContext(Dispatchers.IO) { file.readBytes().copyOf(minOf(file.length().toInt(), 1048576)) }
            entropyVal = withContext(Dispatchers.Default) { NativeLib.entropy(data) }
            val packerId = withContext(Dispatchers.Default) { NativeLib.detectPacker(data) }
            packerName = if (packerId > 0) withContext(Dispatchers.Default) { NativeLib.packerName(packerId) } else "None detected"
            // Byte frequency top 16
            val freq = LongArray(256)
            for (b in data) freq[b.toInt() and 0xFF]++
            byteFreq = freq.mapIndexed { i, c -> i to c }.sortedByDescending { it.second }.take(16)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📊 Memory Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (!hasNative || !hasFile) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp)) {
                    Text("⚠️ Load a file + native lib required", modifier = Modifier.padding(12.dp), color = AccentOrange, fontSize = 13.sp)
                }
            }

            // Entropy
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("📈 Entropy Analysis", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPurple)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (entropyVal / 8.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = when { entropyVal > 7.0 -> AccentRed; entropyVal > 5.0 -> AccentOrange; else -> AccentGreen },
                        trackColor = DarkSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0.0 (uniform)", fontSize = 10.sp, color = TextMuted)
                        Text("%.4f".format(entropyVal), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AccentGreen, fontFamily = FontFamily.Monospace)
                        Text("8.0 (random)", fontSize = 10.sp, color = TextMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(when {
                        entropyVal > 7.5 -> "🔴 Very high — likely encrypted or compressed"
                        entropyVal > 6.0 -> "🟡 High — possible obfuscation"
                        entropyVal > 4.0 -> "🟢 Normal — typical code/data"
                        else -> "🔵 Low — repetitive or padding"
                    }, fontSize = 12.sp, color = TextSecondary)
                }
            }

            // Packer detection
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("🔍 Packer Detection", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Result: $packerName", fontSize = 14.sp, color = if (packerName == "None detected") AccentGreen else AccentRed, fontWeight = FontWeight.Bold)
                }
            }

            // Byte frequency
            if (byteFreq.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📊 Byte Frequency (Top 16)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentOrange)
                        Spacer(Modifier.height(8.dp))
                        val maxFreq = byteFreq.maxOfOrNull { it.second } ?: 1
                        byteFreq.forEach { (byte, count) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("0x${"%02X".format(byte)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentPurple, modifier = Modifier.width(50.dp))
                                LinearProgressIndicator(
                                    progress = { (count.toFloat() / maxFreq).coerceIn(0f, 1f) },
                                    modifier = Modifier.weight(1f).height(8.dp),
                                    color = AccentOrange, trackColor = DarkSurface
                                )
                                Text("$count", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.width(60.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { results.joinToString("\n") },
                filename = "memory_analysis.txt",
                subfolder = "analysis"
            )

        }
    }
}
