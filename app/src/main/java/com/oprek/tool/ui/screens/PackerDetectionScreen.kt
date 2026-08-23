package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
fun PackerDetectionScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var packerName by remember { mutableStateOf("") }
    var entropy by remember { mutableDoubleStateOf(0.0) }
    var entropyRating by remember { mutableStateOf("") }
    var entropyColor by remember { mutableStateOf(AccentGreen) }
    var sections by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasNative by remember { mutableStateOf(false) }

    LaunchedEffect(SharedFileState.revision) {
        try { NativeLib.detectPacker(byteArrayOf()); hasNative = true } catch (_: Exception) {}
        val file = SharedFileState.findFile(context) ?: return@LaunchedEffect
        val data = withContext(Dispatchers.IO) { file.readBytes().copyOf(minOf(file.length().toInt(), 2_000_000)) }
        entropy = withContext(Dispatchers.Default) { NativeLib.entropy(data) }
        val packerId = withContext(Dispatchers.Default) { NativeLib.detectPacker(data) }
        packerName = if (packerId > 0) withContext(Dispatchers.Default) { NativeLib.packerName(packerId) } else "None detected"
        sections = withContext(Dispatchers.Default) { NativeLib.elfGetSections(file.readBytes()).toList() }

        entropyRating = when {
            entropy > 7.5 -> "🔴 Very high — likely encrypted/packed"
            entropy > 7.0 -> "🟠 High — possible packing/obfuscation"
            entropy > 6.0 -> "🟡 Moderate — some obfuscation possible"
            entropy > 4.0 -> "🟢 Normal — typical code/data"
            else -> "🔵 Low — repetitive or padding"
        }
        entropyColor = when {
            entropy > 7.5 -> AccentRed
            entropy > 7.0 -> AccentOrange
            entropy > 6.0 -> AccentOrange
            entropy > 4.0 -> AccentGreen
            else -> AccentCyan
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🔍 Packer Detection", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            // Entropy
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("📈 Entropy Analysis", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPurple)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { (entropy / 8.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(16.dp), color = entropyColor, trackColor = DarkSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0.0", fontSize = 10.sp, color = TextMuted)
                        Text("%.4f".format(entropy), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = entropyColor, fontFamily = FontFamily.Monospace)
                        Text("8.0", fontSize = 10.sp, color = TextMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(entropyRating, fontSize = 13.sp, color = entropyColor)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Packer detection
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("🧩 Packer Detection", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentCyan)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (packerName == "None detected") Icons.Default.CheckCircle else Icons.Default.Warning, null,
                            tint = if (packerName == "None detected") AccentGreen else AccentRed, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(packerName, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (packerName == "None detected") AccentGreen else AccentRed)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Section entropy
            if (sections.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📊 Section Entropy", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentOrange)
                        Spacer(Modifier.height(8.dp))
                        Text("High entropy sections (>7.0) may indicate encryption", fontSize = 11.sp, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        sections.take(20).forEach { sec ->
                            val parts = sec.split("|")
                            val name = parts.getOrElse(0) { "?" }
                            val size = parts.getOrElse(3) { "0" }.toLongOrNull() ?: 0
                            if (size > 0 && (name.contains(".text") || name.contains(".data") || name.contains(".rodata") || name.contains(".bss"))) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(name, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentCyan, modifier = Modifier.width(80.dp))
                                    Text("${size}B", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tips
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("💡 Detection Tips", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                    Spacer(Modifier.height(8.dp))

                    Text("• UPX: string \"UPX!\" in sections, entropy ~6-7", fontSize = 11.sp, color = TextSecondary)
                    Text("• Themida: high entropy (>7.5), unusual section names", fontSize = 11.sp, color = TextSecondary)
                    Text("• OLLVM: many NOPs, control flow flattening, entropy ~6-7", fontSize = 11.sp, color = TextSecondary)
                    Text("• Encrypted: very high entropy (>7.5) across all sections", fontSize = 11.sp, color = TextSecondary)
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "Packer detection complete" },
                filename = "packer.txt",
                subfolder = "analysis"
            )

        }
    }
}
