package com.oprek.tool.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ResolvedSymbol(
    val name: String, val value: Long, val size: Long,
    val binding: String, val type: String, val section: String,
    val hexBytes: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolResolverScreen(navController: NavController) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("") }
    var symbols by remember { mutableStateOf(listOf<ResolvedSymbol>()) }
    var filter by remember { mutableStateOf("") }
    var selectedSymbol by remember { mutableStateOf<ResolvedSymbol?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var fileData by remember { mutableStateOf<ByteArray?>(null) }

    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val path = getPathFromUri(context, it)
            if (path != null) {
                fileName = File(path).name
                isProcessing = true
                scope.launch(Dispatchers.IO) {
                    val data = File(path).readBytes()
                    val syms = resolveSymbols(data)
                    withContext(Dispatchers.Main) {
                        fileData = data
                        symbols = syms
                        isProcessing = false
                    }
                }
            }
        }
    }

    val filtered = symbols.filter {
        filter.isEmpty() || it.name.contains(filter, ignoreCase = true) ||
                it.section.contains(filter, ignoreCase = true) ||
                it.binding.contains(filter, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏷️ Symbol Resolver", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AccentGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // File selector
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(if (fileName.isNotEmpty()) fileName else "Select ELF binary", modifier = Modifier.weight(1f), color = if (fileName.isNotEmpty()) TextPrimary else TextSecondary)
                    TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Open") }
                }
            }

            // Search filter
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Search symbols...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) {
                        Icon(Icons.Default.Clear, null)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentPurple)
            )

            // Stats
            Text("  ${filtered.size} / ${symbols.size} symbols", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(4.dp))

            if (isProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp), color = AccentPurple)
            }

            // Symbol detail card
            selectedSymbol?.let { sym ->
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.1f)), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sym.name, color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { selectedSymbol = null }) { Icon(Icons.Default.Close, null, tint = TextMuted) }
                        }
                        Text("Address: 0x${String.format("%016X", sym.value)}", color = AccentCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("Size: ${sym.size} bytes (${String.format("0x%X", sym.size)})", color = AccentOrange, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("Binding: ${sym.binding} | Type: ${sym.type}", color = TextPrimary, fontSize = 11.sp)
                        Text("Section: ${sym.section}", color = TextSecondary, fontSize = 11.sp)
                        if (sym.hexBytes.isNotEmpty()) {
                            Text("Bytes: ${sym.hexBytes}", color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { /* Copy address */ }, label = { Text("Copy Addr", fontSize = 10.sp) })
                            AssistChip(onClick = { /* Jump to offset */ }, label = { Text("Jump To", fontSize = 10.sp) })
                            AssistChip(onClick = { /* Copy hex */ }, label = { Text("Copy Hex", fontSize = 10.sp) })
                        }
                    }
                }
            }

            // Symbol list
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { sym ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedSymbol = sym }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Binding indicator
                        Icon(
                            when (sym.binding) {
                                "GLOBAL" -> Icons.Default.Public
                                "LOCAL" -> Icons.Default.Person
                                "WEAK" -> Icons.Default.Weakness
                                else -> Icons.Default.HelpOutline
                            },
                            null,
                            tint = when (sym.binding) {
                                "GLOBAL" -> AccentGreen
                                "LOCAL" -> AccentOrange
                                "WEAK" -> AccentPurple
                                else -> TextMuted
                            },
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(sym.name, color = AccentCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text("0x${String.format("%08X", sym.value)}", color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(4.dp))
                        Text(sym.type, color = TextMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun resolveSymbols(data: ByteArray): List<ResolvedSymbol> {
    if (data.size < 52) return emptyList()
    if (data[0] != 0x7F.toByte() || data[1] != 'E'.code.toByte() || data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()) {
        return emptyList()
    }

    val buf = ByteBuffer.wrap(data)
    buf.order(ByteOrder.LITTLE_ENDIAN)
    val is64 = data[4] == 2.toByte()
    val result = mutableListOf<ResolvedSymbol>()

    if (is64) {
        val shOff = buf.getLong(40)
        val shNum = buf.getShort(60).toInt() and 0xFFFF
        val shEntSize = buf.getShort(58).toInt() and 0xFFFF
        val shStrndx = buf.getShort(62).toInt() and 0xFFFF

        if (shOff <= 0 || shNum <= 0) return emptyList()

        // String table
        val strTabIdx = shOff + shStrndx * shEntSize
        val strOffset = buf.getLong(strTabIdx.toInt() + 24)
        val strSize = buf.getLong(strTabIdx.toInt() + 32).toInt()

        fun readStr(idx: Int): String {
            if (idx < 0 || idx >= strSize || strOffset + idx >= data.size.toLong()) return ""
            val end = data.indexOf(0, (strOffset + idx).toInt())
            return if (end > 0) String(data, (strOffset + idx).toInt(), end - (strOffset + idx).toInt()) else ""
        }

        for (i in 0 until minOf(shNum, 200)) {
            val off = (shOff + i * shEntSize).toInt()
            if (off + shEntSize > data.size) break
            val type = buf.getInt(off + 4)

            if (type == 2 || type == 11) { // SHT_SYMTAB / SHT_DYNSYM
                val symOff = buf.getLong(off + 24)
                val symEntSize = buf.getLong(off + 56).toInt()
                val symCount = if (symEntSize > 0) (buf.getLong(off + 32) / symEntSize).toInt() else 0
                val symStrIdx = buf.getInt(off + 24)
                val symStrTable = buf.getLong(off + 40)
                val symStrOffset = buf.getLong(symStrTable.toInt() + 24)
                val symStrSize = buf.getLong(symStrTable.toInt() + 32).toInt()

                fun readSymStr(idx: Int): String {
                    if (idx < 0 || idx >= symStrSize || symStrOffset + idx >= data.size.toLong()) return ""
                    val end = data.indexOf(0, (symStrOffset + idx).toInt())
                    return if (end > 0) String(data, (symStrOffset + idx).toInt(), end - (symStrOffset + idx).toInt()) else ""
                }

                val secName = readStr(i * shEntSize)

                for (j in 1 until minOf(symCount, 50000)) {
                    val sOff = (symOff + j * symEntSize).toInt()
                    if (sOff + 24 > data.size) break

                    val sNameIdx = buf.getInt(sOff)
                    val sInfo = buf.get(sOff + 4).toInt() and 0xFF
                    val sShndx = buf.getShort(sOff + 6).toInt() and 0xFFFF
                    val sValue = buf.getLong(sOff + 8)
                    val sSize = buf.getLong(sOff + 16)

                    val name = readSymStr(sNameIdx)
                    if (name.isEmpty()) continue

                    val binding = when (sInfo shr 4) {
                        0 -> "LOCAL"
                        1 -> "GLOBAL"
                        2 -> "WEAK"
                        else -> "OTHER(${sInfo shr 4})"
                    }
                    val symType = when (sInfo and 0xF) {
                        0 -> "NOTYPE"
                        1 -> "OBJECT"
                        2 -> "FUNC"
                        3 -> "SECTION"
                        4 -> "FILE"
                        else -> "OTHER(${sInfo and 0xF})"
                    }

                    // Read hex bytes if function
                    val hexBytes = if (symType == "FUNC" && sValue > 0 && sSize in 1..64 && sValue + sSize <= data.size.toLong()) {
                        data.sliceArray(sValue.toInt() until (sValue + sSize).toInt()).joinToString(" ") { String.format("%02X", it) }
                    } else ""

                    result.add(ResolvedSymbol(name, sValue, sSize, binding, symType, secName, hexBytes))
                }
            }
        }
    } else {
        // ELF32
        val shOff = buf.getInt(32).toLong() and 0xFFFFFFFFL
        val shNum = buf.getShort(48).toInt() and 0xFFFF
        val shEntSize = buf.getShort(46).toInt() and 0xFFFF

        if (shOff <= 0 || shNum <= 0) return emptyList()

        for (i in 0 until minOf(shNum, 200)) {
            val off = (shOff + i * shEntSize).toInt()
            if (off + shEntSize > data.size) break
            val type = buf.getInt(off + 4)

            if (type == 2) {
                val symOff = buf.getInt(off + 16).toLong() and 0xFFFFFFFFL
                val symEntSize = buf.getInt(off + 36)
                val symCount = if (symEntSize > 0) (buf.getInt(off + 20) / symEntSize) else 0
                val symStrOff = buf.getInt(off + 24).toLong() and 0xFFFFFFFFL
                val symStrOffset = buf.getInt(symStrOff.toInt() + 16).toLong() and 0xFFFFFFFFL
                val symStrSize = buf.getInt(symStrOff.toInt() + 20)

                fun readSymStr(idx: Int): String {
                    if (idx < 0 || idx >= symStrSize || symStrOffset + idx >= data.size.toLong()) return ""
                    val end = data.indexOf(0, (symStrOffset + idx).toInt())
                    return if (end > 0) String(data, (symStrOffset + idx).toInt(), end - (symStrOffset + idx).toInt()) else ""
                }

                val secNameIdx = buf.getInt(i * shEntSize)
                val secName = if (secNameIdx in 0 until 1000) "" else "" // simplified

                for (j in 1 until minOf(symCount, 50000)) {
                    val sOff = (symOff + j * symEntSize).toInt()
                    if (sOff + 16 > data.size) break
                    val sNameIdx = buf.getInt(sOff)
                    val sValue = buf.getInt(sOff + 4).toLong() and 0xFFFFFFFFL
                    val sSize = buf.getInt(sOff + 8).toLong() and 0xFFFFFFFFL
                    val sInfo = buf.get(sOff + 12).toInt() and 0xFF

                    val name = readSymStr(sNameIdx)
                    if (name.isEmpty()) continue

                    val binding = when (sInfo shr 4) { 0 -> "LOCAL"; 1 -> "GLOBAL"; 2 -> "WEAK"; else -> "OTHER" }
                    val symType = when (sInfo and 0xF) { 1 -> "OBJECT"; 2 -> "FUNC"; else -> "OTHER" }

                    result.add(ResolvedSymbol(name, sValue, sSize, binding, symType, secName))
                }
            }
        }
    }

    return result.sortedByDescending { it.binding == "GLOBAL" }.thenBy { it.name }
}

private fun getPathFromUri(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                val name = it.getString(idx)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val file = File(context.cacheDir, name)
                file.outputStream().use { out -> inputStream.copyTo(out) }
                return file.absolutePath
            }
        }
    }
    return null
}
