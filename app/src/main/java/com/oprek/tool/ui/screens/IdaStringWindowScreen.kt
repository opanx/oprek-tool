package com.oprek.tool.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
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
fun IdaStringWindowScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var strings by remember { mutableStateOf(listOf<Triple<Long, String, String>>()) }
    var search by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: byteArrayOf()
                val result = mutableListOf<Triple<Long, String, String>>()
                val sb = StringBuilder(); var start = 0L
                for (i in bytes.indices) {
                    val b = bytes[i].toInt() and 0xFF
                    if (b in 0x20..0x7E) { if (sb.isEmpty()) start = i.toLong(); sb.append(b.toChar()) }
                    else {
                        if (sb.length >= 4) {
                            val s = sb.toString()
                            val type = when {
                                s.contains("http") -> "URL"
                                s.contains("@") -> "EMAIL"
                                s.contains("lib/") || s.contains(".so") -> "LIB"
                                s.contains("chmod") || s.contains("curl") || s.contains("echo") -> "CMD"
                                else -> "STR"
                            }
                            result.add(start to s to type)
                        }
                        sb.clear()
                    }
                }
                withContext(Dispatchers.Main) { strings = result; loaded = true }
            } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("String Window", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            if (!loaded) { Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open Binary") } }
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Filter strings...") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = AccentCyan) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))
            Text("${strings.size} strings", fontSize = 11.sp, color = TextMuted)
            LazyColumn(Modifier.weight(1f)) {
                val filtered = strings.filter { search.isEmpty() || it.second.contains(search, true) }
                itemsIndexed(filtered.take(1000)) { _, (addr, s, type) ->
                    val color = when(type) { "URL" -> AccentBlue; "CMD" -> AccentRed; "LIB" -> AccentOrange; "EMAIL" -> AccentPurple; else -> AccentGreen }
                    Card(Modifier.fillMaxWidth().padding(vertical = 1.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(4.dp)) {
                        Row(Modifier.padding(4.dp).horizontalScroll(rememberScrollState())) {
                            Text("0x${"%08X".format(addr)} ", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("${type.padEnd(4)} ", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(s, color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
