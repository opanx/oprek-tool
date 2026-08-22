package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.core.NativeLib
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.oprek.tool.ui.components.OutputButton
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisassemblerScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var archIndex by remember { mutableStateOf(1) } // ARM64 default
    var modeIndex by remember { mutableStateOf(2) } // ARM64 default
    var offsetHex by remember { mutableStateOf("0") }
    var countStr by remember { mutableStateOf("100") }
    var result by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var hasNative by remember { mutableStateOf(false) }
    var autoDetectedArch by remember { mutableStateOf("") }

    val arches = listOf("ARM (32)", "ARM64", "x86")
    val modes = listOf("ARM", "Thumb", "ARM64", "x86-64", "x86-32")

    LaunchedEffect(SharedFileState.revision) {
        try {
            NativeLib.elfValidate(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))
            hasNative = true
        } catch (_: Exception) {}
    }

    // Auto-detect architecture when file changes
    LaunchedEffect(SharedFileState.revision) {
        val file = context.cacheDir.listFiles { f -> f.isFile && f.length() > 0 }?.maxByOrNull { it.lastModified() }
        if (file != null) {
            try {
                // Read just the ELF header to detect arch
                val headerBytes = StreamingIO.readRange(file, 0, 64)
                if (headerBytes.size >= 18 && headerBytes[0] == 0x7F.toByte() && headerBytes[1] == 0x45.toByte()) {
                    val isLE = headerBytes[5] == 1.toByte()
                    val machine = if (isLE) {
                        (headerBytes[18].toInt() and 0xFF) or ((headerBytes[19].toInt() and 0xFF) shl 8)
                    } else {
                        ((headerBytes[18].toInt() and 0xFF) shl 8) or (headerBytes[19].toInt() and 0xFF)
                    }
                    val archMode = NativeLib.detectArchFromElf(machine)
                    archIndex = archMode.first
                    modeIndex = archMode.second
                    autoDetectedArch = when (machine) {
                        0x28 -> "ARM"
                        0xB7 -> "AArch64"
                        0x03 -> "x86"
                        0x3E -> "x86_64"
                        else -> "Unknown"
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔬 Disassembler (Capstone)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            if (!hasNative) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp)) {
                    Text("⚠️ Capstone native library not loaded. Disassembly unavailable.", modifier = Modifier.padding(12.dp), color = AccentRed, fontSize = 13.sp)
                }
            }

            if (autoDetectedArch.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp)) {
                    Text("✅ Auto-detected: $autoDetectedArch", modifier = Modifier.padding(12.dp), color = AccentGreen, fontSize = 13.sp)
                }
            }

            // Config
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Architecture", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        arches.forEachIndexed { idx, name ->
                            FilterChip(selected = archIndex == idx, onClick = { archIndex = idx },
                                label = { Text(name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.3f)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Mode", fontSize = 12.sp, color = AccentCyan)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        modes.forEachIndexed { idx, name ->
                            FilterChip(selected = modeIndex == idx, onClick = { modeIndex = idx },
                                label = { Text(name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.3f)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = offsetHex, onValueChange = { offsetHex = it },
                            label = { Text("Offset (hex)") }, modifier = Modifier.weight(1f), singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentPurple))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = countStr, onValueChange = { countStr = it },
                            label = { Text("Count") }, modifier = Modifier.width(80.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentPurple))
                    }
                }
            }

            // Disassemble button
            Button(onClick = {
                isLoading = true
                scope.launch(Dispatchers.Default) {
                    try {
                        val file = SharedFileState.findFile(context)
                        if (file == null || !file.exists() || file.isDirectory) {
                            result = "No file loaded. Open a file first."
                            isLoading = false
                            return@launch
                        }
                        val offset = try {
                            offsetHex.removePrefix("0x").removePrefix("0X").toLong(16)
                        } catch (_: Exception) { 0L }
                        val count = countStr.toIntOrNull() ?: 100

                        // Streaming: read only what's needed
                        val readSize = minOf(count * 8L, file.length() - offset, 20000L).toInt()
                        if (readSize <= 0) {
                            result = "Offset out of range"
                            isLoading = false
                            return@launch
                        }
                        val data = StreamingIO.readRange(file, offset, readSize)
                        val res = withContext(Dispatchers.IO) {
                            NativeLib.disassemble(data, offset, archIndex, modeIndex, count)
                        }
                        result = if (res.isBlank()) "No instructions disassembled" else res
                    } catch (e: Exception) {
                        result = "Error: ${e.message}"
                    }
                    isLoading = false
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(12.dp), enabled = !isLoading && hasNative) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Code, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Disassemble", fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(12.dp))

            // Result
            if (result.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Output (${result.lines().size} lines)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                        Spacer(Modifier.height(8.dp))
                        Text(result, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutputButton(
                content = { result },
                filename = "disasm.txt",
                subfolder = "disasm"
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
