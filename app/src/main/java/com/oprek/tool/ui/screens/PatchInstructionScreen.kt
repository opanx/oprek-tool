package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.core.SharedFileState
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchInstructionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var offset by remember { mutableStateOf("") }
    var insnType by remember { mutableStateOf("NOP") }
    var preview by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    // Auto-load from SharedFileState
    val rev = SharedFileState.revision
    LaunchedEffect(rev) {
        val f = SharedFileState.findFile(context)
        if (f != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val b = withContext(Dispatchers.IO) { f.readBytes() }
                    withContext(Dispatchers.Main) { fileBytes = b; loaded = true; offset = "" }
                } catch (_: Exception) {}
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try { val b = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf(); withContext(Dispatchers.Main) { fileBytes = b; loaded = true; offset = "" } } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Patch Instruction", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) { Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open ELF") } }
            if (loaded) {
                OutlinedTextField(value = offset, onValueChange = { offset = it }, label = { Text("Offset (hex)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))
                Spacer(Modifier.height(8.dp))
                Text("Patch type:", color = TextSecondary, fontSize = 12.sp)
                Row { listOf("NOP", "RET", "RET X0=0", "JMP").forEach { t ->
                    FilterChip(selected = insnType == t, onClick = { insnType = t }, label = { Text(t, fontSize = 10.sp) },
                        modifier = Modifier.padding(end = 4.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(0.2f)))
                }}
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    error = ""; preview = ""; saved = false
                    try {
                        val off = offset.toLong(16)
                        if (off + 4 > fileBytes.size) { error = "Offset out of range"; return@Button }
                        val orig = "%02X %02X %02X %02X".format(fileBytes[off.toInt()], fileBytes[off.toInt()+1], fileBytes[off.toInt()+2], fileBytes[off.toInt()+3])
                        val patched = when (insnType) {
                            "NOP" -> byteArrayOf(0x1F, 0x20, 0x03, 0xD5.toByte()) // ARM64 NOP
                            "RET" -> byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()) // ARM64 RET
                            "RET X0=0" -> byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x52.toByte()) + byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()) // MOV W0,#0; RET
                            "JMP" -> byteArrayOf(0x00, 0x00, 0x00, 0x14.toByte()) // B . (self-loop)
                            else -> byteArrayOf(0x1F, 0x20, 0x03, 0xD5.toByte())
                        }
                        preview = "Original: $orig\nPatched: ${patched.joinToString(" ") { "%02X".format(it) }}\nType: $insnType"
                        // Apply
                        for (i in patched.indices) fileBytes[off.toInt() + i] = patched[i]
                        saved = true
                    } catch (e: Exception) { error = e.message ?: "Error" }
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) { Text("Apply Patch") }
                if (error.isNotEmpty()) Text(error, color = AccentRed, fontSize = 11.sp)
                if (preview.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) { Text(preview, modifier = Modifier.padding(12.dp), color = AccentGreen, fontSize = 11.sp) } }
                if (saved) { Spacer(Modifier.height(8.dp)); Text("✓ Patch applied (in memory)", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp) }

            }

        }
    }
}
