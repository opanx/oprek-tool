package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.oprek.tool.ui.theme.*
import com.oprek.tool.utils.ShellScriptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScriptScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var scriptInfo by remember { mutableStateOf<com.oprek.tool.utils.ShellScriptInfo?>(null) }
    var rawContent by remember { mutableStateOf("") }
    var extractedBinaries by remember { mutableStateOf<List<String>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull() ?: return@LaunchedEffect
        if (!file.name.endsWith(".sh") && !file.name.endsWith(".bash") && !file.name.endsWith(".zsh")) return@LaunchedEffect
        rawContent = withContext(Dispatchers.IO) { file.readText() }
        scriptInfo = withContext(Dispatchers.Default) { ShellScriptParser.parse(rawContent) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📜 Shell Script Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("script", rawContent))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        if (scriptInfo == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Open a .sh file to analyze", color = TextSecondary)
            }
        } else {
            val info = scriptInfo!!
            Column(Modifier.padding(padding).fillMaxSize()) {
                // Info card
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📜", fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Shell Script", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentGreen)
                                Text("Interpreter: ${info.interpreter}", fontSize = 12.sp, color = AccentCyan)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoChip("Lines", "${info.lineCount}", AccentPurple)
                            InfoChip("Size", "${info.size}B", AccentOrange)
                            InfoChip("Commands", "${info.commands.size}", AccentCyan)
                            InfoChip("URLs", "${info.urls.size}", AccentBlue)
                        }
                    }
                }

                // Tabs
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Commands" to info.commands, "URLs" to info.urls, "Variables" to info.variables,
                        "Functions" to info.functions, "Obfuscated" to info.obfuscatedStrings, "Binary" to emptyList<String>()).forEachIndexed { idx, (name, _) ->
                        FilterChip(selected = selectedTab == idx, onClick = { selectedTab = idx }, label = { Text(name, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.3f)))
                    }
                }

                Spacer(Modifier.height(8.dp))


                // Content
                when (selectedTab) {
                    0 -> SimpleList(info.commands, "command", AccentGreen, clipboard)
                    1 -> SimpleList(info.urls, "url", AccentBlue, clipboard)
                    2 -> SimpleList(info.variables, "variable", AccentPurple, clipboard)
                    3 -> SimpleList(info.functions, "function", AccentOrange, clipboard)
                    4 -> SimpleList(info.obfuscatedStrings, "obfuscated", AccentRed, clipboard)
                    5 -> {
                        // Binary payloads
                        if (info.binaryOffsets.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No binary payloads detected", color = TextSecondary)
                            }
                        } else {
                            LazyColumn(Modifier.padding(12.dp)) {
                                items(info.binaryOffsets) { bp ->
                                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Archive, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(bp.type.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentOrange)
                                                Text(bp.description, fontSize = 11.sp, color = TextSecondary)
                                            }
                                            Button(onClick = {
                                                isExtracting = true
                                                scope.launch(Dispatchers.Default) {
                                                    val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull()
                                                    if (file != null) {
                                                        val content = withContext(Dispatchers.IO) { file.readText() }
                                                        val extracted = ShellScriptParser.extractBinary(content, bp)
                                                        if (extracted != null) {
                                                            val out = File(context.cacheDir, "oprek/extracted_${bp.type}_${System.currentTimeMillis()}")
                                                            withContext(Dispatchers.IO) { out.writeBytes(extracted) }
                                                            extractedBinaries = extractedBinaries + "${out.name} (${extracted.size} bytes)"
                                                            withContext(Dispatchers.Main) { Toast.makeText(context, "Extracted ${extracted.size} bytes!", Toast.LENGTH_SHORT).show() }
                                                        }
                                                    }
                                                    isExtracting = false
                                                }
                                            }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                                Text("Extract", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = TextMuted)
    }
}

@Composable
fun SimpleList(items: List<String>, type: String, color: androidx.compose.ui.graphics.Color, clipboard: ClipboardManager) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No ${type}s detected", color = TextSecondary)
        }
    } else {
        LazyColumn(Modifier.padding(12.dp)) {
            itemsIndexed(items) { idx, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(if (idx % 2 == 0) DarkBg else DarkSurface).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${idx + 1}. ", fontSize = 11.sp, color = TextMuted, modifier = Modifier.width(30.dp))
                    Text(item, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = color, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText(type, item))
                    }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(12.dp), tint = TextMuted)
                    }
                }
            }

        }
    }
}
