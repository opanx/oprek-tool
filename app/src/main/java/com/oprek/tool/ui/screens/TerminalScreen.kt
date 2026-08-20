package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var command by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<TerminalLine>() }
    var isRunning by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<String>() }
    var historyIdx by remember { mutableIntStateOf(-1) }

    fun addLine(text: String, isCmd: Boolean = false, isError: Boolean = false) {
        lines.add(TerminalLine(text, isCmd, isError))
    }

    fun runCmd(cmd: String) {
        if (cmd.isBlank()) return
        history.add(cmd)
        historyIdx = history.size
        addLine("$ $cmd", isCmd = true)
        isRunning = true

        scope.launch(Dispatchers.IO) {
            try {
                val parts = cmd.trim().split("\\s+".toRegex())
                if (parts.isEmpty()) { isRunning = false; return@launch }

                when (parts[0]) {
                    "clear" -> withContext(Dispatchers.Main) { lines.clear() }
                    "help" -> withContext(Dispatchers.Main) {
                        addLine("Built-in commands: clear, help, logcat, share")
                        addLine("System commands: ls, cat, file, strings, xxd, readelf, objdump")
                    }
                    "logcat" -> {
                        val filter = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
                        val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "logcat -d -t 100 $filter"))
                        BufferedReader(InputStreamReader(proc.inputStream)).useLines { seq ->
                            seq.forEach { line ->
                                scope.launch(Dispatchers.Main) { addLine(line) }
                            }
                        }
                    }
                    "share" -> {
                        val text = lines.joinToString("\n") { it.text }
                        withContext(Dispatchers.Main) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share terminal output"))
                        }
                    }
                    else -> {
                        val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
                        val stdout = BufferedReader(InputStreamReader(proc.inputStream))
                        val stderr = BufferedReader(InputStreamReader(proc.errorStream))

                        var line: String?
                        while (stdout.readLine().also { line = it } != null) {
                            line?.let { l ->
                                withContext(Dispatchers.Main) { addLine(l) }
                            }
                        }
                        while (stderr.readLine().also { line = it } != null) {
                            line?.let { l ->
                                withContext(Dispatchers.Main) { addLine(l, isError = true) }
                            }
                        }
                        val exitCode = proc.waitFor()
                        withContext(Dispatchers.Main) { addLine("[exit: $exitCode]", isError = exitCode != 0) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { addLine("Error: ${e.message}", isError = true) }
            }
            withContext(Dispatchers.Main) { isRunning = false }
        }
    }


            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { output.joinToString("\n") },
                filename = "terminal_output.txt",
                subfolder = "terminal"
            )

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💻 Terminal", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { lines.clear() }) { Icon(Icons.Default.DeleteSweep, "Clear") }
                    IconButton(onClick = {
                        val text = lines.joinToString("\n") { it.text }
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("terminal", text))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy All") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = Color(0xFF0A0E14)
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0A0E14))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (lines.isEmpty()) {
                    item {
                        Text("OprekTool Terminal v2.0\nType 'help' for built-in commands.\n", fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace, color = AccentGreen)
                    }
                }
                itemsIndexed(lines) { _, line ->
                    Text(
                        line.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when { line.isCommand -> AccentCyan; line.isError -> AccentRed; else -> AccentGreen },
                        modifier = Modifier.padding(vertical = 1.dp)
                            .clickable {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("line", line.text))
                            }
                    )
                }
            }

            HorizontalDivider(color = AccentGreen.copy(alpha = 0.3f))

            Row(
                Modifier.fillMaxWidth().background(Color(0xFF0D1117)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$ ", fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = AccentGreen)
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter command...", color = TextMuted) },
                    singleLine = true,
                    enabled = !isRunning,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, cursorColor = AccentGreen, unfocusedBorderColor = Color.Transparent)
                )
                IconButton(onClick = {
                    runCmd(command)
                    command = ""
                }, enabled = command.isNotEmpty() && !isRunning) {
                    Icon(Icons.Default.Send, "Run", tint = if (command.isNotEmpty()) AccentGreen else TextMuted)
                }
            }
        }
        }
    }

data class TerminalLine(val text: String, val isCommand: Boolean = false, val isError: Boolean = false)
