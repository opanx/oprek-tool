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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicSectionScreen(navController: NavController) {
    var entries by remember { mutableStateOf(listOf<com.oprek.tool.engine.DynamicEntry>()) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Dynamic Section", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 12.dp)) {

            items(entries) { entry ->
                val color = when(entry.dTag) {
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

    LaunchedEffect(Unit) { try { ElfFullEngine.parseSectionHeaders(); entries = ElfFullEngine.parseDynamicSection() } catch (_: Exception) {} }
}
