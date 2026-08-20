package com.oprek.tool.core

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val type: FileType,
    val md5: String,
    val sha256: String,
    val magic: String,
    val lastModified: Long
)

enum class FileType { ELF, APK, SH, SO, BIN, DEX, UNKNOWN }

object FileAnalyzer {

    fun getFileInfo(file: File): FileInfo {
        val bytes = file.readBytes()
        val type = detectType(file.name, bytes)
        return FileInfo(
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            type = type,
            md5 = bytes.md5(),
            sha256 = bytes.sha256(),
            magic = getMagic(bytes),
            lastModified = file.lastModified()
        )
    }

    fun detectType(name: String, bytes: ByteArray): FileType {
        val ext = name.substringAfterLast('.').lowercase()
        return when {
            ext == "apk" || (bytes.size > 4 && bytes.sliceArray(0..3).contentEquals(byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte()))) -> FileType.APK
            ext == "so" || (bytes.size > 4 && bytes.startsWith(byteArrayOf(0x7F.toByte(), 0x45.toByte(), 0x4C.toByte(), 0x46.toByte())) && ext == "so") -> FileType.SO
            bytes.size > 4 && bytes.startsWith(byteArrayOf(0x7F.toByte(), 0x45.toByte(), 0x4C.toByte(), 0x46.toByte())) -> FileType.ELF
            ext == "sh" || ext == "bash" -> FileType.SH
            ext == "dex" -> FileType.DEX
            ext == "bin" || ext == "dat" || ext == "img" -> FileType.BIN
            else -> FileType.UNKNOWN
        }
    }

    fun extractStrings(file: File, minLength: Int = 4): List<StringPair> {
        val bytes = file.readBytes()
        val strings = mutableListOf<StringPair>()
        val sb = StringBuilder()

        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 0x20..0x7E) {
                sb.append(b.toChar())
            } else {
                if (sb.length >= minLength) {
                    strings.add(StringPair(i.toLong() - sb.length, sb.toString()))
                }
                sb.clear()
            }
        }
        return strings
    }

    fun getHexDump(file: File, offset: Long = 0, length: Int = 512): HexChunk {
        val raf = RandomAccessFile(file, "r")
        raf.seek(offset)
        val buf = ByteArray(minOf(length.toLong(), raf.length() - offset).toInt())
        raf.readFully(buf)
        raf.close()
        return HexChunk(offset, buf)
    }

    fun getHexDumpFull(file: File, maxBytes: Int = 65536): HexChunk {
        val size = minOf(file.length().toInt(), maxBytes)
        val bytes = file.readBytes().copyOf(size)
        return HexChunk(0, bytes)
    }

    fun patchByte(file: File, offset: Long, newByte: Byte): Boolean {
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(offset)
                raf.write(newByte.toInt())
            }
            true
        } catch (e: Exception) { false }
    }

    fun patchBytes(file: File, offset: Long, newBytes: ByteArray): Boolean {
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(offset)
                raf.write(newBytes)
            }
            true
        } catch (e: Exception) { false }
    }

    // ELF analysis
    fun parseElfHeaders(file: File): ElfInfo {
        val bytes = file.readBytes()
        if (bytes.size < 64 || !bytes.startsWith(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))) {
            return ElfInfo.invalid("Not an ELF file")
        }
        val is64 = bytes[4] == 2.toByte()
        val isLE = bytes[5] == 1.toByte()
        val endian = if (isLE) "Little Endian" else "Big Endian"

        return if (is64) {
            val entry = readU64(bytes, 24, isLE)
            val phoff = readU64(bytes, 32, isLE)
            val shoff = readU64(bytes, 40, isLE)
            val phnum = readU16(bytes, 56, isLE).toInt()
            val shnum = readU16(bytes, 60, isLE).toInt()
            ElfInfo(true, endian, entry, phoff, shoff, phnum, shnum, bytes.size.toLong())
        } else {
            val entry = readU32(bytes, 24, isLE).toLong()
            val phoff = readU32(bytes, 28, isLE).toLong()
            val shoff = readU32(bytes, 32, isLE).toLong()
            val phnum = readU16(bytes, 44, isLE).toInt()
            val shnum = readU16(bytes, 48, isLE).toInt()
            ElfInfo(false, endian, entry, phoff, shoff, phnum, shnum, bytes.size.toLong())
        }
    }

    fun parseElfSections(file: File): List<ElfSection> {
        val bytes = file.readBytes()
        if (bytes.size < 64 || !bytes.startsWith(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))) return emptyList()
        val is64 = bytes[4] == 2.toByte()
        val isLE = bytes[5] == 1.toByte()
        val sections = mutableListOf<ElfSection>()

        val shoff = if (is64) readU64(bytes, 40, isLE) else readU32(bytes, 32, isLE).toLong()
        val shnum = if (is64) readU16(bytes, 60, isLE) else readU16(bytes, 48, isLE)
        val shentsize = if (is64) 64 else 40

        for (i in 0 until shnum) {
            val base = shoff + i.toLong() * shentsize
            if (base + shentsize > bytes.size) break
            val nameIdx = readU32(bytes, base.toInt(), isLE)
            val offset = if (is64) readU64(bytes, base.toInt() + 24, isLE) else readU32(bytes, base.toInt() + 16, isLE).toLong()
            val size = if (is64) readU64(bytes, base.toInt() + 32, isLE) else readU32(bytes, base.toInt() + 20, isLE).toLong()
            val sectType = readU32(bytes, base.toInt() + 4, isLE).toInt()

            val name = if (nameIdx > 0u && nameIdx.toInt() < bytes.size) {
                val start = nameIdx.toInt()
                var end = start
                while (end < bytes.size && bytes[end] != 0.toByte()) end++
                String(bytes, start, (end - start).coerceAtMost(64))
            } else "?"

            sections.add(ElfSection(name, offset, size, sectType))
        }
        return sections
    }

    // APK analysis
    fun parseApkInfo(file: File): ApkInfo {
        val bytes = file.readBytes()
        if (bytes.size < 4 || !bytes.startsWith(byteArrayOf(0x50, 0x4B, 0x03, 0x04))) {
            return ApkInfo.invalid("Not a valid APK")
        }

        val entries = mutableListOf<ApkEntry>()
        var pos = 0
        while (pos + 30 <= bytes.size) {
            if (readU32LE(bytes, pos) != 0x04034B50u) break
            val compMethod = readU16LE(bytes, pos + 8)
            val compSize = readU32LE(bytes, pos + 18).toInt()
            val uncompSize = readU32LE(bytes, pos + 22).toInt()
            val nameLen = readU16LE(bytes, pos + 26).toInt()
            val extraLen = readU16LE(bytes, pos + 28).toInt()
            val name = if (pos + 30 + nameLen <= bytes.size) {
                String(bytes, pos + 30, nameLen)
            } else "?"
            entries.add(ApkEntry(name, compMethod, compSize.toLong(), uncompSize.toLong()))
            pos += 30 + nameLen + extraLen + compSize
            if (pos <= 0) break // overflow guard
        }

        val hasDex = entries.any { it.name.endsWith(".dex") }
        val hasNativeLibs = entries.any { it.name.contains("lib/") && it.name.endsWith(".so") }
        val hasManifest = entries.any { it.name == "AndroidManifest.xml" }

        return ApkInfo(
            entries = entries,
            totalEntries = entries.size,
            hasDex = hasDex,
            hasNativeLibs = hasNativeLibs,
            hasManifest = hasManifest,
            size = file.length()
        )
    }

    // Helpers
    private fun ByteArray.md5(): String = MessageDigest.getInstance("MD5").digest(this).joinToString("") { "%02x".format(it) }
    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        return this.sliceArray(prefix.indices).contentEquals(prefix)
    }
    private fun getMagic(b: ByteArray): String {
        if (b.size < 4) return "Unknown"
        return when {
            b.startsWith(byteArrayOf(0x7F.toByte(), 0x45.toByte(), 0x4C.toByte(), 0x46.toByte())) -> "ELF ${if (b[4]==2.toByte()) "64-bit" else "32-bit"}"
            b.startsWith(byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())) -> "ZIP/APK"
            b.startsWith(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())) -> "DEX/Class"
            b.startsWith(byteArrayOf(0x23.toByte(), 0x21.toByte())) -> "Shell Script (#!/)"
            b[0] == 0x4D.toByte() && b[1] == 0x5A.toByte() -> "PE/EXE"
            b.size > 4 && b[0] == 0xFE.toByte() && b[1] == 0xED.toByte() && b[2] == 0xFA.toByte() && b[3] == 0xCE.toByte() -> "Mach-O 32-bit"
            b.size > 4 && b[0] == 0xFE.toByte() && b[1] == 0xED.toByte() && b[2] == 0xFA.toByte() && b[3] == 0xCF.toByte() -> "Mach-O 64-bit"
            b.size > 4 && b[0] == 0x64.toByte() && b[1] == 0x65.toByte() && b[2] == 0x78.toByte() && b[3] == 0x0A.toByte() -> "DEX"
            b.size > 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && b[2] == 0x03.toByte() && b[3] == 0x04.toByte() -> "ZIP/APK/JAR"
            b.size > 2 && b[0] == 0x23.toByte() && b[1] == 0x21.toByte() -> "Shell Script (#!)"
            b.size > 4 && b[0] == 0x42.toByte() && b[1] == 0x5A.toByte() && b[2] == 0x68.toByte() -> "BZip2"
            b.size > 4 && b[0] == 0x1F.toByte() && b[1] == 0x8B.toByte() -> "GZip"
            b.size > 4 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() -> "PNG"
            b.size > 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "JPEG"
            b.size > 4 && b[0] == 0x47.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte() -> "GIF" 
            else -> "Unknown (${b.take(4).joinToString("") { "%02x".format(it) }})"
        }
    }
    private fun readU16(b: ByteArray, off: Int, le: Boolean) = if (le) (b[off].toInt() and 0xFF or ((b[off+1].toInt() and 0xFF) shl 8)) else ((b[off].toInt() and 0xFF) shl 8 or (b[off+1].toInt() and 0xFF))
    private fun readU32(b: ByteArray, off: Int, le: Boolean): UInt {
        return if (le) ((b[off].toInt() and 0xFF).toUInt() or ((b[off+1].toInt() and 0xFF).toUInt() shl 8) or ((b[off+2].toInt() and 0xFF).toUInt() shl 16) or ((b[off+3].toInt() and 0xFF).toUInt() shl 24))
        else (((b[off].toInt() and 0xFF).toUInt() shl 24) or ((b[off+1].toInt() and 0xFF).toUInt() shl 16) or ((b[off+2].toInt() and 0xFF).toUInt() shl 8) or ((b[off+3].toInt() and 0xFF).toUInt()))
    }
    private fun readU64(b: ByteArray, off: Int, le: Boolean): Long {
        return if (le) readU32(b, off, le).toLong() or (readU32(b, off + 4, le).toLong() shl 32)
        else (readU32(b, off, le).toLong() shl 32) or readU32(b, off + 4, le).toLong()
    }
    private fun readU32LE(b: ByteArray, off: Int) = (b[off].toInt() and 0xFF).toUInt() or ((b[off+1].toInt() and 0xFF).toUInt() shl 8) or ((b[off+2].toInt() and 0xFF).toUInt() shl 16) or ((b[off+3].toInt() and 0xFF).toUInt() shl 24)
    private fun readU16LE(b: ByteArray, off: Int) = (b[off].toInt() and 0xFF) or ((b[off+1].toInt() and 0xFF) shl 8)
}

data class StringPair(val offset: Long, val value: String)

data class HexChunk(val startOffset: Long, val data: ByteArray) {
    fun toHexLines(): List<String> {
        val lines = mutableListOf<String>()
        for (i in data.indices step 16) {
            val addr = "%08X".format(startOffset + i)
            val hex = StringBuilder()
            val asc = StringBuilder()
            for (j in 0 until 16) {
                if (i + j < data.size) {
                    hex.append("%02X ".format(data[i + j]))
                    val c = data[i + j].toInt() and 0xFF
                    asc.append(if (c in 0x20..0x7E) c.toChar() else '.')
                } else {
                    hex.append("   ")
                    asc.append(' ')
                }
                if (j == 7) hex.append(' ')
            }
            lines.add("$addr  $hex |$asc|")
        }
        return lines
    }
}

data class ElfInfo(
    val is64Bit: Boolean,
    val endian: String,
    val entryPoint: Long,
    val phOffset: Long,
    val shOffset: Long,
    val phCount: Int,
    val shCount: Int,
    val fileSize: Long
) {
    companion object {
        fun invalid(reason: String) = ElfInfo(false, reason, 0, 0, 0, 0, 0, 0)
    }
    val isValid get() = entryPoint > 0
}

data class ElfSection(val name: String, val offset: Long, val size: Long, val type: Int) {
    val typeStr get() = when(type) { 0->"NULL" 1->"PROGBITS" 2->"SYMTAB" 3->"STRTAB" 4->"RELA" 5->"HASH" 6->"DYNAMIC" 7->"NOTE" 8->"NOBITS" 9->"REL" 11->"DYNSYM" 14->"INIT_ARRAY" 15->"FINI_ARRAY" 16->"PREINIT_ARRAY" else->"TYPE_$type" }
}

data class ApkInfo(
    val entries: List<ApkEntry>,
    val totalEntries: Int,
    val hasDex: Boolean,
    val hasNativeLibs: Boolean,
    val hasManifest: Boolean,
    val size: Long
) {
    companion object {
        fun invalid(reason: String) = ApkInfo(emptyList(), 0, false, false, false, 0)
    }
}

data class ApkEntry(val name: String, val method: Int, val compressedSize: Long, val uncompressedSize: Long) {
    val methodStr get() = when(method) { 0->"STORED" 8->"DEFLATED" else->"METHOD_$method" }
}
