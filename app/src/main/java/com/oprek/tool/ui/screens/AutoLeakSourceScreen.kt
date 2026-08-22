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
    var extractedFiles by remember { mutableStateOf(listOf<Pair<String, String>>()) }

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
        extractedFiles = emptyList()
        progress = 0f
        scope.launch(Dispatchers.IO) {
            addLog("[+] Starting deep leak scan on: ${f.name}")
            addLog("[+] File size: ${f.length()} bytes")

            val rawData = withContext(Dispatchers.IO) { f.readBytes() }

            // Extract ALL printable strings (binary-safe)
            val allStrings = extractAllStrings(rawData, 4)
            addLog("[+] Extracted ${allStrings.size} printable strings")

            // Also extract as text for regex scanning
            val textMode = extractPrintableText(rawData)

            val results = mutableListOf<LeakItem>()
            val seen = mutableSetOf<String>()
            val extractedSrcFiles = mutableListOf<Pair<String, String>>()

            fun addResult(cat: String, sev: String, value: String, ctx: String) {
                val key = "$cat:$value"
                if (key !in seen && value.length > 3) {
                    seen.add(key)
                    results.add(LeakItem(cat, sev, value, ctx))
                }
            }

            // ===== PHASE 1: URLs =====
            progress = 0.05f
            addLog("[*] Phase 1/13: Scanning URLs...")
            var urlCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val urlPat = Regex("""https?://[\x20-\x7E]{3,300}""")
                for (m in urlPat.findAll(rawText)) {
                    val url = m.value.trimEnd(' ', '.', ',', ';', ')', '>')
                    if (url.length > 8) {
                        addResult("URL", "HIGH", url, "Network endpoint")
                        urlCount++
                    }
                }
            } catch (_: Exception) {}
            addLog("    → Found $urlCount URLs")

            // ===== PHASE 2: IPs + Ports =====
            progress = 0.10f
            addLog("[*] Phase 2/13: Scanning IP addresses...")
            var ipCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val ipPat = Regex("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]{2,5})?\b""")
                for (m in ipPat.findAll(rawText)) {
                    val ip = m.value
                    val first = ip.split(".")[0].split(":")[0].toIntOrNull() ?: 0
                    if (first in 1..223 && !ip.startsWith("127.") && !ip.startsWith("0.") && !ip.startsWith("255.")) {
                        addResult("IP Address", "HIGH", ip, "Hardcoded IP:port")
                        ipCount++
                    }
                }
            } catch (_: Exception) {}
            addLog("    → Found $ipCount IPs")

            // ===== PHASE 3: Emails =====
            progress = 0.15f
            addLog("[*] Phase 3/13: Scanning emails...")
            var emailCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val emailPat = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,20}""")
                for (m in emailPat.findAll(rawText)) {
                    addResult("Email", "MEDIUM", m.value, "Email address")
                    emailCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $emailCount emails")

            // ===== PHASE 4: Secrets & Tokens =====
            progress = 0.20f
            addLog("[*] Phase 4/13: Scanning secrets & tokens...")
            var secretCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val secretPat = Regex("""(?:api[_\-]?key|token|secret|password|passwd|auth[_\-]?token|credential|private[_\-]?key|access[_\-]?key|license[_\-]?key|apikey|apisecret|client[_\-]?secret|app[_\-]?secret|encryption[_\-]?key|signing[_\-]?key)\s*[:=]\s*['"]?([^\s'"<>]{3,120})['"]?""", RegexOption.IGNORE_CASE)
                for (m in secretPat.findAll(rawText)) {
                    val val_ = m.groupValues[1].trim()
                    if (val_.length > 3 && val_ != "null" && val_ != "undefined") {
                        addResult("Secret", "CRITICAL", "${m.groupValues[0].substringBefore("=").trim()} = $val_", "Hardcoded secret")
                        secretCount++
                    }
                }
                val supaKey = Regex("""eyJ[A-Za-z0-9_-]{50,500}""")
                for (m in supaKey.findAll(rawText)) {
                    addResult("JWT/Supabase Key", "CRITICAL", m.value.take(80) + "...", "Supabase/anon key")
                    secretCount++
                }
                val convexKey = Regex("""(?:NS-|AK-|PK-|SK-|TK-)[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}""")
                for (m in convexKey.findAll(rawText)) {
                    addResult("Convex Key", "CRITICAL", m.value, "Convex API key")
                    secretCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $secretCount secrets")

            // ===== PHASE 5: JWT & License Keys =====
            progress = 0.30f
            addLog("[*] Phase 5/13: Scanning JWT & license keys...")
            var licCount = 0
            var jwtCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val jwtPat = Regex("""eyJ[A-Za-z0-9_-]{40,500}\.[A-Za-z0-9_-]{40,500}\.[A-Za-z0-9_-]{10,200}""")
                for (m in jwtPat.findAll(rawText)) {
                    addResult("JWT Token", "CRITICAL", m.value.take(60) + "...", "Full JWT")
                    jwtCount++
                }
                val lic2 = Regex("""LIC[\-]?[A-Z0-9]{4}[\-][A-Z0-9]{4}[\-][A-Z0-9]{4}[\-][A-Z0-9]{4}[\-][A-Z0-9]{4}""")
                for (m in lic2.findAll(rawText)) {
                    addResult("License Key", "CRITICAL", m.value, "LIC-format key")
                    licCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $licCount license keys + $jwtCount JWTs")

            // ===== PHASE 6: SQL =====
            progress = 0.38f
            addLog("[*] Phase 6/13: Scanning SQL queries...")
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

            // ===== PHASE 7: Base64 =====
            progress = 0.45f
            addLog("[*] Phase 7/13: Decoding Base64 strings...")
            var b64Count = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val b64Pat = Regex("""[A-Za-z0-9+/]{20,300}={0,2}""")
                for (m in b64Pat.findAll(rawText)) {
                    if (b64Count >= 500) break
                    try {
                        val decoded = android.util.Base64.decode(m.value, android.util.Base64.DEFAULT)
                        val decodedStr = String(decoded, Charsets.UTF_8)
                        if (decodedStr.length > 5 && decodedStr.any { it.isLetter() } && !decodedStr.contains(0.toChar())) {
                            when {
                                decodedStr.contains("http") -> addResult("Base64→URL", "CRITICAL", decodedStr.take(150), "Base64-encoded URL")
                                decodedStr.contains("key", ignoreCase = true) -> addResult("Base64→Secret", "CRITICAL", decodedStr.take(150), "Base64-encoded secret")
                                decodedStr.contains("L/") || decodedStr.contains("com/") -> addResult("Base64→Java", "HIGH", decodedStr.take(150), "Base64-encoded Java class")
                                decodedStr.contains("SELECT") -> addResult("Base64→SQL", "HIGH", decodedStr.take(150), "Base64-encoded SQL")
                                decodedStr.contains("supabase", ignoreCase = true) -> addResult("Base64→Supabase", "CRITICAL", decodedStr.take(150), "Base64-encoded Supabase")
                                else -> addResult("Base64", "MEDIUM", "→ ${decodedStr.take(120)}", "Decoded Base64 string")
                            }
                            b64Count++
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            addLog("    → Decoded $b64Count meaningful Base64 strings")

            // ===== PHASE 8: Shell script analysis =====
            progress = 0.55f
            addLog("[*] Phase 8/13: Shell script deep analysis...")
            var shellCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val curlPat = Regex("""(?:curl|wget|fetch)\s+(?:-[a-zA-Z]+\s+)*['"]?(https?://[^\s'"]+)['"]?""")
                for (m in curlPat.findAll(rawText)) {
                    addResult("Shell→URL", "HIGH", m.value.take(200), "Shell network request")
                    shellCount++
                }
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

            // ===== PHASE 9: Source Code Extraction (THE KEY FEATURE) =====
            progress = 0.60f
            addLog("[*] Phase 9/13: Extracting source code files...")
            var srcCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)

                // Extract C/C++ files (.cpp, .h, .c, .hpp)
                val cppHeaders = listOf("#include", "#define", "#ifdef", "#ifndef", "#pragma", "#ifdef __cplusplus",
                    "using namespace", "class ", "struct ", "enum ", "typedef ", "extern \"C\"",
                    "void ", "int ", "bool ", "float ", "long ", "unsigned ", "static ", "const ",
                    "namespace ", "template", "virtual ", "override", "nullptr", "nullptr_t")
                
                // Extract Java files
                val javaHeaders = listOf("package ", "import ", "public class", "private class", "protected class",
                    "abstract class", "interface ", "extends ", "implements ", "void ", "public static",
                    "private static", "protected static", "final ", "static ", "synchronized")

                // Extract Python files
                val pyHeaders = listOf("import ", "from ", "def ", "class ", "if __name__", "__init__", "__main__",
                    "self.", "print(", "return ", "elif ", "else:", "for ", "while ", "try:", "except:",
                    "with ", "as ", "lambda ", "yield ", "async ", "await ")

                // Extract Shell scripts
                val shHeaders = listOf("#!/bin/bash", "#!/bin/sh", "#!/usr/bin/env bash", "#!/usr/bin/env sh",
                    "echo ", "printf ", "read ", "if [", "then", "fi", "done", "while [", "for i in",
                    "case ", "esac", "function ", "source ", ". ./")

                // Look for file content patterns
                val fileContentPatterns = mapOf(
                    "#include" to "C/C++ Header",
                    "#define" to "C/C++ Macro",
                    "package " to "Java/Kotlin",
                    "import " to "Java/Python/Kotlin",
                    "def " to "Python",
                    "class " to "Class Definition",
                    "struct " to "C/C++ Struct",
                    "enum " to "Enum Definition",
                    "typedef " to "C/C++ Typedef",
                    "function " to "Shell/JS Function",
                    "void " to "C/C++/Java Method",
                    "int main(" to "C/C++ Main",
                    "int argc" to "C/C++ Main",
                    "argv" to "C/C++ Main",
                    "argc, char *argv" to "C/C++ Main",
                    "__attribute__" to "GCC Attribute",
                    "JNIEXPORT" to "JNI Export",
                    "JNI_OnLoad" to "JNI Load",
                    "RegisterNatives" to "JNI Register",
                    "FindClass" to "JNI Class",
                    "GetMethodID" to "JNI Method",
                    "GetFieldID" to "JNI Field",
                    "CallVoidMethod" to "JNI Call",
                    "NewStringUTF" to "JNI String",
                    "GetStringUTFChars" to "JNI String",
                    "GetByteArrayElements" to "JNI Array",
                    "Android.mk" to "Android NDK Build",
                    "Application.mk" to "Android NDK Config",
                    "build.gradle" to "Gradle Build",
                    "CMakeLists.txt" to "CMake Build",
                    "Makefile" to "Makefile",
                    "AndroidManifest.xml" to "Android Manifest",
                    "assets.xml" to "Android Assets",
                    "strings.xml" to "Android Strings",
                    "styles.xml" to "Android Styles",
                    "colors.xml" to "Android Colors",
                    "dimens.xml" to "Android Dimensions",
                    "themes.xml" to "Android Themes",
                    "proguard" to "ProGuard Config",
                    "gradle.properties" to "Gradle Properties",
                    "settings.gradle" to "Gradle Settings",
                    "local.properties" to "Local Properties",
                    "gradlew" to "Gradle Wrapper",
                    "gradlew.bat" to "Gradle Wrapper (Windows)",
                    "gradle-wrapper.properties" to "Gradle Wrapper Config",
                    "gradle-wrapper.jar" to "Gradle Wrapper JAR",
                    "gradle-wrapper.jar.sha256" to "Gradle Wrapper SHA256",
                    "gradle-wrapper.jar.sha1" to "Gradle Wrapper SHA1",
                    "gradle-wrapper.jar.md5" to "Gradle Wrapper MD5"
                )

                // Scan for source code patterns
                for ((pattern, fileType) in fileContentPatterns) {
                    var idx = 0
                    while (true) {
                        val pos = rawText.indexOf(pattern, idx, ignoreCase = false)
                        if (pos < 0) break
                        // Extract surrounding context (up to 200 chars)
                        val start = (pos - 50).coerceAtLeast(0)
                        val end = (pos + 200).coerceAtMost(rawText.length)
                        val context = rawText.substring(start, end).trim()
                        if (context.length > 20) {
                            addResult("Source", "HIGH", "[$fileType] $context", "Source code pattern")
                            srcCount++
                        }
                        idx = pos + 1
                    }
                }

                // Look for embedded file paths
                val filePathPat = Regex("""(?:^|\s)(/[a-zA-Z0-9._/\-]+\.(cpp|h|c|hpp|java|kt|py|sh|mk|xml|gradle|json|yaml|yml|toml|cfg|conf|ini|properties|txt|md))\b""", RegexOption.MULTILINE)
                for (m in filePathPat.findAll(rawText)) {
                    addResult("File Path", "HIGH", m.value.trim(), "Embedded file path")
                    srcCount++
                }

                // Look for build system files
                val buildFiles = listOf("Android.mk", "Application.mk", "CMakeLists.txt", "build.gradle",
                    "Makefile", "AndroidManifest.xml", "proguard-rules.pro", "gradle.properties",
                    "settings.gradle", "local.properties", "gradlew", "gradlew.bat")
                for (bf in buildFiles) {
                    var idx = 0
                    while (true) {
                        val pos = rawText.indexOf(bf, idx, ignoreCase = false)
                        if (pos < 0) break
                        addResult("Build File", "HIGH", bf, "Build system file reference")
                        srcCount++
                        idx = pos + 1
                    }
                }

                // Look for JNI signatures (most important for cheat source)
                val jniSigs = Regex("""(?:Java_|Lcom/|Landroid/|Lunity/)[a-zA-Z0-9_/]+;""")
                for (m in jniSigs.findAll(rawText)) {
                    addResult("JNI Signature", "HIGH", m.value, "JNI class/method signature")
                    srcCount++
                }

                // Look for offset definitions (most important for cheat source)
                val offsetDefs = Regex("""(?:offset|OFFSET|Off)[_]?[A-Za-z]+\s*[:=]\s*0x[0-9A-Fa-f]+""")
                for (m in offsetDefs.findAll(rawText)) {
                    addResult("Offset", "CRITICAL", m.value, "Memory offset definition")
                    srcCount++
                }

                // Look for function definitions
                val funcDefs = Regex("""(?:void|int|bool|float|long|unsigned|static|const|auto)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\([^)]*\)\s*\{""")
                for (m in funcDefs.findAll(rawText)) {
                    addResult("Function", "MEDIUM", m.value.take(100), "Function definition")
                    srcCount++
                }

                // Look for struct/class definitions
                val structDefs = Regex("""(?:struct|class|enum|interface|typedef)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*[:{]""")
                for (m in structDefs.findAll(rawText)) {
                    addResult("Type Def", "MEDIUM", m.value, "Type definition")
                    srcCount++
                }

            } catch (_: Exception) {}
            addLog("    → Found $srcCount source code patterns")

            // ===== PHASE 10: JNI + Hooks + Anti-Debug =====
            progress = 0.70f
            addLog("[*] Phase 10/13: Scanning JNI, hooks, anti-debug...")
            var jniCount = 0
            var hookCount = 0
            var antiCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)

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
                        addResult("JNI", "MEDIUM", func, "JNI function @ 0x${"%X".format(pos)}")
                        jniCount++
                        idx = pos + 1
                    }
                }

                val hookPatterns = listOf("DobbyHook", "dobbyHook", "InlineHook", "hook_function",
                    "PLTHook", "GOTHook", "xHook", "whale", "Substrate", "MSHookFunction",
                    "hook_register", "dobby", "shadowhook", "intercept", "detour", "trampoline")
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

                val antiPatterns = listOf("frida", "Frida", "xposed", "Xposed", "ptrace", "TracerPid",
                    "/proc/self/status", "/proc/self/maps", "isDebugger", "Debug.isDebugger",
                    "android.os.Debug", "anti_debug", "detectDebugger", "checkRoot", "detectRoot",
                    "SafetyNet", "Play Integrity", "attestation", "anti_vm", "detectEmulator")
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
            addLog("    → JNI: $jniCount | Hooks: $hookCount | Anti-debug: $antiCount")

            // ===== PHASE 11: Telegram, domains, crypto =====
            progress = 0.80f
            addLog("[*] Phase 11/13: Telegram, domains, crypto...")
            var miscCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val tgPat = Regex("""@[a-zA-Z][a-zA-Z0-9_]{2,30}""")
                val notTg = setOf("param", "override", "return", "import", "include", "define",
                    "pragma", "public", "private", "protected", "static", "const", "null",
                    "true", "false", "void", "int", "char", "float", "double", "long",
                    "begin", "end", "loop", "done", "then", "else", "echo", "test",
                    "type", "name", "size", "file", "path", "data", "code", "exec")
                for (m in tgPat.findAll(rawText)) {
                    val word = m.value.substring(1)
                    if (word !in notTg && word.length > 3 && !word.all { it.isDigit() }) {
                        addResult("Telegram", "MEDIUM", m.value, "Telegram/user handle")
                        miscCount++
                    }
                }
                val hostingPat = Regex("""[a-zA-Z0-9][a-zA-Z0-9.\-]*\.(?:workers\.dev|vercel\.app|netlify\.app|render\.com|railway\.app|herokuapp\.com|firebaseio\.com|cloudflare\.com|my\.id|pages\.dev)""")
                for (m in hostingPat.findAll(rawText)) {
                    addResult("Domain", "HIGH", m.value, "Cloud-hosted domain")
                    miscCount++
                }
                val cryptoPat = Regex("""(?:AES[_\-]?256|AES[_\-]?128|RSA[_\-]?\d+|SHA[_\-]?256|SHA[_\-]?512|MD5|HMAC|bcrypt|XOR|ChaCha20)""", RegexOption.IGNORE_CASE)
                for (m in cryptoPat.findAll(rawText)) {
                    addResult("Crypto", "MEDIUM", m.value, "Cryptographic operation")
                    miscCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $miscCount misc patterns")

            // ===== PHASE 12: Auth endpoints =====
            progress = 0.90f
            addLog("[*] Phase 12/13: Scanning auth + hosting endpoints...")
            var authCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)
                val authPat = Regex("""(?:supabase|firebase|auth0|keycloak|accounts\.google|login|signin|signup|verify|authenticate|authorize|oauth)\.?[a-zA-Z]*\.(?:com|io|dev|app|net|org|cloud)""", RegexOption.IGNORE_CASE)
                for (m in authPat.findAll(rawText)) {
                    addResult("Auth Service", "CRITICAL", m.value, "Authentication service endpoint")
                    authCount++
                }
                val convexPat = Regex("""[a-zA-Z0-9][a-zA-Z0-9\-]*\.convex\.site""")
                for (m in convexPat.findAll(rawText)) {
                    addResult("Convex", "CRITICAL", m.value, "Convex serverless endpoint")
                    authCount++
                }
                val cfPat = Regex("""[a-zA-Z0-9][a-zA-Z0-9\-]*\.workers\.dev""")
                for (m in cfPat.findAll(rawText)) {
                    addResult("CF Worker", "CRITICAL", m.value, "Cloudflare Worker endpoint")
                    authCount++
                }
                val ghPat = Regex("""raw\.githubusercontent\.com/[^\s'"]{10,200}""")
                for (m in ghPat.findAll(rawText)) {
                    addResult("GitHub Raw", "HIGH", m.value, "GitHub raw file content")
                    authCount++
                }
                val dlPat = Regex("""(?:mediafire\.com|drive\.google\.com|mega\.nz|dropbox\.com)[^\s'"]{0,200}""", RegexOption.IGNORE_CASE)
                for (m in dlPat.findAll(rawText)) {
                    addResult("Download", "HIGH", m.value.take(200), "File hosting download link")
                    authCount++
                }
            } catch (_: Exception) {}
            addLog("    → Found $authCount auth/hosting endpoints")

            // ===== PHASE 13: Full Source Reconstruction =====
            progress = 0.95f
            addLog("[*] Phase 13/13: Reconstructing source structure...")
            var reconCount = 0
            try {
                val rawText = String(rawData, Charsets.US_ASCII)

                // Look for complete file content patterns
                // C/C++ includes (indicates header files)
                val includes = Regex("""#include\s*[<"]([^>"]+)[>"]""").findAll(rawText).map { it.groupValues[1] }.distinct().toList()
                if (includes.isNotEmpty()) {
                    extractedSrcFiles.add("include_dependencies" to includes.joinToString("\n"))
                    addResult("Source", "HIGH", "C/C++ includes: ${includes.size} files", "Include dependencies")
                    reconCount++
                }

                // Java imports
                val imports = Regex("""import\s+([a-zA-Z0-9._]+);""").findAll(rawText).map { it.groupValues[1] }.distinct().toList()
                if (imports.isNotEmpty()) {
                    extractedSrcFiles.add("java_imports" to imports.joinToString("\n"))
                    addResult("Source", "HIGH", "Java imports: ${imports.size} packages", "Import dependencies")
                    reconCount++
                }

                // Namespace declarations
                val namespaces = Regex("""namespace\s+([a-zA-Z0-9_:]+)\s*\{""").findAll(rawText).map { it.groupValues[1] }.distinct().toList()
                if (namespaces.isNotEmpty()) {
                    extractedSrcFiles.add("namespaces" to namespaces.joinToString("\n"))
                    addResult("Source", "MEDIUM", "C++ namespaces: ${namespaces.size}", "Namespace declarations")
                    reconCount++
                }

                // Class declarations
                val classes = Regex("""(?:class|struct)\s+([a-zA-Z0-9_]+)\s*(?::\s*(?:public|private|protected)\s+[a-zA-Z0-9_]+)?\s*\{""").findAll(rawText).map { it.groupValues[1] }.distinct().toList()
                if (classes.isNotEmpty()) {
                    extractedSrcFiles.add("classes" to classes.joinToString("\n"))
                    addResult("Source", "MEDIUM", "Classes/Structs: ${classes.size}", "Class declarations")
                    reconCount++
                }

                // Function definitions
                val functions = Regex("""(?:void|int|bool|float|long|unsigned|static|const|auto|std::string)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\([^)]*\)""").findAll(rawText).map { it.groupValues[1] }.distinct().toList()
                if (functions.isNotEmpty()) {
                    extractedSrcFiles.add("functions" to functions.joinToString("\n"))
                    addResult("Source", "MEDIUM", "Functions: ${functions.size}", "Function declarations")
                    reconCount++
                }

                // Global variables
                val globals = Regex("""(?:bool|int|float|long|unsigned|const|static)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*[=;]""").findAll(rawText).map { it.groupValues[1] }.distinct().toList()
                if (globals.isNotEmpty()) {
                    extractedSrcFiles.add("globals" to globals.joinToString("\n"))
                    addResult("Source", "MEDIUM", "Global variables: ${globals.size}", "Global variable declarations")
                    reconCount++
                }

                // Macros
                val macros = Regex("""#define\s+([A-Z_][A-Z0-9_]*)\s*(.+?)$""", RegexOption.MULTILINE).findAll(rawText).map { "${it.groupValues[1]} = ${it.groupValues[2].trim()}" }.distinct().toList()
                if (macros.isNotEmpty()) {
                    extractedSrcFiles.add("macros" to macros.joinToString("\n"))
                    addResult("Source", "MEDIUM", "Macros: ${macros.size}", "Preprocessor macros")
                    reconCount++
                }

                // Enum values
                val enums = Regex("""enum\s+(?:class\s+)?([a-zA-Z0-9_]+)\s*\{([^}]+)\}""").findAll(rawText)
                for (m in enums) {
                    val enumName = m.groupValues[1]
                    val enumBody = m.groupValues[2]
                    extractedSrcFiles.add("enum_$enumName" to "enum $enumName {$enumBody}")
                    addResult("Source", "MEDIUM", "Enum: ${m.groupValues[1]}", "Enum declaration")
                    reconCount++
                }

                // Struct definitions with fields
                val structs = Regex("""struct\s+([a-zA-Z0-9_]+)\s*\{([^}]+)\}""").findAll(rawText)
                for (m in structs) {
                    val structName = m.groupValues[1]
                    val structBody = m.groupValues[2]
                    extractedSrcFiles.add("struct_$structName" to "struct $structName {$structBody}")
                    addResult("Source", "HIGH", "Struct: ${m.groupValues[1]}", "Struct definition")
                    reconCount++
                }

            } catch (_: Exception) {}
            addLog("    → Reconstructed $reconCount source elements")

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
            addLog("    📁 Source files: ${extractedSrcFiles.size}")

            withContext(Dispatchers.Main) {
                leaks = unique
                extractedFiles = extractedSrcFiles
                status = "Found ${unique.size} leaked items + ${extractedSrcFiles.size} source elements"
                isRunning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔓 Auto Leak Source v3", fontWeight = FontWeight.Bold) },
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
                    Text("🔓 Auto Leak Source v3", fontWeight = FontWeight.Bold, color = AccentRed, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("13-phase scan + source code reconstruction", color = TextSecondary, fontSize = 10.sp)
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
                            Text("🔍 Deep Scan (13 phases)", fontSize = 11.sp)
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
                listOf("Results (${leaks.size})", "Source (${extractedFiles.size})", "Log (${logLines.size})").forEachIndexed { idx, label ->
                    TextButton(onClick = { selectedTab = idx }, modifier = Modifier.weight(1f)) {
                        Text(label, color = if (selectedTab == idx) AccentCyan else TextSecondary,
                            fontSize = 10.sp, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal)
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
                                Text("Supports: ELF, APK, SO, SH, PY, JS, DEX, ZIP", color = Color.Gray, fontSize = 11.sp)
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
                    // Source elements tab
                    if (extractedFiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No source elements extracted", color = TextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                            items(extractedFiles) { (name, content) ->
                                Card(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text("📄 $name", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(content, color = AccentGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 20)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
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

// ========== UTILITIES ==========

private fun extractAllStrings(data: ByteArray, minLen: Int): Set<String> {
    val result = mutableSetOf<String>()
    val sb = StringBuilder()
    for (b in data) {
        val c = b.toInt() and 0xFF
        if (c in 0x20..0x7E) {
            sb.append(c.toChar())
        } else {
            if (sb.length >= minLen) {
                val s = sb.toString()
                if (s.any { it.isLetter() }) {
                    result.add(s)
                }
            }
            sb.clear()
        }
    }
    return result
}

private fun extractPrintableText(data: ByteArray): String {
    val sb = StringBuilder(minOf(data.size, 5_000_000))
    for (i in 0 until minOf(data.size, 5_000_000)) {
        val c = data[i].toInt() and 0xFF
        sb.append(if (c in 0x20..0x7E || c == 0x0A || c == 0x09) c.toChar() else ' ')
    }
    return sb.toString()
}
