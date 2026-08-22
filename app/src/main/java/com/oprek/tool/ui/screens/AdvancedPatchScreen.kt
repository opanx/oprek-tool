package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.core.FileAnalyzer
import com.oprek.tool.core.NativeLib
import com.oprek.tool.ui.theme.*
import com.oprek.tool.utils.PatchRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedPatchScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var offsetHex by remember { mutableStateOf("") }
    var searchStr by remember { mutableStateOf("") }
    var replaceStr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var hasNative by remember { mutableStateOf(false) }
    var recommendations by remember { mutableStateOf<List<PatchRecommendation>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var patchedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(SharedFileState.revision) {
        try { NativeLib.patchNop(byteArrayOf(0, 0, 0, 0), 0); hasNative = true } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🔧 Advanced Patch", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            if (!hasNative) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp)) {
                    Text("⚠️ Native lib not loaded", modifier = Modifier.padding(12.dp), color = AccentRed)
                }
            }

            // Auto-detect section
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖 Auto-Detect Patches", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange, modifier = Modifier.weight(1f))
                        if (isScanning) CircularProgressIndicator(Modifier.size(18.dp), color = AccentOrange, strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Scans binary for login bypass, license checks, time checks, anti-debug", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        isScanning = true
                        scope.launch(Dispatchers.Default) {
                            try {
                                val file = SharedFileState.findFile(context)
                                if (file != null) {
                                    val data = withContext(Dispatchers.IO) {
                                        val raf = java.io.RandomAccessFile(file, "r")
                                        val size = minOf(raf.length(), 5_000_000L).toInt()
                                        val buf = ByteArray(size)
                                        raf.readFully(buf)
                                        raf.close()
                                        buf
                                    }
                                    recommendations = withContext(Dispatchers.Default) { com.oprek.tool.utils.PatternDetector.detectPatchPatterns(data) }
                                }
                            } catch (e: Exception) {
                                result = "Scan error: ${e.message}"
                            }
                            isScanning = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentOrange), shape = RoundedCornerShape(8.dp),
                        enabled = !isScanning) {
                        Icon(Icons.Default.Rocket, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Binary", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Patch recommendations
            if (recommendations.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Found ${recommendations.size} patches", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                scope.launch(Dispatchers.Default) {
                                    val file = SharedFileState.findFile(context) ?: return@launch
                                    if (file.length() > 100 * 1024 * 1024) {

                                        // File too large for in-memory processing

                                    }

                                    val data = withContext(Dispatchers.IO) { if (file.isFile) file.readBytes() else byteArrayOf() }
                                    var count = 0
                                    for (rec in recommendations) {
                                        try {
                                            when {
                                                rec.suggestedPatch.contains("NOP") -> { NativeLib.patchNop(data, rec.offset); count++ }
                                                rec.suggestedPatch.contains("MOV W0") -> { NativeLib.patchRetZero(data, rec.offset); count++ }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    if (count > 0) {
                                        FileAnalyzer.patchBytes(file, 0, data)
                                        patchedCount = count
                                        result = "Patched $count patterns!"
                                    }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed), shape = RoundedCornerShape(8.dp)) {
                                Text("Patch All ($recommendations.size)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        recommendations.forEach { rec ->
                            val severityColor = when (rec.severity) { "high" -> AccentRed; "medium" -> AccentOrange; else -> AccentCyan }
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(DarkSurface).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${rec.type} [${rec.severity.uppercase()}]", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = severityColor)
                                    Text(rec.description, fontSize = 10.sp, color = TextSecondary)
                                    Text("0x${"%08X".format(rec.offset)}: ${rec.originalBytes}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentCyan)
                                }
                                IconButton(onClick = {
                                    scope.launch(Dispatchers.Default) {
                                        val file = SharedFileState.findFile(context) ?: return@launch
                                        if (file.length() > 100 * 1024 * 1024) {

                                            // File too large for in-memory processing

                                        }

                                        val data = withContext(Dispatchers.IO) { file.readBytes() }
                                        try {
                                            when {
                                                rec.suggestedPatch.contains("NOP") -> NativeLib.patchNop(data, rec.offset)
                                                rec.suggestedPatch.contains("MOV W0") -> NativeLib.patchRetZero(data, rec.offset)
                                            }
                                            FileAnalyzer.patchBytes(file, 0, data)
                                            result = "Patched ${rec.type} at 0x${"%08X".format(rec.offset)}"
                                        } catch (e: Exception) { result = "Error: ${e.message}" }
                                    }
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Build, "Patch", Modifier.size(16.dp), tint = severityColor)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Quick patches
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("⚡ Manual Patches", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = offsetHex, onValueChange = { offsetHex = it }, label = { Text("Offset (hex)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentPurple))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickPatchBtn("NOP", AccentGreen) { applyQuickPatch(context, offsetHex, "nop") { result = it } }
                        QuickPatchBtn("RET", AccentOrange) { applyQuickPatch(context, offsetHex, "ret") { result = it } }
                        QuickPatchBtn("RET 0", AccentCyan) { applyQuickPatch(context, offsetHex, "ret0") { result = it } }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // String patch
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📝 String Patch", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = searchStr, onValueChange = { searchStr = it }, label = { Text("Search string") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = replaceStr, onValueChange = { replaceStr = it }, label = { Text("Replace string") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch(Dispatchers.Default) {
                            val file = SharedFileState.findFile(context) ?: return@launch
                            if (file.length() > 100 * 1024 * 1024) {

                                // File too large for in-memory processing

                            }

                            val data = withContext(Dispatchers.IO) { file.readBytes() }
                            val searchBytes = searchStr.toByteArray()
                            val replaceBytes = replaceStr.toByteArray().copyOf(searchBytes.size)
                            var count = 0
                            var pos = 0
                            while (pos <= data.size - searchBytes.size) {
                                var found = true
                                for (j in searchBytes.indices) { if (data[pos + j] != searchBytes[j]) { found = false; break } }
                                if (found) { System.arraycopy(replaceBytes, 0, data, pos, replaceBytes.size); count++ }
                                pos++
                            }
                            if (count > 0) {
                                FileAnalyzer.patchBytes(file, 0, data)
                                result = "Replaced $count occurrences"
                            } else result = "String not found"
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), shape = RoundedCornerShape(12.dp)) {
                        Text("Patch String", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (result.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.1f)), shape = RoundedCornerShape(12.dp)) {
                    Text(result, modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentGreen)
                }
            }
        }
    }
}

@Composable
fun QuickPatchBtn(label: String, color: Color, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun applyQuickPatch(context: android.content.Context, offsetHex: String, type: String, onResult: (String) -> Unit) {
    val file = SharedFileState.findFile(context)
    if (file == null) { onResult("No file loaded"); return }
    try {
        val offset = offsetHex.removePrefix("0x").removePrefix("0X").toLong(16)
        if (file.length() > 100 * 1024 * 1024) {

            // File too large for in-memory processing

        }

        val data = file.readBytes()
        when (type) {
            "nop" -> NativeLib.patchNop(data, offset)
            "ret" -> NativeLib.patchRet(data, offset)
            "ret0" -> NativeLib.patchRetZero(data, offset)
        }
        FileAnalyzer.patchBytes(file, 0, data)
        onResult("Patched $type at 0x${"%08X".format(offset)}")
    } catch (e: Exception) { onResult("Error: ${e.message}") }
}
