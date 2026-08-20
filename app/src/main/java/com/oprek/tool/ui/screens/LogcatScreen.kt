package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.compose.ui.graphics.Color
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var filter by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<String>() }
    var isRunning by remember { mutableStateOf(false) }
    var lineCount by remember { mutableIntStateOf(0) }

    // Auto-refresh logcat
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            if (!isRunning && filter.isNotEmpty()) {
                // Auto-refresh when filter is set
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📋 Logcat", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { lines.clear(); lineCount = 0 }) { Icon(Icons.Default.DeleteSweep, "Clear") }
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("logcat", lines.joinToString("\n")))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = filter, onValueChange = { filter = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Filter (e.g. MyTag:*)", color = TextMuted) }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    lines.clear(); isRunning = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val f = if (filter.isNotBlank()) " -s $filter" else ""
                            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "logcat -d -t 500$f"))
                            BufferedReader(InputStreamReader(proc.inputStream)).useLines { seq ->
                                seq.forEach { line ->
                                    scope.launch(Dispatchers.Main) { lines.add(line); lineCount++ }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { lines.add("Error: ${e.message}") }
                        }
                        isRunning = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), shape = RoundedCornerShape(8.dp)) {
                    if (isRunning) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                }
            }
            Text("Lines: $lineCount", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(horizontal = 12.dp))
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { logs.joinToString("\n") },
                filename = "logcat.txt",
                subfolder = "logcat"
            )

                items(lines) { line ->
                    Text(line, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = when {
                        line.contains("E/") || line.contains("FATAL") -> AccentRed
                        line.contains("W/") -> AccentOrange
                        line.contains("D/") -> AccentCyan
                        else -> AccentGreen
                    }, modifier = Modifier.padding(vertical = 0.5.dp))
                }
            }
        }
    }
}
