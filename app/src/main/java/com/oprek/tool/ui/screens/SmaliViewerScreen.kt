package com.oprek.tool.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Smali/Dalvik bytecode viewer.
 * Parses DEX files and shows instruction sequences per method.
 * 100% native Kotlin — no external tools needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmaliViewerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<File?>(null) }
    var output by remember { mutableStateOf(listOf<String>()) }
    var isBusy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        scope.launch(Dispatchers.IO) {
            isBusy = true
            output = listOf("[*] Loading DEX/ELF...")
            try {
                val name = uri.lastPathSegment ?: "unknown"
                val cache = File(context.cacheDir, "smali_$name")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cache).use { out -> input.copyTo(out) }
                }
                val data = cache.readBytes()
                val result = mutableListOf<String>()
                result.add("[+] File: $name (${fmtSmali(data.size.toLong())})")
                if (data.size >= 12 && data[0] == 0x64.toByte() && data[1] == 0x65.toByte() && data[2] == 0x78.toByte() && data[3] == 0x0A.toByte()) {
                    result.addAll(parseDexSmali(data))
                } else {
                    // Try as raw — scan for DEX signatures
                    result.add("[*] Not a DEX file, scanning for embedded DEX...")
                    var found = 0
                    for (i in 0 until data.size - 4) {
                        if (data[i] == 0x64.toByte() && data[i + 1] == 0x65.toByte() && data[i + 2] == 0x78.toByte() && data[i + 3] == 0x0A.toByte()) {
                            if (i + 112 <= data.size) {
                                val sz = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(i + 32)
                                if (sz in 100..100_000_000 && i + sz <= data.size) {
                                    result.add("[+] Embedded DEX @ 0x${String.format("%06X", i)} (${fmtSmali(sz.toLong())})")
                                    result.addAll(parseDexSmali(data.sliceArray(i until i + sz)))
                                    found++
                                    if (found >= 3) break
                                }
                            }
                        }
                    }
                    if (found == 0) result.add("[-] No DEX files found in binary")
                }
                loaded = cache
                withContext(Dispatchers.Main) { output = result; isBusy = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { output = listOf("[-] Error: ${e.message}"); isBusy = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📱 Smali Viewer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AccentGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // File card
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = AccentPurple)
                    Spacer(Modifier.width(8.dp))
                    Text(if (loaded != null) loaded!!.name else "No file loaded", color = if (loaded != null) TextPrimary else TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("Open", fontSize = 12.sp) }
                }
            }
            if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp), color = AccentPurple)
            if (output.isEmpty() && !isBusy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📱", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Smali Viewer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentPurple)
                        Text("Parse DEX bytecode — class/method/field listings", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                    items(output) { line ->
                        Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = when {
                                line.startsWith("[+]") -> AccentGreen
                                line.startsWith("[-]") -> AccentRed
                                line.startsWith("[!]") -> AccentOrange
                                line.startsWith("[*]") -> AccentCyan
                                line.contains("class") || line.contains(".method") -> AccentPurple
                                else -> TextPrimary
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun parseDexSmali(data: ByteArray): List<String> {
    val out = mutableListOf<String>()
    if (data.size < 112) return out
    val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    val fileSize = buf.getInt(32)
    val headerSize = buf.getInt(36)
    val stringCount = buf.getInt(56)
    val typeCount = buf.getInt(60)
    val protoCount = buf.getInt(64)
    val fieldCount = buf.getInt(68)
    val methodCount = buf.getInt(80)
    val classCount = buf.getInt(96)

    out.add("[+] DEX Header:")
    out.add("    File size: ${fmtSmali(fileSize.toLong())}")
    out.add("    Header size: $headerSize")
    out.add("    Strings: $stringCount")
    out.add("    Types: $typeCount")
    out.add("    Prototypes: $protoCount")
    out.add("    Fields: $fieldCount")
    out.add("    Methods: $methodCount")
    out.add("    Classes: $classCount")
    out.add("")

    // Parse string IDs
    val strOff = buf.getInt(52)
    if (strOff > 0 && strOff < data.size) {
        out.add("[+] String IDs (${minOf(stringCount, 200)} / $stringCount shown):")
        for (i in 0 until minOf(stringCount, 200)) {
            val off = strOff + i * 4
            if (off + 4 > data.size) break
            val sOff = buf.getInt(off)
            if (sOff > 0 && sOff < data.size) {
                val utf16Size = data[sOff].toInt() and 0xFF
                val str = readDexMutf8(data, sOff + 1)
                if (str.isNotEmpty()) {
                    out.add("    [$i] \"$str\"")
                }
            }
        }
        if (stringCount > 200) out.add("    ... and ${stringCount - 200} more")
    }
    out.add("")

    // Parse type IDs
    val typeOff = buf.getInt(56 + 4) // type_ids_off at offset 60
    if (typeOff > 0 && typeOff < data.size) {
        out.add("[+] Type IDs (${minOf(typeCount, 100)} / $typeCount shown):")
        for (i in 0 until minOf(typeCount, 100)) {
            val off = typeOff + i * 4
            if (off + 4 > data.size) break
            val descIdx = buf.getInt(off)
            if (descIdx >= 0 && descIdx < stringCount) {
                // Look up the string
                val sOffPtr = strOff + descIdx * 4
                if (sOffPtr + 4 <= data.size) {
                    val sOff = buf.getInt(sOffPtr)
                    if (sOff > 0 && sOff < data.size) {
                        val desc = readDexMutf8(data, sOff + 1)
                        val kind = when {
                            desc.startsWith("L") -> "class"
                            desc.startsWith("[") -> "array"
                            else -> "primitive"
                        }
                        out.add("    [$i] $desc ($kind)")
                    }
                }
            }
        }
        if (typeCount > 100) out.add("    ... and ${typeCount - 100} more")
    }
    out.add("")

    // Parse method IDs
    val methodOff = buf.getInt(88 + 4) // method_ids_off at offset 92
    if (methodOff > 0 && methodOff < data.size && methodCount > 0) {
        out.add("[+] Method IDs (${minOf(methodCount, 100)} / $methodCount shown):")
        for (i in 0 until minOf(methodCount, 100)) {
            val off = methodOff + i * 8
            if (off + 8 > data.size) break
            val classIdx = buf.getShort(off).toInt() and 0xFFFF
            val protoIdx = buf.getShort(off + 2).toInt() and 0xFFFF
            val nameIdx = buf.getShort(off + 4).toInt() and 0xFFFF

            // Resolve class name
            val className = if (classIdx < typeCount && typeOff > 0) {
                val tdOff = typeOff + classIdx * 4
                if (tdOff + 4 <= data.size) {
                    val descIdx = buf.getInt(tdOff)
                    val sPtr = strOff + descIdx * 4
                    if (sPtr + 4 <= data.size) {
                        val sOff = buf.getInt(sPtr)
                        if (sOff > 0 && sOff < data.size) readDexMutf8(data, sOff + 1) else "?"
                    } else "?"
                } else "?"
            } else "?"

            // Resolve method name
            val methodName = if (nameIdx < stringCount && strOff > 0) {
                val sPtr = strOff + nameIdx * 4
                if (sPtr + 4 <= data.size) {
                    val sOff = buf.getInt(sPtr)
                    if (sOff > 0 && sOff < data.size) readDexMutf8(data, sOff + 1) else "?"
                } else "?"
            } else "?"

            val shortClass = className.removePrefix("L").removeSuffix(";").replace("/", ".")
            out.add("    [$i] $shortClass->$methodName")
        }
        if (methodCount > 100) out.add("    ... and ${methodCount - 100} more")
    }

    return out
}

/** Read MUTF-8 string from DEX */
private fun readDexMutf8(data: ByteArray, off: Int): String {
    if (off < 0 || off >= data.size) return ""
    val sb = StringBuilder()
    var i = off
    while (i < data.size && data[i] != 0.toByte()) {
        val b = data[i].toInt() and 0xFF
        if (b < 0x80) {
            sb.append(b.toChar())
        } else if (b in 0xC0..0xDF && i + 1 < data.size) {
            val b2 = data[i + 1].toInt() and 0x3F
            sb.append(((b and 0x1F) shl 6 or b2).toChar())
            i++
        } else if (b in 0xE0..0xEF && i + 2 < data.size) {
            val b2 = data[i + 1].toInt() and 0x3F
            val b3 = data[i + 2].toInt() and 0x3F
            sb.append(((b and 0x0F) shl 12 or (b2 shl 6) or b3).toChar())
            i += 2
        } else {
            sb.append('?')
        }
        i++
    }
    return sb.toString()
}

private fun fmtSmali(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1048576 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / 1048576.0)} MB"
}
