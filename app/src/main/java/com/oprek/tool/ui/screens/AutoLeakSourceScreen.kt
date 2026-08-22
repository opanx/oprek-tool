package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState

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
    var progress by remember { mutableStateOf(0f) }
    var logLines by remember { mutableStateOf(listOf<String>()) }

    fun addLog(msg: String) { logLines = logLines + msg }

    // Auto-refresh when file changes
    val rev = SharedFileState.revision
    LaunchedEffect(rev) {
        val f = SharedFileState.findFile(context)
        if (f != null) {
            fileName = f.name
            status = "Loaded: ${f.name} (${f.length()} bytes)"
            addLog("[+] Auto-loaded: ${f.name}")
        }
    }

    fun scan() {
        val f = SharedFileState.findFile(context) ?: run {
            addLog("[-] No file loaded!")
            return
        }
        isRunning = true
        leaks = emptyList()
        logLines = emptyList()
        progress = 0f
        scope.launch(Dispatchers.IO) {
            addLog("[+] Starting leak scan on: ${f.name}")
            addLog("[+] File size: ${f.length()} bytes")
            val data = f.readBytes()
            val text = String(data, Charsets.US_ASCII)
            val results = mutableListOf<LeakItem>()

            // Phase 1: URLs (10%)
            progress = 0.1f
            addLog("[*] Phase 1/8: Scanning URLs...")
            val urls = Regex("""https?://[^\s\x00"'<>\\]{5,200}""").findAll(text)
            var urlCount = 0
            for (m in urls) { results.add(LeakItem("URL", "HIGH", m.value, "Network endpoint")); urlCount++ }
            addLog("    → Found $urlCount URLs")

            // Phase 2: IPs (20%)
            progress = 0.2f
            addLog("[*] Phase 2/8: Scanning IP addresses...")
            val ips = Regex("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]+)?\b""").findAll(text)
            var ipCount = 0
            for (m in ips) {
                if (!m.value.startsWith("0.") && !m.value.startsWith("127.") && !m.value.startsWith("255.")) {
                    results.add(LeakItem("IP Address", "HIGH", m.value, "Hardcoded IP address")); ipCount++
                }
            }
            addLog("    → Found $ipCount IPs")

            // Phase 3: Emails (30%)
            progress = 0.3f
            addLog("[*] Phase 3/8: Scanning emails...")
            val emails = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").findAll(text)
            var emailCount = 0
            for (m in emails) { results.add(LeakItem("Email", "MEDIUM", m.value, "Email address")); emailCount++ }
            addLog("    → Found $emailCount emails")

            // Phase 4: Secrets & tokens (40%)
            progress = 0.4f
            addLog("[*] Phase 4/8: Scanning secrets & tokens...")
            val tokens = Regex("""(?:api[_-]?key|token|secret|password|passwd|pwd|auth|credential|private[_-]?key|access[_-]?key)\s*[:=]\s*['"]?([^\s'"]{5,})""", RegexOption.IGNORE_CASE).findAll(text)
            var secretCount = 0
            for (m in tokens) { results.add(LeakItem("Secret", "CRITICAL", m.groupValues[1], "Hardcoded secret/key")); secretCount++ }
            addLog("    → Found $secretCount secrets")

            // Phase 5: JWT & License keys (50%)
            progress = 0.5f
            addLog("[*] Phase 5/8: Scanning JWT & license keys...")
            val jwts = Regex("""eyJ[a-zA-Z0-9_-]{50,500}""").findAll(text)
            for (m in jwts) { results.add(LeakItem("JWT Token", "CRITICAL", m.value.take(80) + "...", "JWT token")) }
            val licenses = Regex("""(?:LIC|KEY|LICENSE|LICENCE)[-_]?[A-Z0-9]{4,}[-_]?[A-Z0-9]{4,}[-_]?[A-Z0-9]{4,}""", RegexOption.IGNORE_CASE).findAll(text)
            var licCount = 0
            for (m in licenses) { results.add(LeakItem("License Key", "CRITICAL", m.value, "License key pattern")); licCount++ }
            addLog("    → Found $licCount license keys + ${jwts.count()} JWTs")

            // Phase 6: SQL & database (60%)
            progress = 0.6f
            addLog("[*] Phase 6/8: Scanning SQL queries...")
            val sql = Regex("""(?:SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER)\s+\w+""", RegexOption.IGNORE_CASE).findAll(text)
            var sqlCount = 0
            for (m in sql) { results.add(LeakItem("SQL Query", "HIGH", m.value.take(100), "SQL statement")); sqlCount++ }
            addLog("    → Found $sqlCount SQL queries")

            // Phase 7: Base64 & crypto (70%)
            progress = 0.7f
            addLog("[*] Phase 7/8: Decoding Base64 strings...")
            val b64 = Regex("""[A-Za-z0-9+/]{40,}={0,2}""").findAll(text)
            var b64Count = 0
            for (m in b64) {
                try {
                    val decoded = android.util.Base64.decode(m.value, android.util.Base64.DEFAULT)
                    val decodedStr = String(decoded)
                    if (decodedStr.any { it.isLetter() } && decodedStr.length > 5) {
                        results.add(LeakItem("Base64", "MEDIUM", "→ ${decodedStr.take(100)}", "Encoded string"))
                        b64Count++
                    }
                } catch (_: Exception) {}
            }
            addLog("    → Decoded $b64Count Base64 strings")

            // Phase 8: Telegram, paths, crypto (90%)
            progress = 0.9f
            addLog("[*] Phase 8/8: Scanning misc patterns...")
            val tg = Regex("""@[a-zA-Z0-9_]{3,30}""").findAll(text)
            for (m in tg) {
                val word = m.value.substring(1)
                if (word.length > 3 && !word.all { it.isDigit() }) {
                    results.add(LeakItem("Telegram", "MEDIUM", m.value, "Telegram handle"))
                }
            }
            val paths = Regex("""/[a-zA-Z0-9._/-]{5,100}""").findAll(text)
            for (m in paths) {
                if (m.value.contains("/")) results.add(LeakItem("File Path", "LOW", m.value, "File system path"))
            }
            val crypto = Regex("""(?:AES|RSA|DES|SHA256|MD5|HMAC|bcrypt)\s*[(\s]""", RegexOption.IGNORE_CASE).findAll(text)
            for (m in crypto) { results.add(LeakItem("Crypto", "MEDIUM", m.value.trim(), "Cryptographic operation")) }

            // Deduplicate and sort
            val unique = results.distinctBy { it.value }.sortedByDescending {
                when (it.severity) { "CRITICAL" -> 4; "HIGH" -> 3; "MEDIUM" -> 2; else -> 1 }
            }

            progress = 1.0f
            addLog("")
            addLog("[+] Scan complete!")
            addLog("[+] Total unique leaks: ${unique.size}")
            addLog("    CRITICAL: ${unique.count { it.severity == "CRITICAL" }}")
            addLog("    HIGH: ${unique.count { it.severity == "HIGH" }}")
            addLog("    MEDIUM: ${unique.count { it.severity == "MEDIUM" }}")
            addLog("    LOW: ${unique.count { it.severity == "LOW" }}")

            withContext(Dispatchers.Main) {
                leaks = unique
                status = "Found ${unique.size} leaked items"
                isRunning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔓 Auto Leak Source", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { scan() }) { Icon(Icons.Default.Refresh, "Scan") }
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
            // Info card
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("🔓 Auto Leak Source Analyzer", fontWeight = FontWeight.Bold, color = AccentRed, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("URLs, IPs, emails, tokens, secrets, SQL, crypto, licenses, Base64", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(fileName.ifEmpty { "No file loaded" }, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))

                    // Progress bar
                    if (isRunning) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = AccentRed)
                        Spacer(Modifier.height(4.dp))
                        Text("Scanning... ${"%.0f".format(progress * 100)}%", color = AccentOrange, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                    }

                    Button(onClick = { scan() }, enabled = !isRunning, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                        if (isRunning) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Scanning...", fontSize = 11.sp)
                        } else {
                            Text("🔍 Scan for Leaked Data", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Severity badges
            if (leaks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CRITICAL" to AccentRed, "HIGH" to AccentOrange, "MEDIUM" to Color(0xFFFFD740), "LOW" to TextSecondary).forEach { (sev, color) ->
                        val count = leaks.count { it.severity == sev }
                        if (count > 0) Text("● $sev: $count", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Two tabs: Results + Log
            var selectedTab by remember { mutableStateOf(0) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                listOf("📋 Results (${leaks.size})", "📝 Log (${logLines.size})").forEachIndexed { idx, label ->
                    val isSelected = selectedTab == idx
                    TextButton(onClick = { selectedTab = idx }, modifier = Modifier.weight(1f)) {
                        Text(label, color = if (isSelected) AccentCyan else TextSecondary, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            // Content
            when (selectedTab) {
                0 -> { // Results
                    if (leaks.isEmpty() && status.isNotEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔓", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                                Text(status, color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else if (leaks.isEmpty() && !isRunning) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                                Text("Load a file and tap Scan", color = TextSecondary, fontSize = 13.sp)
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
                1 -> { // Log
                    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                        itemsIndexed(logLines) { _, line ->
                            val color = when {
                                line.startsWith("[+]") -> AccentGreen
                                line.startsWith("[-]") -> AccentRed
                                line.startsWith("[*]") -> AccentCyan
                                line.startsWith("    →") -> AccentOrange
                                else -> TextSecondary
                            }
                            Text(line, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
