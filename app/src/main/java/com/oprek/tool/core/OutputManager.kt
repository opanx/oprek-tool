package com.oprek.tool.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object OutputManager {

    private const val OUTPUT_DIR = "OprekTool"
    private const val SUB_DIR = "output"

    private fun getOutputDir(context: Context): File {
        // Try MediaStore first (Android 10+), fallback to direct path
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$OUTPUT_DIR/$SUB_DIR")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "$OUTPUT_DIR/$SUB_DIR")
        }
    }

    fun getOutputDirPath(context: Context): String {
        val dir = getOutputDir(context)
        dir.mkdirs()
        return dir.absolutePath
    }

    /**
     * Save text content to output directory.
     * @param context Android context
     * @param filename Filename (e.g. "strings_output.txt")
     * @param content Text content to save
     * @param subfolder Optional subfolder (e.g. "patches", "analysis")
     * @return Full path of saved file, or null on failure
     */
    fun saveText(context: Context, filename: String, content: String, subfolder: String = ""): String? {
        return try {
            val baseDir = if (subfolder.isNotEmpty()) {
                File(getOutputDir(context), subfolder).also { it.mkdirs() }
            } else {
                getOutputDir(context).also { it.mkdirs() }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val finalName = if (safeName.contains(".")) {
                val name = safeName.substringBeforeLast(".")
                val ext = safeName.substringAfterLast(".")
                "${name}_${timestamp}.$ext"
            } else {
                "${safeName}_${timestamp}.txt"
            }

            val file = File(baseDir, finalName)
            file.writeText(content)
            file.absolutePath
        } catch (e: Exception) {
            // Fallback: try direct path
            try {
                val dir = File("/sdcard/$OUTPUT_DIR/$SUB_DIR/${if(subfolder.isNotEmpty())"$subfolder/" else ""}")
                dir.mkdirs()
                val file = File(dir, filename)
                file.writeText(content)
                file.absolutePath
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Save binary data to output directory.
     */
    fun saveBytes(context: Context, filename: String, data: ByteArray, subfolder: String = ""): String? {
        return try {
            val baseDir = if (subfolder.isNotEmpty()) {
                File(getOutputDir(context), subfolder).also { it.mkdirs() }
            } else {
                getOutputDir(context).also { it.mkdirs() }
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val name = safeName.substringBeforeLast(".")
            val ext = if (safeName.contains(".")) safeName.substringAfterLast(".") else "bin"
            val file = File(baseDir, "${name}_${timestamp}.$ext")
            file.writeBytes(data)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save patches as hex format.
     */
    fun savePatches(context: Context, patches: List<Pair<Long, ByteArray>>, description: String = ""): String? {
        val sb = StringBuilder()
        sb.appendLine("# OprekTool Patch Export")
        sb.appendLine("# Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        if (description.isNotEmpty()) sb.appendLine("# $description")
        sb.appendLine("# Total patches: ${patches.size}")
        sb.appendLine()
        for ((offset, bytes) in patches) {
            sb.appendLine("0x${"%08X".format(offset)}: ${bytes.joinToString(" ") { "%02X".format(it) }}")
        }
        return saveText(context, "patches.hex", sb.toString(), "patches")
    }

    /**
     * Save disassembly output.
     */
    fun saveDisasm(context: Context, content: String, filename: String = "disasm.txt"): String? {
        return saveText(context, filename, content, "disasm")
    }

    /**
     * Save analysis results.
     */
    fun saveAnalysis(context: Context, content: String, filename: String = "analysis.txt"): String? {
        return saveText(context, filename, content, "analysis")
    }

    /**
     * Save deobfuscated strings.
     */
    fun saveDeobfuscated(context: Context, content: String): String? {
        return saveText(context, "deobfuscated.txt", content, "deobfuscate")
    }

    /**
     * Save bookmarks.
     */
    fun saveBookmarks(context: Context, bookmarks: String): String? {
        return saveText(context, "bookmarks.json", bookmarks, "bookmarks")
    }

    /**
     * Save ELF info.
     */
    fun saveElfInfo(context: Context, content: String): String? {
        return saveText(context, "elf_info.txt", content, "elf")
    }

    /**
     * Save hook scripts.
     */
    fun saveHookScript(context: Context, content: String, filename: String = "hook.js"): String? {
        return saveText(context, filename, content, "hooks")
    }

    /**
     * Get list of all saved files.
     */
    fun listOutputs(context: Context): List<Pair<String, Long>> {
        val dir = getOutputDir(context)
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath to it.length() }
            .toList()
            .sortedByDescending { it.second }
    }

    /**
     * Get total output size in bytes.
     */
    fun getOutputSize(context: Context): Long {
        val dir = getOutputDir(context)
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
