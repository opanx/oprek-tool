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
            val rawData = withContext(Dispatchers.IO) { f.readBytes() }

            // CRITICAL FIX: Extract only printable ASCII text from binary
            // This prevents regex hangs on binary/JNI data
            val MAX_TEXT_SCAN = 5_000_000 // 5MB max for regex scanning
            val printable = StringBuilder(minOf(rawData.size, MAX_TEXT_SCAN))
            for (i in 0 until minOf(rawData.size, MAX_TEXT_SCAN)) {
                val b = rawData[i].toInt() and 0xFF
                if (b in 0x20..0x7E || b == 0x0A || b == 0x09) {
                    printable.append(b.toChar())
                } else {
                    printable.append(' ') // Replace non-printable with space
                }
            }
            val text = printable.toString()
            addLog("[+] Extracted ${text.length} printable chars (binary-safe)")

            val results = mutableListOf<LeakItem>()

            // Phase 1: URLs (5%)
            progress = 0.05f
            addLog("[*] Phase 1/9: Scanning URLs...")
            try {
                val urls = Regex("""https?://[\x20-\x7E]{5,200}""").findAll(text)
                var urlCount = 0
                for (m in urls) { results.add(LeakItem("URL", "HIGH", m.value.trim(), "Network endpoint")); urlCount++ }
                addLog("    → Found $urlCount URLs")
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

            // Phase 2: IPs (10%)
            progress = 0.10f
            addLog("[*] Phase 2/9: Scanning IP addresses...")
            try {
                val ips = Regex("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]+)?\b""").findAll(text)
                var ipCount = 0
                for (m in ips) {
                    if (!m.value.startsWith("0.") && !m.value.startsWith("127.") && !m.value.startsWith("255.")) {
                        results.add(LeakItem("IP Address", "HIGH", m.value, "Hardcoded IP address")); ipCount++
                    }
                }
                addLog("    → Found $ipCount IPs")
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

            // Phase 3: Emails (15%)
            progress = 0.15f
            addLog("[*] Phase 3/9: Scanning emails...")
            try {
                val emails = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").findAll(text)
                var emailCount = 0
                for (m in emails) { results.add(LeakItem("Email", "MEDIUM", m.value, "Email address")); emailCount++ }
                addLog("    → Found $emailCount emails")
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

            // Phase 4: Secrets & tokens (25%)
            progress = 0.25f
            addLog("[*] Phase 4/9: Scanning secrets & tokens...")
            try {
                val tokenPat = Regex("""(?:api[_-]?key|token|secret|password|passwd|pwd|auth|credential|private[_-]?key|access[_-]?key|license[_-]?key|activation[_-]?key|serial[_-]?key)\s*[:=]\s*['"]?([^\s'"]{5,80})""", RegexOption.IGNORE_CASE)
                var secretCount = 0
                for (m in tokenPat.findAll(text)) { results.add(LeakItem("Secret", "CRITICAL", m.groupValues[1].trim(), "Hardcoded secret/key")); secretCount++ }
                addLog("    → Found $secretCount secrets")
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

            // Phase 5: JWT & License keys (35%)
            progress = 0.35f
            addLog("[*] Phase 5/9: Scanning JWT & license keys...")
            try {
                val jwts = Regex("""eyJ[a-zA-Z0-9_-]{40,500}""").findAll(text)
                for (m in jwts) { results.add(LeakItem("JWT Token", "CRITICAL", m.value.take(80) + "...", "JWT token")) }
                val licenses = Regex("""(?:LIC|KEY|LICENSE|LICENCE|REGKEY|ACTKEY)[-_]?[A-Z0-9]{4,}[-_]?[A-Z0-9]{4,}[-_]?[A-Z0-9]{4,}""", RegexOption.IGNORE_CASE).findAll(text)
                var licCount = 0
                for (m in licenses) { results.add(LeakItem("License Key", "CRITICAL", m.value, "License key pattern")); licCount++ }
                addLog("    → Found $licCount license keys + ${jwts.count()} JWTs")
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

            // Phase 6: SQL (45%)
            progress = 0.45f
            addLog("[*] Phase 6/9: Scanning SQL queries...")
            try {
                val sql = Regex("""(?:SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER)\s+\w+""", RegexOption.IGNORE_CASE).findAll(text)
                var sqlCount = 0
                for (m in sql) { results.add(LeakItem("SQL Query", "HIGH", m.value.take(100), "SQL statement")); sqlCount++ }
                addLog("    → Found $sqlCount SQL queries")
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

            // Phase 7: Base64 (55%) - only scan printable chunks, max 200 matches
            progress = 0.55f
            addLog("[*] Phase 7/9: Decoding Base64 strings (max 200)...")
            try {
                val b64Pat = Regex("""[A-Za-z0-9+/]{40,200}={0,2}""")
                var b64Count = 0
                for (m in b64Pat.findAll(text)) {
                    if (b64Count >= 200) break // Limit to prevent hang
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
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

            // Phase 8: JNI / Java class signatures (70%) - DETECT JNI注册 + class refs
            progress = 0.70f
            addLog("[*] Phase 8/9: Scanning JNI + Java signatures...")
            try {
                // JNI RegisterNatives patterns
                val jniPat = Regex("""RegisterNatives|FindClass|GetMethodID|GetFieldID|CallVoidMethod|CallIntMethod|NewStringUTF|GetStringUTFChars|GetByteArrayElements""")
                var jniCount = 0
                for (m in jniPat.findAll(text)) { results.add(LeakItem("JNI", "MEDIUM", m.value, "JNI function reference")); jniCount++ }
                addLog("    → Found $jniCount JNI references")

                // Java class/package references
                val javaPat = Regex("""L[a-z]+(?:/[a-z]+){2,};""")
                var javaCount = 0
                for (m in javaPat.findAll(text)) {
                    if (javaCount < 100) { // Limit
                        results.add(LeakItem("Java Class", "MEDIUM", m.value, "JNI class descriptor")); javaCount++
                    }
                }
                addLog("    → Found $javaCount Java class descriptors")

                // DobbyHook / inline hook patterns
                val hookPat = Regex("""DobbyHook|dobbyHook|InlineHook|hook_function|PLTHook|GOTHook|xHook|whale""")
                var hookCount = 0
                for (m in hookPat.findAll(text)) { results.add(LeakItem("Hook", "HIGH", m.value, "Hooking framework reference")); hookCount++ }
                addLog("    → Found $hookCount hook references")

                // Anti-tamper / anti-debug
                val antiPat = Regex("""frida|xposed|ptrace|TracerPid|/proc/self/status|isDebugger|Debug\.isDebugger|android.os.Debug""")
                var antiCount = 0
                for (m in antiPat.findAll(text)) { results.add(LeakItem("Anti-Debug", "HIGH", m.value, "Anti-analysis mechanism")); antiCount++ }
                addLog("    → Found $antiCount anti-debug patterns")
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

            // Phase 9: Misc - Telegram, paths, crypto (90%)
            progress = 0.90f
            addLog("[*] Phase 9/9: Scanning misc patterns...")
            try {
                // Telegram handles
                val tg = Regex("""@[a-zA-Z0-9_]{3,30}""").findAll(text)
                var tgCount = 0
                for (m in tg) {
                    val word = m.value.substring(1)
                    if (word.length > 3 && !word.all { it.isDigit() } && tgCount < 50) {
                        results.add(LeakItem("Telegram", "MEDIUM", m.value, "Telegram handle")); tgCount++
                    }
                }
                // File paths (limit)
                val pathPat = Regex("""/[a-zA-Z0-9._/-]{5,80}""").findAll(text)
                var pathCount = 0
                for (m in pathPat) {
                    if (pathCount < 100 && m.value.contains("/")) {
                        results.add(LeakItem("File Path", "LOW", m.value, "File system path")); pathCount++
                    }
                }
                // Crypto
                val crypto = Regex("""(?:AES|RSA|DES|SHA256|MD5|HMAC|bcrypt)\s*[(\s]""", RegexOption.IGNORE_CASE).findAll(text)
                for (m in crypto) { results.add(LeakItem("Crypto", "MEDIUM", m.value.trim(), "Cryptographic operation")) }
                addLog("    → Found ${tgCount} Telegram, ${pathCount} paths")
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

            // Also scan raw bytes for interesting patterns (not just text)
            addLog("[*] Bonus: Scanning raw bytes...")
            try {
                // Check for ELF-specific auth patterns in raw bytes
                val data = rawData
                val dataStr = String(data, Charsets.US_ASCII).filter { it.code in 0x20..0x7E }

                // Supabase / auth URLs
                val authUrls = Regex("""(?:supabase|firebase|auth0|keycloak|accounts\.google|login|signin|signup|verify|authenticate|authorize)\.?[a-zA-Z]*\.(?:com|io|dev|app|net)""", RegexOption.IGNORE_CASE).findAll(dataStr)
                var authCount = 0
                for (m in authUrls) { results.add(LeakItem("Auth URL", "CRITICAL", m.value, "Authentication endpoint")); authCount++ }
                addLog("    → Found $authCount auth endpoints")

                // Obfuscated domains (common cheat panel hosting)
                val obfDomains = Regex("""(?:my\.id|workers\.dev|vercel\.app|netlify\.app|render\.com|railway\.app)""", RegexOption.IGNORE_CASE).findAll(dataStr)
                for (m in obfDomains) { results.add(LeakItem("Domain", "HIGH", m.value, "Cloud-hosted domain")) }
            } catch (e: Exception) { addLog("    → Error: ${e.message}") }

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
                    Text("URLs, IPs, secrets, JWT, SQL, Base64, JNI, hooks, anti-debug", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(fileName.ifEmpty { "No file loaded" }, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
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

            if (leaks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CRITICAL" to AccentRed, "HIGH" to AccentOrange, "MEDIUM" to Color(0xFFFFD740), "LOW" to TextSecondary).forEach { (sev, color) ->
                        val count = leaks.count { it.severity == sev }
                        if (count > 0) Text("● $sev: $count", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            var selectedTab by remember { mutableStateOf(0) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                listOf("📋 Results (${leaks.size})", "📝 Log (${logLines.size})").forEachIndexed { idx, label ->
                    val isSelected = selectedTab == idx
                    TextButton(onClick = { selectedTab = idx }, modifier = Modifier.weight(1f)) {
                        Text(label, color = if (isSelected) AccentCyan else TextSecondary, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            when (selectedTab) {
                0 -> {
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
                1 -> {
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
