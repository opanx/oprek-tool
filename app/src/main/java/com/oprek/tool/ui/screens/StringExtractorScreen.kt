package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*
import java.io.File

/**
 * StringExtractor v2 — Regex search, encoding detect, filter by type, batch export
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringExtractorScreen(navController: NavController, vm: MainViewModel) {
    val context = LocalContext.current
    val strings by vm.strings.collectAsState()
    var filterQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(0) } // 0=All, 1=URL, 2=Auth, 3=Path, 4=IP, 5=Email, 6=Base64
    var minLength by remember { mutableStateOf(4) }
    var showSettings by remember { mutableStateOf(false) }

    val stringValues = strings.map { it.value }
    val filteredStrings = remember(stringValues, filterQuery, filterType) {
        stringValues.filter { entry ->
            val matchesQuery = filterQuery.isEmpty() || entry.contains(filterQuery, ignoreCase = true)
            val matchesType = when (filterType) {
                1 -> entry.contains("http", true) || entry.contains(".com", true) || entry.contains(".net", true) || entry.contains(".id", true)
                2 -> entry.contains("login", true) || entry.contains("auth", true) || entry.contains("license", true) || entry.contains("key", true) || entry.contains("device", true) || entry.contains("expire", true)
                3 -> entry.startsWith("/") || entry.contains("\\\\") || entry.contains(".so") || entry.contains(".dex") || entry.contains(".dat")
                4 -> Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").containsMatchIn(entry)
                5 -> entry.contains("@") && entry.contains(".")
                6 -> entry.length > 20 && entry.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
                else -> true
            }
            matchesQuery && matchesType
        }
    }

    LaunchedEffect(Unit) { vm.extractStrings(minLength) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📝 Strings v2", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (filteredStrings.isNotEmpty()) {
                        IconButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("strings", filteredStrings.joinToString("\n")))
                            Toast.makeText(context, "Copied ${filteredStrings.size} strings!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy All") }
                        IconButton(onClick = {
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OprekTool/output")
                            dir.mkdirs()
                            val outFile = File(dir, "strings_${System.currentTimeMillis()}.txt")
                            outFile.writeText(filteredStrings.joinToString("\n"))
                            Toast.makeText(context, "Exported ${filteredStrings.size} strings", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Save, "Export") }
                    }
                    IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Tune, "Settings") }
                    IconButton(onClick = { vm.extractStrings(minLength) }) { Icon(Icons.Default.Refresh, "Reload") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showSettings) {
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("⚙️ Min Length: $minLength", fontSize = 12.sp, color = AccentCyan)
                        Slider(value = minLength.toFloat(), onValueChange = { minLength = it.toInt() },
                            valueRange = 2f..20f, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { vm.extractStrings(minLength) }, Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Extract") }
                    }
                }
            }

            // Search + Filter
            OutlinedTextField(value = filterQuery, onValueChange = { filterQuery = it },
                placeholder = { Text("Search strings...") }, modifier = Modifier.fillMaxWidth().padding(8.dp),
                singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null, tint = AccentCyan) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))

            // Type filter chips
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("All" to 0, "URLs" to 1, "Auth" to 2, "Paths" to 3, "IPs" to 4, "Email" to 5, "Base64" to 6).forEach { (label, idx) ->
                    FilterChip(selected = filterType == idx, onClick = { filterType = idx },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                }
            }

            // Count
            Text("  ${filteredStrings.size} / ${strings.size} strings", fontSize = 11.sp, color = AccentGreen,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

            // String list
            LazyColumn(Modifier.fillMaxSize()) {
                items(filteredStrings) { str ->
                    Text(str, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = when {
                            str.contains("http", true) -> AccentCyan
                            str.contains("login", true) || str.contains("auth", true) -> AccentRed
                            str.startsWith("/") -> AccentPurple
                            str.contains("@") -> AccentOrange
                            Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").containsMatchIn(str) -> AccentOrange
                            else -> TextSecondary
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                            .background(DarkBg).padding(horizontal = 4.dp))
                }
                if (filteredStrings.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Text("No strings found", color = TextSecondary) } }
                }
            }
        }
    }
}
