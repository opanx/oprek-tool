package com.oprek.tool.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.engine.ElfFullEngine
import com.oprek.tool.engine.SymbolEntry
import com.oprek.tool.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolTableScreen(navController: NavController) {
    var symbols by remember { mutableStateOf(listOf<SymbolEntry>()) }
    var search by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("ALL") }
    val types = listOf("ALL", "FUNC", "OBJECT", "NOTYPE")

    Scaffold(topBar = {
        TopAppBar(title = { Text("Symbol Table", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg))
    }, containerColor = DarkBg) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(value = search, onValueChange = { search = it },
                label = { Text("Search symbols...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null, tint = AccentCyan) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, cursorColor = AccentCyan))
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                types.forEach { t ->
                    FilterChip(selected = typeFilter == t, onClick = { typeFilter = t },
                        label = { Text(t, fontSize = 10.sp) }, modifier = Modifier.padding(end = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f)))
                }
            }
            Text("  ${symbols.size} symbols", fontSize = 11.sp, color = TextMuted)
            LazyColumn(Modifier.padding(horizontal = 8.dp)) {

                val filtered = symbols.filter {
                    (search.isEmpty() || it.stName.contains(search, true)) &&
                    (typeFilter == "ALL" || it.stType.contains(typeFilter, true))
                }
                items(filtered.take(500)) { sym ->
                    val color = when (sym.stType) {
                        "STT_FUNC" -> AccentGreen; "STT_OBJECT" -> AccentOrange; else -> TextMuted
                    }
                    Card(Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(4.dp)) {
                        Row(Modifier.padding(6.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            Text("0x${"%08X".format(sym.stValue)} ", color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("${sym.stName.take(40).padEnd(40)} ", color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("${sym.stType.padEnd(12)} ", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Text("${sym.stBind} ", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            if (sym.stSize > 0) Text("[${sym.stSize}]", color = AccentCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        try {
            ElfFullEngine.parseSectionHeaders()
            symbols = ElfFullEngine.parseSymbolTable()
        } catch (_: Exception) {}

    }
}
