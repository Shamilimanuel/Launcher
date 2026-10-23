package com.shamil.launcher.ui.search

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** The kinds of results search can show. Each one can be switched off in Seelie settings → Search & commands. */
enum class SearchGroup(val key: String, val title: String, val sub: String) {
    Calc("calc", "Calculator", "Type a sum like 12*7 and see the answer"),
    Apps("apps", "Apps", "Your installed apps, best match nearest your thumb"),
    Settings("settings", "Phone settings", "Wi-Fi, battery, display and about 25 more"),
    Contacts("contacts", "Contacts", "People in your phone book (Seelie asks first)"),
    Web("web", "The web", "\"Search the web for …\" in your browser"),
    Play("play", "Play Store", "\"Search Play Store for …\" to find new apps"),
}

/** The search engines to pick from. [id] is what's saved, so never rename one. */
enum class WebEngine(val id: String, val label: String, private val address: String) {
    Google("google", "Google", "https://www.google.com/search?q="),
    DuckDuckGo("ddg", "DuckDuckGo", "https://duckduckgo.com/?q="),
    Startpage("startpage", "Startpage", "https://www.startpage.com/sp/search?query="),
    Bing("bing", "Bing", "https://www.bing.com/search?q=");

    /** The web address that searches for [query]. Uri.encode turns spaces and & into safe %-codes. */
    fun urlFor(query: String): String = address + Uri.encode(query)
}

/**
 * Remembers which result groups are on and which search engine he picked (saved on the phone).
 * Compose state, like LookStore: flip a switch in settings and the search box follows straight away.
 * There's ONE store for the whole app: [get].
 */
class SearchStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)

    /** On/off per group. Everything starts on. */
    private val switches = mutableStateMapOf<SearchGroup, Boolean>().apply {
        SearchGroup.entries.forEach { put(it, prefs.getBoolean(it.key, true)) }
    }

    var engineId by mutableStateOf(prefs.getString(KEY_ENGINE, null) ?: WebEngine.DuckDuckGo.id)
        private set

    val engine: WebEngine get() = WebEngine.entries.firstOrNull { it.id == engineId } ?: WebEngine.DuckDuckGo

    fun isOn(group: SearchGroup): Boolean = switches[group] ?: true

    /** Named "turn", not "set…", so it can't clash with a property setter (the LookStore lesson). */
    fun turn(group: SearchGroup, on: Boolean) {
        switches[group] = on
        prefs.edit { putBoolean(group.key, on) }
    }

    fun useEngine(engine: WebEngine) {
        engineId = engine.id
        prefs.edit { putString(KEY_ENGINE, engine.id) }
    }

    companion object {
        private const val KEY_ENGINE = "engine"
        @Volatile private var instance: SearchStore? = null

        fun get(context: Context): SearchStore =
            instance ?: synchronized(this) { instance ?: SearchStore(context.applicationContext).also { instance = it } }
    }
}
