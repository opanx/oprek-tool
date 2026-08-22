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
import com.oprek.tool.core.SharedFileState
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
            addLog("[+] Starting deep leak scan on: ${f.name}")
            addLog("[+] File size: ${f.length()} bytes")

            val rawData = withContext(Dispatchers.IO) { f.readBytes() }

            // Extract printable strings from BOTH text and binary regions
            // Text mode: read as text
            val textMode = extractPrintableText(rawData)
            addLog("[+] Text mode: ${textMode.length} chars extracted")

            // Binary mode: extract ALL printable sequences >= 4 chars
            val binaryStrings = extractBinaryStrings(rawData, 4)
            addLog("[+] Binary strings: ${binaryStrings.size} sequences found")

            val results = mutableListOf<LeakItem>()
            val seen = mutableSetOf<String>() // dedup

            fun addResult(cat: String, sev: String, value: String, ctx: String) {
                val key = "$cat:$value"
                if (key !in seen && value.length > 3) {
                    seen.add(key)
                    results.add(LeakItem(cat, sev, value, ctx))
                }
            }

            // ===== PHASE 1: URLs (both text and binary) =====
            progress = 0.05f
            addLog("[*] Phase 1/11: Scanning URLs...")
            var urlCount = 0
            try {
                val urlPat = Regex("""https?://[\x20-\x7E]{3,300}""")
                for (src in listOf(textMode) + binaryStrings.joinToString(" ")) {
                    for (m in urlPat.findAll(src)) {
                        val url = m.value.trimEnd(' ', '.', ',', ';', ')', '>')
                        if (url.length > 8) {
                            addResult("URL", "HIGH", url, "Network endpoint")
                            urlCount++
                        }
                    }
                }
            } catch (_: Exception) {}
            // Also search raw bytes for http:// and https://
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val rawUrls = Regex("""https?://[\x20-\x7E]{3,200}""")
                for (m in rawUrls.findAll(rawText)) {
                    val url = m.value.trimEnd(' ', '.', ',', ';')
                    if (url.length > 8) {
                        addResult("URL", "HIGH", url, "Network endpoint (raw)")
                        urlCount++
                    }
                }
            } catch (_: Exception) {}
            addLog("    → Found $urlCount URLs")

            // ===== PHASE 2: IPs + Ports =====
            progress = 0.10f
            addLog("[*] Phase 2/11: Scanning IP addresses...")
            var ipCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val ipPat = Regex("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]{2,5})?\b""")
                for (m in ipPat.findAll(rawText)) {
                    val ip = m.value
                    val parts = ip.split(".")
                    val first = parts[0].split(":")[0].toIntOrNull() ?: 0
                    if (first in 1..223 && !ip.startsWith("127.") && !ip.startsWith("0.") && !ip.startsWith("255.")) {
                        addResult("IP Address", "HIGH", ip, "Hardcoded IP:port")
                        ipCount++
                    }
                }
            } catch (_: Exception) {}
            addLog("    → Found $ipCount IPs")

            // ===== PHASE 3: Emails =====
            progress = 0.15f
            addLog("[*] Phase 3/11: Scanning emails...")
            var emailCount = 0
            try {
                val emailPat = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,20}""")
                for (m in emailPat.findAll(textMode)) {
                    addResult("Email", "MEDIUM", m.value, "Email address")
                    emailCount++
                }
                // Also search raw
                val rawText = String(rawData, Charsets.US_ASCII)
                for (m in emailPat.findAll(rawText)) {
                    addResult("Email", "MEDIUM", m.value, "Email address (raw)")
                    emailCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $emailCount emails")

            // ===== PHASE 4: Secrets & Tokens =====
            progress = 0.20f
            addLog("[*] Phase 4/11: Scanning secrets & tokens...")
            var secretCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                // API keys, tokens, passwords, etc.
                val secretPat = Regex("""(?:api[_\-]?key|token|secret|password|passwd|pwd|auth[_\-]?token|credential|private[_\-]?key|access[_\-]?key|license[_\-]?key|activation[_\-]?key|serial[_\-]?key|apikey|apisecret|client[_\-]?secret|app[_\-]?secret|encryption[_\-]?key|signing[_\-]?key)\s*[:=]\s*['"]?([^\s'"<>]{3,120})['"]?""", RegexOption.IGNORE_CASE)
                for (m in secretPat.findAll(rawText)) {
                    val val_ = m.groupValues[1].trim()
                    if (val_.length > 3 && !val_.startsWith("0.") && val_ != "null" && val_ != "undefined") {
                        addResult("Secret", "CRITICAL", "${m.groupValues[0].substringBefore("=").trim()} = $val_", "Hardcoded secret")
                        secretCount++
                    }
                }
                // Supabase anon keys
                val supaKey = Regex("""eyJ[A-Za-z0-9_-]{50,500}""")
                for (m in supaKey.findAll(rawText)) {
                    addResult("JWT/Supabase Key", "CRITICAL", m.value.take(80) + "...", "Supabase/anon key")
                    secretCount++
                }
                // Convex keys
                val convexKey = Regex("""(?:NS-|AK-|PK-|SK-|TK-)[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}""")
                for (m in convexKey.findAll(rawText)) {
                    addResult("Convex Key", "CRITICAL", m.value, "Convex API key")
                    secretCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $secretCount secrets")

            // ===== PHASE 5: JWT & License Keys =====
            progress = 0.30f
            addLog("[*] Phase 5/11: Scanning JWT & license keys...")
            var licCount = 0
            var jwtCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                // JWT tokens
                val jwtPat = Regex("""eyJ[A-Za-z0-9_-]{40,500}\.[A-Za-z0-9_-]{40,500}\.[A-Za-z0-9_-]{10,200}""")
                for (m in jwtPat.findAll(rawText)) {
                    addResult("JWT Token", "CRITICAL", m.value.take(60) + "...", "Full JWT (header.payload.signature)")
                    jwtCount++
                }
                // License patterns
                val licPat = Regex("""(?:LIC|KEY|LICENSE|LICENCE|REGKEY|ACTKEY|REGISTRATION)[\-_]?[A-Z0-9]{2,}[\-_][A-Z0-9]{2,}[\-_][A-Z0-9]{2,}[\-_][A-Z0-9]{2,}""", RegexOption.IGNORE_CASE)
                for (m in licPat.findAll(rawText)) {
                    addResult("License Key", "CRITICAL", m.value, "License/registration key")
                    licCount++
                }
                // LIC-XXXX patterns (common in cheat panels)
                val lic2 = Regex("""LIC[\-]?[A-Z0-9]{4}[\-][A-Z0-9]{4}[\-][A-Z0-9]{4}[\-][A-Z0-9]{4}[\-][A-Z0-9]{4}""")
                for (m in lic2.findAll(rawText)) {
                    addResult("License Key", "CRITICAL", m.value, "LIC-format key")
                    licCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $licCount license keys + $jwtCount JWTs")

            // ===== PHASE 6: SQL queries =====
            progress = 0.38f
            addLog("[*] Phase 6/11: Scanning SQL queries...")
            var sqlCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val sqlPat = Regex("""(?:SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|UNION)\s+.{5,200}?(?:;|\bFROM\b|\bWHERE\b|\bINTO\b|\bSET\b)""", RegexOption.IGNORE_CASE)
                for (m in sqlPat.findAll(rawText)) {
                    addResult("SQL Query", "HIGH", m.value.take(150), "SQL statement")
                    sqlCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $sqlCount SQL queries")

            // ===== PHASE 7: Base64 decoded strings =====
            progress = 0.45f
            addLog("[*] Phase 7/11: Decoding Base64 strings...")
            var b64Count = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val b64Pat = Regex("""[A-Za-z0-9+/]{20,300}={0,2}""")
                for (m in b64Pat.findAll(rawText)) {
                    if (b64Count >= 500) break
                    try {
                        val decoded = android.util.Base64.decode(m.value, android.util.Base64.DEFAULT)
                        val decodedStr = String(decoded, Charsets.UTF_8)
                        // Only keep if it looks meaningful
                        if (decodedStr.length > 5 && decodedStr.any { it.isLetter() } &&
                            !decodedStr.contains(0.toChar()) && decodedStr.count { it.isLetter() } > decodedStr.length / 3) {
                            // Check if decoded contains interesting stuff
                            when {
                                decodedStr.contains("http") -> addResult("Base64→URL", "CRITICAL", decodedStr.take(150), "Base64-encoded URL")
                                decodedStr.contains("key", ignoreCase = true) || decodedStr.contains("secret", ignoreCase = true) -> addResult("Base64→Secret", "CRITICAL", decodedStr.take(150), "Base64-encoded secret")
                                decodedStr.contains("password", ignoreCase = true) -> addResult("Base64→Password", "CRITICAL", decodedStr.take(150), "Base64-encoded password")
                                decodedStr.contains("L/") || decodedStr.contains("com/") -> addResult("Base64→Java", "HIGH", decodedStr.take(150), "Base64-encoded Java class")
                                decodedStr.contains("SELECT") || decodedStr.contains("INSERT") -> addResult("Base64→SQL", "HIGH", decodedStr.take(150), "Base64-encoded SQL")
                                decodedStr.contains("supabase", ignoreCase = true) -> addResult("Base64→Supabase", "CRITICAL", decodedStr.take(150), "Base64-encoded Supabase ref")
                                else -> addResult("Base64", "MEDIUM", "→ ${decodedStr.take(120)}", "Decoded Base64 string")
                            }
                            b64Count++
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            addLog("    → Decoded $b64Count meaningful Base64 strings")

            // ===== PHASE 8: Shell script analysis (Bash/SH specific) =====
            progress = 0.55f
            addLog("[*] Phase 8/11: Shell script deep analysis...")
            var shellCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                // Obfuscated URLs (base64 encoded in shell)
                val encodedUrls = Regex("""(?:echo|printf|curl|wget|eval|exec)\s+(?:['"]?\$?\(|['"])[A-Za-z0-9+/=]{20,}""", RegexOption.IGNORE_CASE)
                for (m in encodedUrls.findAll(rawText)) {
                    addResult("Shell→Encoded", "HIGH", m.value.take(150), "Shell command with encoded data")
                    shellCount++
                }
                // curl/wget with URLs
                val curlPat = Regex("""(?:curl|wget|fetch|http_client)\s+(?:-[a-zA-Z]+\s+)*['"]?(https?://[^\s'"]+)['"]?""")
                for (m in curlPat.findAll(rawText)) {
                    addResult("Shell→URL", "HIGH", m.value.take(200), "Shell network request")
                    shellCount++
                }
                // eval/decode patterns (obfuscation)
                val evalPat = Regex("""(?:eval|exec|source|\.\/|bash\s+-c|sh\s+-c)\s+['"]([^'"]{10,200})['"]""")
                for (m in evalPat.findAll(rawText)) {
                    addResult("Shell→Eval", "HIGH", m.value.take(200), "Shell eval/exec (possible obfuscation)")
                    shellCount++
                }
                // Embedded binary detection
                val binPat = Regex("""(?:cat|dd|base64\s+-d|xxd\s+-r)\s+(?:<<\s*'?EOF'?|['"]([^'"]+)['"]|/dev/[^ ]+)""")
                for (m in binPat.findAll(rawText)) {
                    addResult("Shell→Binary", "HIGH", m.value.take(200), "Shell binary embedding")
                    shellCount++
                }
                // Environment variables with secrets
                val envPat = Regex("""(?:export\s+|)([A-Z_]{3,30})=(['"]?[^'"\n]{5,200})['"]?""")
                for (m in envPat.findAll(rawText)) {
                    val varName = m.groupValues[1]
                    val varVal = m.groupValues[2]
                    if (varName.contains("KEY") || varName.contains("SECRET") || varName.contains("TOKEN") ||
                        varName.contains("PASSWORD") || varName.contains("AUTH") || varName.contains("API") ||
                        varName.contains("SERVER") || varName.contains("HOST") || varName.contains("URL") ||
                        varName.contains("ENDPOINT") || varName.contains("LICENSE")) {
                        addResult("Shell→Env", "CRITICAL", "$varName=$varVal", "Sensitive environment variable")
                        shellCount++
                    }
                }
            } catch (_: Exception) {}
            addLog("    → Found $shellCount shell patterns")

            // ===== PHASE 9: JNI + Java + Hooks + Anti-Debug =====
            progress = 0.65f
            addLog("[*] Phase 9/11: Scanning JNI, hooks, anti-debug...")
            var jniCount = 0
            var hookCount = 0
            var antiCount = 0
            var javaCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)

                // JNI functions
                val jniFuncs = listOf("RegisterNatives", "FindClass", "GetMethodID", "GetFieldID",
                    "CallVoidMethod", "CallIntMethod", "CallBooleanMethod", "NewStringUTF",
                    "GetStringUTFChars", "GetByteArrayElements", "ReleaseByteArrayElements",
                    "JNI_OnLoad", "GetStaticMethodID", "GetStaticFieldID", "CallStaticVoidMethod",
                    "CallStaticIntMethod", "CallStaticObjectMethod", "GetObjectClass", "GetSuperclass",
                    "IsAssignableFrom", "Throw", "ThrowNew", "ExceptionCheck", "ExceptionClear",
                    "GetJavaVM", "AttachCurrentThread", "DetachCurrentThread", "MonitorEnter", "MonitorExit")
                for (func in jniFuncs) {
                    var idx = 0
                    while (true) {
                        val pos = rawText.indexOf(func, idx, ignoreCase = true)
                        if (pos < 0) break
                        addResult("JNI", "MEDIUM", func, "JNI function @ offset 0x${"%X".format(pos)}")
                        jniCount++
                        idx = pos + 1
                    }
                }

                // Java class descriptors (Lcom/package/Class;)
                val javaClassPat = Regex("""L[a-z][a-z0-9]*(?:/[a-z][a-z0-9]*){2,};""")
                for (m in javaClassPat.findAll(rawText)) {
                    if (javaCount < 500) {
                        addResult("Java Class", "MEDIUM", m.value, "JNI class descriptor")
                        javaCount++
                    }
                }

                // Java method signatures
                val javaMethodPat = Regex("""L[a-z][a-z0-9]*(?:/[a-z][a-z0-9]*){1,};[a-zA-Z][a-zA-Z0-9]*\([^)]*\)[A-Z]""")
                for (m in javaMethodPat.findAll(rawText)) {
                    if (javaCount < 500) {
                        addResult("Java Method", "MEDIUM", m.value, "JNI method signature")
                        javaCount++
                    }
                }

                // Hook frameworks
                val hookPatterns = listOf("DobbyHook", "dobbyHook", "dobby_inject", "InlineHook",
                    "hook_function", "PLTHook", "GOTHook", "xHook", "whale", "Substrate",
                    "hookMethod", "hook_func", "intercept", "detour", "trampoline",
                    "MSHookFunction", "PLTReplace", "GOTReplace", "hook_register",
                    "dobby", "shadowhook", "xmhprof")
                for (h in hookPatterns) {
                    var idx = 0
                    while (true) {
                        val pos = rawText.indexOf(h, idx, ignoreCase = true)
                        if (pos < 0) break
                        addResult("Hook", "HIGH", h, "Hook framework @ 0x${"%X".format(pos)}")
                        hookCount++
                        idx = pos + 1
                    }
                }

                // Anti-debug / anti-analysis
                val antiPatterns = listOf("frida", "Frida", "FRIDA", "xposed", "Xposed", "XPOSED",
                    "ptrace", "TracerPid", "/proc/self/status", "/proc/self/maps",
                    "isDebugger", "Debug.isDebugger", "android.os.Debug",
                    "Debug.waitForDebugger", "anti_debug", "anti_debugger",
                    "detectDebugger", "checkRoot", "detectRoot", "su -c",
                    "/system/bin/su", "/system/xbin/su", "Superuser",
                    "RootBeer", "SafetyNet", "Play Integrity", "attestation",
                    "anti_vm", "detectEmulator", "emulator_detection",
                    "ro.hardware", "ro.product.model", "build.fingerprint",
                    "check_online", "license_check", "auth_check", "key_check",
                    "verify_license", "validate_key", "server_check", "heartbeat")
                for (a in antiPatterns) {
                    var idx = 0
                    while (true) {
                        val pos = rawText.indexOf(a, idx, ignoreCase = false)
                        if (pos < 0) break
                        addResult("Anti-Debug", "HIGH", a, "Anti-analysis @ 0x${"%X".format(pos)}")
                        antiCount++
                        idx = pos + 1
                    }
                }
            } catch (_: Exception) {}
            addLog("    → JNI: $jniCount | Java: $javaCount | Hooks: $hookCount | Anti-debug: $antiCount")

            // ===== PHASE 10: Telegram, domains, crypto, paths =====
            progress = 0.80f
            addLog("[*] Phase 10/11: Telegram, domains, crypto, paths...")
            var miscCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                // Telegram handles
                val tgPat = Regex("""@[a-zA-Z][a-zA-Z0-9_]{2,30}""")
                for (m in tgPat.findAll(rawText)) {
                    val word = m.value.substring(1)
                    // Filter common non-telegram words
                    val notTg = setOf("param", "override", "return", "import", "include", "define",
                        "pragma", "public", "private", "protected", "static", "const", "null",
                        "true", "false", "void", "int", "char", "float", "double", "long",
                        "begin", "end", "loop", "done", "then", "else", "echo", "test",
                        "type", "name", "size", "file", "path", "data", "code", "exec",
                        "call", "func", "list", "head", "tail", "sort", "uniq", "grep")
                    if (word !in notTg && word.length > 3 && !word.all { it.isDigit() }) {
                        addResult("Telegram", "MEDIUM", m.value, "Telegram/user handle")
                        miscCount++
                    }
                }
                // Cloud hosting domains
                val hostingPat = Regex("""[a-zA-Z0-9][a-zA-Z0-9.\-]*\.(?:workers\.dev|vercel\.app|netlify\.app|render\.com|railway\.app|herokuapp\.com|firebaseio\.com|cloudflare\.com|my\.id|heroku\.com|pages\.dev|deno\.dev)""")
                for (m in hostingPat.findAll(rawText)) {
                    addResult("Domain", "HIGH", m.value, "Cloud-hosted domain")
                    miscCount++
                }
                // Crypto operations
                val cryptoPat = Regex("""(?:AES[_\-]?256|AES[_\-]?128|RSA[_\-]?\d+|SHA[_\-]?256|SHA[_\-]?512|MD5|HMAC|bcrypt|scrypt|PBKDF2|Base64|XOR|DES|Blowfish|Twofish|ChaCha20|Curve25519|Ed25519|secp256k1)""", RegexOption.IGNORE_CASE)
                for (m in cryptoPat.findAll(rawText)) {
                    addResult("Crypto", "MEDIUM", m.value, "Cryptographic operation")
                    miscCount++
                }
                // File paths (useful for reverse engineering)
                val pathPat = Regex("""/data/(?:data|user/0)/[a-zA-Z0-9._]+""")
                for (m in pathPat.findAll(rawText)) {
                    addResult("App Path", "HIGH", m.value, "Android app data path")
                    miscCount++
                }
                val pathPat2 = Regex("""/sdcard/[a-zA-Z0-9._/\-]+""")
                for (m in pathPat2.findAll(rawText)) {
                    addResult("SDCard Path", "MEDIUM", m.value, "External storage path")
                    miscCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $miscCount misc patterns")

            // ===== PHASE 11: Auth endpoints + Convex/Cloudflare Workers =====
            progress = 0.90f
            addLog("[*] Phase 11/11: Scanning auth + hosting endpoints...")
            var authCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                // Auth service patterns
                val authPat = Regex("""(?:supabase|firebase|auth0|keycloak|accounts\.google|login|signin|signup|verify|authenticate|authorize|oauth|saml|jwt|openid)\.?[a-zA-Z]*\.(?:com|io|dev|app|net|org|cloud)""", RegexOption.IGNORE_CASE)
                for (m in authPat.findAll(rawText)) {
                    addResult("Auth Service", "CRITICAL", m.value, "Authentication service endpoint")
                    authCount++
                }
                // Convex URLs
                val convexPat = Regex("""[a-zA-Z0-9][a-zA-Z0-9\-]*\.convex\.site""")
                for (m in convexPat.findAll(rawText)) {
                    addResult("Convex", "CRITICAL", m.value, "Convex serverless endpoint")
                    authCount++
                }
                // Cloudflare Workers
                val cfPat = Regex("""[a-zA-Z0-9][a-zA-Z0-9\-]*\.workers\.dev""")
                for (m in cfPat.findAll(rawText)) {
                    addResult("CF Worker", "CRITICAL", m.value, "Cloudflare Worker endpoint")
                    authCount++
                }
                // GitHub raw/content
                val ghPat = Regex("""raw\.githubusercontent\.com/[^\s'"]{10,200}""")
                for (m in ghPat.findAll(rawText)) {
                    addResult("GitHub Raw", "HIGH", m.value, "GitHub raw file content")
                    authCount++
                }
                // MediaFire / Google Drive / mega
                val dlPat = Regex("""(?:mediafire\.com|drive\.google\.com|mega\.nz|mega\.co\.nz|dropbox\.com)[^\s'"]{0,200}""", RegexOption.IGNORE_CASE)
                for (m in dlPat.findAll(rawText)) {
                    addResult("Download", "HIGH", m.value.take(200), "File hosting download link")
                    authCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $authCount auth/hosting endpoints")

            // ===== DEDUP + SORT =====
            val unique = results.distinctBy { it.value }.sortedByDescending {
                when (it.severity) { "CRITICAL" -> 4; "HIGH" -> 3; "MEDIUM" -> 2; else -> 1 }
            }

            progress = 1.0f
            addLog("")
            addLog("[+] ========== SCAN COMPLETE ==========")
            addLog("[+] Total unique findings: ${unique.size}")
            addLog("    🔴 CRITICAL: ${unique.count { it.severity == "CRITICAL" }}")
            addLog("    🟠 HIGH: ${unique.count { it.severity == "HIGH" }}")
            addLog("    🟡 MEDIUM: ${unique.count { it.severity == "MEDIUM" }}")
            addLog("    ⚪ LOW: ${unique.count { it.severity == "LOW" }}")

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
                title = { Text("🔓 Auto Leak Source v2", fontWeight = FontWeight.Bold) },
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
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("🔓 Auto Leak Source v2", fontWeight = FontWeight.Bold, color = AccentRed, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("11-phase scan: URLs, IPs, secrets, JWT, SQL, Base64, JNI, hooks, shell, auth, crypto", color = TextSecondary, fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(fileName.ifEmpty { "No file loaded" }, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    if (isRunning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = AccentRed)
                        Spacer(Modifier.height(4.dp))
                        Text("Scanning... ${"%.0f".format(progress * 100)}%", color = AccentOrange, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { scan() }, enabled = !isRunning, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Scanning...", fontSize = 11.sp)
                        } else {
                            Text("🔍 Deep Scan (${11} phases)", fontSize = 11.sp)
                        }
                    }
                }
            }

            if (leaks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CRITICAL" to AccentRed, "HIGH" to AccentOrange, "MEDIUM" to Color(0xFFFFD740), "LOW" to TextSecondary).forEach { (sev, color) ->
                        val count = leaks.count { it.severity == sev }
                        if (count > 0) Text("●$sev:$count", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            var selectedTab by remember { mutableIntStateOf(0) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                listOf("Results (${leaks.size})", "Log (${logLines.size})").forEachIndexed { idx, label ->
                    TextButton(onClick = { selectedTab = idx }, modifier = Modifier.weight(1f)) {
                        Text(label, color = if (selectedTab == idx) AccentCyan else TextSecondary,
                            fontSize = 11.sp, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    if (leaks.isEmpty() && !isRunning) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Load file + tap Deep Scan", color = TextSecondary, fontSize = 13.sp)
                                Text("Supports: ELF, APK, SO, SH, PY, JS, DEX", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    } else {
                        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                            itemsIndexed(leaks) { _, item ->
                                val color = when (item.severity) {
                                    "CRITICAL" -> AccentRed; "HIGH" -> AccentOrange; "MEDIUM" -> Color(0xFFFFD740); else -> TextSecondary
                                }
                                Card(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text("[${item.severity}] ${item.category}", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(item.value, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 4)
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

// ========== BINARY STRING EXTRACTION ==========

/**
 * Extract printable ASCII strings from binary data (like Linux `strings` command)
 * Returns sequences of printable chars >= minLen
 */
private fun extractBinaryStrings(data: ByteArray, minLen: Int): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    for (b in data) {
        val c = b.toInt() and 0xFF
        if (c in 0x20..0x7E) {
            sb.append(c.toChar())
        } else {
            if (sb.length >= minLen) {
                result.add(sb.toString())
            }
            sb.clear()
        }
    }
    if (sb.length >= minLen) result.add(sb.toString())
    return result
}

/**
 * Extract printable text from data, replacing binary bytes with space
 */
private fun extractPrintableText(data: ByteArray): String {
    val sb = StringBuilder(minOf(data.size, 5_000_000))
    for (i in 0 until minOf(data.size, 5_000_000)) {
        val c = data[i].toInt() and 0xFF
        sb.append(if (c in 0x20..0x7E || c == 0x0A || c == 0x09) c.toChar() else ' ')
    }
    return sb.toString()
}
