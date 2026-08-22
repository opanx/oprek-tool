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
fun ProgramHeaderScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var headers by remember { mutableStateOf(listOf<com.oprek.tool.engine.ProgramHeader>()) }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun loadFile() {
        val file = SharedFileState.findFile(context)
        if (file == null) { status = "No file loaded. Open from Home first."; return }
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                status = "Loading ${file.name}..."
                ElfFullEngine.load(file)
                val ph = withContext(Dispatchers.Default) { ElfFullEngine.parseProgramHeaders() }
                withContext(Dispatchers.Main) { headers = ph; status = "Loaded ${ph.size} program headers from ${file.name}" }
            } catch (e: Exception) { withContext(Dispatchers.Main) { status = "Error: ${e.message}" } }
            isLoading = false
        }
    }

    val rev = SharedFileState.revision
    LaunchedEffect(rev) { loadFile() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Program Headers", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { headers = emptyList(); loadFile() }) { Icon(Icons.Filled.Refresh, "Refresh") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
        )
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding)) {
            if (status.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(status, color = if (headers.isNotEmpty()) AccentGreen else AccentOrange, fontSize = 11.sp)
                    }
                }
            }
            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                item {
                    Text("Program Headers (${headers.size})", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(headers) { ph ->
                    val color = when {
                        ph.pFlags.contains('X') && ph.pFlags.contains('W') -> AccentRed
                        ph.pFlags.contains('X') -> AccentGreen
                        ph.pFlags.contains('W') -> AccentOrange
                        else -> AccentBlue
                    }
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(10.dp).horizontalScroll(rememberScrollState())) {
                            Text("${ph.pType}  [${ph.pFlags}]", fontWeight = FontWeight.Bold, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text("Offset: 0x${"%X".format(ph.pOffset)}  VAddr: 0x${"%X".format(ph.pVaddr)}", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            Text("FileSz: 0x${"%X".format(ph.pFilesz)}  MemSz: 0x${"%X".format(ph.pMemsz)}  Align: ${ph.pAlign}", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
