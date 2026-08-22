package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.oprek.tool.core.LoadedFileHelper
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LeakItem(val category: String, val severity: String, val value: String, val context: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoLeakSourceScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var leaks by remember { mutableStateOf(listOf<LeakItem>()) }
    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }

    fun loadFile() {
        val f = LoadedFileHelper.findLoadedFile(context)
        if (f != null) {
            fileName = f.name
            status = "Loaded: ${f.name} (${f.length()} bytes)"
        } else {
            status = "No file. Open from Home first."
        }
    }

    fun scan() {
        val f = LoadedFileHelper.findLoadedFile(context) ?: return
        isRunning = true
        leaks = emptyList()
        scope.launch(Dispatchers.IO) {
            val data = f.readBytes()
            val text = String(data, Charsets.US_ASCII)
            val hex = data.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
            val results = mutableListOf<LeakItem>()

            // URLs
            val urls = Regex("""https?://[^\s\x00"'<>]{5,200}""").findAll(text)
            for (m in urls) {
                results.add(LeakItem("URL", "HIGH", m.value, "Network endpoint found"))
            }

            // IP addresses
            val ips = Regex("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]+)?\b""").findAll(text)
            for (m in ips) {
                if (!m.value.startsWith("0.") && !m.value.startsWith("127.")) {
                    results.add(LeakItem("IP Address", "HIGH", m.value, "Hardcoded IP address"))
                }
            }

            // Email addresses
            val emails = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").findAll(text)
            for (m in emails) {
                results.add(LeakItem("Email", "MEDIUM", m.value, "Email address found"))
            }

            // Telegram handles
            val tg = Regex("""@[a-zA-Z0-9_]{3,30}""").findAll(text)
            for (m in tg) {
                val word = m.value.substring(1)
                if (word.length > 3 && !word.all { it.isDigit() }) {
                    results.add(LeakItem("Telegram", "MEDIUM", m.value, "Telegram handle"))
                }
            }

            // File paths
            val paths = Regex("""/[a-zA-Z0-9._/-]{5,100}""").findAll(text)
            for (m in paths) {
                if (m.value.contains("/") && m.value.length > 5) {
                    results.add(LeakItem("File Path", "LOW", m.value, "File system path"))
                }
            }

            // Auth tokens / API keys
            val tokens = Regex("""(?:api[_-]?key|token|secret|password|passwd|pwd)\s*[:=]\s*['"]?([^\s'"]{5,})""", RegexOption.IGNORE_CASE).findAll(text)
            for (m in tokens) {
                results.add(LeakItem("Secret", "CRITICAL", m.groupValues[1], "Hardcoded secret/key"))
            }

            // JWT tokens
            val jwts = Regex("""eyJ[a-zA-Z0-9_-]{50,500}""").findAll(text)
            for (m in jwts) {
                results.add(LeakItem("JWT Token", "CRITICAL", m.value.take(80) + "...", "JWT token found"))
            }

            // Base64 encoded strings (long ones)
            val b64 = Regex("""[A-Za-z0-9+/]{40,}={0,2}""").findAll(text)
            for (m in b64) {
                try {
                    val decoded = android.util.Base64.decode(m.value, android.util.Base64.DEFAULT)
                    val decodedStr = String(decoded)
                    if (decodedStr.any { it.isLetter() } && decodedStr.length > 5) {
                        results.add(LeakItem("Base64", "MEDIUM", "→ ${decodedStr.take(100)}", "Encoded string"))
                    }
                } catch (_: Exception) {}
            }

            // License keys
            val licenses = Regex("""(?:LIC|KEY|LICENSE)[-_]?[A-Z0-9]{4,}[-_]?[A-Z0-9]{4,}[-_]?[A-Z0-9]{4,}""", RegexOption.IGNORE_CASE).findAll(text)
            for (m in licenses) {
                results.add(LeakItem("License Key", "CRITICAL", m.value, "License key pattern"))
            }

            // SQL queries
            val sql = Regex("""(?:SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER)\s+\w+""", RegexOption.IGNORE_CASE).findAll(text)
            for (m in sql) {
                results.add(LeakItem("SQL Query", "HIGH", m.value.take(100), "SQL statement"))
            }

            // Crypto patterns
            val crypto = Regex("""(?:AES|RSA|DES|SHA256|MD5|HMAC|bcrypt)\s*[(\s]""", RegexOption.IGNORE_CASE).findAll(text)
            for (m in crypto) {
                results.add(LeakItem("Crypto", "MEDIUM", m.value.trim(), "Cryptographic operation"))
            }

            // Deduplicate and sort by severity
            val unique = results.distinctBy { it.value }.sortedByDescending {
                when (it.severity) { "CRITICAL" -> 4; "HIGH" -> 3; "MEDIUM" -> 2; else -> 1 }
            }

            withContext(Dispatchers.Main) {
                leaks = unique
                status = "Found ${unique.size} leaked items"
                isRunning = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFile() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔓 Auto Leak Source", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { loadFile() }) { Icon(Icons.Default.Refresh, "Load") }
                    if (leaks.isNotEmpty()) {
                        IconButton(onClick = {
                            val text = leaks.joinToString("\n") { "[${it.severity}] ${it.category}: ${it.value}" }
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("leaks", text))
                            Toast.makeText(context, "Copied ${leaks.size} items!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("🔓 Auto Leak Source Analyzer", fontWeight = FontWeight.Bold, color = AccentRed, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Extracts: URLs, IPs, emails, tokens, secrets, SQL, crypto, license keys", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(fileName.ifEmpty { "No file loaded" }, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { scan() }, enabled = !isRunning && fileName.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                        if (isRunning) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("🔍 Scan for Leaked Data", fontSize = 11.sp)
                    }
                }
            }
            if (leaks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CRITICAL" to AccentRed, "HIGH" to AccentOrange, "MEDIUM" to Color(0xFFFFD740), "LOW" to TextSecondary).forEach { (sev, color) ->
                        val count = leaks.count { it.severity == sev }
                        if (count > 0) Text("● $sev: $count", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (leaks.isEmpty() && status.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔓", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                        Text(status, color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                    itemsIndexed(leaks) { _, item ->
                        val color = when (item.severity) { "CRITICAL" -> AccentRed; "HIGH" -> AccentOrange; "MEDIUM" -> Color(0xFFFFD740); else -> TextSecondary }
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)), shape = RoundedCornerShape(6.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("[${item.severity}] ${item.category}", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(item.value, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 3)
                                Text(item.context, color = TextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
