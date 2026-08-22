package com.oprek.tool.ui.screens

import com.oprek.tool.core.SharedFileState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
fun SectionHeaderScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sections by remember { mutableStateOf(listOf<com.oprek.tool.engine.SectionHeader>()) }
    var filter by remember { mutableStateOf("") }
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
                val secs = withContext(Dispatchers.Default) { ElfFullEngine.parseSectionHeaders() }
                withContext(Dispatchers.Main) { sections = secs; status = "Loaded ${secs.size} sections from ${file.name}" }
            } catch (e: Exception) { withContext(Dispatchers.Main) { status = "Error: ${e.message}" } }
            isLoading = false
        }
    }

    val rev = SharedFileState.revision
    LaunchedEffect(rev) { loadFile() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Section Headers", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { sections = emptyList(); loadFile() }) { Icon(Icons.Filled.Refresh, "Refresh") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
        )
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(value = filter, onValueChange = { filter = it },
                label = { Text("Filter sections...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan, focusedLabelColor = AccentCyan))

            if (status.isNotEmpty() && sections.isEmpty() && !isLoading) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                    Text(status, modifier = Modifier.padding(10.dp), color = AccentOrange, fontSize = 11.sp)
                }
            }

            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                items(sections.filter { filter.isEmpty() || it.shName.contains(filter, true) }) { sec ->
                    val color = when (sec.shType) {
                        "SHT_SYMTAB", "SHT_DYNSYM" -> AccentPurple
                        "SHT_STRTAB" -> AccentBlue
                        "SHT_DYNAMIC" -> AccentOrange
                        "SHT_NOBITS" -> AccentRed
                        "SHT_PROGBITS" -> AccentGreen
                        else -> AccentCyan
                    }
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                        Row(Modifier.padding(8.dp).horizontalScroll(rememberScrollState())) {
                            Text("${sec.shName.padEnd(16)} ", color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("${sec.shType.padEnd(16)} ", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("0x${"%08X".format(sec.shOffset)} ", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("${sec.shSize}B ", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
