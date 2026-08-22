package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.oprek.tool.engine.ElfFullEngine
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicSectionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(listOf<com.oprek.tool.engine.DynamicEntry>()) }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun loadFile() {
        val file = SharedFileState.findFile(context)
        if (file == null) {
            status = "No file loaded. Open from Home first."
            return
        }
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                status = "Loading ${file.name}..."
                ElfFullEngine.load(file)
                val secs = withContext(Dispatchers.Default) { ElfFullEngine.parseSectionHeaders() }
                val dyns = withContext(Dispatchers.Default) { ElfFullEngine.parseDynamicSection() }
                withContext(Dispatchers.Main) {
                    entries = dyns
                    status = "Loaded ${dyns.size} dynamic entries from ${file.name}"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Error: ${e.message}" }
            }
            isLoading = false
        }
    }

    val rev = SharedFileState.revision
    LaunchedEffect(rev) { loadFile() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Dynamic Section", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { entries = emptyList(); loadFile() }) { Icon(Icons.Filled.Refresh, "Refresh") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
        )
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding)) {
            if (status.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(status, color = if (entries.isNotEmpty()) AccentGreen else AccentOrange, fontSize = 11.sp)
                    }
                }
            }
            if (entries.isEmpty() && !isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(status.ifEmpty { "Loading..." }, color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                    items(entries) { entry ->
                        val color = when (entry.dTag) {
                            "DT_NEEDED" -> AccentGreen; "DT_SONAME" -> AccentCyan; "DT_INIT" -> AccentOrange; "DT_FINI" -> AccentRed; else -> AccentBlue
                        }
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                            Row(Modifier.padding(8.dp).horizontalScroll(rememberScrollState())) {
                                Text("${entry.dTag.padEnd(20)} ", color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(entry.dValStr, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
