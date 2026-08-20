package com.oprek.tool.ui.screens

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.engine.ElfFullEngine
import com.oprek.tool.ui.theme.*
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GotPltScreen(navController: NavController) {
    var entries by remember { mutableStateOf(listOf<com.oprek.tool.engine.GotPltEntry>()) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("GOT / PLT", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 12.dp)) {
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { entries.joinToString("\n") { "[${it.index}] 0x${"%08X".format(it.address)} = 0x${"%08X".format(it.value)} ${it.funcName}" } },
                filename = "got_plt.txt",
                subfolder = "elf"
            )

            items(entries) { e ->
                Card(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                    Row(Modifier.padding(6.dp).horizontalScroll(rememberScrollState())) {
                        Text("[${e.index}] ", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("0x${"%08X".format(e.address)} ", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("= 0x${"%08X".format(e.value)} ", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(e.funcName, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { ElfFullEngine.parseSectionHeaders(); entries = ElfFullEngine.parseGotPlt() } catch (_: Exception) {} }
}
