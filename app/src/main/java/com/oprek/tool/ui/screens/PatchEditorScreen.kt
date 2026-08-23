package com.oprek.tool.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchEditorScreen(navController: NavController, vm: MainViewModel) {
    val patches by vm.patches.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var patchOffset by remember { mutableStateOf("") }
    var patchBytes by remember { mutableStateOf("") }
    var showBulkDialog by remember { mutableStateOf(false) }
    var bulkText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patch Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add Patch")
                    }
                    IconButton(onClick = { showBulkDialog = true }) {
                        Icon(Icons.Default.ContentPaste, "Bulk Patch")
                    }
                    IconButton(onClick = {
                        val export = vm.exportPatches()
                        if (export.isNotEmpty()) {
                            val file = com.oprek.tool.core.FileUtils.exportToFile(
                                navController.context, export, "patches_${System.currentTimeMillis()}.txt"
                            )
                        }
                    }) {
                        Icon(Icons.Default.Save, "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (statusMessage.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(statusMessage, fontSize = 12.sp, color = AccentGreen,
                        modifier = Modifier.padding(12.dp))
                }
            }

            // Patch list
            if (patches.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔧", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("No patches yet", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("Add single or bulk patches using the toolbar", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showBulkDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Paste Patches")
                        }
                    }
                }
            } else {
                LazyColumn {
                    itemsIndexed(patches) { idx, patch ->
                        PatchRow(idx, patch)
                    }
                }
            }
        }
    }

    // Single patch dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Patch", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = patchOffset,
                        onValueChange = { patchOffset = it },
                        label = { Text("Offset (hex)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = darkTextFieldColors()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = patchBytes,
                        onValueChange = { patchBytes = it },
                        label = { Text("Bytes (hex, e.g. 00 11 22)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = darkTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val off = patchOffset.removePrefix("0x").removePrefix("0X").toLong(16)
                        val bytes = patchBytes.replace("0x", "").replace("0X", "").replace(" ", "")
                            .chunked(2).map { it.toByte(16) }.toByteArray()
                        vm.patchBytes(off, bytes)
                        showAddDialog = false
                        patchOffset = ""
                        patchBytes = ""
                    } catch (_: Exception) {}
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                    Text("Apply")
                }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
            containerColor = DarkCard
        )
    }

    // Bulk patch dialog
    if (showBulkDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDialog = false },
            title = { Text("Bulk Patches", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Format: one per line\n0x1234: AB CD EF", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bulkText,
                        onValueChange = { bulkText = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        colors = darkTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val patchesToApply = bulkText.lines()
                            .filter { it.contains(":") }
                            .map { line ->
                                val (offStr, byteStr) = line.split(":", limit = 2)
                                val off = offStr.trim().removePrefix("0x").removePrefix("0X").toLong(16)
                                val bytes = byteStr.trim().replace(" ", "").chunked(2)
                                    .map { it.toByte(16) }.toByteArray()
                                off to bytes
                            }
                        vm.bulkPatch(patchesToApply)
                        showBulkDialog = false
                        bulkText = ""
                    } catch (_: Exception) {}
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                    Text("Apply All")
                }
            },
            dismissButton = { TextButton(onClick = { showBulkDialog = false }) { Text("Cancel") } },
            containerColor = DarkCard
        )
    }
}

@Composable
fun PatchRow(idx: Int, patch: com.oprek.tool.PatchEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(if (idx % 2 == 0) DarkBg else DarkSurface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#${idx + 1}", fontSize = 11.sp, color = TextMuted, modifier = Modifier.width(30.dp))
        Text("0x${"%08X".format(patch.offset)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = AccentPurple, modifier = Modifier.width(100.dp))
        Text(patch.type.uppercase(), fontSize = 10.sp, color = AccentOrange, modifier = Modifier.width(40.dp))
        Text(
            patch.data.joinToString(" ") { "%02X".format(it) },
            fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}
