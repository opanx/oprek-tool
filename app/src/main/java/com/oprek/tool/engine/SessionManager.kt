package com.oprek.tool.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SessionState(
    val filePath: String = "",
    val fileName: String = "",
    val bookmarks: List<BookmarkEntry> = emptyList(),
    val patches: List<SavedPatch> = emptyList(),
    val notes: String = ""
)

data class BookmarkEntry(
    val offset: Long, val name: String, val color: String = "cyan"
)

data class SavedPatch(
    val offset: Long, val originalBytes: String, val newBytes: String, val description: String = ""
)

object SessionManager {
    private const val SESSION_FILE = "oprek_session.json"

    fun saveSession(context: Context, state: SessionState) {
        try {
            val json = JSONObject()
            json.put("filePath", state.filePath)
            json.put("fileName", state.fileName)
            json.put("notes", state.notes)

            val bmArr = JSONArray()
            for (bm in state.bookmarks) {
                val obj = JSONObject()
                obj.put("offset", bm.offset)
                obj.put("name", bm.name)
                obj.put("color", bm.color)
                bmArr.put(obj)
            }
            json.put("bookmarks", bmArr)

            val patchArr = JSONArray()
            for (p in state.patches) {
                val obj = JSONObject()
                obj.put("offset", p.offset)
                obj.put("original", p.originalBytes)
                obj.put("new", p.newBytes)
                obj.put("desc", p.description)
                patchArr.put(obj)
            }
            json.put("patches", patchArr)

            File(context.filesDir, SESSION_FILE).writeText(json.toString())
        } catch (_: Exception) {}
    }

    fun loadSession(context: Context): SessionState {
        return try {
            val file = File(context.filesDir, SESSION_FILE)
            if (!file.exists()) return SessionState()
            val json = JSONObject(file.readText())
            val bookmarks = mutableListOf<BookmarkEntry>()
            val bmArr = json.optJSONArray("bookmarks") ?: JSONArray()
            for (i in 0 until bmArr.length()) {
                val obj = bmArr.getJSONObject(i)
                bookmarks.add(BookmarkEntry(obj.getLong("offset"), obj.getString("name"), obj.optString("color", "cyan")))
            }
            val patches = mutableListOf<SavedPatch>()
            val pArr = json.optJSONArray("patches") ?: JSONArray()
            for (i in 0 until pArr.length()) {
                val obj = pArr.getJSONObject(i)
                patches.add(SavedPatch(obj.getLong("offset"), obj.getString("original"), obj.getString("new"), obj.optString("desc", "")))
            }
            SessionState(
                json.optString("filePath", ""),
                json.optString("fileName", ""),
                bookmarks, patches,
                json.optString("notes", "")
            )
        } catch (_: Exception) { SessionState() }
    }

    fun clearSession(context: Context) {
        File(context.filesDir, SESSION_FILE).delete()
    }
}
