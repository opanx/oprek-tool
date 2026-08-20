package com.oprek.tool.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized analysis engine - all screens share this.
 * Parses once, caches results, provides typed access.
 */
object AnalysisEngine {

    // Cache: path -> parsed data
    private val cache = ConcurrentHashMap<String, ElfAnalysisResult>()

    // ====== ELF Data Classes ======
    data class ElfHeader(
        val is64Bit: Boolean, val isLE: Boolean,
        val eType: Int, val eMachine: Int, val eVersion: Int,
        val eEntry: Long, val ePhoff: Long, val eShoff: Long,
        val eFlags: Int, val eEhsize: Int, val ePhentsize: Int,
        val ePhnum: Int, val eShentsize: Int, val eShnum: Int,
        val eShstrndx: Int
    ) {
        val arch: String get() = when(eMachine) {
            0x03 -> "x86"; 0x08 -> "MIPS"; 0x14 -> "ARM"; 0x28 -> "AArch64"
            0x3E -> "x86_64"; 0xB7 -> "AArch64"; else -> "0x${eMachine.toString(16)}"
        }
        val typeStr: String get() = when(eType) { 1->"ET_REL"; 2->"ET_EXEC"; 3->"ET_DYN"; 4->"ET_CORE"; else->"0x${eType.toString(16)}" }
        val isStripped: Boolean get() = eShnum == 0 || eShoff == 0L
    }

    data class ProgramHeader(
        val pType: Int, val pOffset: Long, val pVaddr: Long,
        val pPaddr: Long, val pFilesz: Long, val pMemsz: Long,
        val pFlags: Int, val pAlign: Long
    ) {
        val typeStr: String get() = when(pType) {
            0->"PT_NULL"; 1->"PT_LOAD"; 2->"PT_DYNAMIC"; 3->"PT_INTERP"
            4->"PT_NOTE"; 6->"PT_PHDR"; 7->"PT_TLS"; 0x6474e550->"PT_GNU_EH_FRAME"
            0x6474e551->"PT_GNU_STACK"; 0x6474e552->"PT_GNU_RELRO"; else->"0x${pType.toString(16)}"
        }
        val flagsStr: String get() = buildString {
            if (pFlags and 4 != 0) append('R')
            if (pFlags and 2 != 0) append('W')
            if (pFlags and 1 != 0) append('X')
        }
        val permColor: String get() = when {
            pFlags and 1 != 0 && pFlags and 2 != 0 -> "RED"   // RWX
            pFlags and 1 != 0 -> "GREEN"                        // RX
            pFlags and 2 != 0 -> "RED"                          // RW
            else -> "BLUE"                                       // R
        }
    }

    data class SectionHeader(
        val shName: String, val shType: Int, val shFlags: Long,
        val shAddr: Long, val shOffset: Long, val shSize: Long,
        val shLink: Int, val shInfo: Int, val shAddralign: Long,
        val shEntsize: Long
    ) {
        val typeStr: String get() = when(shType) {
            0->"SHT_NULL"; 1->"SHT_PROGBITS"; 2->"SHT_SYMTAB"; 3->"SHT_STRTAB"
            4->"SHT_RELA"; 5->"SHT_HASH"; 6->"SHT_DYNAMIC"; 7->"SHT_NOTE"
            8->"SHT_NOBITS"; 9->"SHT_REL"; 0x0b->"SHT_DYNSYM"; 0x0e->"SHT_INIT_ARRAY"
            0x0f->"SHT_FINI_ARRAY"; else->"SHT_0x${shType.toString(16)}"
        }
        val flagsStr: String get() = buildString {
            if (shFlags and 0x2 != 0L) append('A') // ALLOC
            if (shFlags and 0x1 != 0L) append('W') // WRITE
            if (shFlags and 0x4 != 0L) append('X') // EXECINSTR
        }
        val isExecutable: Boolean get() = shFlags and 0x4 != 0L
        val isWritable: Boolean get() = shFlags and 0x2 != 0L
    }

    data class SymbolEntry(
        val stName: String, val stValue: Long, val stSize: Long,
        val stInfo: Int, val stOther: Int, val stShndx: Int
    ) {
        val bind: String get() = when(stInfo shr 4) { 0->"LOCAL"; 1->"GLOBAL"; 2->"WEAK"; else->"BIND_${stInfo shr 4}" }
        val type: String get() = when(stInfo and 0xF) { 0->"NOTYPE"; 1->"OBJECT"; 2->"FUNC"; 3->"SECTION"; 4->"FILE"; else->"TYPE_${stInfo and 0xF}" }
        val isFunc: Boolean get() = (stInfo and 0xF) == 2
        val ndxStr: String get() = when(stShndx) { 0->"UND"; 0xFFF1.toInt()->"ABS"; 0xFFF2.toInt()->"COMMON"; else->"$stShndx" }
    }

    data class RelocationEntry(
        val rOffset: Long, val rInfo: Long, val rAddend: Long,
        val rType: Int, val rSymIdx: Int, val symName: String
    )

    data class DynamicEntry(val dTag: Long, val dVal: Long, val dValStr: String = "")

    data class ElfAnalysisResult(
        val header: ElfHeader?,
        val programHeaders: List<ProgramHeader>,
        val sections: List<SectionHeader>,
        val symbols: List<SymbolEntry>,
        val dynsym: List<SymbolEntry>,
        val relocations: List<RelocationEntry>,
        val dynamicEntries: List<DynamicEntry>,
        val gotEntries: List<Pair<Long, String>>,
        val strtab: ByteArray,
        val dynstr: ByteArray,
        val fileBytes: ByteArray
    )

    // ====== Public API ======

    fun analyzeElf(file: File): ElfAnalysisResult {
        val cached = cache[file.absolutePath]
        if (cached != null) return cached

        val bytes = file.readBytes()
        val result = parseElf(bytes)
        cache[file.absolutePath] = result
        return result
    }

    fun analyzeElf(bytes: ByteArray): ElfAnalysisResult = parseElf(bytes)

    fun getCached(path: String): ElfAnalysisResult? = cache[path]

    fun clearCache(path: String? = null) {
        if (path != null) cache.remove(path) else cache.clear()
    }

    // ====== ELF Parser ======

    private fun parseElf(data: ByteArray): ElfAnalysisResult {
        if (data.size < 16 || !isElf(data)) {
            return ElfAnalysisResult(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), byteArrayOf(), byteArrayOf(), data)
        }

        val is64 = data[4] == 2.toByte()
        val isLE = data[5] == 1.toByte()

        val header = parseHeader(data, is64, isLE)
        val programHeaders = parseProgramHeaders(data, header)
        val sections = parseSections(data, header)
        val strtab = findStrtab(data, sections)
        val dynstr = findDynstr(data, sections)
        val symbols = parseSymbols(data, sections, "SHT_SYMTAB", strtab)
        val dynsym = parseSymbols(data, sections, "SHT_DYNSYM", dynstr)
        val relocations = parseRelocations(data, sections, symbols, dynsym)
        val dynamicEntries = parseDynamic(data, sections, dynstr)
        val gotEntries = parseGotPlt(data, sections, dynsym)

        return ElfAnalysisResult(header, programHeaders, sections, symbols, dynsym, relocations, dynamicEntries, gotEntries, strtab, dynstr, data)
    }

    private fun isElf(data: ByteArray): Boolean =
        data.size >= 4 && data[0] == 0x7F.toByte() && data[1] == 0x45.toByte() && data[2] == 0x4C.toByte() && data[3] == 0x46.toByte()

    private fun r16(data: ByteArray, off: Int, le: Boolean): Int =
        if (le) (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)
        else ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)

    private fun r32(data: ByteArray, off: Int, le: Boolean): Long =
        if (le) (data[off].toLong() and 0xFF) or ((data[off + 1].toLong() and 0xFF) shl 8) or
                ((data[off + 2].toLong() and 0xFF) shl 16) or ((data[off + 3].toLong() and 0xFF) shl 24)
        else ((data[off].toLong() and 0xFF) shl 24) or ((data[off + 1].toLong() and 0xFF) shl 16) or
                ((data[off + 2].toLong() and 0xFF) shl 8) or (data[off + 3].toLong() and 0xFF)

    private fun r64(data: ByteArray, off: Int, le: Boolean): Long =
        if (le) r32(data, off, le) or (r32(data, off + 4, le) shl 32)
        else (r32(data, off, le) shl 32) or r32(data, off + 4, le)

    private fun readStr(data: ByteArray, off: Int, maxLen: Int = 256): String {
        val sb = StringBuilder()
        var i = off
        while (i < minOf(off + maxLen, data.size) && data[i] != 0.toByte()) {
            sb.append(data[i].toInt().toChar())
            i++
        }
        return sb.toString()
    }

    private fun readStrFromTable(table: ByteArray, idx: Int, maxLen: Int = 256): String {
        if (idx < 0 || idx >= table.size) return ""
        return readStr(table, idx, maxLen)
    }

    private fun parseHeader(data: ByteArray, is64: Boolean, isLE: Boolean): ElfHeader {
        return ElfHeader(
            is64Bit = is64, isLE = isLE,
            eType = r16(data, 16, isLE), eMachine = r16(data, 18, isLE), eVersion = r32(data, 20, isLE).toInt(),
            eEntry = if (is64) r64(data, 24, isLE) else r32(data, 24, isLE),
            ePhoff = if (is64) r64(data, 32, isLE) else r32(data, 28, isLE),
            eShoff = if (is64) r64(data, 40, isLE) else r32(data, 32, isLE),
            eFlags = r32(data, if (is64) 48 else 36, isLE).toInt(),
            eEhsize = r16(data, if (is64) 52 else 40, isLE),
            ePhentsize = r16(data, if (is64) 54 else 42, isLE),
            ePhnum = r16(data, if (is64) 56 else 44, isLE),
            eShentsize = r16(data, if (is64) 58 else 46, isLE),
            eShnum = r16(data, if (is64) 60 else 48, isLE),
            eShstrndx = r16(data, if (is64) 62 else 50, isLE)
        )
    }

    private fun parseProgramHeaders(data: ByteArray, header: ElfHeader): List<ProgramHeader> {
        val result = mutableListOf<ProgramHeader>()
        val is64 = header.is64Bit
        val isLE = header.isLE
        for (i in 0 until header.ePhnum) {
            val base = header.ePhoff + i.toLong() * header.ePhentsize
            if (base + header.ePhentsize > data.size) break
            val b = base.toInt()
            result.add(ProgramHeader(
                pType = r32(data, b, isLE).toInt(),
                pOffset = if (is64) r64(data, b + 8, isLE) else r32(data, b + 4, isLE),
                pVaddr = if (is64) r64(data, b + 16, isLE) else r32(data, b + 8, isLE),
                pPaddr = if (is64) r64(data, b + 24, isLE) else r32(data, b + 12, isLE),
                pFilesz = if (is64) r64(data, b + 32, isLE) else r32(data, b + 16, isLE),
                pMemsz = if (is64) r64(data, b + 40, isLE) else r32(data, b + 20, isLE),
                pFlags = if (is64) r32(data, b + 4, isLE).toInt() else r32(data, b + 24, isLE).toInt(),
                pAlign = if (is64) r64(data, b + 48, isLE) else r32(data, b + 28, isLE)
            ))
        }
        return result
    }

    private fun parseSections(data: ByteArray, header: ElfHeader): List<SectionHeader> {
        val result = mutableListOf<SectionHeader>()
        val is64 = header.is64Bit
        val isLE = header.isLE

        // Find shstrtab first
        var shstrtabData = byteArrayOf()
        if (header.eShstrndx in 1 until header.eShnum) {
            val secBase = header.eShoff + header.eShstrndx.toLong() * header.eShentsize
            if (secBase + header.eShentsize <= data.size) {
                val b = secBase.toInt()
                val offset = if (is64) r64(data, b + 24, isLE) else r32(data, b + 16, isLE)
                val size = if (is64) r64(data, b + 32, isLE) else r32(data, b + 20, isLE)
                if (offset + size <= data.size) {
                    shstrtabData = data.copyOfRange(offset.toInt(), (offset + size).toInt())
                }
            }
        }

        for (i in 0 until header.eShnum) {
            val base = header.eShoff + i.toLong() * header.eShentsize
            if (base + header.eShentsize > data.size) break
            val b = base.toInt()
            val nameIdx = r32(data, b, isLE).toInt()
            val name = if (shstrtabData.isNotEmpty() && nameIdx < shstrtabData.size) readStrFromTable(shstrtabData, nameIdx) else "?"

            result.add(SectionHeader(
                shName = name,
                shType = r32(data, b + 4, isLE).toInt(),
                shFlags = if (is64) r64(data, b + 8, isLE) else r32(data, b + 8, isLE),
                shAddr = if (is64) r64(data, b + 16, isLE) else r32(data, b + 12, isLE),
                shOffset = if (is64) r64(data, b + 24, isLE) else r32(data, b + 16, isLE),
                shSize = if (is64) r64(data, b + 32, isLE) else r32(data, b + 20, isLE),
                shLink = r32(data, b + if (is64) 40 else 24, isLE).toInt(),
                shInfo = r32(data, b + if (is64) 44 else 28, isLE).toInt(),
                shAddralign = if (is64) r64(data, b + 48, isLE) else r32(data, b + 32, isLE),
                shEntsize = if (is64) r64(data, b + 56, isLE) else r32(data, b + 36, isLE)
            ))
        }
        return result
    }

    private fun findStrtab(data: ByteArray, sections: List<SectionHeader>): ByteArray {
        val sec = sections.find { it.shName == ".strtab" && it.shType == 3 } ?: return byteArrayOf()
        if (sec.shOffset + sec.shSize > data.size) return byteArrayOf()
        return data.copyOfRange(sec.shOffset.toInt(), (sec.shOffset + sec.shSize).toInt())
    }

    private fun findDynstr(data: ByteArray, sections: List<SectionHeader>): ByteArray {
        val sec = sections.find { it.shName == ".dynstr" && it.shType == 3 } ?: return byteArrayOf()
        if (sec.shOffset + sec.shSize > data.size) return byteArrayOf()
        return data.copyOfRange(sec.shOffset.toInt(), (sec.shOffset + sec.shSize).toInt())
    }

    private fun parseSymbols(data: ByteArray, sections: List<SectionHeader>, secType: String, strtab: ByteArray): List<SymbolEntry> {
        val sec = sections.find { it.shType == if (secType == "SHT_SYMTAB") 2 else 0x0b } ?: return emptyList()
        if (sec.shOffset + sec.shSize > data.size || sec.shEntsize == 0L) return emptyList()
        val result = mutableListOf<SymbolEntry>()
        val count = (sec.shSize / sec.shEntsize).toInt()
        val is64 = data.size > 4 && data[4] == 2.toByte()
        val isLE = data.size > 5 && data[5] == 1.toByte()

        for (i in 0 until count) {
            val base = sec.shOffset + i.toLong() * sec.shEntsize
            if (base + sec.shEntsize > data.size) break
            val b = base.toInt()
            val nameIdx = r32(data, b, isLE).toInt()
            val name = if (strtab.isNotEmpty() && nameIdx < strtab.size) readStrFromTable(strtab, nameIdx) else "sym_$i"
            val info = data[(b + if (is64) 4 else 4)].toInt() and 0xFF
            val other = data[(b + if (is64) 5 else 5)].toInt() and 0xFF
            val value = if (is64) r64(data, b + 8, isLE) else r32(data, b + 8, isLE)
            val size = if (is64) r64(data, b + 16, isLE) else r32(data, b + 12, isLE)
            val shndx = if (is64) r16(data, b + 6, isLE) else r16(data, b + 14, isLE)

            result.add(SymbolEntry(name, value, size, info, other, shndx))
        }
        return result
    }

    private fun parseRelocations(data: ByteArray, sections: List<SectionHeader>, symbols: List<SymbolEntry>, dynsym: List<SymbolEntry>): List<RelocationEntry> {
        val result = mutableListOf<RelocationEntry>()
        val is64 = data.size > 4 && data[4] == 2.toByte()
        val isLE = data.size > 5 && data[5] == 1.toByte()
        val allSymbols = symbols + dynsym

        for (sec in sections) {
            if (sec.shType != 9 && sec.shType != 4) continue // SHT_REL or SHT_RELA
            val entrySize = if (sec.shType == 4) (if (is64) 24 else 12) else (if (is64) 16 else 8)
            if (entrySize == 0 || sec.shEntsize == 0L) continue
            val count = (sec.shSize / entrySize).toInt()

            for (i in 0 until count) {
                val base = sec.shOffset + i.toLong() * entrySize
                if (base + entrySize > data.size) break
                val b = base.toInt()
                val rOffset = if (is64) r64(data, b, isLE) else r32(data, b, isLE)
                val rInfo = if (is64) r64(data, b + 8, isLE) else r32(data, b + 4, isLE)
                val rType = (rInfo and if (is64) 0xFFFFFFFFL else 0xFFL).toInt()
                val rSymIdx = (rInfo shr if (is64) 32 else 8).toInt()
                val symName = if (rSymIdx < allSymbols.size) allSymbols[rSymIdx].stName else "sym[$rSymIdx]"
                val rAddend = if (sec.shType == 4) {
                    if (is64) r64(data, b + 16, isLE) else r32(data, b + 8, isLE)
                } else 0L

                result.add(RelocationEntry(rOffset, rInfo, rAddend, rType, rSymIdx, symName))
            }
        }
        return result
    }

    private fun parseDynamic(data: ByteArray, sections: List<SectionHeader>, dynstr: ByteArray): List<DynamicEntry> {
        val sec = sections.find { it.shType == 6 } ?: return emptyList() // SHT_DYNAMIC
        val is64 = data.size > 4 && data[4] == 2.toByte()
        val isLE = data.size > 5 && data[5] == 1.toByte()
        val entrySize = if (is64) 16 else 8
        val count = (sec.shSize / entrySize).toInt()
        val result = mutableListOf<DynamicEntry>()

        for (i in 0 until count) {
            val base = sec.shOffset + i.toLong() * entrySize
            if (base + entrySize > data.size) break
            val b = base.toInt()
            val tag = if (is64) r64(data, b, isLE) else r32(data, b, isLE)
            val value = if (is64) r64(data, b + 8, isLE) else r32(data, b + 4, isLE)
            val valueStr = if (tag == 1L && dynstr.isNotEmpty() && value < dynstr.size) {
                readStrFromTable(dynstr, value.toInt())
            } else "$value"
            result.add(DynamicEntry(tag, value, valueStr))
        }
        return result
    }

    private fun parseGotPlt(data: ByteArray, sections: List<SectionHeader>, dynsym: List<SymbolEntry>): List<Pair<Long, String>> {
        val got = sections.find { it.shName == ".got.plt" || it.shName == ".got" } ?: return emptyList()
        val is64 = data.size > 4 && data[4] == 2.toByte()
        val entrySize = if (is64) 8L else 4L
        val count = (got.shSize / entrySize).toInt()
        val result = mutableListOf<Pair<Long, String>>()

        for (i in 0 until count) {
            val addr = got.shAddr + i.toLong() * entrySize
            val off = got.shOffset + i.toLong() * entrySize
            if (off + entrySize > data.size) break
            val isLE = data.size > 5 && data[5] == 1.toByte()
            val value = if (is64) r64(data, off.toInt(), isLE) else r32(data, off.toInt(), isLE)
            val name = if (i < dynsym.size) dynsym[i].stName else "entry_$i"
            result.add(addr to name)
        }
        return result
    }

    // ====== XREF Engine ======

    fun findXrefs(result: ElfAnalysisResult, targetAddr: Long): List<Long> {
        val text = result.sections.find { it.shName == ".text" } ?: return emptyList()
        if (text.shOffset + text.shSize > result.fileBytes.size) return emptyList()
        val xrefs = mutableListOf<Long>()
        val isLE = result.header?.isLE ?: true

        for (a in text.shOffset until minOf(text.shOffset + text.shSize, result.fileBytes.size.toLong()) step 4) {
            if (a + 4 > result.fileBytes.size) break
            val b = a.toInt()
            val insn = r32(result.fileBytes, b, isLE)
            val opc = (insn shr 26) and 0x3F
            if (opc == 0x25L || opc == 0x05L) { // BL or B
                val imm26 = insn and 0x3FFFFFF
                val signExt = if (imm26 and 0x2000000 != 0L) (imm26 or (-0x4000000L)) else imm26
                val bt = a + signExt * 4
                if (bt == targetAddr) xrefs.add(a)
            }
        }
        return xrefs
    }

    // ====== Function Recognition ======

    fun recognizeFunctions(result: ElfAnalysisResult): List<FunctionInfo> {
        val functions = mutableListOf<FunctionInfo>()

        // From symbol table
        for (sym in result.symbols) {
            if (sym.isFunc && sym.stValue > 0 && sym.stName.isNotEmpty() && sym.stName != "sym_0") {
                functions.add(FunctionInfo(sym.stName, sym.stValue, sym.stSize, "symbol"))
            }
        }

        // From dynsym
        for (sym in result.dynsym) {
            if (sym.isFunc && sym.stValue > 0 && sym.stName.isNotEmpty()) {
                if (functions.none { it.address == sym.stValue }) {
                    functions.add(FunctionInfo(sym.stName, sym.stValue, sym.stSize, "dynamic"))
                }
            }
        }

        // Heuristic: scan .text for function prologues (STP X29,X30 or PUSH {FP,LR})
        val text = result.sections.find { it.shName == ".text" } ?: return functions
        if (text.shOffset + text.shSize > result.fileBytes.size) return functions
        val isLE = result.header?.isLE ?: true

        var i = text.shOffset.toInt()
        val textEnd = minOf((text.shOffset + text.shSize).toInt(), result.fileBytes.size)
        while (i + 4 <= textEnd) {
            val insn = r32(result.fileBytes, i, isLE)
            // ARM64: STP X29, X30, [SP, #-imm]! = 0xA9xx7BFD
            // ARM32: PUSH {FP, LR} = 0xE92D4800
            val isPrologue = (insn and 0xFFE07FFFu.toLong() == 0xA9007BFDu.toLong()) || // STP X29,X30
                    (insn and 0xFFFF0000u.toLong() == 0xE92D0000u.toLong()) || // PUSH
                    (insn and 0xFFFFFFFFu.toLong() == 0xD503201Fu.toLong()) // NOP (function alignment)
            if (isPrologue) {
                val addr = text.shAddr + i - text.shOffset
                if (functions.none { it.address == addr }) {
                    functions.add(FunctionInfo("sub_${"%X".format(addr)}", addr, 0, "heuristic"))
                }
            }
            i += 4
        }

        return functions.sortedBy { it.address }
    }

    data class FunctionInfo(val name: String, val address: Long, val size: Long, val source: String)

    // ====== File Hashing (streaming) ======

    fun hashFile(file: File, algorithm: String = "SHA-256"): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun hashBytes(bytes: ByteArray, algorithm: String = "SHA-256"): String {
        return MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // ====== Entropy ======

    fun entropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        val freq = IntArray(256)
        for (b in data) freq[b.toInt() and 0xFF]++
        var e = 0.0
        for (f in freq) {
            if (f > 0) {
                val p = f.toDouble() / data.size
                e -= p * kotlin.math.ln(p) / kotlin.math.ln(2.0)
            }
        }
        return e
    }
}
