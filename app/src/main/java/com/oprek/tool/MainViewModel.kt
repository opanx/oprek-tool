package com.oprek.tool

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oprek.tool.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()

    // Current working file
    private val _currentFile = MutableStateFlow<FileInfo?>(null)
    val currentFile: StateFlow<FileInfo?> = _currentFile

    private val _currentRawFile = MutableStateFlow<File?>(null)
    val currentRawFile: StateFlow<File?> = _currentRawFile

    // Hex dump
    private val _hexLines = MutableStateFlow<List<String>>(emptyList())
    val hexLines: StateFlow<List<String>> = _hexLines

    // Strings
    private val _strings = MutableStateFlow<List<StringPair>>(emptyList())
    val strings: StateFlow<List<StringPair>> = _strings

    // ELF
    private val _elfInfo = MutableStateFlow<ElfInfo?>(null)
    val elfInfo: StateFlow<ElfInfo?> = _elfInfo

    private val _elfSections = MutableStateFlow<List<ElfSection>>(emptyList())
    val elfSections: StateFlow<List<ElfSection>> = _elfSections

    // APK
    private val _apkInfo = MutableStateFlow<ApkInfo?>(null)
    val apkInfo: StateFlow<ApkInfo?> = _apkInfo

    // Loading
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    // Patch operations
    private val _patches = MutableStateFlow<List<PatchEntry>>(emptyList())
    val patches: StateFlow<List<PatchEntry>> = _patches

    fun loadFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val name = FileUtils.getFileName(context, uri)
                val tempFile = FileUtils.getTempFile(context, name)
                FileUtils.copyUriToFile(context, uri, tempFile)

                val info = FileAnalyzer.getFileInfo(tempFile)
                _currentFile.value = info
                _currentRawFile.value = tempFile
                _statusMessage.value = "Loaded: ${info.name} (${formatSize(info.size)})"

                // Auto-analyze based on type
                when (info.type) {
                    FileType.ELF, FileType.SO -> {
                        _elfInfo.value = FileAnalyzer.parseElfHeaders(tempFile)
                        _elfSections.value = FileAnalyzer.parseElfSections(tempFile)
                    }
                    FileType.APK -> {
                        _apkInfo.value = FileAnalyzer.parseApkInfo(tempFile)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun loadHex(offset: Long = 0, length: Int = 65536) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentRawFile.value?.let { file ->
                val chunk = if (offset == 0L && length >= file.length().toInt()) {
                    FileAnalyzer.getHexDumpFull(file, length)
                } else {
                    FileAnalyzer.getHexDump(file, offset, length)
                }
                _hexLines.value = chunk.toHexLines()
            }
        }
    }

    fun gotoOffset(offset: Long) {
        loadHex(offset)
        _statusMessage.value = "Jumped to 0x${String.format("%08X", offset)}"
    }

    fun extractStrings(minLength: Int = 4) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentRawFile.value?.let { file ->
                _strings.value = FileAnalyzer.extractStrings(file, minLength)
                _statusMessage.value = "Found ${_strings.value.size} strings"
            }
        }
    }

    fun patchByte(offset: Long, newByte: Byte) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentRawFile.value?.let { file ->
                if (FileAnalyzer.patchByte(file, offset, newByte)) {
                    _patches.value = _patches.value + PatchEntry(offset, byteArrayOf(newByte), "byte")
                    _statusMessage.value = "Patched byte at 0x${"%08X".format(offset)}"
                    // Reload hex
                    loadHex(maxOf(0, offset - 128), 512)
                } else {
                    _statusMessage.value = "Patch failed!"
                }
            }
        }
    }

    fun patchBytes(offset: Long, newBytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentRawFile.value?.let { file ->
                if (FileAnalyzer.patchBytes(file, offset, newBytes)) {
                    _patches.value = _patches.value + PatchEntry(offset, newBytes, "blob")
                    _statusMessage.value = "Patched ${newBytes.size} bytes at 0x${"%08X".format(offset)}"
                    loadHex(maxOf(0, offset - 128), 512)
                } else {
                    _statusMessage.value = "Patch failed!"
                }
            }
        }
    }

    fun bulkPatch(patches: List<Pair<Long, ByteArray>>) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentRawFile.value?.let { file ->
                var success = 0
                for ((offset, bytes) in patches) {
                    if (FileAnalyzer.patchBytes(file, offset, bytes)) success++
                }
                _statusMessage.value = "Applied $success/${patches.size} patches"
                loadHex()
            }
        }
    }

    fun searchHex(pattern: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentRawFile.value?.let { file ->
                val bytes = pattern.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val fileBytes = file.readBytes()
                val results = mutableListOf<Long>()
                for (i in 0..fileBytes.size - bytes.size) {
                    if (fileBytes.sliceArray(i until i + bytes.size).contentEquals(bytes)) {
                        results.add(i.toLong())
                        if (results.size >= 100) break
                    }
                }
                _statusMessage.value = "Found ${results.size} matches"
                if (results.isNotEmpty()) {
                    loadHex(results.first(), 512)
                }
            }
        }
    }

    fun exportPatches(): String {
        return _patches.value.joinToString("\n") { p ->
            "0x${"%08X".format(p.offset)}: ${p.data.joinToString(" ") { "%02X".format(it) }}"
        }
    }

    fun clearStatus() { _statusMessage.value = "" }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1048576 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / 1048576.0)}MB"
    }
}

data class PatchEntry(val offset: Long, val data: ByteArray, val type: String)
