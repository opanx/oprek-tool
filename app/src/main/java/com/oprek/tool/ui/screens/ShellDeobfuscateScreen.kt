package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellDeobfuscateScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Triple<Int, String, String>>()) }
    var output by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                    withContext(Dispatchers.Main) { content = text; loaded = true }
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Shell Deobfuscator", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) {
                Button(onClick = { picker.launch(arrayOf("text/*", "*/*")) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open Shell Script") }
            }
            if (loaded) {
                OutlinedTextField(value = content, onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp),
                    label = { Text("Script content") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))
                Button(onClick = {
                    results = mutableListOf()
                    val lines = content.lines()
                    for ((i, line) in lines.withIndex()) {
                        // Base64 decode
                        val b64Match = Regex("base64\\s+-d\\s*<<<\\s*(['\"]?)([A-Za-z0-9+/=]+)\\1").find(line)
                        if (b64Match != null) {
                            try {
                                val decoded = String(Base64.decode(b64Match.groupValues[2], Base64.DEFAULT))
                                results = results + Triple(i+1, "Base64", decoded)
                            } catch (_: Exception) {}
                        }
                        // Hex decode (xxd -r)
                        if (line.contains("xxd") && line.contains("-r")) {
                            results = results + Triple(i+1, "Hex Escape", "(hex data detected)")
                        }
                        // ROT13
                        val rot13 = line.map { c ->
                            when {
                                c in 'a'..'m' || c in 'A'..'M' -> (c.code + 13).toChar()
                                c in 'n'..'z' || c in 'N'..'Z' -> (c.code - 13).toChar()
                                else -> c
                            }
                        }.joinToString("")
                        if (rot13 != line && line.length > 5 && line.contains(Regex("[a-zA-Z]{5,}"))) {
                            results = results + Triple(i+1, "ROT13", rot13)
                        }
                        // URL encoded
                        if (line.contains("%")) {
                            try {
                                val decoded = URLDecoder.decode(line, "UTF-8")
                                if (decoded != line) results = results + Triple(i+1, "URL Encode", decoded)
                            } catch (_: Exception) {}
                        }
                    }
                    output = results.joinToString("\n") { "L${it.first} [${it.second}]: ${it.third}" }
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("Deobfuscate") }
                if (results.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))

                    Text("${results.size} obfuscations found:", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 12.sp)
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(results) { _, r ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("Line ${r.first} [${r.second}]", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text(r.third, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 5)
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}
