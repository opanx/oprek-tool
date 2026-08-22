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
                        addLine("═══ Built-in Commands ═══")
                        addLine("clear          - Clear terminal")
                        addLine("help           - Show this help")
                        addLine("logcat         - Android logcat dump")
                        addLine("share          - Share terminal output")
                        addLine("file <path>    - Show file info (magic bytes)")
                        addLine("xxd <path>     - Hex dump of file")
                        addLine("strings <path> - Extract printable strings")
                        addLine("readelf <path> - Show ELF headers")
                        addLine("ls             - List current directory")
                        addLine("pwd            - Print working directory")
                        addLine("date           - Show current date/time")
                        addLine("whoami         - Show current user")
                        addLine("info           - Show app info")
                    }
                    "pwd" -> withContext(Dispatchers.Main) { addLine(System.getProperty("user.dir")) }
                    "date" -> withContext(Dispatchers.Main) { addLine(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())) }
                    "whoami" -> withContext(Dispatchers.Main) { addLine(System.getProperty("user.name") ?: "root") }
                    "info" -> withContext(Dispatchers.Main) {
                        addLine("OprekTool v0.0.6")
                        addLine("Build: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.DISPLAY})")
                        addLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        addLine("Arch: ${System.getProperty("os.arch")}")
                    }
                    "file" -> withContext(Dispatchers.Main) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) addLine("Usage: file <path>", isError = true)
                        else {
                            val f = java.io.File(path)
                            if (!f.exists()) addLine("File not found: ${path}", isError = true)
                            else {
                                addLine("$path: ${f.length()} bytes, ${if (f.isDirectory) "directory" else "file"}")
                                val bytes = f.readBytes().take(16).joinToString(" ") { "%02X".format(it) }
                                addLine("Magic: $bytes")
                            }
                        }
                    }
                    "xxd" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) {
                            withContext(Dispatchers.Main) { addLine("Usage: xxd FILEPATH", isError = true) }
                            return@withContext
                        }
                        val f = java.io.File(path)
                        if (!f.exists()) {
                            withContext(Dispatchers.Main) { addLine("File not found: ${path}", isError = true) }
                            return@withContext
                        }
                        val data = f.readBytes().take(2048)
                        for (i in data.indices step 16) {
                            val hex = data.drop(i).take(16).joinToString(" ") { "%02X".format(it) }
                            val asc = data.drop(i).take(16).map { if (it.toInt() in 0x20..0x7E) it.toInt().toChar() else '.' }.joinToString("")
                            val addr = String.format("%08X", i.toLong())
                            withContext(Dispatchers.Main) { addLine("$addr: $hex  |$asc|") }
                        }
                    }
                    "strings" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) {
                            withContext(Dispatchers.Main) { addLine("Usage: strings FILEPATH", isError = true) }
                            return@withContext
                        }
                        val f = java.io.File(path)
                        if (!f.exists()) {
                            withContext(Dispatchers.Main) { addLine("File not found: ${path}", isError = true) }
                            return@withContext
                        }
                        val data = f.readBytes()
                        val sb = StringBuilder()
                        var cur = StringBuilder()
                        for (b in data) {
                            val c = b.toInt() and 0xFF
                            if (c in 0x20..0x7E) cur.append(c.toChar())
                            else { if (cur.length >= 4) sb.appendLine(cur.toString()); cur.clear() }
                        }
                        val output = sb.toString()
                        output.lines().take(100).forEach { line -> withContext(Dispatchers.Main) { addLine(line) } }
                        if (output.lines().size > 100) withContext(Dispatchers.Main) { addLine("... ${output.lines().size - 100} more strings") }
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
                content = { "Terminal output" },
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
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
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
