package com.oprek.tool.ui.screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.io.File
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellPatcherScreen(navController: NavController) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var original by remember { mutableStateOf("") }
    var patched by remember { mutableStateOf("") }
    var searchStr by remember { mutableStateOf("") }
    var replaceStr by remember { mutableStateOf("") }
    var patchCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull() ?: return@LaunchedEffect
        if (!file.name.endsWith(".sh") && !file.name.endsWith(".bash")) return@LaunchedEffect
        original = withContext(kotlinx.coroutines.Dispatchers.IO) { file.readText() }
        patched = original
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🔧 Shell Patcher", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull() ?: return@IconButton
                        val backup = File(context.cacheDir, "oprek/backup_${System.currentTimeMillis()}.sh")
                        backup.writeText(original)
                        file.writeText(patched)
                        Toast.makeText(context, "Patched + backup saved!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.Save, "Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            // Quick patches
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("⚡ Quick Patches", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { patched = patched.replace(Regex("curl "), "echo '[CURL BLOCKED]' #"); patchCount++ },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed), shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("curl→echo", fontSize = 10.sp) }
                        Button(onClick = { patched = patched.replace(Regex("wget "), "echo '[WGET BLOCKED]' #"); patchCount++ },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed), shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("wget→echo", fontSize = 10.sp) }
                        Button(onClick = { patched = patched.replace(Regex("\\[.*==.*\\]"), "[ 1 != 1 ]"); patchCount++ },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Bypass if", fontSize = 10.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Search & replace
                    OutlinedTextField(value = searchStr, onValueChange = { searchStr = it }, label = { Text("Search") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = replaceStr, onValueChange = { replaceStr = it }, label = { Text("Replace") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen))
                    Spacer(Modifier.height(8.dp))

                    Button(onClick = {
                        if (searchStr.isNotEmpty()) {
                            patched = patched.replace(searchStr, replaceStr)
                            patchCount++
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(8.dp)) {
                        Text("Replace All ($patchCount patches)", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Preview
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📝 Patched Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("patched", patched)); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp), tint = AccentGreen) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(patched, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { output.ifEmpty { "No output" } } },
                filename = "patched_shell.sh",
                subfolder = "shell"
            )

        }
    }
}
