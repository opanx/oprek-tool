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

data class GotEntry(val index: Int, val type: String, val address: Long, val value: Long, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GotPltScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(listOf<GotEntry>()) }
    var status by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }

    fun loadAndParse() {
        val f = context.cacheDir.listFiles()?.filter { it.isFile && it.length() > 40 }
            ?.maxByOrNull { it.lastModified() }
        if (f == null) { status = "No file. Open from Home first."; return }
        fileName = f.name
        scope.launch(Dispatchers.IO) {
            try {
                val data = f.readBytes()
                if (data[0] != 0x7F.toByte() || data[1] != 0x45.toByte()) {
                    withContext(Dispatchers.Main) { status = "Not ELF"; return@withContext }
                }
                val is64 = data[4] == 2.toByte()
                val le = data[5] == 1.toByte()
                val res = mutableListOf<GotEntry>()
                var idx = 0

                if (is64) {
                    // ELF64
                    val shOff = readU64(data, 0x28, le).toInt()
                    val shNum = readU16(data, 0x3C, le)
                    val shStrNdx = readU16(data, 0x3E, le)
                    val shEntSz = readU16(data, 0x3A, le)

                    // String table section header
                    val strSec = shOff + shStrNdx * shEntSz
                    val strOff = readU64(data, strSec + 0x18, le).toInt()
                    val strSz = readU64(data, strSec + 0x20, le).toInt()

                    for (i in 0 until shNum) {
                        val s = shOff + i * shEntSz
                        if (s + 0x28 > data.size) break
                        val nameIdx = readU32(data, s, le).toInt()
                        val shType = readU32(data, s + 4, le).toInt()
                        val shAddr = readU64(data, s + 0x10, le)
                        val shOffset = readU64(data, s + 0x18, le).toInt()
                        val shSize = readU64(data, s + 0x20, le).toInt()

                        val name = if (nameIdx in 0 until strOff + strSz) {
                            readString(data, strOff + nameIdx)
                        } else ""

                        if (name == ".got" || name == ".got.plt") {
                            val count = (shSize / 8).coerceAtMost(500)
                            for (j in 0 until count) {
                                val off = shOffset + j * 8
                                if (off + 8 <= data.size) {
                                    res.add(GotEntry(idx++, "GOT", shAddr + j * 8L, readU64(data, off, le), name))
                                }
                            }
                        }
                        if (name == ".plt" || name == ".plt.got") {
                            val stubSz = 16
                            val count = (shSize / stubSz).coerceAtMost(200)
                            for (j in 0 until count) {
                                res.add(GotEntry(idx++, "PLT", shAddr + j * stubSz.toLong(), 0, name))
                            }
                        }
                    }
                } else {
                    // ELF32
                    val shOff = readU32(data, 0x20, le).toInt()
                    val shNum = readU16(data, 0x30, le)
                    val shStrNdx = readU16(data, 0x32, le)
                    val shEntSz = readU16(data, 0x2E, le)

                    val strSec = shOff + shStrNdx * shEntSz
                    val strOff = readU32(data, strSec + 0x10, le).toInt()
                    val strSz = readU32(data, strSec + 0x14, le).toInt()

                    for (i in 0 until shNum) {
                        val s = shOff + i * shEntSz
                        if (s + 0x28 > data.size) break
                        val nameIdx = readU32(data, s, le).toInt()
                        val shAddr = readU32(data, s + 0xC, le).toLong()
                        val shOffset = readU32(data, s + 0x10, le).toInt()
                        val shSize = readU32(data, s + 0x14, le).toInt()

                        val name = if (nameIdx in 0 until strOff + strSz) {
                            readString(data, strOff + nameIdx)
                        } else ""

                        if (name == ".got" || name == ".got.plt") {
                            val count = (shSize / 4).coerceAtMost(500)
                            for (j in 0 until count) {
                                val off = shOffset + j * 4
                                if (off + 4 <= data.size) {
                                    res.add(GotEntry(idx++, "GOT", shAddr + j * 4L, readU32(data, off, le).toLong(), name))
                                }
                            }
                        }
                        if (name == ".plt") {
                            val count = (shSize / 12).coerceAtMost(200)
                            for (j in 0 until count) {
                                res.add(GotEntry(idx++, "PLT", shAddr + j * 12L, 0, name))
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    entries = res
                    status = "Found ${res.size} entries (${res.count { it.type == "GOT" }} GOT, ${res.count { it.type == "PLT" }} PLT)"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Error: ${e.message}" }
            }
        }
    }

    LaunchedEffect(Unit) { loadAndParse() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 GOT / PLT", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { entries = emptyList(); loadAndParse() }) { Icon(Icons.Default.Refresh, "Reload") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("📊 GOT/PLT Parser", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text(fileName, color = AccentGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
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
            if (entries.isEmpty() && status.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                        Text(status, color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                    itemsIndexed(entries) { _, e ->
                        val bg = if (e.type == "GOT") AccentGreen.copy(alpha = 0.08f) else AccentPurple.copy(alpha = 0.08f)
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(6.dp)) {
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
    if (off + 4 > b.size) return 0u
    return if (le) {
        (b[off].toInt() and 0xFF).toUInt() or ((b[off + 1].toInt() and 0xFF).toUInt() shl 8) or
                ((b[off + 2].toInt() and 0xFF).toUInt() shl 16) or ((b[off + 3].toInt() and 0xFF).toUInt() shl 24)
    } else {
        ((b[off].toInt() and 0xFF).toUInt() shl 24) or ((b[off + 1].toInt() and 0xFF).toUInt() shl 16) or
                ((b[off + 2].toInt() and 0xFF).toUInt() shl 8) or (b[off + 3].toInt() and 0xFF).toUInt()
    }
}

private fun readU16(b: ByteArray, off: Int, le: Boolean): Int {
    if (off + 2 > b.size) return 0
    return if (le) (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    else ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
}

private fun readU64(b: ByteArray, off: Int, le: Boolean): Long {
    if (off + 8 > b.size) return 0L
    return if (le) {
        var v = 0L; for (i in 0..7) v = v or ((b[off + i].toLong() and 0xFF) shl (i * 8)); v
    } else {
        var v = 0L; for (i in 0..7) v = v or ((b[off + i].toLong() and 0xFF) shl ((7 - i) * 8)); v
    }
}

private fun readString(b: ByteArray, off: Int): String {
    val sb = StringBuilder()
    var i = off
    while (i < b.size && b[i] != 0.toByte()) {
        sb.append(b[i].toInt().toChar())
        i++
    }
    return sb.toString()
}
