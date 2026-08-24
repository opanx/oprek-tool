@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalLayoutApi::class)
package com.oprek.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
// AutoDump v3 — Real IL2CPP dump pipeline
// Strategy A: Valid metadata → parse TypeDef/MethodDef/FieldDef → dump.cs
// Strategy B: Encrypted/missing metadata → raw dump for PC Il2CppDumper
// ═══════════════════════════════════════════════════════════════

data class GamePreset(
    val name: String,
    val pkg: String,
    val il2cppLib: String,
    val metaSearch: String,  // extra hint for metadata search
    val desc: String
)

private val gamePresets = listOf(
    GamePreset("MLBB", "com.mobile.legends", "libunity.so", "libil2cpp", "Unity IL2CPP — metadata usually encrypted at runtime"),
    GamePreset("FF MAX", "com.dts.freefiremax", "libil2cpp.so", "", "Garena Free Fire Max"),
    GamePreset("FF", "com.dts.freefireth", "libil2cpp.so", "", "Garena Free Fire"),
    GamePreset("PUBG", "com.tencent.ig", "libil2cpp.so", "", "PUBG Mobile"),
    GamePreset("PUBGM HD", "com.tencent.tmgp.pubgmhd", "libil2cpp.so", "", "PUBG Mobile HD"),
    GamePreset("Genshin", "com.miHoYo.GenshinImpact", "libil2cpp.so", "", "Genshin Impact"),
    GamePreset("BloodStrike", "com.proximabeta.mf.ussdk", "libil2cpp.so", "", "NetEase BloodStrike"),
    GamePreset("CODM", "com.garena.game.codm", "libil2cpp.so", "", "Call of Duty Mobile"),
    GamePreset("Brawl Stars", "com.supercell.brawlstars", "libil2cpp.so", "", "Supercell Brawl Stars"),
    GamePreset("Standoff 2", "com.axlebolt.standoff2", "libil2cpp.so", "", "Standoff 2"),
    GamePreset("Manual", "", "", "", "Enter package + lib name manually"),
)

private const val MAGIC_META = -559038737  // 0xFAB11BAF

// ═══════════════════════════════════════════════════════════════
// IL2CPP Metadata Parser (v29)
// ═══════════════════════════════════════════════════════════════

private class Il2CppMetadataParser(private val data: ByteArray) {
    private var lastResult: ParseResult? = null
    private var offset = 0
    private val bb: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    fun readInt(): Int { bb.position(offset); val v = bb.int; offset += 4; return v }
    fun readUInt(): Long = readInt().toLong() and 0xFFFFFFFFL
    fun readShort(): Int { bb.position(offset); val v = bb.short.toInt(); offset += 2; return v }
    fun readByte(): Int { val v = data[offset].toInt() and 0xFF; offset++; return v }
    fun seek(o: Int) { offset = o }
    fun remaining() = data.size - offset

    data class Il2CppTypeDefinition(
        val nameIndex: Int,
        val namespaceIndex: Int,
        val byvalTypeIndex: Int,
        val declaringTypeIndex: Int,
        val parentIndex: Int,
        val elementTypeIndex: Int,
        val methodStart: Int,
        val methodCount: Int,
        val fieldStart: Int,
        val fieldCount: Int,
        val eventStart: Int,
        val eventCount: Int,
        val propertyStart: Int,
        val propertyCount: Int,
        val nestedTypeStart: Int,
        val nestedTypeCount: Int,
        val interfacesStart: Int,
        val interfaceCount: Int,
        val vtableStart: Int,
        val vtableCount: Int,
        val interfacesStart2: Int,
        val interfaceCount2: Int,
        val flags: Int
    )

    data class Il2CppMethodDefinition(
        val nameIndex: Int,
        val declaringType: Int,
        val return_type: Int,
        val parameterStart: Int,
        val parameterCount: Short,
        val genericContainerIndex: Int,
        val methodIndex: Int,
        val invokerIndex: Int,
        val reversePInvokeWrapperIndex: Int,
        val rgctxStart: Int,
        val rgctxCount: Int,
        val token: Int
    )

    data class Il2CppFieldDefinition(
        val nameIndex: Int,
        val typeIndex: Int,
        val token: Int
    )

    data class Il2CppStringLiteral(
        val length: Int,
        val dataIndex: Int
    )

    data class ParseResult(
        val version: Int,
        val stringLiteralOffset: Long,
        val stringLiteralCount: Int,
        val stringLiteralDataOffset: Long,
        val typeDefOffset: Int,
        val typeDefCount: Int,
        val methodDefOffset: Int,
        val methodDefCount: Int,
        val fieldDefOffset: Int,
        val fieldDefCount: Int,
        val stringLiteralTableOffset: Long,
        val stringLiteralTableSize: Int,
        val stringTableOffset: Long,
        val stringTableSize: Int,
        val stringLiteralData: ByteArray,
        val stringTable: ByteArray,
        val typeDefinitions: List<Il2CppTypeDefinition>,
        val methodDefinitions: List<Il2CppMethodDefinition>,
        val fieldDefinitions: List<Il2CppFieldDefinition>,
        val stringLiterals: List<Il2CppStringLiteral>,
        val strings: Map<Int, String>
    )

    private fun readStringFromTable(table: ByteArray, index: Int): String {
        if (index < 0 || index >= table.size) return "?"
        val sb = StringBuilder()
        var i = index
        while (i < table.size) {
            val c = table[i].toInt() and 0xFF
            if (c == 0) break
            sb.append(c.toChar())
            i++
        }
        return sb.toString()
    }

    fun parse(): ParseResult? {
        if (data.size < 64) return null

        // Validate magic
        val magic = readInt()
        if (magic != MAGIC_META) return null

        // Read version
        val version = readInt()

        // Skip padding (4 bytes)
        val padding = readInt()

        // String literal table
        val stringLiteralOffset = readUInt()
        val stringLiteralCount = readInt()

        // Skip some fields
        val stringLiteralDataOffset = readUInt()

        // Type definitions
        val typeDefOffset = readInt()
        val typeDefCount = readInt()

        // Method definitions
        val methodDefOffset = readInt()
        val methodDefCount = readInt()

        // Field definitions
        val fieldDefOffset = readInt()
        val fieldDefCount = readInt()

        // Event definitions
        val eventDefOffset = readInt()
        val eventDefCount = readInt()

        // Property definitions
        val propertyDefOffset = readInt()
        val propertyDefCount = readInt()

        // Read string literal table
        val stringLiteralTableOffset = stringLiteralOffset
        seek(stringLiteralOffset.toInt())
        val stringLiteralTableSize = remaining()
        val stringLiteralTable = ByteArray(stringLiteralTableSize.coerceAtMost(1048576)) // max 1MB
        System.arraycopy(data, stringLiteralOffset.toInt(), stringLiteralTable, 0, stringLiteralTable.size)

        // Read string table (from stringLiteralDataOffset)
        val strDataOff = stringLiteralDataOffset.toInt()
        seek(strDataOff)
        val strTableSize = (data.size - strDataOff).coerceAtMost(4194304) // max 4MB
        val stringTable = ByteArray(strTableSize)
        System.arraycopy(data, strDataOff, stringTable, 0, strTableSize)

        // Parse TypeDefinitions (each is ~72 bytes for v29)
        val typeDefSize = 72
        val typeDefs = mutableListOf<Il2CppTypeDefinition>()
        for (i in 0 until typeDefCount.coerceAtMost(100000)) {
            val pos = typeDefOffset + i * typeDefSize
            if (pos + typeDefSize > data.size) break
            seek(pos)
            val td = Il2CppTypeDefinition(
                nameIndex = readInt(),
                namespaceIndex = readInt(),
                byvalTypeIndex = readInt(),
                declaringTypeIndex = readInt(),
                parentIndex = readInt(),
                elementTypeIndex = readInt(),
                methodStart = readInt(),
                methodCount = readShort(),
                fieldStart = readInt(),
                fieldCount = readShort(),
                eventStart = readInt(),
                eventCount = readShort(),
                propertyStart = readInt(),
                propertyCount = readShort(),
                nestedTypeStart = readInt(),
                nestedTypeCount = readShort(),
                interfacesStart = readInt(),
                interfaceCount = readShort(),
                vtableStart = readInt(),
                vtableCount = readShort(),
                interfacesStart2 = readInt(),
                interfaceCount2 = readShort(),
                flags = readInt()
            )
            typeDefs.add(td)
        }

        // Parse MethodDefinitions (each is ~40 bytes for v29)
        val methodDefSize = 40
        val methodDefs = mutableListOf<Il2CppMethodDefinition>()
        for (i in 0 until methodDefCount.coerceAtMost(500000)) {
            val pos = methodDefOffset + i * methodDefSize
            if (pos + methodDefSize > data.size) break
            seek(pos)
            val md = Il2CppMethodDefinition(
                nameIndex = readInt(),
                declaringType = readInt(),
                return_type = readInt(),
                parameterStart = readInt(),
                parameterCount = readShort().toShort(),
                genericContainerIndex = readInt(),
                methodIndex = readInt(),
                invokerIndex = readInt(),
                reversePInvokeWrapperIndex = readInt(),
                rgctxStart = readInt(),
                rgctxCount = readInt(),
                token = readInt()
            )
            methodDefs.add(md)
        }

        // Parse FieldDefinitions (each is ~12 bytes for v29)
        val fieldDefSize = 12
        val fieldDefs = mutableListOf<Il2CppFieldDefinition>()
        for (i in 0 until fieldDefCount.coerceAtMost(1000000)) {
            val pos = fieldDefOffset + i * fieldDefSize
            if (pos + fieldDefSize > data.size) break
            seek(pos)
            val fd = Il2CppFieldDefinition(
                nameIndex = readInt(),
                typeIndex = readInt(),
                token = readInt()
            )
            fieldDefs.add(fd)
        }

        // Build string lookup map from string table
        val strings = mutableMapOf<Int, String>()
        for (td in typeDefs) {
            if (td.nameIndex !in strings) strings[td.nameIndex] = readStringFromTable(stringTable, td.nameIndex)
            if (td.namespaceIndex !in strings) strings[td.namespaceIndex] = readStringFromTable(stringTable, td.namespaceIndex)
        }
        for (md in methodDefs) {
            if (md.nameIndex !in strings) strings[md.nameIndex] = readStringFromTable(stringTable, md.nameIndex)
        }
        for (fd in fieldDefs) {
            if (fd.nameIndex !in strings) strings[fd.nameIndex] = readStringFromTable(stringTable, fd.nameIndex)
        }

        // Parse string literals
        val stringLiterals = mutableListOf<Il2CppStringLiteral>()
        seek(stringLiteralOffset.toInt())
        for (i in 0 until stringLiteralCount.coerceAtMost(100000)) {
            if (remaining() < 8) break
            val sl = Il2CppStringLiteral(
                length = readInt(),
                dataIndex = readInt()
            )
            stringLiterals.add(sl)
        }

        val result = ParseResult(
            version = version,
            stringLiteralOffset = stringLiteralOffset,
            stringLiteralCount = stringLiteralCount,
            stringLiteralDataOffset = stringLiteralDataOffset,
            typeDefOffset = typeDefOffset,
            typeDefCount = typeDefCount,
            methodDefOffset = methodDefOffset,
            methodDefCount = methodDefCount,
            fieldDefOffset = fieldDefOffset,
            fieldDefCount = fieldDefCount,
            stringLiteralTableOffset = stringLiteralTableOffset,
            stringLiteralTableSize = stringLiteralTableSize,
            stringTableOffset = strDataOff.toLong(),
            stringTableSize = strTableSize,
            stringLiteralData = stringLiteralTable,
            stringTable = stringTable,
            typeDefinitions = typeDefs,
            methodDefinitions = methodDefs,
            fieldDefinitions = fieldDefs,
            stringLiterals = stringLiterals,
            strings = strings
        )
        lastResult = result
        return result
    }

    // Build dump.cs from parsed data
    fun generateDumpCs(
        pkg: String,
        lib: String,
        il2cppStart: Long,
        il2cppEnd: Long,
        il2cppSize: Long,
        metaStart: Long,
        ts: String,
        version: Int,
        strategyA: Boolean,
        extraStrings: List<Pair<Long, String>> = emptyList()
    ): String {
        val r = lastResult
        val sb = StringBuilder()

        sb.appendLine("// dump.cs — Generated by OprekTool AutoDump v3")
        sb.appendLine("// Package: $pkg | Lib: $lib")
        sb.appendLine("// Strategy: ${if (strategyA) "A (metadata parsed)" else "B (encrypted metadata)"}")
        sb.appendLine("// $lib: 0x${"%X".format(il2cppStart)} - 0x${"%X".format(il2cppEnd)} (${il2cppSize / 1024}KB)")
        sb.appendLine("// Metadata: 0x${"%X".format(metaStart)} (version $version)")
        sb.appendLine("// Date: $ts")
        sb.appendLine("")

        if (strategyA) {
            val rTypeDefs = r?.typeDefinitions ?: emptyList()
            val rMethodDefs = r?.methodDefinitions ?: emptyList()
            val rFieldDefs = r?.fieldDefinitions ?: emptyList()
            val typeDefOff = r?.typeDefOffset ?: 0
            val methodDefOff = r?.methodDefOffset ?: 0
            val fieldDefOff = r?.fieldDefOffset ?: 0

            // Emit TypeDefinitions
            sb.appendLine("// ============================================================")
            sb.appendLine("// TypeDefinitions (${rTypeDefs.size}) @ 0x${"%X".format(typeDefOff)}")
            sb.appendLine("// ============================================================")
            sb.appendLine("")
            for ((idx, td) in rTypeDefs.withIndex()) {
                val tdOffset = typeDefOff + idx * 72  // 72 bytes per TypeDef
                val name = r?.strings?.get(td.nameIndex) ?: "Type_$idx"
                val ns = r?.strings?.get(td.namespaceIndex) ?: ""
                val fqn = if (ns.isNotEmpty()) "$ns.$name" else name
                val parentName = r?.strings?.get(rTypeDefs.getOrNull(td.parentIndex.toInt())?.nameIndex ?: -1) ?: "System.Object"
                sb.appendLine("// ─── TypeDef #$idx @ 0x${"%X".format(tdOffset)} (name@0x${"%X".format(td.nameIndex)}) ───")
                sb.appendLine("public class $fqn : $parentName {")
                sb.appendLine("    // Flags: 0x${"%X".format(td.flags)} | byvalType: ${td.byvalTypeIndex} | parent: ${td.parentIndex}")
                sb.appendLine("    // Methods: ${td.methodCount} (start#${td.methodStart}) | Fields: ${td.fieldCount} (start#${td.fieldStart})")
                sb.appendLine("    // Events: ${td.eventCount} | Properties: ${td.propertyCount}")
                sb.appendLine("    // NestedTypes: ${td.nestedTypeCount} | Interfaces: ${td.interfaceCount} | VTable: ${td.vtableCount}")
                sb.appendLine("")

                // Emit methods for this type with hex offset
                for (mi in td.methodStart until td.methodStart + td.methodCount) {
                    if (mi < 0 || mi >= rMethodDefs.size) continue
                    val md = rMethodDefs[mi]
                    val mdOffset = methodDefOff + mi * 40  // 40 bytes per MethodDef
                    val mName = r?.strings?.get(md.nameIndex) ?: "Method_$mi"
                    val retName = r?.strings?.get(md.return_type) ?: "void"
                    val mToken = "0x${"%X".format(md.token)}"
                    sb.appendLine("    // Method #$mi @ 0x${"%X".format(mdOffset)} | token=$mToken | name@0x${"%X".format(md.nameIndex)}")
                    sb.appendLine("    // $retName $mName(params ${md.parameterCount})")
                    sb.appendLine("    // declaringType: ${md.declaringType} | methodIndex: ${md.methodIndex} | invokerIndex: ${md.invokerIndex}")
                    sb.appendLine("")
                }

                // Emit fields for this type with hex offset
                for (fi in td.fieldStart until td.fieldStart + td.fieldCount) {
                    if (fi < 0 || fi >= rFieldDefs.size) continue
                    val fd = rFieldDefs[fi]
                    val fdOffset = fieldDefOff + fi * 12  // 12 bytes per FieldDef
                    val fName = r?.strings?.get(fd.nameIndex) ?: "Field_$fi"
                    val fToken = "0x${"%X".format(fd.token)}"
                    sb.appendLine("    // Field #$fi @ 0x${"%X".format(fdOffset)} | token=$fToken | name@0x${"%X".format(fd.nameIndex)} type=${fd.typeIndex}")
                    sb.appendLine("    // $fName")
                }
                sb.appendLine("}")
                sb.appendLine("")
            }

            // Emit MethodDefinitions index (flat list with offsets)
            sb.appendLine("// ============================================================")
            sb.appendLine("// MethodDefinitions Index (${rMethodDefs.size}) @ 0x${"%X".format(methodDefOff)}")
            sb.appendLine("// ============================================================")
            sb.appendLine("")
            for ((idx, md) in rMethodDefs.take(2000).withIndex()) {
                val mdOffset = methodDefOff + idx * 40
                val mName = r?.strings?.get(md.nameIndex) ?: "?"
                val mToken = "0x${"%X".format(md.token)}"
                sb.appendLine("// Method #$idx @ 0x${"%X".format(mdOffset)} | $mToken | $mName (type=${md.declaringType}, params=${md.parameterCount}, idx=${md.methodIndex})")
            }
            if (rMethodDefs.size > 2000) sb.appendLine("// ... and ${rMethodDefs.size - 2000} more methods")
            sb.appendLine("")

            // Emit FieldDefinitions index (flat list with offsets)
            sb.appendLine("// ============================================================")
            sb.appendLine("// FieldDefinitions Index (${rFieldDefs.size}) @ 0x${"%X".format(fieldDefOff)}")
            sb.appendLine("// ============================================================")
            sb.appendLine("")
            for ((idx, fd) in rFieldDefs.take(5000).withIndex()) {
                val fdOffset = fieldDefOff + idx * 12
                val fName = r?.strings?.get(fd.nameIndex) ?: "?"
                val fToken = "0x${"%X".format(fd.token)}"
                sb.appendLine("// Field #$idx @ 0x${"%X".format(fdOffset)} | $fToken | $fName (type=${fd.typeIndex})")
            }
            if (rFieldDefs.size > 5000) sb.appendLine("// ... and ${rFieldDefs.size - 5000} more fields")
            sb.appendLine("")

            // Emit extracted strings
            if (extraStrings.isNotEmpty()) {
                sb.appendLine("// ============================================================")
                sb.appendLine("// Extracted Strings (${extraStrings.size})")
                sb.appendLine("// ============================================================")
                extraStrings.take(5000).forEach { (off, s) -> sb.appendLine("// 0x${"%08X".format(off)} $s") }
                if (extraStrings.size > 5000) sb.appendLine("// ... and ${extraStrings.size - 5000} more")
            }
        } else {
            // Strategy B — encrypted metadata
            sb.appendLine("// ============================================================")
            sb.appendLine("// ENCRYPTED METADATA — Raw dump for PC Il2CppDumper")
            sb.appendLine("// ============================================================")
            sb.appendLine("")
            sb.appendLine("// Metadata is encrypted at runtime — cannot parse locally.")
            sb.appendLine("// Steps:")
            sb.appendLine("//   1. Copy lib + meta from /sdcard/Download/OprekTool/dump/")
            sb.appendLine("//   2. Use Il2CppDumper on Windows/Linux/Mac:")
            sb.appendLine("//      il2cppdumper <lib> <metadata>")
            sb.appendLine("")
            if (extraStrings.isNotEmpty()) {
                sb.appendLine("// ============================================================")
                sb.appendLine("// Strings from lib binary (${extraStrings.size})")
                sb.appendLine("// ============================================================")
                extraStrings.take(5000).forEach { (off, s) -> sb.appendLine("// 0x${"%08X".format(off)} $s") }
                if (extraStrings.size > 5000) sb.appendLine("// ... and ${extraStrings.size - 5000} more")
            }
        }

        return sb.toString()
    }
}

// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDumpScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var selectedPreset by remember { mutableIntStateOf(0) }
    var manualPkg by remember { mutableStateOf("") }
    var manualLib by remember { mutableStateOf("libil2cpp.so") }
    var dumpCsContent by remember { mutableStateOf("") }
    var canCancel by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }

    fun addLine(msg: String) { output = output + msg }
    fun setProgress(p: Float) { progress = p }

    // ─── su shell helper ───
    fun suShell(cmd: String): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Exception) { "" }

    // ─── Find PID with fallback ───
    fun findPid(pkg: String): Int? {
        var out = suShell("pidof $pkg").trim()
        var pid = out.split(Regex("\\s+")).firstOrNull { it.all { c -> c.isDigit() } }?.toIntOrNull()
        if (pid != null) return pid
        val psOut = suShell("ps -A")
        psOut.lineSequence().forEach { line ->
            if (line.contains(pkg)) {
                val parts = line.trim().split(Regex("\\s+"))
                pid = parts.getOrNull(1)?.toIntOrNull()
                if (pid != null) return pid
            }
        }
        return null
    }

    // ─── Check metadata magic ───
    fun isMetadataValid(data: ByteArray): Boolean {
        if (data.size < 8) return false
        val magic = (data[0].toInt() and 0xFF) or
            ((data[1].toInt() and 0xFF) shl 8) or
            ((data[2].toInt() and 0xFF) shl 16) or
            ((data[3].toInt() and 0xFF) shl 24)
        return magic == MAGIC_META
    }

    // ─── Read memory via Python seek ───
    fun readMemViaPython(pid: Int, start: Long, size: Long, outFile: File): Boolean {
        val script = """
import sys
pid, start, size, path = $pid, $start, $size, r"${outFile.absolutePath}"
try:
    with open(f"/proc/{pid}/mem", "rb") as mem, open(path, "wb") as out:
        mem.seek(start)
        left = size
        while left > 0:
            chunk = mem.read(min(1024*1024, left))
            if not chunk: break
            out.write(chunk)
            left -= len(chunk)
    print("OK")
except Exception as e:
    print(f"ERR:{e}", file=sys.stderr)
    sys.exit(1)
""".trimIndent()
        val tmp = File.createTempFile("dump_", ".py", context.cacheDir)
        tmp.writeText(script)
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "python3 ${tmp.absolutePath}"))
            val result = p.inputStream.bufferedReader().readText()
            p.waitFor()
            return result.contains("OK") && outFile.exists() && outFile.length() > 0
        } catch (e: Exception) { return false }
        finally { tmp.delete() }
    }

    // ═══════════════════════════════════════════════════════════
    // MAIN DUMP FUNCTION
    // ═══════════════════════════════════════════════════════════
    fun runDump() {
        val preset = gamePresets[selectedPreset]
        val pkg = if (selectedPreset == gamePresets.size - 1) manualPkg.trim() else preset.pkg
        val il2cppLib = if (selectedPreset == gamePresets.size - 1) manualLib.trim() else preset.il2cppLib

        if (pkg.isBlank()) { addLine("❌ Enter a package name"); return }

        isRunning = true
        canCancel = true
        cancelled = false
        output = emptyList()
        dumpCsContent = ""
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        addLine("═══ AutoDump v3 — Real IL2CPP Parser ═══")
        addLine("Package: $pkg | Lib: $il2cppLib")
        addLine("Time: $ts")
        addLine("")

        scope.launch(Dispatchers.IO) {
            // Step 1: Check root
            addLine("🔐 Checking root...")
            val rootCheck = suShell("id")
            if (!rootCheck.contains("uid=0")) {
                addLine("❌ No root access! This tool requires root.")
                addLine("   Install Magisk/KernelSU and grant root to OprekTool")
                withContext(Dispatchers.Main) { isRunning = false; canCancel = false }
                return@launch
            }
            addLine("✅ Root confirmed (uid=0)")
            setProgress(0.05f)

            // Step 2: Find PID
            addLine("\n🎯 Finding PID for $pkg...")
            val pidRaw = findPid(pkg)
            if (pidRaw == null) {
                addLine("❌ Process not found: $pkg")
                addLine("   💡 Make sure the game is running!")
                addLine("   💡 Open the game, enter lobby/match, then try again")
                withContext(Dispatchers.Main) { isRunning = false; canCancel = false }
                return@launch
            }
            val pid = pidRaw
            addLine("✅ PID: $pid")
            setProgress(0.1f)

            if (cancelled) { withContext(Dispatchers.Main) { isRunning = false; canCancel = false }; return@launch }

            // Step 3: Parse memory maps
            addLine("\n📋 Parsing /proc/$pid/maps...")
            val mapsRaw = suShell("cat /proc/$pid/maps")
            val maps = mapsRaw.lines().filter { it.isNotBlank() }
            val readable = maps.filter { it.substringAfter(" ").substringBefore(" ").getOrElse(0) { 'r' } == 'r' }
            val codeRegions = maps.filter { it.substringAfter(" ").substringBefore(" ").contains("x") }
            addLine("   Total: ${maps.size} | Readable: ${readable.size} | Code: ${codeRegions.size}")

            if (cancelled) { withContext(Dispatchers.Main) { isRunning = false; canCancel = false }; return@launch }

            // Step 4: Find IL2CPP library (fallback chain)
            addLine("\n🎯 Finding $il2cppLib...")
            var il2cppLine = maps.find { it.contains(il2cppLib) && it.contains("r-xp") }
                ?: maps.find { it.contains(il2cppLib) }
            var actualLib = il2cppLib

            // Fallback chain
            val fallbacks = listOf("libunity.so", "libcsharp.so", "libil2cpp.so")
            for (fb in fallbacks) {
                if (il2cppLine != null) break
                if (fb != il2cppLib) {
                    addLine("   ⚠️ $il2cppLib not found, trying $fb...")
                    il2cppLine = maps.find { it.contains(fb) && it.contains("r-xp") }
                        ?: maps.find { it.contains(fb) }
                    if (il2cppLine != null) actualLib = fb
                }
            }

            // Find largest .so code region
            if (il2cppLine == null) {
                addLine("   ⚠️ No known IL2CPP lib, finding largest .so...")
                var bestSize = 0L
                for (region in codeRegions) {
                    if (!region.contains(".so")) continue
                    val range = region.substringBefore(" ")
                    val parts = range.split("-")
                    if (parts.size != 2) continue
                    val s = parts[0].toLongOrNull(16) ?: continue
                    val e = parts[1].toLongOrNull(16) ?: continue
                    if (e - s > bestSize && e - s < 500_000_000) {
                        bestSize = e - s
                        il2cppLine = region
                        actualLib = region.substringAfterLast(" ").trim()
                    }
                }
            }

            if (il2cppLine == null) {
                addLine("❌ No IL2CPP library found in process!")
                addLine("   Available .so files:")
                codeRegions.filter { it.contains(".so") }.take(10).forEach {
                    addLine("   → ${it.substringAfterLast(" ").trim()}")
                }
                addLine("   💡 Make sure the game is fully loaded (enter lobby/match)")
                withContext(Dispatchers.Main) { isRunning = false; canCancel = false }
                return@launch
            }

            val il2cppRange = il2cppLine.substringBefore(" ")
            val parts = il2cppRange.split("-")
            val il2cppStart = parts[0].toLongOrNull(16) ?: 0L
            val il2cppEnd = parts[1].toLongOrNull(16) ?: 0L
            val il2cppSize = il2cppEnd - il2cppStart
            addLine("✅ $actualLib @ 0x${"%X".format(il2cppStart)} (${il2cppSize / 1024}KB)")
            setProgress(0.2f)

            if (cancelled) { withContext(Dispatchers.Main) { isRunning = false; canCancel = false }; return@launch }

            // Step 5: Search for metadata
            addLine("\n📦 Searching for global-metadata.dat (0xFAB11BAF)...")
            val magic = intArrayOf(0xAF, 0x1B, 0xF1, 0xFA)
            var metaOffset = 0L
            var metaFound = false

            // Strategy 1: Search near IL2CPP lib regions
            addLine("   Strategy 1: Near $actualLib regions...")
            for (region in maps.filter { it.contains(actualLib) }) {
                val perms = region.substringAfter(" ").substringBefore(" ")
                if (perms.isEmpty() || perms[0] != 'r') continue
                val range = region.substringBefore(" ")
                val rangeParts = range.split("-")
                if (rangeParts.size != 2) continue
                val start = rangeParts[0].toLongOrNull(16) ?: continue
                val end = rangeParts[1].toLongOrNull(16) ?: continue
                val size = (end - start).toInt().coerceAtMost(4194304)
                if (size < 4) continue
                val tmpFile = File(context.cacheDir, "meta_s1_${start}.bin")
                if (readMemViaPython(pid, start, size.toLong(), tmpFile)) {
                    val data = tmpFile.readBytes()
                    for (i in 0 until data.size - 4) {
                        if (data[i].toInt() and 0xFF == magic[0] &&
                            data[i + 1].toInt() and 0xFF == magic[1] &&
                            data[i + 2].toInt() and 0xFF == magic[2] &&
                            data[i + 3].toInt() and 0xFF == magic[3]) {
                            metaOffset = start + i
                            metaFound = true
                            addLine("   ✅ Found @ 0x${"%X".format(metaOffset)}")
                            break
                        }
                    }
                    tmpFile.delete()
                }
                if (metaFound) break
            }

            // Strategy 2: Search [anon:dalvik-*] regions
            if (!metaFound) {
                addLine("   Strategy 2: dalvik anonymous regions...")
                var scanned = 0
                for (region in maps.filter { it.contains("dalvik") && !it.contains(".oat") }) {
                    if (cancelled) break
                    val range = region.substringBefore(" ")
                    val rangeParts = range.split("-")
                    if (rangeParts.size != 2) continue
                    val start = rangeParts[0].toLongOrNull(16) ?: continue
                    val end = rangeParts[1].toLongOrNull(16) ?: continue
                    val size = (end - start).toInt().coerceAtMost(2097152)
                    if (size < 4 || size > 50_000_000) continue
                    val tmpFile = File(context.cacheDir, "meta_s2_${start}.bin")
                    if (readMemViaPython(pid, start, size.toLong(), tmpFile)) {
                        val data = tmpFile.readBytes()
                        for (i in 0 until data.size - 4) {
                            if (data[i].toInt() and 0xFF == magic[0] &&
                                data[i + 1].toInt() and 0xFF == magic[1] &&
                                data[i + 2].toInt() and 0xFF == magic[2] &&
                                data[i + 3].toInt() and 0xFF == magic[3]) {
                                metaOffset = start + i
                                metaFound = true
                                addLine("   ✅ Found @ 0x${"%X".format(metaOffset)}")
                                break
                            }
                        }
                        tmpFile.delete()
                    }
                    scanned++
                    if (scanned % 50 == 0) addLine("   ...$scanned dalvik regions scanned...")
                    if (metaFound || scanned > 500) break
                }
            }

            // Strategy 3: Search all readable regions (limited)
            if (!metaFound) {
                addLine("   Strategy 3: All readable regions (500 max)...")
                var scanned = 0
                for (region in readable) {
                    if (cancelled) break
                    val range = region.substringBefore(" ")
                    val rangeParts = range.split("-")
                    if (rangeParts.size != 2) continue
                    val start = rangeParts[0].toLongOrNull(16) ?: continue
                    val end = rangeParts[1].toLongOrNull(16) ?: continue
                    val size = (end - start).toInt().coerceAtMost(2097152)
                    if (size < 4 || size > 50_000_000) continue
                    val tmpFile = File(context.cacheDir, "meta_s3_${start}.bin")
                    if (readMemViaPython(pid, start, size.toLong(), tmpFile)) {
                        val data = tmpFile.readBytes()
                        for (i in 0 until data.size - 4) {
                            if (data[i].toInt() and 0xFF == magic[0] &&
                                data[i + 1].toInt() and 0xFF == magic[1] &&
                                data[i + 2].toInt() and 0xFF == magic[2] &&
                                data[i + 3].toInt() and 0xFF == magic[3]) {
                                metaOffset = start + i
                                metaFound = true
                                addLine("   ✅ Found @ 0x${"%X".format(metaOffset)}")
                                break
                            }
                        }
                        tmpFile.delete()
                    }
                    scanned++
                    if (scanned % 100 == 0) addLine("   ...$scanned regions scanned...")
                    if (metaFound || scanned > 500) break
                }
            }

            if (!metaFound) {
                addLine("   ⚠️ Metadata NOT found in memory — likely encrypted")
            }

            setProgress(0.4f)

            if (cancelled) { withContext(Dispatchers.Main) { isRunning = false; canCancel = false }; return@launch }

            // Step 6: Dump libil2cpp.so
            addLine("\n💾 Dumping $actualLib...")
            val outDir = File("/sdcard/Download/OprekTool/dump")
            outDir.mkdirs()
            val libOutFile = File(outDir, "${actualLib.replace(".so", "")}_$pkg.bin")

            // Try direct file copy first (faster)
            val libFullPath = maps.find { it.contains(actualLib) }?.substringAfterLast(" ")?.trim() ?: ""
            if (libFullPath.isNotEmpty() && File(libFullPath).exists()) {
                try {
                    val src = File(libFullPath)
                    src.copyTo(libOutFile, overwrite = true)
                    addLine("✅ Copied from filesystem: ${libOutFile.absolutePath} (${libOutFile.length() / 1024}KB)")
                } catch (e: Exception) {
                    addLine("   File copy failed ($e), dumping from memory...")
                    val ok = readMemViaPython(pid, il2cppStart, il2cppSize, libOutFile)
                    if (ok) addLine("✅ Dumped from memory: ${libOutFile.absolutePath} (${libOutFile.length() / 1024}KB)")
                    else addLine("❌ Failed to dump $actualLib")
                }
            } else {
                val ok = readMemViaPython(pid, il2cppStart, il2cppSize, libOutFile)
                if (ok) addLine("✅ Dumped: ${libOutFile.absolutePath} (${libOutFile.length() / 1024}KB)")
                else addLine("❌ Failed to dump $actualLib")
            }
            setProgress(0.6f)

            if (cancelled) { withContext(Dispatchers.Main) { isRunning = false; canCancel = false }; return@launch }

            // Step 6b: Full memory dump — dump ALL regions near libil2cpp.so
            addLine("\n💾 Full memory dump — all readable regions near $actualLib...")
            val fullDumpFile = File(outDir, "full_memory_${pkg}.bin")
            val libRegions = maps.filter { it.contains(actualLib) }
            var totalDumped = 0L
            var regionCount = 0
            for (region in libRegions) {
                val perms = region.substringAfter(" ").substringBefore(" ")
                if (perms.isEmpty() || perms[0] != 'r') continue
                val range = region.substringBefore(" ")
                val rangeParts = range.split("-")
                if (rangeParts.size != 2) continue
                val start = rangeParts[0].toLongOrNull(16) ?: continue
                val end = rangeParts[1].toLongOrNull(16) ?: continue
                val size = end - start
                if (size < 4 || size > 200_000_000) continue
                // Append each region to full dump file
                val tmpRegion = File(context.cacheDir, "region_${start}.bin")
                if (readMemViaPython(pid, start, size, tmpRegion)) {
                    try {
                        val regionData = tmpRegion.readBytes()
                        java.io.FileOutputStream(fullDumpFile, true).use { fos ->
                            fos.write(regionData)
                        }
                        totalDumped += regionData.size
                        regionCount++
                    } catch (_: Exception) {}
                    tmpRegion.delete()
                }
                if (regionCount % 10 == 0) addLine("   ...$regionCount regions (${totalDumped / 1024}KB)...")
            }
            if (totalDumped > 0) {
                addLine("✅ Full memory dump: ${fullDumpFile.absolutePath} (${totalDumped / 1024}KB from $regionCount regions)")
            } else {
                addLine("   ⚠️ No regions dumped")
            }
            setProgress(0.65f)

            if (cancelled) { withContext(Dispatchers.Main) { isRunning = false; canCancel = false }; return@launch }

            // Step 7: Dump metadata
            val metaOutFile = File(outDir, "global-metadata_$pkg.dat")
            if (metaFound) {
                addLine("\n💾 Dumping metadata from 0x${"%X".format(metaOffset)}...")
                // Read 16MB or until end of region
                val readSize = 16777216L
                val ok = readMemViaPython(pid, metaOffset, readSize, metaOutFile)
                if (ok) addLine("✅ Metadata: ${metaOutFile.absolutePath} (${metaOutFile.length() / 1024}KB)")
                else addLine("❌ Failed to dump metadata")
            } else {
                // Metadata NOT found in memory — try extracting from APK
                addLine("\n📦 Metadata encrypted in memory — trying APK extraction...")
                val apkPath = suShell("pm path $pkg").trim().removePrefix("package:")
                if (apkPath.isNotEmpty() && File(apkPath).exists()) {
                    addLine("   APK: $apkPath")
                    try {
                        val apkZip = java.util.zip.ZipFile(apkPath)
                        // Find global-metadata.dat in APK
                        val metaEntry = apkZip.getEntry("assets/bin/Data/StreamingAssets/global-metadata.dat")
                            ?: apkZip.getEntry("assets/global-metadata.dat")
                            ?: apkZip.getEntry("global-metadata.dat")
                        if (metaEntry != null) {
                            val metaInput = apkZip.getInputStream(metaEntry)
                            val metaBytes = metaInput.readBytes()
                            metaInput.close()
                            if (metaBytes.size > 64) {
                                metaOutFile.writeBytes(metaBytes)
                                metaFound = true
                                addLine("   ✅ Found in APK: ${metaEntry.name} (${metaBytes.size / 1024}KB)")
                                if (isMetadataValid(metaBytes)) {
                                    addLine("   ✅ Valid magic 0xFAB11BAF — can parse!")
                                } else {
                                    addLine("   ⚠️ Magic invalid — still encrypted in APK too")
                                }
                            }
                        } else {
                            addLine("   ❌ No global-metadata.dat in APK assets")
                            // List APK assets for debugging
                            val assetEntries = apkZip.entries().asSequence()
                                .filter { it.name.startsWith("assets/") && it.name.endsWith(".dat") }
                                .take(10).toList()
                            if (assetEntries.isNotEmpty()) {
                                addLine("   Found .dat files in assets:")
                                assetEntries.forEach { addLine("     → ${it.name} (${it.size / 1024}KB)") }
                            }
                        }
                        apkZip.close()
                    } catch (e: Exception) {
                        addLine("   ❌ APK extraction failed: ${e.message}")
                    }
                } else {
                    addLine("   ❌ APK not found (need root to read)")
                }
            }
            setProgress(0.7f)

            if (cancelled) { withContext(Dispatchers.Main) { isRunning = false; canCancel = false }; return@launch }

            // Step 8: Parse metadata (Strategy A) or generate raw dump (Strategy B)
            addLine("")

            // Extract strings from lib binary with offset tracking
            addLine("\n📝 Extracting strings from lib binary...")
            val libData = if (libOutFile.exists()) libOutFile.readBytes() else ByteArray(0)
            val extraStrings = mutableListOf<Pair<Long, String>>() // (offset, string)
            if (libData.isNotEmpty()) {
                val sb = StringBuilder()
                var strStart = 0L
                for ((i, b) in libData.withIndex()) {
                    val c = b.toInt() and 0xFF
                    if (c in 0x20..0x7E) {
                        if (sb.isEmpty()) strStart = i.toLong()
                        sb.append(c.toChar())
                    } else {
                        if (sb.length >= 6) extraStrings.add(Pair(strStart, sb.toString()))
                        sb.clear()
                    }
                    if (extraStrings.size >= 10000) break
                }
            }
            addLine("   Extracted ${extraStrings.size} strings from binary")
            addLine("   First 5: ${extraStrings.take(5).joinToString(", ") { "0x${"%08X".format(it.first)} ${it.second.take(20)}" }}")

            // Try Strategy A: Parse metadata
            var strategyA = false
            if (metaFound && metaOutFile.exists() && metaOutFile.length() > 64) {
                addLine("\n📖 Attempting metadata parse (Strategy A)...")
                try {
                    val metaData = metaOutFile.readBytes()
                    if (isMetadataValid(metaData)) {
                        addLine("✅ Valid metadata magic 0xFAB11BAF")
                        addLine("   Parsing TypeDef/MethodDef/FieldDef...")

                        val parser = Il2CppMetadataParser(metaData)
                        val result = parser.parse()

                        if (result != null) {
                            strategyA = true
                            addLine("✅ Metadata version: ${result.version}")
                            addLine("   TypeDefs: ${result.typeDefCount}")
                            addLine("   MethodDefs: ${result.methodDefCount}")
                            addLine("   FieldDefs: ${result.fieldDefCount}")
                            addLine("   StringLiterals: ${result.stringLiteralCount}")
                            addLine("   String table: ${result.stringTableSize} bytes")

                            // Generate dump.cs
                            dumpCsContent = parser.generateDumpCs(
                                pkg = pkg,
                                lib = actualLib,
                                il2cppStart = il2cppStart,
                                il2cppEnd = il2cppEnd,
                                il2cppSize = il2cppSize,
                                metaStart = metaOffset,
                                ts = ts,
                                version = result.version,
                                strategyA = true,
                                extraStrings = extraStrings
                            )

                            val dumpCsFile = File(outDir, "dump_$pkg.cs")
                            dumpCsFile.writeText(dumpCsContent)
                            addLine("✅ dump.cs saved: ${dumpCsFile.absolutePath}")
                            addLine("   ${result.typeDefinitions.size} types, ${result.methodDefinitions.size} methods, ${result.fieldDefinitions.size} fields")
                        } else {
                            addLine("⚠️ Failed to parse metadata structure — may be corrupted")
                            addLine("   Falling back to Strategy B (raw dump)")
                        }
                    } else {
                        val probeBytes = if (metaData.size >= 4) {
                            "0x${"%02X%02X%02X%02X".format(metaData[0], metaData[1], metaData[2], metaData[3])}"
                        } else "too small"
                        addLine("⚠️ Metadata magic invalid ($probeBytes ≠ 0xFAB11BAF)")
                        addLine("   Metadata is encrypted at runtime — common for protected games")
                        addLine("   Using Strategy B (raw dump)")
                    }
                } catch (e: Exception) {
                    addLine("⚠️ Parse error: ${e.message}")
                    addLine("   Using Strategy B (raw dump)")
                }
            } else if (!metaFound) {
                addLine("\n⚠️ No metadata found — encrypted/absent at runtime")
                addLine("   Using Strategy B (raw dump)")
            }

            setProgress(0.9f)

            // Generate dump.cs for Strategy B
            if (!strategyA) {
                dumpCsContent = Il2CppMetadataParser(ByteArray(0)).generateDumpCs(
                    pkg = pkg,
                    lib = actualLib,
                    il2cppStart = il2cppStart,
                    il2cppEnd = il2cppEnd,
                    il2cppSize = il2cppSize,
                    metaStart = if (metaFound) metaOffset else 0,
                    ts = ts,
                    version = 0,
                    strategyA = false,
                    extraStrings = extraStrings
                )
                val dumpCsFile = File(outDir, "dump_$pkg.cs")
                dumpCsFile.writeText(dumpCsContent)
                addLine("✅ dump.cs saved: ${dumpCsFile.absolutePath}")
            }

            // Step 9: Summary
            addLine("\n═══════════════════════════════════════════")
            addLine("📊 DUMP SUMMARY")
            addLine("═══════════════════════════════════════════")
            addLine("Package: $pkg | PID: $pid")
            addLine("Library: $actualLib @ 0x${"%X".format(il2cppStart)}")
            addLine("  → ${libOutFile.absolutePath} (${if (libOutFile.exists()) "${libOutFile.length() / 1024}KB" else "FAILED"})")
            if (fullDumpFile.exists()) {
                addLine("Full Dump: ${fullDumpFile.absolutePath} (${fullDumpFile.length() / 1024}KB)")
            }
            if (metaFound && metaOutFile.exists()) {
                addLine("Metadata: 0x${"%X".format(metaOffset)} → ${metaOutFile.absolutePath} (${metaOutFile.length() / 1024}KB)")
            } else {
                addLine("Metadata: ⚠️ Encrypted — extracted from APK if available")
            }
            addLine("")
            if (strategyA) {
                addLine("✅ Strategy A: Real structure parse → dump.cs")
                addLine("   Open dump_$pkg.cs for full TypeDef/MethodDef/FieldDef")
            } else {
                addLine("📋 Strategy B: Raw dump for PC processing")
                addLine("   Copy from /sdcard/Download/OprekTool/dump/")
                addLine("   Use Il2CppDumper on PC:")
                addLine("   $ il2cppdumper <lib>.bin global-metadata.dat")
            }
            addLine("")
            addLine("📁 Output: /sdcard/Download/OprekTool/dump/")
            setProgress(1.0f)

            withContext(Dispatchers.Main) { isRunning = false; canCancel = false }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════════
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎯 AutoDump v3", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("dump", output.joinToString("\n")))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(8.dp)) {
            // Game selector
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("📱 Select Game", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))

                    // Preset chips
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        gamePresets.forEachIndexed { idx, preset ->
                            FilterChip(
                                selected = selectedPreset == idx,
                                onClick = { selectedPreset = idx },
                                label = { Text(preset.name, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = AccentGreen
                                )
                            )
                        }
                    }

                    // Manual input
                    if (selectedPreset == gamePresets.size - 1) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualPkg,
                            onValueChange = { manualPkg = it },
                            label = { Text("Package name", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = manualLib,
                            onValueChange = { manualLib = it },
                            label = { Text("IL2CPP library (e.g. libil2cpp.so)", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text("${gamePresets[selectedPreset].pkg} → ${gamePresets[selectedPreset].il2cppLib}", fontSize = 9.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        Text(gamePresets[selectedPreset].desc, fontSize = 9.sp, color = TextMuted)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Dump button
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { runDump() },
                            modifier = Modifier.weight(1f).height(44.dp),
                            enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Dumping... ${(progress * 100).toInt()}%", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.CloudDownload, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start Dump", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (isRunning && canCancel) {
                            Button(
                                onClick = { cancelled = true },
                                modifier = Modifier.height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                            }
                        }
                    }

                    // Progress bar
                    if (isRunning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = AccentGreen,
                            trackColor = DarkBg
                        )
                    }
                }
            }

            // Output - Modern collapsible sections v3
            var showTypeDefs by remember { mutableStateOf(true) }
            var showMethods by remember { mutableStateOf(false) }
            var showFields by remember { mutableStateOf(false) }
            var showStrings by remember { mutableStateOf(false) }
            var showLog by remember { mutableStateOf(true) }

            val typeDefLines = output.filter { it.contains("TypeDef #") || it.contains("public class") }
            val methodLines = output.filter { it.contains("Method #") || (it.contains("void ") && it.contains("params")) }
            val fieldLines = output.filter { it.contains("Field #") }
            val stringLines = output.filter { it.contains("0x") && !it.contains("TypeDef") && !it.contains("Method") && !it.contains("Field") && !it.startsWith("═") && !it.contains("public class") }
            val logLines = output.filter { !typeDefLines.contains(it) && !methodLines.contains(it) && !fieldLines.contains(it) && !stringLines.contains(it) }

            Card(
                Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(8.dp).fillMaxSize()) {
                    Text("📋 Output (${output.size} lines)", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))

                    // Modern collapsible filter chips + expand/collapse all
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = showLog, onClick = { showLog = !showLog },
                                label = { Text("📋 Log (${logLines.size})", fontSize = 8.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.2f), selectedLabelColor = AccentGreen))
                            FilterChip(selected = showTypeDefs, onClick = { showTypeDefs = !showTypeDefs },
                                label = { Text("🏷 TypeDefs (${typeDefLines.size})", fontSize = 8.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCyan.copy(alpha = 0.2f), selectedLabelColor = AccentCyan))
                            FilterChip(selected = showMethods, onClick = { showMethods = !showMethods },
                                label = { Text("⚙ Methods (${methodLines.size})", fontSize = 8.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange.copy(alpha = 0.2f), selectedLabelColor = AccentOrange))
                            FilterChip(selected = showFields, onClick = { showFields = !showFields },
                                label = { Text("📂 Fields (${fieldLines.size})", fontSize = 8.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple.copy(alpha = 0.2f), selectedLabelColor = AccentPurple))
                            FilterChip(selected = showStrings, onClick = { showStrings = !showStrings },
                                label = { Text("🔤 Strings (${stringLines.size})", fontSize = 8.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF9C27B0).copy(alpha = 0.2f), selectedLabelColor = Color(0xFF9C27B0)))
                        }
                        // Expand/Collapse all button
                        IconButton(onClick = {
                            val allExpanded = showLog && showTypeDefs && showMethods && showFields && showStrings
                            showLog = !allExpanded
                            showTypeDefs = !allExpanded
                            showMethods = !allExpanded
                            showFields = !allExpanded
                            showStrings = !allExpanded
                        }) {
                            Icon(
                                if (showLog && showTypeDefs && showMethods && showFields && showStrings) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = "Toggle all",
                                tint = AccentGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    val visibleLines = mutableListOf<String>()
                    if (showLog) visibleLines.addAll(logLines)
                    if (showTypeDefs) visibleLines.addAll(typeDefLines)
                    if (showMethods) visibleLines.addAll(methodLines)
                    if (showFields) visibleLines.addAll(fieldLines)
                    if (showStrings) visibleLines.addAll(stringLines)

                    LazyColumn(Modifier.weight(1f)) {
                        items(visibleLines) { line ->
                            val color = when {
                                line.startsWith("✅") -> AccentGreen
                                line.startsWith("❌") -> AccentRed
                                line.startsWith("⚠") -> AccentOrange
                                line.startsWith("═") -> AccentCyan
                                line.startsWith("📊") -> AccentCyan
                                line.contains("TypeDef #") -> AccentCyan
                                line.contains("Method #") -> AccentOrange
                                line.contains("Field #") -> AccentPurple
                                line.contains("public class") -> AccentCyan
                                line.contains("0x") && !line.startsWith("//") -> Color(0xFFFF79C6)
                                line.startsWith("//") -> TextMuted
                                else -> TextPrimary
                            }
                            Text(line, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = color, lineHeight = 12.sp)
                        }
                    }

                    if (dumpCsContent.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("dumpcs", dumpCsContent))
                                Toast.makeText(context, "dump.cs copied! (${dumpCsContent.lines().size} lines)", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("📋 Copy dump.cs (${dumpCsContent.lines().size} lines)", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
