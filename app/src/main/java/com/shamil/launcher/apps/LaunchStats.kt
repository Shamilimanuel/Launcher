package com.shamil.launcher.apps

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers when he opened each app from Seelie (the last 30 times per app), only on the phone.
 * Used for "Suggested" in Edit favourites: the apps he opens most that aren't favourites yet.
 * Needs no permission, unlike Android's own usage stats.
 */
class LaunchStats private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("launch_stats", Context.MODE_PRIVATE)
    private val opens: MutableMap<String, MutableList<Long>> = read()

    @Synchronized
    fun record(packageName: String) {
        val times = opens.getOrPut(packageName) { mutableListOf() }
        times += System.currentTimeMillis()
        while (times.size > KEEP) times.removeAt(0)
        save()
    }

    /** How often each app was opened in the last 14 days, most-opened first. */
    @Synchronized
    fun mostOpened(): List<Pair<String, Int>> {
        val since = System.currentTimeMillis() - WINDOW_MS
        return opens.mapValues { (_, times) -> times.count { it >= since } }
            .filterValues { it > 0 }
            .toList()
            .sortedByDescending { it.second }
    }

    private fun read(): MutableMap<String, MutableList<Long>> = try {
        val root = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        root.keys().asSequence().associateWith { pkg ->
            val list = root.getJSONArray(pkg)
            MutableList(list.length()) { list.getLong(it) }
        }.toMutableMap()
    } catch (e: Exception) {
        mutableMapOf()
    }

    private fun save() {
        val root = JSONObject()
        opens.forEach { (pkg, times) -> root.put(pkg, JSONArray(times)) }
        prefs.edit { putString(KEY, root.toString()) }
    }

    companion object {
        private const val KEY = "opens"
        private const val KEEP = 30
        private const val WINDOW_MS = 14 * 24 * 60 * 60 * 1000L
        @Volatile private var instance: LaunchStats? = null

        fun get(context: Context): LaunchStats =
            instance ?: synchronized(this) { instance ?: LaunchStats(context.applicationContext).also { instance = it } }
    }
}
