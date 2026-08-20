package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchBranchScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var branches by remember { mutableStateOf(listOf<Pair<Long, String>>()) }
    var loaded by remember { mutableStateOf(false) }
    var patchedCount by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try { val b = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf(); withContext(Dispatchers.Main) { fileBytes = b; loaded = true } } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Patch Branch", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) { Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open ELF") } }
            if (loaded) {
                Button(onClick = {
                    val found = mutableListOf<Pair<Long, String>>()
                    for (i in 0 until fileBytes.size - 3 step 4) {
                        val insn = fileBytes[i].toInt() and 0xFF or ((fileBytes[i+1].toInt() and 0xFF) shl 8) or
                            ((fileBytes[i+2].toInt() and 0xFF) shl 16) or ((fileBytes[i+3].toInt() and 0xFF) shl 24)
                        val opc26 = (insn shr 26) and 0x3F
                        val opc8 = (insn shr 24) and 0xFF
                        if (opc8 == 0x54) {
                            val cond = insn and 0xF
                            val condStr = listOf("EQ","NE","CS","CC","MI","PL","VS","VC","HI","LS","GE","LT","GT","LE","AL","NV")[cond]
                            found.add(i.toLong() to "B.$condStr")
                        }
                    }
                    branches = found
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("Scan Conditional Branches") }
                Spacer(Modifier.height(8.dp))

                Text("${branches.size} conditional branches found", color = AccentCyan, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f)) {
                    items(branches) { (addr, cond) ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(6.dp)) {
                            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("0x${"%08X".format(addr)}", color = AccentCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text(cond, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                                Button(onClick = {
                                    val nop = byteArrayOf(0x1F, 0x20, 0x03, 0xD5.toByte())
                                    for (j in 0..3) fileBytes[addr.toInt()+j] = nop[j]
                                    patchedCount++
                                    branches = branches.filter { it.first != addr }
                                }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) { Text("NOP", fontSize = 10.sp) }
                            }
                        }
                    }
                }
                if (patchedCount > 0) Text("✓ $patchedCount branches NOP'd", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "$patchedCount branches patched" },
                filename = "patch_branch.txt",
                subfolder = "patches"
            )

        }
    }
}
