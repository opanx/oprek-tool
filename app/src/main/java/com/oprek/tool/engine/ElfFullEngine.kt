package com.oprek.tool.engine

import java.io.File

data class ElfFullHeader(
    val magic: String, val elfClass: String, val elfData: String,
    val eType: String, val eMachine: String, val eVersion: Long,
    val eEntry: Long, val ePhoff: Long, val eShoff: Long,
    val eFlags: Long, val eEhsize: Int, val ePhentsize: Int,
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

data class DynamicEntry(val dTag: String, val dVal: Long, val dValStr: String)
data class RelocationEntry(val rOffset: Long, val rType: String, val rSym: String, val rAddend: Long)
data class GotPltEntry(val index: Int, val address: Long, val value: Long, val funcName: String)

object ElfFullEngine {
    private var data: ByteArray = byteArrayOf()
    private var is64 = false
    private var isLE = true
    private var dynstr: ByteArray = byteArrayOf()
    private var symbols: MutableList<SymbolEntry> = mutableListOf()
    private var sections: MutableList<SectionHeader> = mutableListOf()

    fun load(file: File) { load(file.readBytes()) }

    fun load(bytes: ByteArray) {
        data = bytes; if (bytes.size < 16) return
        is64 = bytes[4] == 2.toByte(); isLE = bytes[5] == 1.toByte()
        parseSectionHeaders(); parseAllSymbols()
    }

    private fun isElf(): Boolean = data.size >= 4 && data[0] == 0x7F.toByte() && data[1] == 0x45.toByte() && data[2] == 0x4C.toByte() && data[3] == 0x46.toByte()

    fun parseHeader(): ElfFullHeader {
        if (!isElf()) return ElfFullHeader("N/A","N/A","N/A","N/A","N/A",0,0,0,0,0,0,0,0,0,0,"Invalid ELF")
        val machine = rU16(18)
        val machineStr = when(machine) { 0x03->"x86"; 0x08->"MIPS"; 0x14->"ARM"; 0x28->"AArch64"; 0x3E->"x86_64"; 0xB7->"AArch64"; else->"0x${machine.toString(16)}" }
        val eType = when(val t = rU16(16)) { 1->"ET_REL"; 2->"ET_EXEC"; 3->"ET_DYN"; 4->"ET_CORE"; else->"0x${t.toString(16)}" }
        val arch = when { is64 && machine==0xB7->"ARM64"; !is64 && machine==0x28->"ARM"; is64 && machine==0x3E->"x86_64"; !is64 && machine==0x03->"x86"; else->"$machineStr ${if(is64)"64"else"32"}" }
        return ElfFullHeader("7F 45 4C 46", if(is64)"ELF64" else "ELF32", if(isLE)"Little" else "Big",
            eType, "$machineStr($machine)", rU32(20).toLong(),
            if(is64) rU64(24) else rU32(24).toLong(),
            if(is64) rU64(32) else rU32(28).toLong(),
            if(is64) rU64(40) else rU32(32).toLong(),
            rU32(if(is64)48 else 36).toLong(),
            if(is64) rU16(52) else rU16(40),
            if(is64) rU16(54) else rU16(42),
            if(is64) rU16(56) else rU16(44),
            if(is64) rU16(58) else rU16(46),
            if(is64) rU16(60) else rU16(48),
            if(is64) rU16(62) else rU16(50), arch)
    }

    fun parseProgramHeaders(): List<ProgramHeader> {
        if (!isElf()) return emptyList()
        val phoff = if(is64) rU64(32) else rU32(28).toLong()
        val phnum = if(is64) rU16(56) else rU16(44)
        val phentsize = if(is64) rU16(54) else rU16(42)
        val result = mutableListOf<ProgramHeader>()
        for (i in 0 until phnum) {
            val base = phoff + i.toLong() * phentsize
            if (base + phentsize > data.size) break
            val pType = when(val t = rU32At(base)) { 0L->"PT_NULL"; 1L->"PT_LOAD"; 2L->"PT_DYNAMIC"; 3L->"PT_INTERP"; 4L->"PT_NOTE"; 6L->"PT_PHDR"; 0x6474e550L->"PT_GNU_EH_FRAME"; 0x6474e551L->"PT_GNU_STACK"; 0x6474e552L->"PT_GNU_RELRO"; else->"0x${t.toString(16)}" }
            val pOff = if(is64) rU64At(base+8) else rU32At(base+4)
            val pV = if(is64) rU64At(base+16) else rU32At(base+8)
            val pP = if(is64) rU64At(base+24) else rU32At(base+12)
            val pFs = if(is64) rU64At(base+32) else rU32At(base+16)
            val pMs = if(is64) rU64At(base+40) else rU32At(base+20)
            val flags = if(is64) { val f=rU32At(base+4).toInt(); buildString{if(f and 4!=0)append('R');if(f and 2!=0)append('W');if(f and 1!=0)append('X')} }
            else { val f=rU32At(base+24).toInt(); buildString{if(f and 4!=0)append('R');if(f and 2!=0)append('W');if(f and 1!=0)append('X')} }
            val pA = if(is64) rU64At(base+48) else rU32At(base+28)
            result.add(ProgramHeader(pType, pOff, pV, pP, pFs, pMs, flags, pA))
        }
        return result
    }

    fun parseSectionHeaders(): List<SectionHeader> {
        if (!isElf()) return emptyList()
        val h = parseHeader(); val result = mutableListOf<SectionHeader>(); sections.clear()
        val shstrtabOff = if(h.eShstrndx in 1 until h.eShnum) { val b=h.eShoff+h.eShstrndx.toLong()*h.eShentsize; if(is64)rU64At(b+24) else rU32At(b+16) } else 0L
        for (i in 0 until h.eShnum) {
            val base = h.eShoff + i.toLong() * h.eShentsize
            if (base + h.eShentsize > data.size) break
            val nameIdx = rU32At(base)
            val shType = rU32At(base+4)
            val typeStr = when(shType) { 0L->"SHT_NULL"; 1L->"SHT_PROGBITS"; 2L->"SHT_SYMTAB"; 3L->"SHT_STRTAB"; 4L->"SHT_RELA"; 6L->"SHT_DYNAMIC"; 7L->"SHT_NOTE"; 8L->"SHT_NOBITS"; 9L->"SHT_REL"; 0x0bL->"SHT_DYNSYM"; 0x0eL->"SHT_INIT_ARRAY"; 0x0fL->"SHT_FINI_ARRAY"; else->"SHT_$shType" }
            val shFlags = if(is64) rU64At(base+8) else rU32At(base+8)
            val shAddr = if(is64) rU64At(base+16) else rU32At(base+12)
            val shOffset = if(is64) rU64At(base+24) else rU32At(base+16)
            val shSize = if(is64) rU64At(base+32) else rU32At(base+20)
            val shLink = rU32At(base + if(is64)40 else 24).toInt()
            val shInfo = rU32At(base + if(is64)44 else 28).toInt()
            val shAddralign = if(is64) rU64At(base+48) else rU32At(base+32)
            val shEntsize = if(is64) rU64At(base+56) else rU32At(base+36)
            val name = if (shstrtabOff > 0 && nameIdx > 0) { val s=(shstrtabOff+nameIdx).toInt(); if(s<data.size) readStr(s, 64) else "?" } else "?"
            val entry = SectionHeader(name, typeStr, shFlags, shAddr, shOffset, shSize, shLink, shInfo, shAddralign, shEntsize)
            result.add(entry); sections.add(entry)
        }
        return result
    }

    fun parseSymbolTable(): List<SymbolEntry> = symbols.toList()

    fun getDynsymSymbols(): List<SymbolEntry> {
        val sec = sections.find { it.shType == "SHT_DYNSYM" } ?: return emptyList()
        return parseSymbolsFromSection(sec, dynstr)
    }

    fun getSymtabSymbols(): List<SymbolEntry> {
        val sec = sections.find { it.shType == "SHT_SYMTAB" } ?: return emptyList()
        val strtab = sections.getOrNull(sec.shLink)
        val strtabData = if (strtab != null && strtab.shOffset + strtab.shSize <= data.size) data.copyOfRange(strtab.shOffset.toInt(), (strtab.shOffset + strtab.shSize).toInt()) else byteArrayOf()
        return parseSymbolsFromSection(sec, strtabData)
    }

    fun parseDynamicSection(): List<DynamicEntry> {
        val sec = sections.find { it.shType == "SHT_DYNAMIC" } ?: return emptyList()
        val result = mutableListOf<DynamicEntry>(); val es = if(is64)16 else 8; val cnt = (sec.shSize/es).toInt()
        for (i in 0 until cnt) {
            val base = sec.shOffset + i.toLong() * es; if (base + es > data.size) break
            val tag = if(is64) rU64At(base) else rU32At(base); val v = if(is64) rU64At(base+8) else rU32At(base+4)
            val tagStr = when(tag) { 1L->"DT_NEEDED"; 6L->"DT_SYMTAB"; 7L->"DT_RELA"; 10L->"DT_STRSZ"; 12L->"DT_INIT"; 13L->"DT_FINI"; 15L->"DT_SONAME"; 16L->"DT_RPATH"; 20L->"DT_PLTGOT"; 25L->"DT_JMPREL"; else->"DT_$tag" }
            val vs = if (tag == 1L && dynstr.isNotEmpty() && v < dynstr.size) readStrFrom(dynstr, v.toInt(), 128) else "$v"
            result.add(DynamicEntry(tagStr, v, vs))
        }
        return result
    }

    fun parseRelocations(): List<RelocationEntry> {
        val result = mutableListOf<RelocationEntry>()
        for (sec in sections) {
            if (sec.shType != "SHT_REL" && sec.shType != "SHT_RELA") continue
            val es = if (sec.shType == "SHT_RELA") (if(is64)24 else 12) else (if(is64)16 else 8)
            val cnt = (sec.shSize/es).toInt()
            for (i in 0 until cnt) {
                val base = sec.shOffset + i.toLong() * es; if (base + es > data.size) break
                val rOff = if(is64) rU64At(base) else rU32At(base)
                val info = if(is64) rU64At(base+8) else rU32At(base+4)
                val rType = (info and if(is64)0xFFFFFFFFL else 0xFFL).toInt()
                val rSymIdx = (info shr if(is64)32 else 8).toInt()
                val symName = if (rSymIdx < symbols.size) symbols[rSymIdx].stName else "sym[$rSymIdx]"
                val rAddend = if (sec.shType == "SHT_RELA") { if(is64) rU64At(base+16) else rU32At(base+8) } else 0L
                result.add(RelocationEntry(rOff, "R_TYPE_$rType", symName, rAddend))
            }
        }
        return result
    }

    fun parseGotPlt(): List<GotPltEntry> {
        val result = mutableListOf<GotPltEntry>()
        val got = sections.find { it.shName == ".got.plt" || it.shName == ".got" } ?: return result
        val dynsym = getDynsymSymbols(); val es = if(is64)8L else 4L; val cnt = (got.shSize / es).toInt()
        for (i in 0 until cnt) {
            val addr = got.shAddr + i.toLong() * es; val off = got.shOffset + i.toLong() * es
            if (off + es > data.size) break
            val value = if(is64) rU64At(off) else rU32At(off)
            val fn = if (i < dynsym.size) dynsym[i].stName else "entry_$i"
            result.add(GotPltEntry(i, addr, value, fn))
        }
        return result
    }

    fun findXrefs(targetAddr: Long): List<Long> {
        val results = mutableListOf<Long>()
        val text = sections.find { it.shName == ".text" } ?: return results
        for (a in text.shOffset until minOf(text.shOffset + text.shSize, data.size.toLong()) step 4) {
            if (a + 4 > data.size) break
            val insn = rU32At(a); val opc = (insn shr 26) and 0x3F
            if (opc == 0x25L || opc == 0x05L) {
                val imm26 = insn and 0x3FFFFFF
                val signExt = if (imm26 and 0x2000000 != 0L) (imm26 or 0xFFFFFFFFFC000000L) else imm26
                val bt = a + signExt * 4
                if (bt == targetAddr) results.add(a)
            }
        }
        return results
    }

    private fun parseAllSymbols() {
        symbols.clear()
        for (sec in sections) {
            if (sec.shType == "SHT_SYMTAB" || sec.shType == "SHT_DYNSYM") {
                val strtab = sections.getOrNull(sec.shLink)
                val strtabData = if (strtab != null && strtab.shOffset + strtab.shSize <= data.size) data.copyOfRange(strtab.shOffset.toInt(), (strtab.shOffset + strtab.shSize).toInt()) else dynstr
                symbols.addAll(parseSymbolsFromSection(sec, strtabData))
            }
        }
        // also load dynstr
        val dynstrSec = sections.find { it.shType == "SHT_STRTAB" && it.shName == ".dynstr" }
        if (dynstrSec != null && dynstrSec.shOffset + dynstrSec.shSize <= data.size) {
            dynstr = data.copyOfRange(dynstrSec.shOffset.toInt(), (dynstrSec.shOffset + dynstrSec.shSize).toInt())
        }
    }

    private fun parseSymbolsFromSection(sec: SectionHeader, strtabData: ByteArray): List<SymbolEntry> {
        val result = mutableListOf<SymbolEntry>()
        val es = if(is64) 24 else 16; val cnt = (sec.shSize / es).toInt()
        for (i in 0 until cnt) {
            val base = sec.shOffset + i.toLong() * es; if (base + es > data.size) break
            val nameIdx = rU32At(base)
            val info = data[(base+4).toInt()].toInt() and 0xFF
            val bind = when(info shr 4) { 0->"LOCAL"; 1->"GLOBAL"; 2->"WEAK"; else->"BIND_${info shr 4}" }
            val type = when(info and 0xF) { 0->"NOTYPE"; 1->"OBJECT"; 2->"FUNC"; 3->"SECTION"; 4->"FILE"; else->"TYPE_${info and 0xF}" }
            val value = if(is64) rU64At(base+8) else rU32At(base+8)
            val size = if(is64) rU64At(base+16) else rU32At(base+12)
            val other = data[(base+5).toInt()].toInt() and 0xFF
            val vis = when(other and 3) { 0->"DEFAULT"; 1->"INTERNAL"; 2->"HIDDEN"; 3->"PROTECTED"; else->"UNK" }
            val shndx = if(is64) rU16At(base+6) else rU16At(base+14)
            val ndxStr = when(shndx) { 0->"UND"; 0xFFF1->"ABS"; 0xFFF2->"COMMON"; else->"$shndx" }
            val name = if (strtabData.isNotEmpty() && nameIdx < strtabData.size) readStrFrom(strtabData, nameIdx.toInt(), 128) else "sym_$i"
            result.add(SymbolEntry(name, value, size, "$info", bind, type, vis, ndxStr))
        }
        return result
    }

    private fun readStr(off: Int, max: Int): String { val sb=StringBuilder(); for(i in off until minOf(off+max, data.size)){if(data[i]==0.toByte())break;sb.append(data[i].toInt().toChar())}; return sb.toString() }
    private fun readStrFrom(b: ByteArray, off: Int, max: Int): String { val sb=StringBuilder(); for(i in off until minOf(off+max, b.size)){if(b[i]==0.toByte())break;sb.append(b[i].toInt().toChar())}; return sb.toString() }

    private fun rU16(off: Int): Int { if(off+2>data.size)return 0; return if(isLE) (data[off].toInt() and 0xFF) or ((data[off+1].toInt() and 0xFF) shl 8) else ((data[off].toInt() and 0xFF) shl 8) or (data[off+1].toInt() and 0xFF) }
    private fun rU32(off: Int): Long { if(off+4>data.size)return 0; return if(isLE) (data[off].toLong() and 0xFF) or ((data[off+1].toLong() and 0xFF) shl 8) or ((data[off+2].toLong() and 0xFF) shl 16) or ((data[off+3].toLong() and 0xFF) shl 24) else ((data[off].toLong() and 0xFF) shl 24) or ((data[off+1].toLong() and 0xFF) shl 16) or ((data[off+2].toLong() and 0xFF) shl 8) or (data[off+3].toLong() and 0xFF) }
    private fun rU64(off: Int): Long { if(off+8>data.size)return 0; return if(isLE) rU32(off) or (rU32(off+4) shl 32) else (rU32(off) shl 32) or rU32(off+4) }
    private fun rU16At(off: Long): Int = rU16(off.toInt())
    private fun rU32At(off: Long): Long = rU32(off.toInt())
    private fun rU64At(off: Long): Long = rU64(off.toInt())
}
