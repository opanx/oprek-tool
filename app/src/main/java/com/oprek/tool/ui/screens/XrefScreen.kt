package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrefScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var targetAddr by remember { mutableStateOf("") }
    var xrefs by remember { mutableStateOf(listOf<Long>()) }
    var loaded by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try { val b = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf(); withContext(Dispatchers.Main) { fileBytes = b; loaded = true } } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("XREF Viewer", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open Binary") }
            }
            if (loaded) {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = targetAddr, onValueChange = { targetAddr = it },
                        label = { Text("Target address (hex)") }, modifier = Modifier.weight(1f).padding(end = 8.dp),
                        singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))
                    Button(onClick = {
                        xrefs = listOf()
                        try {
                            val target = targetAddr.toLong(16)
                            val results = mutableListOf<Long>()
                            for (i in 0 until fileBytes.size - 3 step 4) {
                                val insn = fileBytes[i].toInt() and 0xFF or ((fileBytes[i+1].toInt() and 0xFF) shl 8) or
                                    ((fileBytes[i+2].toInt() and 0xFF) shl 16) or ((fileBytes[i+3].toInt() and 0xFF) shl 24)
                                val opc = (insn shr 26) and 0x3F
                                if (opc == 0x25 || opc == 0x05) {
                                    val imm26 = insn and 0x3FFFFFF
                                    val signExt = if (imm26 and 0x2000000 != 0) (imm26 or 0xFC000000.toInt()).toLong() else imm26.toLong()
                                    val branchTarget = i + signExt * 4
                                    if (branchTarget == target) results.add(i.toLong())
                                }
                            }
                            xrefs = results
                        } catch (_: Exception) {}
                    }) { Text("Find XREF") }
                }
                Spacer(Modifier.height(12.dp))
                Text("${xrefs.size} cross-references found:", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {

                    itemsIndexed(xrefs) { _, addr ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                            Text("0x${"%08X".format(addr)}  →  0x${targetAddr}", modifier = Modifier.padding(8.dp),
                                color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "${xrefs.size} xrefs found" },
                filename = "xrefs.txt",
                subfolder = "xref"
            )

        }
    }
}
