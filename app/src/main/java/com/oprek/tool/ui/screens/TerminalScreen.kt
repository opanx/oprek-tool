@file:Suppress("DEPRECATION")
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


    // Safe file read helper
    fun safeReadBytes(file: java.io.File): ByteArray? = try {
        file.readBytes()
    } catch (e: Exception) { null }
    
    fun safeReadText(file: java.io.File): String? = try {
        file.readText()
    } catch (e: Exception) { null }
    
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
                        addLine("")
                        addLine("── File Operations ──")
                        addLine("ls [path]      - List directory contents")
                        addLine("cat <path>     - Print file contents")
                        addLine("head <path>    - Show first 20 lines")
                        addLine("tail <path>    - Show last 20 lines")
                        addLine("wc <path>      - Word/line/byte count")
                        addLine("cp <src> <dst> - Copy file")
                        addLine("mv <src> <dst> - Move/rename file")
                        addLine("rm <path>      - Delete file")
                        addLine("mkdir <path>   - Create directory")
                        addLine("touch <path>   - Create empty file")
                        addLine("chmod <mode> <path> - Change permissions")
                        addLine("find <dir> <name> - Find files by name")
                        addLine("grep <pattern> <path> - Search in file")
                        addLine("sed <path> <old> <new> - Find & replace")
                        addLine("edit <path>    - Edit file (opens editor)")
                        addLine("write <path> <content> - Write to file")
                        addLine("append <path> <content> - Append to file")
                        addLine("")
                        addLine("── Analysis ──")
                        addLine("file <path>    - Show file info (magic bytes)")
                        addLine("xxd <path>     - Hex dump of file")
                        addLine("strings <path> - Extract printable strings")
                        addLine("readelf <path> - Show ELF headers")
                        addLine("unzip <path>   - List ZIP/APK contents")
                        addLine("md5 <path>     - Calculate MD5 hash")
                        addLine("sha256 <path>  - Calculate SHA256 hash")
                        addLine("")
                        addLine("── System ──")
                        addLine("pwd            - Print working directory")
                        addLine("date           - Show current date/time")
                        addLine("whoami         - Show current user")
                        addLine("info           - Show app info")
                        addLine("env            - Show environment variables")
                        addLine("df             - Show disk usage")
                        addLine("ps             - Show running processes")
                    }
                    "pwd" -> withContext(Dispatchers.Main) { addLine(System.getProperty("user.dir") ?: ".") }
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
                                val bytes = (safeReadBytes(f) ?: byteArrayOf()).take(16).joinToString(" ") { "%02X".format(it) }
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
                        val data = (safeReadBytes(f) ?: byteArrayOf()).take(2048)
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
                        val data = safeReadBytes(f) ?: byteArrayOf()
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
                    "cat" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: cat <path>", isError = true) }; return@withContext }
                        val f = java.io.File(path)
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: $path", isError = true) }; return@withContext }
                        if (f.isDirectory) { withContext(Dispatchers.Main) { addLine("Is a directory: $path", isError = true) }; return@withContext }
                        val lines2 = f.readLines()
                        lines2.take(500).forEach { l -> withContext(Dispatchers.Main) { addLine(l) } }
                        if (lines2.size > 500) withContext(Dispatchers.Main) { addLine("... ${lines2.size - 500} more lines") }
                    }
                    "head" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: head <path>", isError = true) }; return@withContext }
                        val f = java.io.File(path)
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: $path", isError = true) }; return@withContext }
                        f.readLines().take(20).forEach { l -> withContext(Dispatchers.Main) { addLine(l) } }
                    }
                    "tail" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: tail <path>", isError = true) }; return@withContext }
                        val f = java.io.File(path)
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: $path", isError = true) }; return@withContext }
                        f.readLines().takeLast(20).forEach { l -> withContext(Dispatchers.Main) { addLine(l) } }
                    }
                    "wc" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: wc <path>", isError = true) }; return@withContext }
                        val f = java.io.File(path)
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: $path", isError = true) }; return@withContext }
                        val text = safeReadText(f) ?: ""
                        val lines3 = text.lines().size
                        val words = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                        val bytes = f.length()
                        withContext(Dispatchers.Main) { addLine("  $lines3  $words  $bytes  $path") }
                    }
                    "cp" -> withContext(Dispatchers.IO) {
                        if (parts.size < 3) { withContext(Dispatchers.Main) { addLine("Usage: cp <src> <dst>", isError = true) }; return@withContext }
                        val src = java.io.File(parts[1])
                        val dst = java.io.File(parts[2])
                        if (!src.exists()) { withContext(Dispatchers.Main) { addLine("Source not found: ${parts[1]}", isError = true) }; return@withContext }
                        src.copyTo(dst, overwrite = true)
                        withContext(Dispatchers.Main) { addLine("Copied ${parts[1]} -> ${parts[2]}") }
                    }
                    "mv" -> withContext(Dispatchers.IO) {
                        if (parts.size < 3) { withContext(Dispatchers.Main) { addLine("Usage: mv <src> <dst>", isError = true) }; return@withContext }
                        val src = java.io.File(parts[1])
                        val dst = java.io.File(parts[2])
                        if (!src.exists()) { withContext(Dispatchers.Main) { addLine("Source not found: ${parts[1]}", isError = true) }; return@withContext }
                        src.renameTo(dst)
                        withContext(Dispatchers.Main) { addLine("Moved ${parts[1]} -> ${parts[2]}") }
                    }
                    "rm" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: rm <path>", isError = true) }; return@withContext }
                        val f = java.io.File(path)
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: $path", isError = true) }; return@withContext }
                        f.delete()
                        withContext(Dispatchers.Main) { addLine("Deleted: $path") }
                    }
                    "mkdir" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: mkdir <path>", isError = true) }; return@withContext }
                        java.io.File(path).mkdirs()
                        withContext(Dispatchers.Main) { addLine("Created: $path") }
                    }
                    "touch" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: touch <path>", isError = true) }; return@withContext }
                        java.io.File(path).createNewFile()
                        withContext(Dispatchers.Main) { addLine("Created: $path") }
                    }
                    "chmod" -> withContext(Dispatchers.IO) {
                        if (parts.size < 3) { withContext(Dispatchers.Main) { addLine("Usage: chmod <mode> <path>", isError = true) }; return@withContext }
                        val f = java.io.File(parts[2])
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: ${parts[2]}", isError = true) }; return@withContext }
                        f.setReadable(true); f.setWritable(true); f.setExecutable(true)
                        withContext(Dispatchers.Main) { addLine("Set permissions on ${parts[2]}") }
                    }
                    "find" -> withContext(Dispatchers.IO) {
                        if (parts.size < 3) { withContext(Dispatchers.Main) { addLine("Usage: find <dir> <name>", isError = true) }; return@withContext }
                        val dir = java.io.File(parts[1])
                        if (!dir.exists()) { withContext(Dispatchers.Main) { addLine("Dir not found: ${parts[1]}", isError = true) }; return@withContext }
                        val name = parts[2]
                        var count = 0
                        dir.walkTopDown().maxDepth(5).forEach { f ->
                            if (f.name.contains(name, ignoreCase = true)) {
                                withContext(Dispatchers.Main) { addLine(f.absolutePath) }
                                count++
                                if (count > 100) return@forEach
                            }
                        }
                        withContext(Dispatchers.Main) { addLine("Found: $count files") }
                    }
                    "grep" -> withContext(Dispatchers.IO) {
                        if (parts.size < 3) { withContext(Dispatchers.Main) { addLine("Usage: grep <pattern> <path>", isError = true) }; return@withContext }
                        val pattern = parts[1]
                        val f = java.io.File(parts[2])
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: ${parts[2]}", isError = true) }; return@withContext }
                        val matches = f.readLines().filter { it.contains(pattern, ignoreCase = true) }
                        matches.take(100).forEach { l -> withContext(Dispatchers.Main) { addLine(l) } }
                        withContext(Dispatchers.Main) { addLine("${matches.size} matches found") }
                    }
                    "sed" -> withContext(Dispatchers.IO) {
                        if (parts.size < 4) { withContext(Dispatchers.Main) { addLine("Usage: sed <path> <old> <new>", isError = true) }; return@withContext }
                        val f = java.io.File(parts[1])
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: ${parts[1]}", isError = true) }; return@withContext }
                        val old = parts[2]
                        val new = parts[3]
                        val text2 = safeReadText(f) ?: ""
                        val count2 = text2.split(old).size - 1
                        f.writeText(text2.replace(old, new))
                        withContext(Dispatchers.Main) { addLine("Replaced $count2 occurrences of '$old' with '$new'") }
                    }
                    "write" -> withContext(Dispatchers.IO) {
                        if (parts.size < 3) { withContext(Dispatchers.Main) { addLine("Usage: write <path> <content>", isError = true) }; return@withContext }
                        val f = java.io.File(parts[1])
                        val content = cmd.substringAfter(parts[0]).substringAfter(parts[1]).trim()
                        f.writeText(content)
                        withContext(Dispatchers.Main) { addLine("Written ${content.length} bytes to ${parts[1]}") }
                    }
                    "append" -> withContext(Dispatchers.IO) {
                        if (parts.size < 3) { withContext(Dispatchers.Main) { addLine("Usage: append <path> <content>", isError = true) }; return@withContext }
                        val f = java.io.File(parts[1])
                        val content = cmd.substringAfter(parts[0]).substringAfter(parts[1]).trim()
                        f.appendText(content + "\n")
                        withContext(Dispatchers.Main) { addLine("Appended ${content.length} bytes to ${parts[1]}") }
                    }
                    "ls" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "." }
                        val dir = java.io.File(path)
                        if (!dir.exists()) { withContext(Dispatchers.Main) { addLine("Path not found: $path", isError = true) }; return@withContext }
                        if (!dir.isDirectory) { withContext(Dispatchers.Main) { addLine("Not a directory: $path", isError = true) }; return@withContext }
                        val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
                        files.take(100).forEach { file ->
                            val icon = if (file.isDirectory) "d" else "-"
                            val size = if (file.isDirectory) "<DIR>" else "${file.length()}"
                            withContext(Dispatchers.Main) { addLine("$icon ${file.name.padEnd(30)} $size") }
                        }
                        withContext(Dispatchers.Main) { addLine("${files.size} items") }
                    }
                    "md5" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: md5 <path>", isError = true) }; return@withContext }
                        val f = java.io.File(path)
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: $path", isError = true) }; return@withContext }
                        val digest = java.security.MessageDigest.getInstance("MD5")
                        f.inputStream().use { dis ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (dis.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
                        }
                        val hash = digest.digest().joinToString("") { "%02x".format(it) }
                        withContext(Dispatchers.Main) { addLine("MD5($path) = $hash") }
                    }
                    "sha256" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: sha256 <path>", isError = true) }; return@withContext }
                        val f = java.io.File(path)
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: $path", isError = true) }; return@withContext }
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        f.inputStream().use { dis ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (dis.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
                        }
                        val hash = digest.digest().joinToString("") { "%02x".format(it) }
                        withContext(Dispatchers.Main) { addLine("SHA256($path) = $hash") }
                    }
                    "unzip" -> withContext(Dispatchers.IO) {
                        val path = parts.getOrElse(1) { "" }
                        if (path.isEmpty()) { withContext(Dispatchers.Main) { addLine("Usage: unzip <path>", isError = true) }; return@withContext }
                        val f = java.io.File(path)
                        if (!f.exists()) { withContext(Dispatchers.Main) { addLine("File not found: $path", isError = true) }; return@withContext }
                        try {
                            val zf = java.util.zip.ZipFile(f)
                            val entries = zf.entries()
                            var count3 = 0
                            while (entries.hasMoreElements()) {
                                val e = entries.nextElement()
                                withContext(Dispatchers.Main) { addLine("${e.size.toString().padStart(10)}  ${e.name}") }
                                count3++
                                if (count3 > 200) { withContext(Dispatchers.Main) { addLine("... (truncated)") }; break }
                            }
                            zf.close()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { addLine("Error: ${e.message}", isError = true) }
                        }
                    }
                    "env" -> withContext(Dispatchers.Main) {
                        val envVars = listOf("PATH", "HOME", "USER", "SHELL", "LANG", "TMPDIR")
                        envVars.forEach { key -> addLine("$key=${System.getenv(key) ?: ""}") }
                    }
                    "df" -> withContext(Dispatchers.IO) {
                        val dir2 = java.io.File("/sdcard")
                        val total = dir2.totalSpace
                        val free = dir2.freeSpace
                        val used = total - free
                        withContext(Dispatchers.Main) {
                            addLine("Filesystem      Size   Used  Free")
                            addLine("/sdcard    ${formatSize(total)} ${formatSize(used)} ${formatSize(free)}")
                        }
                    }
                    "ps" -> withContext(Dispatchers.Main) {
                        addLine("PID   NAME")
                        try {
                            val proc2 = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "ps 2>/dev/null | head -30"))
                            BufferedReader(InputStreamReader(proc2.inputStream)).useLines { seq ->
                                seq.forEach { line2 -> addLine(line2) }
                            }
                        } catch (e: Exception) { addLine("Error: ${e.message}", isError = true) }
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
                    colors = darkTextFieldColors()
                )
                IconButton(onClick = {
                    runCmd(command)
                    command = ""
                }, enabled = command.isNotEmpty() && !isRunning) {
                    @Suppress("DEPRECATION") Icon(Icons.Filled.Send, "Run", tint = if (command.isNotEmpty()) AccentGreen else TextMuted)
                }
            }
        }
        }
    }

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        else -> "${bytes / (1024L * 1024 * 1024)}GB"
    }
}

data class TerminalLine(val text: String, val isCommand: Boolean = false, val isError: Boolean = false)
