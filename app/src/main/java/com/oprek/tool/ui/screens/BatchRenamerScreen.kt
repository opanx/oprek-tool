@file:Suppress("DEPRECATION")
package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRenamerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var findPattern by remember { mutableStateOf("") }
    var replaceWith by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var selectedDir by remember { mutableStateOf("") }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            selectedDir = it.path ?: ""
            scope.launch(Dispatchers.IO) {
                val dir = File("/storage/emulated/0/${selectedDir.replace("/tree/primary:", "")}")
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles() ?: emptyArray()
                    withContext(Dispatchers.Main) {
                        output = listOf("[+] Directory: ${dir.absolutePath}", "[+] Files: ${files.size}")
                        files.take(20).forEach { f -> output = output + "  ${f.name} (${f.length()} bytes)" }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📝 Batch Renamer", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Batch rename files with find & replace", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(findPattern, { findPattern = it }, label = { Text("Find pattern", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(replaceWith, { replaceWith = it }, label = { Text("Replace with", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useRegex, onCheckedChange = { useRegex = it })
                        Text("Regex mode", fontSize = 11.sp, color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { dirPicker.launch(null) }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), shape = RoundedCornerShape(8.dp)) {
                            Text("Select Dir", fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        if (selectedDir.isBlank() || findPattern.isBlank()) return@Button
                        isRunning = true
                        scope.launch(Dispatchers.IO) {
                            val dir = File("/storage/emulated/0/${selectedDir.replace("/tree/primary:", "")}")
                            val files = dir.listFiles() ?: emptyArray()
                            var count = 0
                            for (f in files) {
                                val newName = if (useRegex) {
                                    f.name.replace(Regex(findPattern), replaceWith)
                                } else {
                                    f.name.replace(findPattern, replaceWith)
                                }
                                if (newName != f.name) {
                                    val newFile = File(dir, newName)
                                    if (!newFile.exists()) {
                                        f.renameTo(newFile)
                                        withContext(Dispatchers.Main) { output = output + "✅ ${f.name} → $newName" }
                                        count++
                                    } else {
                                        withContext(Dispatchers.Main) { output = output + "⚠️ Skip ${f.name} (target exists)" }
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                output = output + "[+] Renamed $count files"
                                isRunning = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(40.dp), enabled = !isRunning && selectedDir.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(8.dp)) {
                        Text("Rename Files", fontSize = 11.sp)
                    }
                }
            }
            if (isRunning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentGreen)
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("output", output.joinToString("\n")))
                        }) { Text("Copy", fontSize = 10.sp, color = AccentCyan) }
                    }
                    LazyColumn { items(output) { line -> Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = if (line.startsWith("✅")) AccentGreen else if (line.startsWith("⚠")) AccentOrange else TextPrimary, lineHeight = 12.sp) } }
                }
            }
        }
    }
}
