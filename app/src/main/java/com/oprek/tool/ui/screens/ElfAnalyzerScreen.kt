package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElfAnalyzerScreen(navController: NavController, vm: MainViewModel) {
    val elfInfo by vm.elfInfo.collectAsState()
    val sections by vm.elfSections.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ELF Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        if (elfInfo == null || !elfInfo!!.isValid) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ELF file loaded\nOpen a .so or ELF binary from Home", color = TextSecondary)
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                // ELF Header Info
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("📋 ELF Header", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPurple)
                            Spacer(Modifier.height(8.dp))
                            val info = elfInfo!!
                            ElfField("Architecture", if (info.is64Bit) "ELF64 (x86-64 / AArch64)" else "ELF32 (x86 / ARM)")
                            ElfField("Endianness", info.endian)
                            ElfField("Entry Point", "0x${"%016X".format(info.entryPoint)}")
                            ElfField("Program Headers", "${info.phCount} @ 0x${"%08X".format(info.phOffset)}")
                            ElfField("Section Headers", "${info.shCount} @ 0x${"%08X".format(info.shOffset)}")
                            ElfField("File Size", formatSize(info.fileSize))
                        }
                    }
                }

                // Section table header
                if (sections.isNotEmpty()) {
                    item {
                        Text("  📂 Sections (${sections.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            color = AccentPurple, modifier = Modifier.padding(top = 8.dp))
                    }
                    itemsIndexed(sections) { idx, section ->
                        SectionRow(idx, section)
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }

            }
        }
    }
}

@Composable
fun ElfField(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 12.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
        Text(value, fontSize = 12.sp, color = AccentGreen, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SectionRow(idx: Int, section: com.oprek.tool.core.ElfSection) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(if (idx % 2 == 0) DarkBg else DarkSurface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(section.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentCyan,
            modifier = Modifier.width(100.dp), maxLines = 1)
        Text(section.typeStr, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentOrange,
            modifier = Modifier.width(70.dp))
        Text("0x${"%08X".format(section.offset)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
            modifier = Modifier.width(90.dp))
        Text(formatSize(section.size), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)

    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1048576 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / 1048576.0)}MB"
}
