package com.oprek.tool.core

import android.content.Context
import java.io.File

/**
 * Shared helper to find the currently loaded file.
 * Files are stored in cacheDir/oprek/ by FileUtils.getTempFile()
 */
object LoadedFileHelper {

    fun findLoadedFile(context: Context): File? {
        // Check cacheDir/oprek/ first (where FileUtils stores files)
        val oprekDir = File(context.cacheDir, "oprek")
        if (oprekDir.exists()) {
            val f = oprekDir.listFiles()?.filter { it.isFile && it.length() > 0 }
                ?.maxByOrNull { it.lastModified() }
            if (f != null) return f
        }
        // Fallback: check cacheDir root
        val rootFile = context.cacheDir.listFiles()?.filter { it.isFile && it.length() > 0 }
            ?.maxByOrNull { it.lastModified() }
        return rootFile
    }

    fun findLoadedFileName(context: Context): String {
        return findLoadedFile(context)?.name ?: "No file loaded"
    }

    fun findLoadedFileSize(context: Context): Long {
        return findLoadedFile(context)?.length() ?: 0L
    }
}
