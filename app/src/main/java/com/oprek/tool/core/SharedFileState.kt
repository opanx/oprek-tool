package com.oprek.tool.core

import com.oprek.tool.core.SharedFileState

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Global shared file state. ALL screens should observe this.
 * When a file is loaded via MainViewModel, this state is updated,
 * and ALL screens auto-refresh their file reference.
 */
object SharedFileState {

    // Monotonic counter - incremented every time a new file is loaded
    private val _revision = mutableIntStateOf(0)
    val revision: Int get() = _revision.intValue

    // Timestamp of last file load
    private val lastLoadTime = AtomicLong(0)

    // Cached file reference (updated on each revision)
    @Volatile
    private var cachedFile: File? = null

    @Volatile
    private var cachedFileName: String = ""

    @Volatile
    private var cachedFileSize: Long = 0

    /**
     * Call this from MainViewModel.loadFile() after successful copy.
     * This triggers ALL screens to re-read the file.
     */
    fun notifyFileLoaded(file: File) {
        cachedFile = file
        cachedFileName = file.name
        cachedFileSize = file.length()
        lastLoadTime.set(System.currentTimeMillis())
        _revision.intValue = _revision.intValue + 1
    }

    /**
     * Find the currently loaded file. Same logic as LoadedFileHelper
     * but uses cached reference for speed.
     */
    fun findFile(context: Context): File? {
        // Fast path: return cached file if it still exists
        cachedFile?.let { if (it.exists() && it.length() > 0) return it }

        // Slow path: scan cache directory
        val result = SharedFileState.findFile(context)
        if (result != null) {
            cachedFile = result
            cachedFileName = result.name
            cachedFileSize = result.length()
        }
        return result
    }

    fun findFileName(context: Context): String {
        findFile(context)
        return cachedFileName.ifEmpty { LoadedFileHelper.findLoadedFileName(context) }
    }

    fun findFileSize(context: Context): Long {
        findFile(context)
        return if (cachedFileSize > 0) cachedFileSize else LoadedFileHelper.findLoadedFileSize(context)
    }

    fun hasFile(): Boolean = cachedFile?.let { it.exists() && it.length() > 0 } ?: false
}
