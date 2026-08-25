package com.oprek.tool.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.oprek.tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * OFRAK-like Binary Patcher
 * Semantic patching with ELF section/symbol awareness
 */
data class SectionEntry(
    val name: String, val type: Int, val flags: Long,
    val offset: Long, val size: Long, val addr: Long
)

data class SymbolEntry(
    val name: String, val value: Long, val size: Long,
    val info: Int, val shndx: Int, val sectionName: String
)

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

data class RelocEntry(
    val offset: Long, val info: Long, val addend: Long,
    val type: Int, val symbolName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinaryPatcherScreen(navController: NavController) {
    val context = LocalContext.current
    var filePath by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<ByteArray?>(null) }
    var sections by remember { mutableStateOf(listOf<SectionEntry>()) }
    var symbols by remember { mutableStateOf(listOf<SymbolEntry>()) }
    var relocs by remember { mutableStateOf(listOf<RelocEntry>()) }
    var output by remember { mutableStateOf(listOf<String>()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }

    // Patch fields
    var patchOffset by remember { mutableStateOf("") }
    var patchBytes by remember { mutableStateOf("") }
    var searchPattern by remember { mutableStateOf("") }
    var replacePattern by remember { mutableStateOf("") }
    var nopCount by remember { mutableStateOf("4") }
    var nopTarget by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf<SectionEntry?>(null) }

    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val path = getPathFromUri(context, it)
            if (path != null) {
                filePath = path
                fileName = File(path).name
                scope.launch(Dispatchers.IO) {
                    val result = parseElfFull(File(path))
                    withContext(Dispatchers.Main) {
                        data = result.first
                        sections = result.second
                        symbols = result.third
                        relocs = result.fourth
                        output = listOf(
                            "[+] Loaded: ${fileName}",
                            "[+] Size: ${data?.size ?: 0} bytes",
                            "[+] Sections: ${sections.size}",
                            "[+] Symbols: ${symbols.size}",
                            "[+] Relocations: ${relocs.size}"
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 OFRAK Binary Patcher", fontWeight = FontWeight.Bold) },
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
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(if (fileName.isNotEmpty()) fileName else "Select ELF/PE/DEX binary", modifier = Modifier.weight(1f), color = if (fileName.isNotEmpty()) TextPrimary else TextSecondary)
                    TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Open") }
                }
            }

            if (data != null) {
                // Tabs
                TabRow(selectedTabIndex = selectedTab, containerColor = DarkCard) {
                    listOf("Patch", "Sections", "Symbols", "Relocs", "Output").forEachIndexed { i, t ->
                        Tab(selectedTab == i, onClick = { selectedTab = i }, text = { Text(t, fontSize = 12.sp) })
                    }
                }

                when (selectedTab) {
                    0 -> PatchTab(
                        patchOffset, { patchOffset = it }, patchBytes, { patchBytes = it },
                        searchPattern, { searchPattern = it }, replacePattern, { replacePattern = it },
                        nopCount, { nopCount = it }, nopTarget, { nopTarget = it },
                        sections, selectedSection, { selectedSection = it },
                        data!!, filePath!!, isProcessing, { isProcessing = it },
                        output, { output = it }, scope
                    )
                    1 -> SectionTab(sections) { sec -> selectedSection = sec; patchOffset = "0x${String.format("%016X", sec.offset)}" }
                    2 -> SymbolTab(symbols) { sym -> patchOffset = "0x${String.format("%016X", sym.value)}" }
                    3 -> RelocTab(relocs)
                    4 -> OutputTab(output)
                }
            } else {
                // Empty state
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔧", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("OFRAK Binary Patcher", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                        Text("Open a binary to start semantic patching", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun PatchTab(
    patchOffset: String, onOffsetChange: (String) -> Unit,
    patchBytes: String, onBytesChange: (String) -> Unit,
    searchPattern: String, onSearchChange: (String) -> Unit,
    replacePattern: String, onReplaceChange: (String) -> Unit,
    nopCount: String, onNopCountChange: (String) -> Unit,
    nopTarget: String, onNopTargetChange: (String) -> Unit,
    sections: List<SectionEntry>, selectedSection: SectionEntry?, onSectionSelect: (SectionEntry) -> Unit,
    data: ByteArray, filePath: String, isProcessing: Boolean, onProcessingChange: (Boolean) -> Unit,
    output: List<String>, onOutputChange: (List<String>) -> Unit, scope: kotlinx.coroutines.CoroutineScope
) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Section quick-select
        item {
            Text("Quick Jump to Section", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                sections.take(20).forEach { sec ->
                    FilterChip(
                        selected = selectedSection?.name == sec.name,
                        onClick = { onSectionSelect(sec) },
                        label = { Text(sec.name, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = DarkCard)
                    )
                }
            }
        }

        // NOP Patcher
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("NOP Patcher", color = AccentRed, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = nopTarget,
                        onValueChange = onNopTargetChange,
                        label = { Text("Offset (hex or symbol name)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentRed, focusedLabelColor = AccentRed)
                    )
                    OutlinedTextField(
                        value = nopCount,
                        onValueChange = onNopCountChange,
                        label = { Text("Number of bytes") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentRed, focusedLabelColor = AccentRed)
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (!isProcessing) {
                                onProcessingChange(true)
                                scope.launch(Dispatchers.IO) {
                                    val result = patchNop(data, filePath, nopTarget, nopCount.toIntOrNull() ?: 4)
                                    withContext(Dispatchers.Main) {
                                        onOutputChange(output + result)
                                        onProcessingChange(false)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        enabled = !isProcessing && nopTarget.isNotEmpty()
                    ) { Text("Apply NOP Patch") }
                }
            }
        }

        // Byte Patch
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Byte Patch", color = AccentOrange, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = patchOffset,
                        onValueChange = onOffsetChange,
                        label = { Text("Offset (hex)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentOrange, focusedLabelColor = AccentOrange)
                    )
                    OutlinedTextField(
                        value = patchBytes,
                        onValueChange = onBytesChange,
                        label = { Text("New bytes (hex: FF 90 E0 D5)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentOrange, focusedLabelColor = AccentOrange)
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (!isProcessing) {
                                onProcessingChange(true)
                                scope.launch(Dispatchers.IO) {
                                    val result = patchBytesAt(data, filePath, patchOffset, patchBytes)
                                    withContext(Dispatchers.Main) {
                                        onOutputChange(output + result)
                                        onProcessingChange(false)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        enabled = !isProcessing && patchOffset.isNotEmpty() && patchBytes.isNotEmpty()
                    ) { Text("Apply Byte Patch") }
                }
            }
        }

        // Search & Replace
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Search & Replace", color = AccentPurple, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = searchPattern,
                        onValueChange = onSearchChange,
                        label = { Text("Search (hex)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentPurple, focusedLabelColor = AccentPurple)
                    )
                    OutlinedTextField(
                        value = replacePattern,
                        onValueChange = onReplaceChange,
                        label = { Text("Replace with (hex)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentPurple, focusedLabelColor = AccentPurple)
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (!isProcessing) {
                                onProcessingChange(true)
                                scope.launch(Dispatchers.IO) {
                                    val result = searchReplaceHex(data, filePath, searchPattern, replacePattern)
                                    withContext(Dispatchers.Main) {
                                        onOutputChange(output + result)
                                        onProcessingChange(false)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        enabled = !isProcessing && searchPattern.isNotEmpty() && replacePattern.isNotEmpty()
                    ) { Text("Search & Replace") }
                }
            }
        }

        // Bulk Section NOP
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Bulk NOP Section", color = AccentCyan, fontWeight = FontWeight.Bold)
                    Text("NOP entire section (e.g., .text, .plt)", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    selectedSection?.let { sec ->
                        Text("Selected: ${sec.name} @ 0x${String.format("%016X", sec.offset)} (${sec.size} bytes)", color = AccentCyan, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                if (!isProcessing) {
                                    onProcessingChange(true)
                                    scope.launch(Dispatchers.IO) {
                                        val result = nopSection(data, filePath, sec)
                                        withContext(Dispatchers.Main) {
                                            onOutputChange(output + result)
                                            onProcessingChange(false)
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            enabled = !isProcessing
                        ) { Text("NOP Entire Section") }
                    } ?: run {
                        Text("Select a section first from the tabs above", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTab(sections: List<SectionEntry>, onSectionClick: (SectionEntry) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Name", color = AccentGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.25f), fontSize = 11.sp)
                Text("Offset", color = AccentGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.25f), fontSize = 11.sp)
                Text("Size", color = AccentGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.2f), fontSize = 11.sp)
                Text("Addr", color = AccentGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f), fontSize = 11.sp)
            }
            HorizontalDivider(color = AccentGreen.copy(alpha = 0.3f))
        }
        items(sections) { sec ->
            Row(
                Modifier.fillMaxWidth().clickable { onSectionClick(sec) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sec.name, color = AccentCyan, modifier = Modifier.weight(0.25f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("0x${String.format("%016X", sec.offset)}", color = TextPrimary, modifier = Modifier.weight(0.25f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("${sec.size}", color = AccentOrange, modifier = Modifier.weight(0.2f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("0x${String.format("%016X", sec.addr)}", color = TextMuted, modifier = Modifier.weight(0.3f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        item { Spacer(Modifier.height(8.dp)); Text("Total: ${sections.size} sections", color = TextSecondary, fontSize = 12.sp) }
    }
}

@Composable
fun SymbolTab(symbols: List<SymbolEntry>, onSymbolClick: (SymbolEntry) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val filtered = symbols.filter { filter.isEmpty() || it.name.contains(filter, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filter symbols...") },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentPurple)
        )
        LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
            items(filtered) { sym ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSymbolClick(sym) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DataObject, null, tint = when { sym.shndx == 0 -> AccentRed; sym.shndx == 0xFFFF -> AccentOrange; else -> AccentPurple }, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(sym.name, color = AccentCyan, modifier = Modifier.weight(1f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("0x${String.format("%016X", sym.value)}", color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(4.dp))
                    Text("sz:${sym.size}", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun RelocTab(relocs: List<RelocEntry>) {
    var filter by remember { mutableStateOf("") }
    val filtered = relocs.filter { filter.isEmpty() || it.symbolName.contains(filter, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filter relocations...") },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, focusedBorderColor = AccentOrange)
        )
        LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
            items(filtered) { rel ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("0x${String.format("%016X", rel.offset)}", color = TextPrimary, modifier = Modifier.weight(0.25f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("R_${rel.type}", color = AccentOrange, modifier = Modifier.weight(0.2f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(rel.symbolName, color = AccentCyan, modifier = Modifier.weight(0.55f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun OutputTab(output: List<String>) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        items(output) { line ->
            Text(
                line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                color = when {
                    line.startsWith("[+]") -> AccentGreen
                    line.startsWith("[-]") -> AccentRed
                    line.startsWith("[!]") -> AccentOrange
                    line.startsWith("[*]") -> AccentCyan
                    else -> TextPrimary
                },
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
        if (output.isEmpty()) {
            item { Text("No output yet. Apply a patch to see results.", color = TextSecondary) }
        }
    }
}

// ─── ELF Parser ───
private fun parseElfFull(file: File): Quad<ByteArray, List<SectionEntry>, List<SymbolEntry>, List<RelocEntry>> {
    // Returns: (data, sections, (symbols, relocs))
    val data = file.readBytes()
    if (data.size < 52) return Quad(data, emptyList(), emptyList(), emptyList())

    val buf = ByteBuffer.wrap(data)
    buf.order(ByteOrder.LITTLE_ENDIAN)

    val isElf = data[0] == 0x7F.toByte() && data[1] == 'E'.code.toByte() && data[2] == 'L'.code.toByte() && data[3] == 'F'.code.toByte()
    if (!isElf) return Quad(data, emptyList(), emptyList(), emptyList())

    val is64 = data[4] == 2.toByte()
    val sections = mutableListOf<SectionEntry>()
    val symbols = mutableListOf<SymbolEntry>()
    val relocs = mutableListOf<RelocEntry>()

    if (is64) {
        if (data.size < 64) return Quad(data, emptyList(), emptyList(), emptyList())
        val shOff = buf.getLong(40)
        val shNum = buf.getShort(60).toInt() and 0xFFFF
        val shEntSize = buf.getShort(58).toInt() and 0xFFFF
        val shStrndx = buf.getShort(62).toInt() and 0xFFFF

        if (shOff > 0 && shNum > 0 && shOff + shNum * shEntSize <= data.size.toLong()) {
            // Read string table
            val strTabOff = shOff + shStrndx * shEntSize
            val strOffset = buf.getLong(strTabOff.toInt() + 24)
            val strSize = buf.getLong(strTabOff.toInt() + 32).toInt()

            for (i in 0 until minOf(shNum, 200)) {
                val off = (shOff + i * shEntSize).toInt()
                if (off + shEntSize > data.size) break
                val nameIdx = buf.getInt(off)
                val type = buf.getInt(off + 4)
                val flags = buf.getLong(off + 8)
                val addr = buf.getLong(off + 16)
                val offset = buf.getLong(off + 24)
                val size = buf.getLong(off + 32)

                val name = if (nameIdx in 0 until strSize && strOffset + nameIdx < data.size.toLong()) {
                    val end = data.indexOf(0, (strOffset + nameIdx).toInt())
                    if (end > 0) String(data, (strOffset + nameIdx).toInt(), end - (strOffset + nameIdx).toInt()) else "str_$nameIdx"
                } else "str_$nameIdx"

                sections.add(SectionEntry(name, type, flags, offset, size, addr))

                // Parse symbols
                if (type == 2 || type == 11) { // SHT_SYMTAB or SHT_DYNSYM
                    val symOff = buf.getLong(off + 24)
                    val symEntSize = buf.getLong(off + 56).toInt()
                    val symCount = if (symEntSize > 0) (size / symEntSize).toInt() else 0
                    val symStrOff = buf.getLong(off + 40) // sh_link -> string table
                    val symStrOffset = buf.getLong(symStrOff.toInt() + 24)
                    val symStrSize = buf.getLong(symStrOff.toInt() + 32).toInt()

                    for (j in 1 until minOf(symCount, 10000)) {
                        val sOff = (symOff + j * symEntSize).toInt()
                        if (sOff + 24 > data.size) break
                        val sName = buf.getInt(sOff)
                        val sInfo = buf.get(sOff + 4).toInt() and 0xFF
                        val sShndx = buf.getShort(sOff + 6).toInt() and 0xFFFF
                        val sValue = buf.getLong(sOff + 8)
                        val sSize = buf.getLong(sOff + 16)

                        val sNameStr = if (sName in 0 until symStrSize && symStrOffset + sName < data.size.toLong()) {
                            val end = data.indexOf(0, (symStrOffset + sName).toInt())
                            if (end > 0) String(data, (symStrOffset + sName).toInt(), end - (symStrOffset + sName).toInt()) else ""
                        } else ""

                        if (sNameStr.isNotEmpty()) {
                            symbols.add(SymbolEntry(sNameStr, sValue, sSize, sInfo, sShndx, name))
                        }
                    }
                }

                // Parse relocations
                if (type == 4 || type == 9) { // SHT_RELA or SHT_REL
                    val relOff = buf.getLong(off + 24)
                    val relEntSize = buf.getLong(off + 56).toInt()
                    val relCount = if (relEntSize > 0) (size / relEntSize).toInt() else 0

                    for (j in 0 until minOf(relCount, 10000)) {
                        val rOff = (relOff + j * relEntSize).toInt()
                        if (rOff + 8 > data.size) break
                        val rOffset = buf.getLong(rOff)
                        val rInfo = buf.getLong(rOff + 8)
                        val rAddend = if (type == 4 && rOff + 16 <= data.size) buf.getLong(rOff + 16) else 0L

                        val rType = (rInfo and 0xFFFFFFFFL).toInt()
                        val rSym = (rInfo shr 32).toInt()

                        val symName = if (rSym < symbols.size) symbols[rSym].name else "sym_$rSym"
                        relocs.add(RelocEntry(rOffset, rInfo, rAddend, rType, symName))
                    }
                }
            }
        }
    } else {
        // ELF32
        val shOff = buf.getInt(32).toLong() and 0xFFFFFFFFL
        val shNum = buf.getShort(48).toInt() and 0xFFFF
        val shEntSize = buf.getShort(46).toInt() and 0xFFFF
        val shStrndx = buf.getShort(50).toInt() and 0xFFFF

        if (shOff > 0 && shNum > 0 && shOff + shNum * shEntSize <= data.size.toLong()) {
            val strTabOff = shOff + shStrndx * shEntSize
            val strOffset = buf.getInt(strTabOff.toInt() + 16).toLong() and 0xFFFFFFFFL
            val strSize = buf.getInt(strTabOff.toInt() + 20).toInt()

            for (i in 0 until minOf(shNum, 200)) {
                val off = (shOff + i * shEntSize).toInt()
                if (off + shEntSize > data.size) break
                val nameIdx = buf.getInt(off)
                val type = buf.getInt(off + 4)
                val flags = buf.getInt(off + 8).toLong() and 0xFFFFFFFFL
                val addr = buf.getInt(off + 12).toLong() and 0xFFFFFFFFL
                val offset = buf.getInt(off + 16).toLong() and 0xFFFFFFFFL
                val size = buf.getInt(off + 20).toLong() and 0xFFFFFFFFL

                val name = if (nameIdx in 0 until strSize && strOffset + nameIdx < data.size.toLong()) {
                    val end = data.indexOf(0, (strOffset + nameIdx).toInt())
                    if (end > 0) String(data, (strOffset + nameIdx).toInt(), end - (strOffset + nameIdx).toInt()) else "str_$nameIdx"
                } else "str_$nameIdx"

                sections.add(SectionEntry(name, type, flags, offset, size, addr))

                if (type == 2) { // SHT_SYMTAB
                    val symOff = buf.getInt(off + 16).toLong() and 0xFFFFFFFFL
                    val symEntSize = buf.getInt(off + 36)
                    val symCount = if (symEntSize > 0) (size / symEntSize).toInt() else 0
                    val symStrOff = buf.getInt(off + 24).toLong() and 0xFFFFFFFFL
                    val symStrOffset = buf.getInt(symStrOff.toInt() + 16).toLong() and 0xFFFFFFFFL
                    val symStrSize = buf.getInt(symStrOff.toInt() + 20)

                    for (j in 1 until minOf(symCount, 10000)) {
                        val sOff = (symOff + j * symEntSize).toInt()
                        if (sOff + 16 > data.size) break
                        val sName = buf.getInt(sOff)
                        val sValue = buf.getInt(sOff + 4).toLong() and 0xFFFFFFFFL
                        val sSize = buf.getInt(sOff + 8).toLong() and 0xFFFFFFFFL
                        val sInfo = buf.get(sOff + 12).toInt() and 0xFF
                        val sShndx = buf.getShort(sOff + 14).toInt() and 0xFFFF

                        val sNameStr = if (sName in 0 until symStrSize && symStrOffset + sName < data.size.toLong()) {
                            val end = data.indexOf(0, (symStrOffset + sName).toInt())
                            if (end > 0) String(data, (symStrOffset + sName).toInt(), end - (symStrOffset + sName).toInt()) else ""
                        } else ""

                        if (sNameStr.isNotEmpty()) {
                            symbols.add(SymbolEntry(sNameStr, sValue, sSize, sInfo, sShndx, name))
                        }
                    }
                }
            }
        }
    }

    return Quad(data, sections, symbols, relocs)
}

// ─── Patch Functions ───
private fun patchNop(data: ByteArray, path: String, target: String, count: Int): List<String> {
    val result = mutableListOf<String>()
    val file = File(path)
    if (!file.exists()) return listOf("[-] File not found: $path")

    // Resolve offset from hex or symbol name
    val offset = try {
        target.removePrefix("0x").removePrefix("0X").toLong(16)
    } catch (e: Exception) { return listOf("[-] Invalid offset: $target") }

    if (offset + count > data.size) return listOf("[-] Exceeds file size")

    val backup = File(path + ".bak")
    backup.writeBytes(data)

    // Detect NOP pattern
    val nop = if (data.size > 4 && data[4] == 2.toByte()) {
        byteArrayOf(0x1F.toByte(), 0x20.toByte(), 0x03.toByte(), 0xD5.toByte()) // ARM64 NOP
    } else {
        byteArrayOf(0x00, 0x00, 0x00, 0x00) // ARM/x86 NOP
    }

    for (i in 0 until count step 4) {
        val len = minOf(4, count - i)
        System.arraycopy(nop, 0, data, (offset + i).toInt(), len)
    }
    file.writeBytes(data)
    result.add("[+] NOP'd $count bytes at 0x${String.format("%016X", offset)}")
    result.add("[+] NOP pattern: ${nop.joinToString(" ") { String.format("%02X", it) }}")
    result.add("[+] Backup: ${backup.absolutePath}")
    return result
}

private fun patchBytesAt(data: ByteArray, path: String, offsetStr: String, bytesStr: String): List<String> {
    val result = mutableListOf<String>()
    val file = File(path)
    if (!file.exists()) return listOf("[-] File not found: $path")

    val offset = try { offsetStr.removePrefix("0x").removePrefix("0X").toLong(16) } catch (e: Exception) { return listOf("[-] Invalid offset: $offsetStr") }
    val bytes = try { bytesStr.trim().split("\\s+".toRegex()).map { it.toInt(16).toByte() }.toByteArray() } catch (e: Exception) { return listOf("[-] Invalid hex bytes: $bytesStr") }

    if (offset + bytes.size > data.size) return listOf("[-] Exceeds file size")

    val backup = File(path + ".bak")
    backup.writeBytes(data)

    val original = data.sliceArray(offset.toInt() until (offset + bytes.size).toInt())
    System.arraycopy(bytes, 0, data, offset.toInt(), bytes.size)
    file.writeBytes(data)

    result.add("[+] Patched ${bytes.size} bytes at 0x${String.format("%016X", offset)}")
    result.add("[+] Original: ${original.joinToString(" ") { String.format("%02X", it) }}")
    result.add("[+] New:      ${bytes.joinToString(" ") { String.format("%02X", it) }}")
    result.add("[+] Backup: ${backup.absolutePath}")
    return result
}

private fun searchReplaceHex(data: ByteArray, path: String, searchStr: String, replaceStr: String): List<String> {
    val result = mutableListOf<String>()
    val file = File(path)
    if (!file.exists()) return listOf("[-] File not found: $path")

    val search = try { searchStr.trim().split("\\s+".toRegex()).map { it.toInt(16).toByte() }.toByteArray() } catch (e: Exception) { return listOf("[-] Invalid search hex") }
    val replace = try { replaceStr.trim().split("\\s+".toRegex()).map { it.toInt(16).toByte() }.toByteArray() } catch (e: Exception) { return listOf("[-] Invalid replace hex") }

    if (search.size != replace.size) return listOf("[-] Search and replace must be same length")

    val backup = File(path + ".bak")
    backup.writeBytes(data)

    var count = 0
    var idx = 0
    while (idx <= data.size - search.size) {
        if (data.sliceArray(idx until idx + search.size).contentEquals(search)) {
            System.arraycopy(replace, 0, data, idx, replace.size)
            result.add("[+] Replaced at 0x${String.format("%016X", idx.toLong())}")
            count++
            idx += search.size
        } else {
            idx++
        }
    }

    file.writeBytes(data)
    result.add(0, "[+] Found & replaced $count occurrences")
    result.add("[+] Backup: ${backup.absolutePath}")
    return result
}

private fun nopSection(data: ByteArray, path: String, section: SectionEntry): List<String> {
    val result = mutableListOf<String>()
    val file = File(path)
    if (!file.exists()) return listOf("[-] File not found: $path")

    val offset = section.offset.toInt()
    val size = section.size.toInt()
    if (offset + size > data.size) return listOf("[-] Section exceeds file size")

    val backup = File(path + ".bak")
    backup.writeBytes(data)

    val nop = if (data.size > 4 && data[4] == 2.toByte()) {
        byteArrayOf(0x1F.toByte(), 0x20.toByte(), 0x03.toByte(), 0xD5.toByte())
    } else {
        byteArrayOf(0x00, 0x00, 0x00, 0x00)
    }

    for (i in 0 until size step 4) {
        val len = minOf(4, size - i)
        System.arraycopy(nop, 0, data, offset + i, len)
    }
    file.writeBytes(data)

    result.add("[+] NOP'd section ${section.name}")
    result.add("[+] Offset: 0x${String.format("%016X", section.offset)}")
    result.add("[+] Size: $size bytes")
    result.add("[+] NOP'd ${size / 4} instructions")
    result.add("[+] Backup: ${backup.absolutePath}")
    return result
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
