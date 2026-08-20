package com.oprek.tool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ELFSymbolScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var symbols by remember { mutableStateOf<List<String>>(emptyList()) }
    var dynamic by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val file = File(context.cacheDir, "oprek").listFiles()?.firstOrNull() ?: return@LaunchedEffect
        if (!file.name.endsWith(".so") && !file.name.endsWith(".elf")) return@LaunchedEffect
        scope.launch(Dispatchers.Default) {
            if (file.length() > 100 * 1024 * 1024) {

                // File too large for in-memory processing

            }

            val data = withContext(Dispatchers.IO) { file.readBytes() }
            try {
                val sections = withContext(Dispatchers.IO) { com.oprek.tool.core.NativeLib.elfGetSections(data) }
                symbols = sections.filter { it.contains("SYMTAB") || it.contains("DYNSYM") }
                dynamic = sections.filter { it.contains("DYNAMIC") }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🔍 ELF Symbols & Dynamic", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (symbols.isEmpty() && dynamic.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Open an ELF/SO file to view symbols", color = TextSecondary)
                }
            } else {
                LazyColumn(Modifier.padding(12.dp)) {
                    if (symbols.isNotEmpty()) {
                        item { Text("🔤 Symbol Sections", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPurple, modifier = Modifier.padding(bottom = 8.dp)) }
                        items(symbols) { sym ->
                            val parts = sym.split("|")
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(parts.getOrElse(0) { "?" }, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentCyan, modifier = Modifier.width(100.dp))
                                Text(parts.getOrElse(1) { "" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, modifier = Modifier.weight(1f))
                                Text(parts.getOrElse(2) { "" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentOrange)
                            }
                        }
                    }
                    if (dynamic.isNotEmpty()) {
                        item { Spacer(Modifier.height(16.dp)); Text("⚙️ Dynamic Sections", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentOrange, modifier = Modifier.padding(bottom = 8.dp)) }
                        items(dynamic) { dyn ->
                            val parts = dyn.split("|")
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(parts.getOrElse(0) { "?" }, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AccentCyan, modifier = Modifier.width(100.dp))
                                Text(parts.getOrElse(1) { "" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen, modifier = Modifier.weight(1f))
                                Text(parts.getOrElse(2) { "" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentOrange)
                            }
                        }
                    }
                }
            }
        }
    }
}
