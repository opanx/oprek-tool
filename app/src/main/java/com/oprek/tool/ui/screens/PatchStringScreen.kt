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
fun PatchStringScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(byteArrayOf()) }
    var strings by remember { mutableStateOf(listOf<Pair<Long, String>>()) }
    var searchStr by remember { mutableStateOf("") }
    var replaceStr by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var patchedCount by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try { val b = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf(); withContext(Dispatchers.Main) { fileBytes = b; loaded = true } } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Patch String", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) { Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open Binary") } }
            if (loaded) {
                OutlinedTextField(value = searchStr, onValueChange = { searchStr = it }, label = { Text("Search string") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))
                OutlinedTextField(value = replaceStr, onValueChange = { replaceStr = it }, label = { Text("Replace with") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange, cursorColor = AccentOrange))
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = {
                        strings = mutableListOf(); val sb = StringBuilder(); var start = 0L
                        for (i in fileBytes.indices) {
                            val b = fileBytes[i].toInt() and 0xFF
                            if (b in 0x20..0x7E) { if (sb.isEmpty()) start = i.toLong(); sb.append(b.toChar()) }
                            else { if (sb.length >= 4) strings = strings + (start to sb.toString()); sb.clear() }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) { Text("Scan") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (searchStr.isEmpty() || replaceStr.isEmpty()) return@Button
                        val searchBytes = searchStr.toByteArray(); val replaceBytes = replaceStr.toByteArray()
                        if (replaceBytes.size > searchBytes.size) return@Button
                        for (i in 0 until fileBytes.size - searchBytes.size) {
                            if (fileBytes.sliceArray(i until i + searchBytes.size).contentEquals(searchBytes)) {
                                for (j in replaceBytes.indices) fileBytes[i + j] = replaceBytes[j]
                                for (j in replaceBytes.size until searchBytes.size) fileBytes[i + j] = 0
                                patchedCount++
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) { Text("Patch All") }
                }
                Spacer(Modifier.height(8.dp))

                Text("${strings.size} strings found, $patchedCount patched", color = AccentCyan, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(strings.filter { searchStr.isEmpty() || it.second.contains(searchStr, true) }) { _, (off, s) ->
                        Text("0x${"%08X".format(off)}: \"$s\"", color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { "${strings.size} strings found, $patchedCount patched" },
                filename = "patch_string.txt",
                subfolder = "patches"
            )

        }
    }
}
