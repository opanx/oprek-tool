package com.oprek.tool.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.io.File
import java.util.jar.JarFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isAnalyzing by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isAnalyzing = true
            output = listOf("[*] Analyzing permissions...")
            scope.launch(Dispatchers.IO) {
                val result = analyzePermissions(context, it)
                withContext(Dispatchers.Main) { output = result; isAnalyzing = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛡️ Permission Analyzer", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
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
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Analyze APK permissions for security risks", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Select APK", fontSize = 11.sp)
                    }
                }
            }
            if (isAnalyzing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentOrange)
            Card(Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("📋 Permission Report (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn {
                        items(output) { line ->
                            val color = when {
                                line.contains("🔴") || line.startsWith("[!]") -> AccentRed
                                line.contains("🟡") || line.startsWith("[~]") -> AccentOrange
                                line.contains("🟢") || line.startsWith("[+]") -> AccentGreen
                                line.startsWith("[*]") -> AccentCyan
                                else -> TextPrimary
                            }
                            Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private val dangerousPermissions = mapOf(
    "android.permission.READ_PHONE_STATE" to "🔴 Reads IMEI, phone number, call state",
    "android.permission.READ_PHONE_NUMBERS" to "🔴 Reads phone numbers",
    "android.permission.CALL_PHONE" to "🔴 Makes phone calls without user action",
    "android.permission.PROCESS_OUTGOING_CALLS" to "🔴 Intercepts outgoing calls",
    "android.permission.READ_CALL_LOG" to "🔴 Reads call history",
    "android.permission.WRITE_CALL_LOG" to "🔴 Modifies call history",
    "android.permission.READ_SMS" to "🔴 Reads all SMS messages",
    "android.permission.RECEIVE_SMS" to "🔴 Intercepts incoming SMS",
    "android.permission.SEND_SMS" to "🔴 Sends SMS (premium fraud risk)",
    "android.permission.RECEIVE_MMS" to "🔴 Intercepts MMS",
    "android.permission.READ_CONTACTS" to "🔴 Reads all contacts",
    "android.permission.WRITE_CONTACTS" to "🔴 Modifies contacts",
    "android.permission.GET_ACCOUNTS" to "🟡 Lists device accounts",
    "android.permission.ACCESS_FINE_LOCATION" to "🔴 Precise GPS location",
    "android.permission.ACCESS_COARSE_LOCATION" to "🟡 Approximate location",
    "android.permission.ACCESS_BACKGROUND_LOCATION" to "🔴 Background location tracking",
    "android.permission.READ_EXTERNAL_STORAGE" to "🟡 Reads all storage files",
    "android.permission.WRITE_EXTERNAL_STORAGE" to "🟡 Writes to storage",
    "android.permission.MANAGE_EXTERNAL_STORAGE" to "🔴 Full storage access (all files)",
    "android.permission.CAMERA" to "🔴 Camera access",
    "android.permission.RECORD_AUDIO" to "🔴 Microphone recording",
    "android.permission.READ_MEDIA_IMAGES" to "🟡 Reads photos",
    "android.permission.READ_MEDIA_VIDEO" to "🟡 Reads videos",
    "android.permission.READ_MEDIA_AUDIO" to "🟡 Reads audio files",
    "android.permission.BODY_SENSORS" to "🔴 Body sensors (heart rate, etc.)",
    "android.permission.ACTIVITY_RECOGNITION" to "🟡 Physical activity tracking",
    "android.permission.POST_NOTIFICATIONS" to "🟡 Can send notifications",
    "android.permission.NFC" to "🟡 NFC access",
    "android.permission.BLUETOOTH" to "🟡 Bluetooth access",
    "android.permission.BLUETOOTH_SCAN" to "🟡 Scan Bluetooth devices",
    "android.permission.BLUETOOTH_CONNECT" to "🟡 Connect to Bluetooth devices",
    "android.permission.INTERNET" to "🟢 Internet access",
    "android.permission.ACCESS_NETWORK_STATE" to "🟢 Check network state",
    "android.permission.ACCESS_WIFI_STATE" to "🟢 Check WiFi state",
    "android.permission.WAKE_LOCK" to "🟢 Keep device awake",
    "android.permission.RECEIVE_BOOT_COMPLETED" to "🟡 Auto-start on boot",
    "android.permission.SYSTEM_ALERT_WINDOW" to "🔴 Draw over other apps",
    "android.permission.WRITE_SETTINGS" to "🔴 Modify system settings",
    "android.permission.INSTALL_PACKAGES" to "🔴 Install APK silently",
    "android.permission.DELETE_PACKAGES" to "🔴 Uninstall packages",
    "android.permission.QUERY_ALL_PACKAGES" to "🟡 List all installed apps",
    "android.permission.PACKAGE_USAGE_STATS" to "🟡 App usage statistics",
    "android.permission.BIND_ACCESSIBILITY_SERVICE" to "🔴 Accessibility service (screen control)",
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to "🔴 Read all notifications",
    "android.permission.BIND_DEVICE_ADMIN" to "🔴 Device admin (wipe, lock, etc.)",
    "android.permission.READ_PRIVILEGED_PHONE_STATE" to "🔴 Privileged phone state",
    "android.permission.MOUNT_UNMOUNT_FILESYSTEMS" to "🔴 Mount/unmount filesystems",
    "android.permission.REBOOT" to "🔴 Reboot device",
    "android.permission.SHUTDOWN" to "🔴 Shutdown device",
    "android.permission.DEVICE_POWER" to "🔴 Control device power"
)

private fun analyzePermissions(context: Context, uri: Uri): List<String> {
    val result = mutableListOf<String>()
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return listOf("[-] Cannot open file")
        val bytes = inputStream.readBytes()
        inputStream.close()

        val tempFile = File(context.cacheDir, "perm_check.apk")
        tempFile.writeBytes(bytes)

        val jarFile = JarFile(tempFile)
        val manifestEntry = jarFile.getEntry("AndroidManifest.xml")

        if (manifestEntry == null) {
            tempFile.delete()
            return listOf("[-] AndroidManifest.xml not found")
        }

        val manifestStream = jarFile.getInputStream(manifestEntry)
        val manifestBytes = manifestStream.readBytes()
        manifestStream.close()
        jarFile.close()

        val permissions = mutableListOf<String>()
        val text = String(manifestBytes, Charsets.UTF_16LE)
        val regex = Regex("""android\.permission\.\w+""")
        regex.findAll(text).forEach { match ->
            val perm = match.value
            if (perm !in permissions) permissions.add(perm)
        }

        val customRegex = Regex("""[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}\.[A-Z][A-Z_]+""")
        customRegex.findAll(text).forEach { match ->
            val perm = match.value
            if (perm !in permissions && !perm.startsWith("android.") && !perm.startsWith("com.android.")) {
                permissions.add(perm)
            }
        }

        result.add("[+] APK Permissions Analysis")
        result.add("[+] Total permissions found: ${permissions.size}")
        result.add("")

        val dangerous = permissions.filter { p -> dangerousPermissions.containsKey(p) }
        val unknown = permissions.filter { p -> p.startsWith("android.permission.") && !dangerousPermissions.containsKey(p) }
        val custom = permissions.filter { p -> !p.startsWith("android.permission.") }

        result.add("[+] 🔴 Dangerous/Sensitive Permissions: ${dangerous.size}")
        dangerous.forEach { perm ->
            val desc = dangerousPermissions[perm] ?: ""
            result.add("    $perm")
            result.add("       $desc")
        }

        if (unknown.isNotEmpty()) {
            result.add("")
            result.add("[*] ℹ️ Standard Permissions: ${unknown.size}")
            unknown.forEach { result.add("    $it") }
        }

        if (custom.isNotEmpty()) {
            result.add("")
            result.add("[*] 🔧 Custom Permissions: ${custom.size}")
            custom.forEach { result.add("    $it") }
        }

        result.add("")
        val riskScore = when {
            dangerous.size >= 10 -> "🔴 CRITICAL (${dangerous.size} dangerous permissions)"
            dangerous.size >= 5 -> "🟠 HIGH (${dangerous.size} dangerous permissions)"
            dangerous.size >= 2 -> "🟡 MEDIUM (${dangerous.size} dangerous permissions)"
            dangerous.size >= 1 -> "🟢 LOW (${dangerous.size} dangerous permission)"
            else -> "✅ MINIMAL (no dangerous permissions)"
        }
        result.add("[+] Risk Assessment: $riskScore")

        result.add("")
        if (permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")) {
            result.add("[!] ⚠️ ACCESSIBILITY SERVICE - Can control entire screen!")
        }
        if (permissions.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")) {
            result.add("[!] ⚠️ NOTIFICATION LISTENER - Can read ALL notifications!")
        }
        if (permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE")) {
            result.add("[!] ⚠️ ALL FILES ACCESS - Full storage access!")
        }
        if (permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED")) {
            result.add("[~] Auto-start on boot (persistence mechanism)")
        }
        if (permissions.contains("android.permission.SYSTEM_ALERT_WINDOW")) {
            result.add("[~] Draw over other apps (overlay attack risk)")
        }

        tempFile.delete()
    } catch (e: Exception) {
        result.add("[-] Error: ${e.message}")
    }
    return result
}
