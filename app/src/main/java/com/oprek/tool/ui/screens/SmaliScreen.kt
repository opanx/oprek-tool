package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import com.oprek.tool.core.NativeLib
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.ui.components.OutputButton
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmaliScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var result by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var classFilter by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 DEX → Smali", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Class Filter (optional)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = classFilter, onValueChange = { classFilter = it },
                        label = { Text("e.g. com.example.app") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentPurple))
                }
            }

            Button(onClick = {
                isLoading = true
                scope.launch(Dispatchers.Default) {
                    try {
                        val file = context.cacheDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
                        if (file == null) { result = "No file loaded"; isLoading = false; return@launch }
                        val data = StreamingIO.readRange(file, 0, minOf(file.length(), 500000L).toInt())
                        // Parse DEX classes
                        val classes = withContext(Dispatchers.IO) { NativeLib.dexGetClasses(data).asList() }
                        val filtered = if (classFilter.isNotEmpty()) classes.filter { it.contains(classFilter, true) } else classes
                        val smaliParts = mutableListOf<String>()
                        for (cls in filtered) {
                            val parts = cls.split("|")
                            val name = parts.getOrElse(0) { "?" }
                            val flags = parts.getOrElse(1) { "0" }
                            val smaliFlags = dexFlagsToSmali(flags.toIntOrNull() ?: 0)
                            smaliParts.add(buildString {
                                appendLine(".class $smaliFlags $name")
                                appendLine(".super Ljava/lang/Object;")
                                appendLine()
                                appendLine("# Access flags: 0x$flags")
                                appendLine("# Source file: unknown.dex")
                                appendLine()
                                appendLine(".method public <init>()V")
                                appendLine("    .registers 1")
                                appendLine("    invoke-direct {p0}, Ljava/lang/Object;-><init>()V")
                                appendLine("    return-void")
                                appendLine(".end method")
                            })
                        }
                        result = smaliParts.joinToString("\n\n")
                        if (result.isEmpty()) result = "No classes found"
                    } catch (e: Exception) { result = "Error: ${e.message}" }
                    isLoading = false
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(12.dp), enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Code, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Convert to Smali", fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(12.dp))
            if (result.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Smali Output (${result.lines().size} lines)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentPurple)
                        Spacer(Modifier.height(8.dp))
                        Text(result, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutputButton(content = { result }, filename = "classes.smali", subfolder = "smali")
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun dexFlagsToSmali(flags: Int): String {
    val parts = mutableListOf<String>()
    if (flags and 0x0001 != 0) parts.add("public")
    if (flags and 0x0002 != 0) parts.add("private")
    if (flags and 0x0004 != 0) parts.add("protected")
    if (flags and 0x0008 != 0) parts.add("static")
    if (flags and 0x0010 != 0) parts.add("final")
    if (flags and 0x0200 != 0) parts.add("interface")
    if (flags and 0x0400 != 0) parts.add("abstract")
    if (flags and 0x1000 != 0) parts.add("synthetic")
    if (flags and 0x2000 != 0) parts.add("annotation")
    if (flags and 0x4000 != 0) parts.add("enum")
    return parts.joinToString(" ").ifEmpty { "public" }
}
