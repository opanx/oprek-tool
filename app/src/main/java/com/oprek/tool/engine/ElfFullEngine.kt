package com.oprek.tool.engine

import java.io.File
import java.io.RandomAccessFile

// ====== DATA CLASSES ======

data class ElfFullHeader(
    val magic: String, val elfClass: String, val elfData: String,
    val eType: String, val eMachine: String, val eVersion: Int,
    val eEntry: Long, val ePhoff: Long, val eShoff: Long,
    val eFlags: Int, val eEhsize: Int, val ePhentsize: Int,
    val ePhnum: Int, val eShentsize: Int, val eShnum: Int,
    val eShstrndx: Int, val arch: String
)

data class ProgramHeader(
    val pType: String, val pOffset: Long, val pVaddr: Long,
    val pPaddr: Long, val pFilesz: Long, val pMemsz: Long,
    val pFlags: String, val pAlign: Long
)

data class SectionHeader(
    val shName: String, val shType: String, val shFlags: Long,
    val shAddr: Long, val shOffset: Long, val shSize: Long,
    val shLink: Int, val shInfo: Int, val shAddralign: Long,
    val shEntsize: Long
)

data class SymbolEntry(
    val stName: String, val stValue: Long, val stSize: Long,
    val stInfo: String, val stBind: String, val stType: String,
    val stVis: String, val stShndx: String
)

data class DynamicEntry(
    val dTag: String, val dVal: Long, val dValStr: String
)

data class RelocationEntry(
    val rOffset: Long, val rType: String, val rSym: String, val rAddend: Long
)

data class GotPltEntry(
    val index: Int, val address: Long, val value: Long, val funcName: String
)

// ====== FULL ELF ENGINE ======

object ElfFullEngine {

    private var data: ByteArray = byteArrayOf()
    private var is64 = false
    private var isLE = true
    private var strtab: ByteArray = byteArrayOf()
    private var dynstr: ByteArray = byteArrayOf()
    private var symbols: MutableList<SymbolEntry> = mutableListOf()
    private var sections: MutableList<SectionHeader> = mutableListOf()

    fun load(file: File) {
        val bytes = file.readBytes()
        load(bytes)
    }

    fun load(bytes: ByteArray) {
        data = bytes
        if (bytes.size < 64) return
        is64 = bytes[4] == 2.toByte()
        isLE = bytes[5] == 1.toByte()
        parseStringTables()
        parseAllSymbols()
    }

    // ===== HEADER =====
    fun parseHeader(): ElfFullHeader {
        if (data.size < 64 || !data.startsWith(0x7F, 0x45, 0x4C, 0x46)) {
            return ElfFullHeader("N/A","N/A","N/A","N/A","N/A",0,0,0,0,0,0,0,0,0,0,"Invalid ELF")
        }
        val machine = if (is64) readU16(18) else readU16(18)
        val machineStr = when(machine) {
            0x03 -> "x86"; 0x08 -> "MIPS"; 0x14 -> "ARM"; 0x28 -> "AArch64"
            0x2B -> "SPARC"; 0x3E -> "x86_64"; 0xB7 -> "AArch64"; else -> "0x${machine.toString(16)}"
        }
        val eType = when(val t = if (is64) readU16(16) else readU16(16)) {
            1 -> "ET_REL"; 2 -> "ET_EXEC"; 3 -> "ET_DYN"; 4 -> "ET_CORE"; else -> "0x${t.toString(16)}"
        }
        val arch = when {
            is64 && machine == 0xB7 -> "ARM64 (aarch64)"
            !is64 && machine == 0x28 -> "ARM (thumb)"
            is64 && machine == 0x3E -> "x86_64"
            !is64 && machine == 0x03 -> "x86 (i386)"
            else -> "$machineStr ${if (is64) "64-bit" else "32-bit"}"
        }
        return ElfFullHeader(
            magic = "7F 45 4C 46",
            elfClass = if (is64) "ELF64 (class=2)" else "ELF32 (class=1)",
            elfData = if (isLE) "Little Endian (data=1)" else "Big Endian (data=2)",
            eType = eType, eMachine = "$machineStr ($machine)", eVersion = readU32(if(is64)20 else 20),
            eEntry = if(is64) readU64(24) else readU32(24).toLong(),
            ePhoff = if(is64) readU64(32) else readU32(28).toLong(),
            eShoff = if(is64) readU64(40) else readU32(32).toLong(),
            eFlags = readU32(if(is64)48 else 36),
            eEhsize = if(is64) readU16(52) else readU16(40),
            ePhentsize = if(is64) readU16(54) else readU16(42),
            ePhnum = if(is64) readU16(56) else readU16(44),
            eShentsize = if(is64) readU16(58) else readU16(46),
            eShnum = if(is64) readU16(60) else readU16(48),
            eShstrndx = if(is64) readU16(62) else readU16(50),
            arch = arch
        )
    }

    // ===== PROGRAM HEADERS =====
    fun parseProgramHeaders(): List<ProgramHeader> {
        if (data.isEmpty()) return emptyList()
        val h = parseHeader()
        val result = mutableListOf<ProgramHeader>()
        val entsize = h.ePhentsize
        for (i in 0 until h.ePhnum) {
            val base = h.ePhoff + i.toLong() * entsize
            if (base + entsize > data.size) break
            val pType = when(val t = readU32(base)) {
                0 -> "PT_NULL"; 1 -> "PT_LOAD"; 2 -> "PT_DYNAMIC"; 3 -> "PT_INTERP"
                4 -> "PT_NOTE"; 6 -> "PT_PHDR"; 7 -> "PT_TLS"; 0x6474e550u -> "PT_GNU_EH_FRAME"
                0x6474e551u -> "PT_GNU_STACK"; 0x6474e552u -> "PT_GNU_RELRO"; else -> "0x${t.toString(16)}"
            }
            val pOffset = if(is64) readU64(base+8) else readU32(base+4).toLong()
            val pVaddr = if(is64) readU64(base+16) else readU32(base+8).toLong()
            val pPaddr = if(is64) readU64(base+24) else readU32(base+12).toLong()
            val pFilesz = if(is64) readU64(base+32) else readU32(base+16).toLong()
            val pMemsz = if(is64) readU64(base+40) else readU32(base+20).toLong()
            val pFlags = if(is64) {
                val f = readU32(base+4)
                buildString { if(f and 4!=0) append('R'); if(f and 2!=0) append('W'); if(f and 1!=0) append('X') }
            } else {
                val f = readU32(base+24)
                buildString { if(f and 4!=0) append('R'); if(f and 2!=0) append('W'); if(f and 1!=0) append('X') }
            }
            val pAlign = if(is64) readU64(base+48) else readU32(base+28).toLong()
            result.add(ProgramHeader(pType, pOffset, pVaddr, pPaddr, pFilesz, pMemsz, pFlags, pAlign))
        }
        return result
    }

    // ===== SECTION HEADERS =====
    fun parseSectionHeaders(): List<SectionHeader> {
        if (data.isEmpty()) return emptyList()
        val h = parseHeader()
        val result = mutableListOf<SectionHeader>()
        sections.clear()

        // Get shstrtab
        val shstrtabOff = if (h.eShstrndx in 1 until h.eShnum) {
            val shBase = h.eShoff + h.eShstrndx.toLong() * h.eShentsize
            if(is64) readU64(shBase.toInt()+24) else readU32(shBase.toInt()+16).toLong()
        } else 0L

        for (i in 0 until h.eShnum) {
            val base = h.eShoff + i.toLong() * h.eShentsize
            if (base + h.eShentsize > data.size) break
            val nameIdx = readU32(base)
            val shType = if(is64) readU32(base+4) else readU32(base+4)
            val typeStr = when(shType) {
                0u -> "SHT_NULL"; 1u -> "SHT_PROGBITS"; 2u -> "SHT_SYMTAB"; 3u -> "SHT_STRTAB"
                4u -> "SHT_RELA"; 5u -> "SHT_HASH"; 6u -> "SHT_DYNAMIC"; 7u -> "SHT_NOTE"
                8u -> "SHT_NOBITS"; 9u -> "SHT_REL"; 0x0eu -> "SHT_INIT_ARRAY"; 0x0fu -> "SHT_FINI_ARRAY"
                0x0bu -> "SHT_DYNSYM"; 0x47554e52u -> "SHT_GNU_HASH"; else -> "SHT_$shType"
            }
            val shFlags = if(is64) readU64(base+8) else readU32(base+8).toLong()
            val shAddr = if(is64) readU64(base+16) else readU32(base+12).toLong()
            val shOffset = if(is64) readU64(base+24) else readU32(base+16).toLong()
            val shSize = if(is64) readU64(base+32) else readU32(base+20).toLong()
            val shLink = if(is64) readU32(base+40) else readU32(base+24)
            val shInfo = if(is64) readU32(base+44) else readU32(base+28)
            val shAddralign = if(is64) readU64(base+48) else readU32(base+32).toLong()
            val shEntsize = if(is64) readU64(base+56) else readU32(base+36).toLong()

            val name = if (shstrtabOff > 0 && nameIdx > 0u && nameIdx < data.size) {
                val start = (shstrtabOff + nameIdx).toInt()
                if (start < data.size) readString(start, 64) else "?"
            } else "?"

            val entry = SectionHeader(name, typeStr, shFlags, shAddr, shOffset, shSize, shLink, shInfo, shAddralign, shEntsize)
            result.add(entry)
            sections.add(entry)
        }
        return result
    }

    // ===== SYMBOL TABLE =====
    fun parseSymbolTable(): List<SymbolEntry> {
        return symbols.toList()
    }

    fun getDynsymSymbols(): List<SymbolEntry> {
        val dynsymSec = sections.find { it.shType == "SHT_DYNSYM" } ?: return emptyList()
        return parseSymbolsFromSection(dynsymSec)
    }

    fun getSymtabSymbols(): List<SymbolEntry> {
        val symtabSec = sections.find { it.shType == "SHT_SYMTAB" } ?: return emptyList()
        return parseSymbolsFromSection(symtabSec)
    }

    // ===== DYNAMIC SECTION =====
    fun parseDynamicSection(): List<DynamicEntry> {
        val dynSec = sections.find { it.shType == "SHT_DYNAMIC" } ?: return emptyList()
        val result = mutableListOf<DynamicEntry>()
        val entrySize = if(is64) 16 else 8
        val count = (dynSec.shSize / entrySize).toInt()
        for (i in 0 until count) {
            val base = dynSec.shOffset + i.toLong() * entrySize
            if (base + entrySize > data.size) break
            val tag = if(is64) readU64(base) else readU32(base).toLong()
            val val_ = if(is64) readU64(base+8) else readU32(base+4).toLong()
            val tagStr = when(tag) {
                1 -> "DT_NEEDED"; 5 -> "DT_STRTAB"; 6 -> "DT_SYMTAB"; 7 -> "DT_RELA"
                9 -> "DT_RELAENT"; 10 -> "DT_STRSZ"; 11 -> "DT_SYMENT"; 12 -> "DT_INIT"
                13 -> "DT_FINI"; 15 -> "DT_SONAME"; 16 -> "DT_RPATH"; 17 -> "DT_SYMBOLIC"
                18 -> "DT_REL"; 20 -> "DT_PLTGOT"; 21 -> "DT_PLTRELSZ"; 22 -> "DT_PLTREL"
                23 -> "DT_DEBUG"; 24 -> "DT_TEXTREL"; 25 -> "DT_JMPREL"; 26 -> "DT_BIND_NOW"
                30 -> "DT_FLAGS_1"; 0x6ffffef5 -> "DT_GNU_HASH"; 0x6ffffff0 -> "DT_VERSYM"
                0x6ffffffe -> "DT_VERNEED"; 0x6fffffff -> "DT_VERNEEDNUM"
                else -> "DT_$tag"
            }
            val valStr = if (tag == 1L) {
                // DT_NEEDED: value is offset into dynstr
                if (dynstr.isNotEmpty() && val_ < dynstr.size) {
                    val start = val_.toInt()
                    readStringFromBytes(dynstr, start, 128)
                } else "$val_"
            } else "$val_"
            result.add(DynamicEntry(tagStr, val_, valStr))
        }
        return result
    }

    // ===== RELOCATIONS =====
    fun parseRelocations(): List<RelocationEntry> {
        val result = mutableListOf<RelocationEntry>()
        for (sec in sections) {
            if (sec.shType == "SHT_REL" || sec.shType == "SHT_RELA") {
                val entrySize = if (sec.shType == "SHT_RELA") (if(is64) 24 else 12) else (if(is64) 16 else 8)
                val count = (sec.shSize / entrySize).toInt()
                for (i in 0 until count) {
                    val base = sec.shOffset + i.toLong() * entrySize
                    if (base + entrySize > data.size) break
                    val rOffset = if(is64) readU64(base) else readU32(base).toLong()
                    val info = if(is64) readU64(base+8) else readU32(base+4).toLong()
                    val rType = (info and if(is64)0xFFFFFFFFL else 0xFFL).toInt()
                    val rSymIdx = (info shr if(is64)32 else 8).toInt()
                    val typeStr = relocTypeStr(rType)
                    val symName = if (rSymIdx < symbols.size) symbols[rSymIdx].stName else "sym[$rSymIdx]"
                    val rAddend = if (sec.shType == "SHT_RELA") {
                        if(is64) readS64(base+16) else readS32(base+8)
                    } else 0L
                    result.add(RelocationEntry(rOffset, typeStr, symName, rAddend))
                }
            }
        }
        return result
    }

    // ===== GOT & PLT =====
    fun parseGotPlt(): List<GotPltEntry> {
        val result = mutableListOf<GotPltEntry>()
        val gotSec = sections.find { it.shName == ".got.plt" || it.shName == ".got" } ?: return result
        val dynsym = getDynsymSymbols()
        val entrySize = if(is64) 8u else 4u
        val count = (gotSec.shSize / entrySize.toLong()).toInt()
        for (i in 0 until count) {
            val addr = gotSec.shAddr + i.toLong() * entrySize.toLong()
            val off = gotSec.shOffset + i.toLong() * entrySize.toLong()
            if (off + entrySize > data.size) break
            val value = if(is64) readU64(off) else readU32(off).toLong()
            val funcName = if (i < dynsym.size) dynsym[i].stName else "entry_$i"
            result.add(GotPltEntry(i, addr, value, funcName))
        }
        return result
    }

    // ===== XREF =====
    fun findXrefs(targetAddr: Long): List<Long> {
        val results = mutableListOf<Long>()
        val h = parseHeader()
        // Simple: scan .text for calls/branches to target
        val textSec = sections.find { it.shName == ".text" } ?: return results
        val textStart = textSec.shOffset
        val textEnd = minOf(textStart + textSec.shSize, data.size.toLong())
        for (a in textStart until textEnd step 4) {
            if (a + 4 > data.size) break
            val insn = readU32(a)
            // ARM64 BL: xxxx 1001 01xx xxxx xxxxx xxxxx xxxxx
            // ARM64 B:  xxxx 0001 01xx xxxx xxxxx xxxxx xxxxx
            val opc = (insn shr 26) and 0x3F
            if (opc == 0x25 || opc == 0x05) { // BL or B
                val imm26 = insn and 0x3FFFFFF
                val signExt = if (imm26 and 0x2000000 != 0) (imm26 or 0xFC000000.toInt()).toLong() else imm26.toLong()
                val branchTarget = (a + signExt * 4) + textSec.shAddr - textStart
                if (branchTarget == targetAddr || branchTarget - textSec.shAddr == targetAddr) {
                    results.add(textSec.shAddr + a - textStart)
                }
            }
        }
        return results
    }

    // ===== HELPERS ======
    private fun parseStringTables() {
        val h = parseHeader()
        if (h.eShnum == 0) return
        // Find .strtab and .dynstr
        for (sec in parseSectionHeaders()) {
            if (sec.shType == "SHT_STRTAB") {
                if (sec.shName == ".strtab" || strtab.isEmpty()) {
                    strtab = readSectionData(sec)
                }
                if (sec.shName == ".dynstr") {
                    dynstr = readSectionData(sec)
                }
            }
        }
    }

    private fun parseAllSymbols() {
        symbols.clear()
        for (sec in sections) {
            if (sec.shType == "SHT_SYMTAB" || sec.shType == "SHT_DYNSYM") {
                symbols.addAll(parseSymbolsFromSection(sec))
            }
        }
    }

    private fun parseSymbolsFromSection(sec: SectionHeader): List<SymbolEntry> {
        val result = mutableListOf<SymbolEntry>()
        val entrySize = if(is64) 24 else 16
        val symStrtab = if (sec.shLink < sections.size) sections[sec.shLink] else null
        val strtabData = if (symStrtab != null) readSectionData(symStrtab) else dynstr
        val count = (sec.shSize / entrySize).toInt()
        for (i in 0 until count) {
            val base = sec.shOffset + i.toLong() * entrySize
            if (base + entrySize > data.size) break
            val nameIdx = readU32(base)
            val info = if(is64) data[(base+4).toInt()].toInt() and 0xFF else data[(base+4).toInt()].toInt() and 0xFF
            val bind = when(info shr 4) { 0 -> "STB_LOCAL"; 1 -> "STB_GLOBAL"; 2 -> "STB_WEAK"; else -> "BIND_${info shr 4}" }
            val type = when(info and 0xF) { 0 -> "STT_NOTYPE"; 1 -> "STT_OBJECT"; 2 -> "STT_FUNC"; 3 -> "STT_SECTION"; 4 -> "STT_FILE"; else -> "TYPE_${info and 0xF}" }
            val value = if(is64) readU64(base+8) else readU32(base+8).toLong()
            val size = if(is64) readU64(base+16) else readU32(base+12).toLong()
            val other = if(is64) data[(base+5).toInt()].toInt() and 0xFF else data[(base+5).toInt()].toInt() and 0xFF
            val vis = when(other and 0x3) { 0 -> "STV_DEFAULT"; 1 -> "STV_INTERNAL"; 2 -> "STV_HIDDEN"; 3 -> "STV_PROTECTED"; else -> "UNK" }
            val shndx = if(is64) readU16(base+6) else readU16(base+14)
            val ndxStr = when(shndx) { 0 -> "UND"; 0xFFF1 -> "ABS"; 0xFFF2 -> "COMMON"; else -> "$shndx" }
            val name = if (strtabData.isNotEmpty() && nameIdx < strtabData.size) readStringFromBytes(strtabData, nameIdx.toInt(), 128) else "sym_$i"
            result.add(SymbolEntry(name, value, size, info.toString(), bind, type, vis, ndxStr))
        }
        return result
    }

    private fun readSectionData(sec: SectionHeader): ByteArray {
        val off = sec.shOffset.toInt()
        val size = sec.shSize.toInt()
        if (off + size > data.size || off < 0 || size < 0) return byteArrayOf()
        return data.copyOfRange(off, off + size)
    }

    private fun readString(offset: Int, maxLen: Int): String {
        val sb = StringBuilder()
        for (i in offset until minOf(offset + maxLen, data.size)) {
            if (data[i] == 0.toByte()) break
            sb.append(data[i].toInt().toChar())
        }
        return sb.toString()
    }

    private fun readStringFromBytes(bytes: ByteArray, offset: Int, maxLen: Int): String {
        val sb = StringBuilder()
        for (i in offset until minOf(offset + maxLen, bytes.size)) {
            if (bytes[i] == 0.toByte()) break
            sb.append(bytes[i].toInt().toChar())
        }
        return sb.toString()
    }

    private fun startsWith(vararg bytes: Byte): Boolean {
        if (data.size < bytes.size) return false
        return bytes.indices.all { data[it] == bytes[it] }
    }

    private fun readU16(off: Long): Int = readU16(off.toInt())
    private fun readU16(off: Int): Int {
        if (off + 2 > data.size) return 0
        return if (isLE) (data[off].toInt() and 0xFF) or ((data[off+1].toInt() and 0xFF) shl 8)
        else ((data[off].toInt() and 0xFF) shl 8) or (data[off+1].toInt() and 0xFF)
    }
    private fun readU32(off: Long): UInt = readU32(off.toInt())
    private fun readU32(off: Int): UInt {
        if (off + 4 > data.size) return 0u
        return if (isLE) ((data[off].toInt() and 0xFF).toUInt()) or
            ((data[off+1].toInt() and 0xFF).toUInt() shl 8) or
            ((data[off+2].toInt() and 0xFF).toUInt() shl 16) or
            ((data[off+3].toInt() and 0xFF).toUInt() shl 24)
        else ((data[off].toInt() and 0xFF).toUInt() shl 24) or
            ((data[off+1].toInt() and 0xFF).toUInt() shl 16) or
            ((data[off+2].toInt() and 0xFF).toUInt() shl 8) or
            ((data[off+3].toInt() and 0xFF).toUInt())
    }
    private fun readU64(off: Long): Long {
        if (off + 8 > data.size) return 0L
        return if (isLE) readU32(off.toInt()).toLong() or (readU32((off+4).toInt()).toLong() shl 32)
        else (readU32(off.toInt()).toLong() shl 32) or readU32((off+4).toInt()).toLong()
    }
    private fun readS64(off: Long): Long = readU64(off)
    private fun readS32(off: Long): Long = readU32(off.toInt()).toLong()

    private fun relocTypeStr(type: Int): String = when(type) {
        0 -> "R_ARM_NONE"; 1 -> "R_ARM_PC24"; 2 -> "R_ARM_ABS32"; 23 -> "R_ARM_JUMP_SLOT"; 257 -> "R_AARCH64_ABS64"
        258 -> "R_AARCH64_GLOB_DAT"; 260 -> "R_AARCH64_JUMP_SLOT"; 261 -> "R_AARCH64_RELATIVE"
        1 -> "R_AARCH64_ABS32"; 40 -> "R_X86_64_GLOB_DAT"; 41 -> "R_X86_64_JUMP_SLOT"; 42 -> "R_X86_64_RELATIVE"
        7 -> "R_X86_64_64"; 6 -> "R_X86_64_PC32"
        else -> "R_TYPE_$type"
    }
}
