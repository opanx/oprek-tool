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
import com.oprek.tool.engine.ElfFullEngine
import com.oprek.tool.engine.SymbolEntry
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.oprek.tool.ui.components.OutputButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionListScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var symbols by remember { mutableStateOf(listOf<SymbolEntry>()) }
    var search by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("FUNC") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(context.cacheDir, "temp_${System.currentTimeMillis()}")
                context.contentResolver.openInputStream(it)?.use { input -> file.writeBytes(input.readBytes()) }
                ElfFullEngine.load(file); file.delete()
                val syms = ElfFullEngine.parseSymbolTable().filter { it.stType == "STT_FUNC" }
                withContext(Dispatchers.Main) { symbols = syms }
            } catch (_: Exception) {}
        }}
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Function List", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search functions...") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = AccentCyan) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))
            Row(Modifier.padding(vertical = 4.dp)) {
                listOf("FUNC", "OBJECT", "ALL").forEach { t ->
                    FilterChip(selected = typeFilter == t, onClick = { typeFilter = t }, label = { Text(t, fontSize = 10.sp) },
                        modifier = Modifier.padding(end = 4.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(0.2f)))
                }
                if (symbols.isEmpty()) {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Open ELF", fontSize = 10.sp) }
                }
            }
            Text("${symbols.size} functions", fontSize = 11.sp, color = TextMuted)
            LazyColumn(Modifier.weight(1f)) {
            // Output to /sdcard/oprek-tool/output/
            Spacer(Modifier.height(12.dp))
            OutputButton(
                content = { symbols.joinToString("\n") { "0x${"%08X".format(it.stValue)} ${it.stName} [${it.stSize}B]" } },
                filename = "functions.txt",
                subfolder = "elf"
            )

                val filtered = symbols.filter {
                    (search.isEmpty() || it.stName.contains(search, true)) &&
                    (typeFilter == "ALL" || it.stType.contains(typeFilter, true))
                }
                itemsIndexed(filtered.take(500)) { _, sym ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 1.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(4.dp)) {
                        Row(Modifier.padding(6.dp).horizontalScroll(rememberScrollState())) {
                            Text("0x${"%08X".format(sym.stValue)} ", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("${sym.stName.take(50).padEnd(50)} ", color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            if (sym.stSize > 0) Text("[${sym.stSize}B]", color = AccentCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
