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
fun SectionHeaderScreen(navController: NavController) {
    var sections by remember { mutableStateOf(listOf<com.oprek.tool.engine.SectionHeader>()) }
    var filter by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Section Headers", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(value = filter, onValueChange = { filter = it },
                label = { Text("Filter sections...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan, focusedLabelColor = AccentCyan))
            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                items(sections.filter { filter.isEmpty() || it.shName.contains(filter, true) }) { sec ->
                    val color = when {
                        sec.shType == "SHT_SYMTAB" || sec.shType == "SHT_DYNSYM" -> AccentPurple
                        sec.shType == "SHT_STRTAB" -> AccentBlue
                        sec.shType == "SHT_DYNAMIC" -> AccentOrange
                        sec.shType == "SHT_NOBITS" -> AccentRed
                        sec.shType == "SHT_PROGBITS" -> AccentGreen
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
    LaunchedEffect(Unit) { try { sections = ElfFullEngine.parseSectionHeaders() } catch (_: Exception) {} }
}
