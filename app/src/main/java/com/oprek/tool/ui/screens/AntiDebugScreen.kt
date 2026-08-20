package com.oprek.tool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.io.File
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiDebugScreen(navController: NavController) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        val checks = mutableListOf<Pair<String, String>>()
        // Check TracerPid
        try {
            val tracerPid = File("/proc/self/status").readLines().find { it.startsWith("TracerPid:") }?.trim() ?: "N/A"
            checks.add("TracerPid" to if (tracerPid == "TracerPid:\t0") "✅ Not traced" else "🔴 TRACED: $tracerPid")
        } catch (e: Exception) { checks.add("TracerPid" to "❌ Cannot read") }

        // Check TracerPid for current process
        try {
            val pid = android.os.Process.myPid()
            val status = File("/proc/$pid/status").readLines()
            val tracer = status.find { it.startsWith("TracerPid:") }?.split(":")?.get(1)?.trim() ?: "?"
            checks.add("My TracerPid" to if (tracer == "0") "✅ Clean" else "🔴 Debugged ($tracer)")
        } catch (e: Exception) { checks.add("My TracerPid" to "❌ Error") }

        // Check ptrace
        checks.add("ptrace" to "💡 Use: ptrace(PTRACE_TRACEME) in native code to detect")

        // Check /proc/self/wchan
        try {
            val wchan = File("/proc/self/wchan").readText().trim()
            checks.add("wchan" to "📝 $wchan")
        } catch (e: Exception) { checks.add("wchan" to "❌ Cannot read") }

        // Check for common debugger apps
        val debugApps = listOf("com.android.modulemeta", "com.frida.server", "com.devadvance.rootcloak", "eu.chainfire.supersu")
        debugApps.forEach { app ->
            val found = try { Runtime.getRuntime().exec(arrayOf("pidof", app)).waitFor() == 0 } catch (_: Exception) { false }
            checks.add("$app" to if (found) "🔴 RUNNING" else "✅ Not found")
        }

        // Check SELinux
        try {
            val selinux = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "getenforce")).inputStream.bufferedReader().readText().trim()
            checks.add("SELinux" to "📝 $selinux")
        } catch (e: Exception) { checks.add("SELinux" to "❓ Unknown") }

        // Root check
        val isRoot = File("/system/xbin/su").exists() || File("/system/bin/su").exists()
        checks.add("Root" to if (isRoot) "🟡 Rooted" else "✅ Not rooted")

        results = checks
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🛡️ Anti-Debug Check", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("🛡️ Anti-Debug Detection", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentRed)
                    Spacer(Modifier.height(12.dp))
                    results.forEach { (name, status) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("$name: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan, modifier = Modifier.width(140.dp))
                            Text(status, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("💡 Bypass Tips", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange)
                    Spacer(Modifier.height(8.dp))

                    Text("• Frida: Use frida-server in spawned mode", fontSize = 12.sp, color = TextSecondary)
                    Text("• Magisk: Use Zygisk + DenyList", fontSize = 12.sp, color = TextSecondary)
                    Text("• KernelSU: Use Shamiko module", fontSize = 12.sp, color = TextSecondary)
                    Text("• Native ptrace: Hook ptrace() to return 0", fontSize = 12.sp, color = TextSecondary)
                    Text("• /proc/self/status: Hook open() to return fake status", fontSize = 12.sp, color = TextSecondary)
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { results.joinToString("
") { "${it.first}: ${it.second}" } },
                filename = "antidebug.txt",
                subfolder = "analysis"
            )

        }
    }
}
