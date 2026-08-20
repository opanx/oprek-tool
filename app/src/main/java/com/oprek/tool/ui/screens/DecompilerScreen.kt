package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import com.oprek.tool.engine.AnalysisEngine
import com.oprek.tool.core.NativeLib
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.engine.DecompilerEngine
import com.oprek.tool.ui.components.OutputButton
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecompilerScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var result by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var funcName by remember { mutableStateOf("") }
    var hasNative by remember { mutableStateOf(false) }
    var showAddresses by remember { mutableStateOf(false) }
    var archIndex by remember { mutableIntStateOf(1) } // ARM64 default
    var blockCount by remember { mutableIntStateOf(0) }
    var varCount by remember { mutableIntStateOf(0) }
    var lineCount by remember { mutableIntStateOf(0) }

    // Symbol list from ELF
    var symbols by remember { mutableStateOf(listOf<String>()) }
    var selectedSymbol by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try { NativeLib.elfValidate(byteArrayOf(0x7F, 0x45, 0x4C, 0x46)); hasNative = true } catch (_: Exception) {}
        // Load symbols
        val file = context.cacheDir.listFiles()?.firstOrNull()
        if (file != null) {
            try {
                val analysis = withContext(Dispatchers.IO) { AnalysisEngine.analyzeElf(file) }
                val funcSyms = analysis.symbols.filter { s -> s.isFunc && s.stValue > 0 }.map { s -> "${s.stName} @ 0x${java.lang.Long.toHexString(s.stValue)}" }
                val dynSyms = analysis.dynsym.filter { s -> s.isFunc && s.stValue > 0 }.map { s -> "${s.stName} @ 0x${java.lang.Long.toHexString(s.stValue)}" }
                symbols = (funcSyms + dynSyms).distinct().take(500)
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 Pseudo-C Decompiler", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    if (result.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("decompiled", result))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // Architecture selector
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Target Architecture", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ARM (32)", "ARM64", "x86").forEachIndexed { idx, name ->
                            FilterChip(selected = archIndex == idx, onClick = { archIndex = idx },
                                label = { Text(name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.3f)))
                        }
                    }
                }
            }

            // Function selector
            if (symbols.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Select Function (${symbols.size} available)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                        Spacer(Modifier.height(8.dp))
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = selectedSymbol.ifEmpty { "All functions (disassemble from start)" },
                                onValueChange = { selectedSymbol = it },
                                label = { Text("Function") }, modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen)
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("All functions", fontSize = 11.sp) }, onClick = {
                                    selectedSymbol = ""; funcName = ""; expanded = false
                                })
                                symbols.take(100).forEach { sym ->
                                    DropdownMenuItem(text = { Text(sym, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }, onClick = {
                                        selectedSymbol = sym
                                        funcName = sym.split(" @").first()
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                }
            }

            // Function name (manual input)
            if (symbols.isEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Function Name", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = funcName, onValueChange = { funcName = it },
                            label = { Text("e.g. main, sub_12345 (empty = all)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentPurple))
                    }
                }
            }

            // Options
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show addresses", fontSize = 12.sp, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = showAddresses, onCheckedChange = { showAddresses = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentGreen))
                }
            }

            // Progress
            if (progress.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = AccentPurple, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(progress, fontSize = 12.sp, color = AccentPurple)
                    }
                }
            }

            // Decompile button
            Button(onClick = {
                isLoading = true
                scope.launch(Dispatchers.Default) {
                    try {
                        val file = context.cacheDir.listFiles()?.firstOrNull()
                        if (file == null) { result = "No file loaded"; isLoading = false; return@launch }

                        withContext(Dispatchers.Main) { progress = "Reading file..." }
                        val readSize = minOf(file.length(), 500000L).toInt()
                        val data = StreamingIO.readRange(file, 0, readSize)

                        withContext(Dispatchers.Main) { progress = "Disassembling (${archIndex})..." }
                        val arch = when (archIndex) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 1 }
                        val mode = when (archIndex) { 0 -> 0; 1 -> 2; 2 -> 4; else -> 2 }
                        val disasm = withContext(Dispatchers.IO) {
                            NativeLib.disassemble(data, 0, arch, mode, 2000)
                        }

                        withContext(Dispatchers.Main) { progress = "Building CFG + lifting IR..." }
                        val name = funcName.ifEmpty { "unknown" }
                        val output = withContext(Dispatchers.Default) {
                            DecompilerEngine.generatePseudoC(disasm, name, showAddresses)
                        }

                        result = output
                        lineCount = output.lines().size
                        blockCount = output.lines().count { it.contains("Block 0x") }
                        varCount = output.lines().count { it.trimStart().startsWith("long ") && it.contains(";") }
                        withContext(Dispatchers.Main) { progress = "" }
                    } catch (e: Exception) {
                        result = "Error: ${e.message}"
                        withContext(Dispatchers.Main) { progress = "" }
                    }
                    isLoading = false
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(12.dp), enabled = !isLoading && hasNative) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Code, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Decompile", fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(12.dp))

            // Stats
            if (result.isNotEmpty() && !isLoading) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Lines", "$lineCount", AccentGreen)
                    StatCard("Blocks", "$blockCount", AccentCyan)
                    StatCard("Vars", "$varCount", AccentOrange)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Output
            if (result.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pseudo-C Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("decompiled", result))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentCopy, "Copy", tint = AccentGreen) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(result, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutputButton(content = { result }, filename = "decompile.c", subfolder = "decompile")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color) {
    Card(Modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
            Text(label, fontSize = 10.sp, color = TextSecondary)
        }
    }
}
