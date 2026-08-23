@file:Suppress("DEPRECATION")
package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRemoverScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var permissionsToRemove by remember { mutableStateOf("android.permission.READ_PHONE_STATE\nandroid.permission.ACCESS_FINE_LOCATION\nandroid.permission.READ_SMS\nandroid.permission.SEND_SMS\nandroid.permission.READ_CONTACTS\nandroid.permission.READ_CALL_LOG\nandroid.permission.RECORD_AUDIO\nandroid.permission.CAMERA") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isRunning = true
            output = listOf("[*] Analyzing APK permissions...")
            scope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(it) ?: return@withContext
                    val bytes = stream.readBytes()
                    stream.close()

                    if (bytes.size < 4 || bytes[0] != 0x50.toByte()) {
                        withContext(Dispatchers.Main) { output = listOf("[-] Not a valid APK file"); isRunning = false }
                        return@withContext
                    }

                    // Extract permissions from binary manifest
                    val text = String(bytes, Charsets.UTF_16LE)
                    val perms = mutableListOf<String>()
                    val regex = Regex("""android\.permission\.\w+""")
                    regex.findAll(text).forEach { match -> if (match.value !in perms) perms.add(match.value) }

                    val toRemove = permissionsToRemove.lines().filter { it.isNotBlank() }.map { it.trim() }
                    val found = perms.filter { p -> toRemove.any { r -> p.contains(r, ignoreCase = true) } }
                    val safe = perms.filter { p -> toRemove.none { r -> p.contains(r, ignoreCase = true) } }

                    withContext(Dispatchers.Main) {
                        output = listOf(
                            "[+] APK Permissions Analysis",
                            "[+] Total permissions: ${perms.size}",
                            "[+] 🔴 Dangerous (to remove): ${found.size}",
                            "[+] 🟢 Safe (keep): ${safe.size}",
                            ""
                        )
                        found.forEach { p -> output = output + "  🔴 $p" }
                        output = output + ""
                        safe.forEach { p -> output = output + "  🟢 $p" }
                        output = output + ""
                        output = output + "[*] To actually modify APK, use apktool on PC"
                        output = output + "[*] This tool analyzes and shows what to remove"
                        isRunning = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { output = listOf("[-] Error: ${e.message}"); isRunning = false }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛡️ Permission Remover", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Remove dangerous permissions from APK", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(permissionsToRemove, { permissionsToRemove = it }, label = { Text("Permissions to remove (one per line)", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth().height(100.dp), textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(40.dp), enabled = !isRunning, colors = ButtonDefaults.buttonColors(containerColor = AccentRed), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Select APK", fontSize = 11.sp)
                    }
                }
            }
            if (isRunning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentRed)
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 Report (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cb.setPrimaryClip(ClipData.newPlainText("output", output.joinToString("\n"))) }) { Text("Copy", fontSize = 10.sp, color = AccentCyan) }
                    }
                    LazyColumn { items(output) { line -> Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = when { line.startsWith("[+]") -> AccentGreen; line.startsWith("[-]") -> AccentRed; line.startsWith("🔴") -> AccentRed; line.startsWith("🟢") -> AccentGreen; else -> TextPrimary }, lineHeight = 12.sp) } }
                }
            }
        }
    }
}
