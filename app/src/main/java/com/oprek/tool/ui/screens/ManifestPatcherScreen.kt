package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.oprek.tool.core.StreamingIO
import com.oprek.tool.ui.components.OutputButton
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManifestPatcherScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var manifest by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var patchesApplied by remember { mutableIntStateOf(0) }
    var isLoaded by remember { mutableStateOf(false) }

    data class PatchDef(val label: String, val search: String, val replace: String)
    val commonPatches = listOf(
        PatchDef("Remove DEBUG flag", "android:debuggable=\"true\"", "android:debuggable=\"false\""),
        PatchDef("Add INTERNET permission", "<application", "<uses-permission android:name=\"android.permission.INTERNET\"/>\n    <application"),
        PatchDef("Remove allowBackup", "android:allowBackup=\"true\"", "android:allowBackup=\"false\""),
        PatchDef("Add REQUEST_INSTALL", "<application", "<uses-permission android:name=\"android.permission.REQUEST_INSTALL_PACKAGES\"/>\n    <application"),
        PatchDef("Remove exported flag", "android:exported=\"true\"", "android:exported=\"false\""),
        PatchDef("Add READ/WRITE storage", "<application", "<uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\"/>\n    <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\"/>\n    <application"),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📋 Manifest Patcher", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // Quick patches
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Quick Patches", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                    Spacer(Modifier.height(8.dp))
                    commonPatches.forEach { patch ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(patch.label, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                search = patch.search
                                replace = patch.replace
                                if (manifest.isNotEmpty()) {
                                    val count = manifest.split(patch.search).size - 1
                                    manifest = manifest.replace(patch.search, patch.replace)
                                    patchesApplied += count
                                }
                            }) { Text("Apply", fontSize = 11.sp, color = AccentGreen) }
                        }
                    }
                }
            }

            // Custom search/replace
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Custom Search & Replace", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = search, onValueChange = { search = it },
                        label = { Text("Search") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = replace, onValueChange = { replace = it },
                        label = { Text("Replace with") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (search.isNotEmpty() && manifest.isNotEmpty()) {
                            val count = manifest.split(search).size - 1
                            manifest = manifest.replace(search, replace)
                            patchesApplied += count
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), enabled = manifest.isNotEmpty() && search.isNotEmpty()) {
                        Text("Replace All", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (patchesApplied > 0) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp)) {
                    Text("✅ $patchesApplied patches applied", modifier = Modifier.padding(12.dp), color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            }

            // Manifest content
            if (manifest.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Manifest Content", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentOrange, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("manifest", manifest))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentCopy, "Copy", tint = AccentOrange) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(manifest, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutputButton(content = { manifest }, filename = "AndroidManifest.xml", subfolder = "manifest")
            Spacer(Modifier.height(24.dp))
        }
    }
}
