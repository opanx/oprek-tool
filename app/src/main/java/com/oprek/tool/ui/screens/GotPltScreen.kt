package com.oprek.tool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

data class GotPltEntry(val index: Int, val type: String, val address: Long, val value: Long, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GotPltScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(listOf<GotPltEntry>()) }
    var status by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }

    fun loadFromCache() {
        val f = context.cacheDir.listFiles()?.filter { it.isFile && it.length() > 0 }?.maxByOrNull { it.lastModified() }
        if (f != null) {
            fileName = f.name
            scope.launch(Dispatchers.IO) {
                try {
                    val data = f.readBytes()
                    if (data.size < 40) { withContext(Dispatchers.Main) { status = "File too small" }; return@launch }
                    if (data[0] != 0x7F.toByte() || data[1] != 'E'.code.toByte()) {
                        withContext(Dispatchers.Main) { status = "Not an ELF file" }; return@launch
                    }
                    val is64 = data[4] == 2.toByte()
                    val le = data[5] == 1.toByte()
                    val result = mutableListOf<GotPltEntry>()
                    var idx = 0

                    if (is64) {
                        // ELF64: find .got and .plt sections
                        val shoff = readU64(data, 0x28, le)
                        val shnum = readU16(data, 0x3C, le).toInt()
                        val shstrndx = readU16(data, 0x3E, le).toInt()
                        val shentsize = readU16(data, 0x3A, le).toInt()

                        // Get string table
                        val strSecOff = (shoff + shstrndx.toLong() * shentsize).toInt()
                        val strTabOffset = readU64(data, strSecOff + 0x18, le).toInt()
                        val strTabSize = readU64(data, strSecOff + 0x20, le).toInt()
                        val strTab = if (strTabOffset + strTabSize <= data.size) data.sliceArray(strTabOffset until (strTabOffset + strTabSize)) else byteArrayOf()

                        for (i in 0 until shnum) {
                            val secOff = shoff + i.toLong() * shentsize
                            val shName = readU32(data, secOff.toInt(), le).toInt()
                            val shType = readU32(data, (secOff + 4).toInt(), le).toInt()
                            val shAddr = readU64(data, (secOff + 0x10, le)
                            val shOffset = readU64(data, (secOff + 0x18, le)
                            val shSize = readU64(data, (secOff + 0x20, le)

                            val secName = if (shName < strTab.size) {
                                val end = strTab.indexOf(0.toByte(), shName).let { if (it < 0) strTab.size else it }
                                String(strTab.sliceArray(shName until end))
                            } else ""

                            if (secName == ".got" || secName == ".got.plt") {
                                val entrySize = 8
                                val count = (shSize / entrySize).toInt()
                                for (j in 0 until count.coerceAtMost(500)) {
                                    val off = (shOffset + j.toLong() * entrySize).toInt()
                                    if (off + 8 <= data.size) {
                                        val addr = shAddr + j.toLong() * entrySize
                                        val val_ = readU64(data, off, le)
                                        result.add(GotPltEntry(idx++, "GOT", addr, val_, secName))
                                    }
                                }
                            }
                            if (secName == ".plt" || secName == ".plt.got") {
                                val stubSize = if (is64) 16 else 12
                                val count = (shSize / stubSize).toInt()
                                for (j in 0 until count.coerceAtMost(200)) {
                                    val addr = shAddr + j.toLong() * stubSize
                                    result.add(GotPltEntry(idx++, "PLT", addr, 0, "plt_stub_$j"))
                                }
                            }
                        }
                    } else {
                        // ELF32: similar but 32-bit offsets
                        val shoff = readU32(data, 0x20, le).toLong()
                        val shnum = readU16(data, 0x30, le).toInt()
                        val shstrndx = readU16(data, 0x32, le).toInt()
                        val shentsize = readU16(data, 0x2E, le).toInt()

                        val strSecOff = (shoff + shstrndx.toLong() * shentsize).toInt()
                        val strTabOffset = readU32(data, strSecOff + 0x10, le).toLong()
                        val strTabSize = readU32(data, strSecOff + 0x14, le).toLong()
                        val strTab = if (strTabOffset + strTabSize <= data.size) data.sliceArray(strTabOffset until (strTabOffset + strTabSize)) else byteArrayOf()

                        for (i in 0 until shnum) {
                            val secOff = shoff + i.toLong() * shentsize
                            val shName = readU32(data, secOff.toInt(), le).toInt()
                            val shType = readU32(data, (secOff + 4).toInt(), le).toInt()
                            val shAddr = readU32(data, (secOff + 0xC).toInt(), le).toLong()
                            val shOffset = readU32(data, (secOff + 0x10).toInt(), le).toLong()
                            val shSize = readU32(data, (secOff + 0x14).toInt(), le).toLong()

                            val secName = if (shName < strTab.size) {
                                val end = strTab.indexOf(0.toByte(), shName).let { if (it < 0) strTab.size else it }
                                String(strTab.sliceArray(shName until end))
                            } else ""

                            if (secName == ".got" || secName == ".got.plt") {
                                for (j in 0 until (shSize / 4).toInt().coerceAtMost(500)) {
                                    val off = (shOffset + j.toLong() * 4).toInt()
                                    if (off + 4 <= data.size) {
                                        val addr = shAddr + j.toLong() * 4
                                        val val_ = readU32(data, off, le).toLong()
                                        result.add(GotPltEntry(idx++, "GOT", addr, val_, secName))
                                    }
                                }
                            }
                            if (secName == ".plt") {
                                for (j in 0 until (shSize / 12).toInt().coerceAtMost(200)) {
                                    val addr = shAddr + j.toLong() * 12
                                    result.add(GotPltEntry(idx++, "PLT", addr, 0, "plt_stub_$j"))
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        entries = result
                        status = "Found ${result.size} GOT/PLT entries (${result.count { it.type == "GOT" }} GOT, ${result.count { it.type == "PLT" }} PLT)"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { status = "Error: ${e.message}" }
                }
            }
        } else {
            status = "No file loaded. Open a file first from Home."
        }
    }

    LaunchedEffect(Unit) { loadFromCache() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 GOT / PLT", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { loadFromCache() }) { Icon(Icons.Default.Refresh, "Reload") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Header info
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📊 GOT/PLT Parser", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        if (fileName.isNotEmpty()) Text(fileName, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(status, color = AccentOrange, fontSize = 11.sp)
                    if (entries.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("GOT: ${entries.count { it.type == "GOT" }}", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("PLT: ${entries.count { it.type == "PLT" }}", color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Entries list
            if (entries.isEmpty() && status.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(status, color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                    itemsIndexed(entries) { _, e ->
                        val bgColor = if (e.type == "GOT") AccentGreen.copy(alpha = 0.08f) else AccentPurple.copy(alpha = 0.08f)
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(6.dp)) {
                            Row(Modifier.padding(6.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                Text("[${e.index}] ", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("${e.type} ", color = if (e.type == "GOT") AccentGreen else AccentPurple, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("0x${"%08X".format(e.address)} ", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                if (e.value != 0L) Text("= 0x${"%08X".format(e.value)} ", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(e.name, color = AccentOrange, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readU32(b: ByteArray, off: Int, le: Boolean): UInt {
    return if (le) {
        (b[off].toInt() and 0xFF).toUInt() or ((b[off + 1].toInt() and 0xFF).toUInt() shl 8) or
                ((b[off + 2].toInt() and 0xFF).toUInt() shl 16) or ((b[off + 3].toInt() and 0xFF).toUInt() shl 24)
    } else {
        ((b[off].toInt() and 0xFF).toUInt() shl 24) or ((b[off + 1].toInt() and 0xFF).toUInt() shl 16) or
                ((b[off + 2].toInt() and 0xFF).toUInt() shl 8) or (b[off + 3].toInt() and 0xFF).toUInt()
    }
}

private fun readU16(b: ByteArray, off: Int, le: Boolean): Int {
    return if (le) (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    else ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
}

private fun readU64(b: ByteArray, off: Int, le: Boolean): Long {
    return if (le) {
        var v = 0L
        for (i in 0..7) v = v or ((b[off + i].toLong() and 0xFF) shl (i * 8))
        v
    } else {
        var v = 0L
        for (i in 0..7) v = v or ((b[off + i].toLong() and 0xFF) shl ((7 - i) * 8))
        v
    }
}
