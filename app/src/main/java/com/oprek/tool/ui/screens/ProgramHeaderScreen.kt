package com.oprek.tool.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.engine.ElfFullEngine
import com.oprek.tool.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramHeaderScreen(navController: NavController) {
    val context = LocalContext.current
    var headers by remember { mutableStateOf(listOf<com.oprek.tool.engine.ProgramHeader>()) }
    var loaded by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Program Headers", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding)) {
            if (!loaded) {
                val file = com.oprek.tool.MainViewModel::class.java.getDeclaredMethod("getCurrentRawFile").let { null }
                Button(onClick = {
                    try {
                        headers = ElfFullEngine.parseProgramHeaders()
                        loaded = true
                    } catch (_: Exception) {}
                }, Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                    Text("Load from current file")
                }
            }
            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                item {
                    Text("Program Headers (${headers.size})", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

                }
                items(headers) { ph ->
                    val color = when {
                        ph.pFlags.contains('X') && ph.pFlags.contains('W') -> AccentRed
                        ph.pFlags.contains('X') -> AccentGreen
                        ph.pFlags.contains('W') -> AccentOrange
                        else -> AccentBlue
                    }
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(8.dp)) {
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
