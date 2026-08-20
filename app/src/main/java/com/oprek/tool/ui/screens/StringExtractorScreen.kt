package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.MainViewModel
import com.oprek.tool.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringExtractorScreen(navController: NavController, vm: MainViewModel) {
    val strings by vm.strings.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    var minLength by remember { mutableStateOf("4") }
    var filter by remember { mutableStateOf("") }
    var showFilter by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.extractStrings() }

    val filtered = remember(strings, filter) {
        if (filter.isEmpty()) strings
        else strings.filter { it.value.contains(filter, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📝 Strings (${filtered.size}/${strings.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconRow(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = includeUtf16, onCheckedChange = { includeUtf16 = it })
                    Text("Include UTF-16", fontSize = 12.sp, color = TextSecondary)
                }
                Button(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconRow(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = includeUtf16, onCheckedChange = { includeUtf16 = it })
                    Text("Include UTF-16", fontSize = 12.sp, color = TextSecondary)
                }
                Button(onClick = { showFilter = !showFilter }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    IconRow(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = includeUtf16, onCheckedChange = { includeUtf16 = it })
                    Text("Include UTF-16", fontSize = 12.sp, color = TextSecondary)
                }
                Button(onClick = {
                        val text = filtered.joinToString("\n") { "0x${"%08X".format(it.offset)}: ${it.value}" }
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("strings", text))
                        Toast.makeText(context, "Copied ${filtered.size} strings!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy All") }
                    IconRow(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = includeUtf16, onCheckedChange = { includeUtf16 = it })
                    Text("Include UTF-16", fontSize = 12.sp, color = TextSecondary)
                }
                Button(onClick = { vm.extractStrings(minLength.toIntOrNull() ?: 4) }) {
                        Icon(Icons.Default.Refresh, "Reload")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Search bar (always visible)
            if (showFilter) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("Search strings...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = AccentGreen) },
                    trailingIcon = {
                        if (filter.isNotEmpty()) {
                            IconRow(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = includeUtf16, onCheckedChange = { includeUtf16 = it })
                    Text("Include UTF-16", fontSize = 12.sp, color = TextSecondary)
                }
                Button(onClick = { filter = "" }) {
                                Icon(Icons.Default.Close, "Clear", tint = AccentGreen)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        cursorColor = AccentGreen
                    )
                )
                // Min length + count
                Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Min: ", fontSize = 11.sp, color = TextMuted)
                    OutlinedTextField(
                        value = minLength,
                        onValueChange = { minLength = it },
                        modifier = Modifier.width(50.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconRow(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = includeUtf16, onCheckedChange = { includeUtf16 = it })
                    Text("Include UTF-16", fontSize = 12.sp, color = TextSecondary)
                }
                Button(onClick = { vm.extractStrings(minLength.toIntOrNull() ?: 4) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Check, "Apply", Modifier.size(16.dp), tint = AccentGreen)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${filtered.size} results", fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            }

            if (statusMessage.isNotEmpty()) {
                Text(statusMessage, fontSize = 11.sp, color = AccentGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }

            // String list with highlight
            LazyColumn(Modifier.fillMaxSize()) {

                itemsIndexed(filtered) { idx, sp ->
                    StringRowWithHighlight(idx, sp, filter, context)
                }
                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📝", fontSize = 48.sp)
                                Spacer(Modifier.height(12.dp))
                                if (filter.isNotEmpty()) {
                                    Text("No matches for \"$filter\"", color = AccentOrange, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("No strings found", color = TextSecondary)
                                    Text("Extract strings from a loaded file", fontSize = 13.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StringRowWithHighlight(idx: Int, sp: com.oprek.tool.core.StringPair, filter: String, context: Context) {
    val annotatedText = buildAnnotatedString {
        if (filter.isNotEmpty() && sp.value.contains(filter, ignoreCase = true)) {
            val lowerValue = sp.value.lowercase()
            val lowerFilter = filter.lowercase()
            var start = 0
            var idx = lowerValue.indexOf(lowerFilter, start)
            while (idx >= 0) {
                if (idx > start) append(sp.value.substring(start, idx))
                withStyle(SpanStyle(color = AccentOrange, fontWeight = FontWeight.Bold)) {
                    append(sp.value.substring(idx, idx + filter.length))
                }
                start = idx + filter.length
                idx = lowerValue.indexOf(lowerFilter, start)
            }
            if (start < sp.value.length) append(sp.value.substring(start))
        } else {
            append(sp.value)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .background(if (idx % 2 == 0) DarkBg else DarkSurface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "0x${"%08X".format(sp.offset)}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = AccentPurple,
            modifier = Modifier.width(90.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            annotatedText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = AccentGreen,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        )
        IconRow(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = includeUtf16, onCheckedChange = { includeUtf16 = it })
                    Text("Include UTF-16", fontSize = 12.sp, color = TextSecondary)
                }
                Button(onClick = {
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("str", sp.value))
        }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(12.dp), tint = TextMuted)

        }
    }
}
