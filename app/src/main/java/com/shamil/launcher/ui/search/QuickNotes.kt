package com.shamil.launcher.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** One note saved with "> note …". */
data class QuickNote(val text: String, val savedAt: Long)

/**
 * The quick notes from "> note buy milk", newest first, saved on the phone (prefs "quick_notes").
 * Compose state, so a widget that shows them later updates by itself. One store for the app: [get].
 */
class QuickNotes private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("quick_notes", Context.MODE_PRIVATE)

    var notes by mutableStateOf(read())
        private set

    fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        // Newest on top; keep the last 100 so the list never grows forever.
        notes = (listOf(QuickNote(clean, System.currentTimeMillis())) + notes).take(MAX)
        save()
    }

    fun remove(note: QuickNote) {
        notes = notes - note
        save()
    }

    /** Saved as a small JSON list: [{"text":"buy milk","at":1729700000000}, …]. */
    private fun save() {
        val list = JSONArray()
        notes.forEach { list.put(JSONObject().put("text", it.text).put("at", it.savedAt)) }
        prefs.edit { putString(KEY, list.toString()) }
    }

    private fun read(): List<QuickNote> = try {
        val list = JSONArray(prefs.getString(KEY, null) ?: "[]")
        List(list.length()) { i ->
            val item = list.getJSONObject(i)
            QuickNote(item.getString("text"), item.optLong("at"))
        }
    } catch (e: Exception) {
        emptyList()   // a broken save shouldn't crash the search
    }

    companion object {
        private const val KEY = "notes"
        private const val MAX = 100
        @Volatile private var instance: QuickNotes? = null

        fun get(context: Context): QuickNotes =
            instance ?: synchronized(this) { instance ?: QuickNotes(context.applicationContext).also { instance = it } }
    }
}
