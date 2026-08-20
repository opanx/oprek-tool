package com.oprek.tool.core

import java.io.File
import java.io.RandomAccessFile
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streaming file I/O - never loads entire file into memory.
 * Supports files up to 2GB with minimal memory footprint.
 */
object StreamingIO {

    const val MAX_FILE_SIZE = 200L * 1024 * 1024 // 200MB limit
    const val CHUNK_SIZE = 8192 // 8KB chunks

    /**
     * Check if file is too large to process
     */
    fun isTooLarge(file: File): Boolean = file.length() > MAX_FILE_SIZE

    /**
     * Streaming hash calculation - never loads entire file
     */
    fun hashFile(file: File, algorithm: String = "SHA-256"): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().buffered(CHUNK_SIZE).use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Streaming string extraction - memory efficient
     */
    fun extractStrings(
        file: File,
        minLength: Int = 4,
        maxStrings: Int = 10000,
        progress: ((Float) -> Unit)? = null
    ): List<StringPair> {
        val strings = mutableListOf<StringPair>()
        val sb = StringBuilder()
        var currentOffset = 0L
        val totalSize = file.length()

        file.inputStream().buffered(CHUNK_SIZE).use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            var bytesRead: Int

            while (strings.size < maxStrings) {
                bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                for (i in 0 until bytesRead) {
                    val b = buffer[i].toInt() and 0xFF
                    if (b in 0x20..0x7E) {
                        sb.append(b.toChar())
                    } else {
                        if (sb.length >= minLength) {
                            strings.add(StringPair(currentOffset + i - sb.length, sb.toString()))
                        }
                        sb.clear()
                    }
                }
                currentOffset += bytesRead

                // Report progress
                if (totalSize > 0) {
                    progress?.invoke((currentOffset.toFloat() / totalSize).coerceIn(0f, 1f))
                }
            }
        }

        // Don't forget the last string if file ends with printable chars
        if (sb.length >= minLength) {
            strings.add(StringPair(currentOffset - sb.length, sb.toString()))
        }

        // Also extract UTF-16 strings (common in Windows/Java binaries)
        var utf16Offset = 0L
        var utf16Buf = StringBuilder()
        RandomAccessFile(file, "r").use { raf ->
            val buffer = ByteArray(CHUNK_SIZE)
            raf.seek(0)
            while (utf16Offset < file.length() && strings.size < maxStrings * 2) {
                val read = raf.read(buffer)
                if (read == -1) break
                var i = 0
                while (i + 1 < read) {
                    val lo = buffer[i].toInt() and 0xFF
                    val hi = buffer[i + 1].toInt() and 0xFF
                    val codepoint = lo or (hi shl 8)
                    if (codepoint in 0x20..0x7E && hi == 0) {
                        utf16Buf.append(codepoint.toChar())
                    } else {
                        if (utf16Buf.length >= minLength) {
                            strings.add(StringPair(utf16Offset + i - utf16Buf.length * 2, "[UTF16] ${utf16Buf}"))
                        }
                        utf16Buf.clear()
                    }
                    i += 2
                }
                utf16Offset += read
            }
        }

        return strings
    }

    /**
     * Streaming hex dump - reads chunks on demand
     */
    fun hexDump(file: File, offset: Long, length: Int): HexChunk {
        val raf = RandomAccessFile(file, "r")
        raf.seek(offset)
        val actualLength = minOf(length.toLong(), raf.length() - offset, CHUNK_SIZE * 8L).toInt()
        val buf = ByteArray(actualLength)
        var totalRead = 0
        while (totalRead < actualLength) {
            val read = raf.read(buf, totalRead, actualLength - totalRead)
            if (read == -1) break
            totalRead += read
        }
        raf.close()
        return HexChunk(offset, buf.copyOf(totalRead))
    }

    /**
     * Streaming pattern search - find bytes without loading entire file
     */
    fun searchBytes(
        file: File,
        pattern: ByteArray,
        startOffset: Long = 0,
        maxResults: Int = 1000,
        progress: ((Float) -> Unit)? = null
    ): List<Long> {
        val results = mutableListOf<Long>()
        val totalSize = file.length()
        val patternLen = pattern.size
        if (patternLen == 0 || totalSize < patternLen) return results

        // Read with overlap for pattern matching across chunks
        val overlap = patternLen - 1
        var fileOffset = startOffset

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(startOffset)
            var prevChunk = ByteArray(0)

            while (fileOffset < totalSize && results.size < maxResults) {
                val chunkSize = minOf(CHUNK_SIZE.toLong(), totalSize - fileOffset).toInt()
                val chunk = ByteArray(chunkSize + overlap)
                raf.readFully(chunk, 0, chunkSize)

                // Combine with previous chunk's tail for overlap
                val searchArea = if (prevChunk.isNotEmpty()) {
                    prevChunk.takeLast(overlap).toByteArray() + chunk.copyOf(chunkSize)
                } else {
                    chunk.copyOf(chunkSize)
                }

                // Search in combined area
                var searchStart = if (prevChunk.isNotEmpty()) overlap else 0
                while (searchStart <= searchArea.size - patternLen) {
                    if (searchArea.sliceArray(searchStart until searchStart + patternLen).contentEquals(pattern)) {
                        val foundOffset = fileOffset - (if (prevChunk.isNotEmpty()) overlap.toLong() else 0) + searchStart
                        if (foundOffset >= startOffset) {
                            results.add(foundOffset)
                        }
                    }
                    searchStart++
                }

                prevChunk = chunk.copyOf(chunkSize)
                fileOffset += chunkSize

                progress?.invoke((fileOffset.toFloat() / totalSize).coerceIn(0f, 1f))
            }
        }

        return results
    }

    /**
     * Read a specific range from file
     */
    fun readRange(file: File, offset: Long, length: Int): ByteArray {
        val raf = RandomAccessFile(file, "r")
        raf.seek(offset)
        val actualLength = minOf(length.toLong(), raf.length() - offset).toInt()
        val buf = ByteArray(actualLength)
        raf.readFully(buf)
        raf.close()
        return buf
    }

    /**
     * Patch bytes in file at offset
     */
    fun patchBytes(file: File, offset: Long, newBytes: ByteArray): Boolean {
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(offset)
                raf.write(newBytes)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Patch single byte
     */
    fun patchByte(file: File, offset: Long, newByte: Byte): Boolean {
        return patchBytes(file, offset, byteArrayOf(newByte))
    }

    /**
     * Streaming entropy calculation
     */
    fun calculateEntropy(file: File, offset: Long = 0, length: Long = -1): Double {
        val totalSize = if (length < 0) file.length() - offset else length
        if (totalSize <= 0) return 0.0

        val freq = IntArray(256)
        var totalRead = 0L

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val buffer = ByteArray(CHUNK_SIZE)

            while (totalRead < totalSize) {
                val toRead = minOf(CHUNK_SIZE.toLong(), totalSize - totalRead).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read == -1) break

                for (i in 0 until read) {
                    freq[buffer[i].toInt() and 0xFF]++
                }
                totalRead += read
            }
        }

        if (totalRead == 0L) return 0.0

        var entropy = 0.0
        for (f in freq) {
            if (f > 0) {
                val p = f.toDouble() / totalRead
                entropy -= p * kotlin.math.ln(p) / kotlin.math.ln(2.0)
            }
        }
        return entropy
    }
}
