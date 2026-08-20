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
fun RelocationScreen(navController: NavController) {
    var relocs by remember { mutableStateOf(listOf<com.oprek.tool.engine.RelocationEntry>()) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Relocations", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 12.dp)) {
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { relocs.joinToString("\n") { "0x${"%08X".format(it.rOffset)} ${it.rType} ${it.rSym}" } },
                filename = "relocations.txt",
                subfolder = "elf"
            )

            items(relocs) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                    Row(Modifier.padding(6.dp).horizontalScroll(rememberScrollState())) {
                        Text("0x${"%08X".format(r.rOffset)} ", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("${r.rType.padEnd(24)} ", color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(r.rSym.take(30).padEnd(30), color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        if (r.rAddend != 0L) Text(" +${r.rAddend}", color = AccentOrange, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { ElfFullEngine.parseSectionHeaders(); relocs = ElfFullEngine.parseRelocations() } catch (_: Exception) {} }
}
