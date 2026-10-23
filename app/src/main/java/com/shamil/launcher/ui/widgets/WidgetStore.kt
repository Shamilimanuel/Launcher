package com.shamil.launcher.ui.widgets

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.shamil.launcher.R

/**
 * One kind of widget Seelie can show in the stack: its saved [id], its name and one line about it,
 * and the icon + colour its tile gets in Seelie settings (colours from the prototype).
 */
enum class WidgetKind(val id: String, val title: String, val about: String, @DrawableRes val icon: Int, val tint: Color) {
    MONTH("month", "Month calendar", "Monday first, Sundays pink, today circled", R.drawable.ic_calendar_month, Color(0xFFFF8CA8)),
    GAUGES("gauges", "Gauge tiles", "Round gauges: battery, humidity, temperature", R.drawable.ic_water_drop, Color(0xFF4CC2F1)),
    WEEK("week", "This week", "Today and the six days after it", R.drawable.ic_date_range, Color(0xFF7AA2FF)),
    NOTES("notes", "Quick notes", "A few lines you type yourself", R.drawable.ic_sticky_note_2, Color(0xFFFFD27A));

    companion object {
        /** The kind saved under [id], or null if it's unknown (e.g. a widget from a newer Seelie that got removed). */
        fun byId(id: String): WidgetKind? = entries.firstOrNull { it.id == id }
    }
}

/** How tall an app widget is in the stack (Seelie's own widgets follow the home style, see widgetHeight). */
enum class WidgetSize(val id: String, val title: String, val height: Dp) {
    SMALL("s", "Small", 110.dp),
    MEDIUM("m", "Medium", 160.dp),
    LARGE("l", "Large", 240.dp);

    companion object {
        fun byId(id: String?): WidgetSize = entries.firstOrNull { it.id == id } ?: MEDIUM

        /** A good first size for an app widget, from the smallest height its app says it needs. */
        fun forMinHeight(minHeightDp: Int): WidgetSize = when {
            minHeightDp <= 100 -> SMALL
            minHeightDp <= 170 -> MEDIUM
            else -> LARGE
        }
    }
}

/** How the widget cards are drawn (the prototype's "Card style": Glass / Solid / Outline). */
enum class CardStyle(val id: String, val title: String) {
    GLASS("glass", "Glass"),
    SOLID("solid", "Solid"),
    OUTLINE("outline", "Outline");

    companion object {
        fun byId(id: String?): CardStyle = entries.firstOrNull { it.id == id } ?: GLASS
    }
}

/**
 * One page of the stack: one of Seelie's own widgets, or a real widget from another app
 * (Android gives each of those a number, its "app widget id"). [key] is how it's saved: "month" or "app:42".
 */
sealed interface StackEntry {
    val key: String

    data class BuiltIn(val kind: WidgetKind) : StackEntry {
        override val key get() = kind.id
    }

    data class App(val appWidgetId: Int) : StackEntry {
        override val key get() = "$APP_PREFIX$appWidgetId"
    }

    companion object {
        const val APP_PREFIX = "app:"

        fun fromKey(key: String): StackEntry? =
            if (key.startsWith(APP_PREFIX)) key.removePrefix(APP_PREFIX).toIntOrNull()?.let { App(it) }
            else WidgetKind.byId(key)?.let { BuiltIn(it) }
    }
}

/**
 * Remembers which widgets are in his stack (in order), app widget sizes, the card style and his quick notes,
 * saved on the phone. Like LookStore it's Compose state and there's ONE store for the whole app ([get]),
 * so a change in Seelie settings shows on the home screen straight away.
 */
class WidgetStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    /** The widgets in the stack, first one = the page you see first. Saved as "month,gauges,app:42". */
    var stack by mutableStateOf(load())
        private set

    /** The size he picked for each app widget, by its app widget id. */
    var appSizes by mutableStateOf(loadSizes())
        private set

    var cardStyle by mutableStateOf(CardStyle.byId(prefs.getString(KEY_CARD, null)))
        private set

    /** The quick notes: plain text, one note per line. */
    var notes by mutableStateOf(prefs.getString(KEY_NOTES, "") ?: "")
        private set

    /** Seelie's own widgets he hasn't added yet (shown as "Add a widget" cards). */
    val notAdded: List<WidgetKind> get() = WidgetKind.entries.filter { kind -> stack.none { it is StackEntry.BuiltIn && it.kind == kind } }

    fun add(kind: WidgetKind) {
        if (stack.any { it is StackEntry.BuiltIn && it.kind == kind }) return   // each of Seelie's widgets once is enough
        save(stack + StackEntry.BuiltIn(kind))
    }

    /** Adds a widget from another app (already set up with Android) at the end of the stack. */
    fun addApp(appWidgetId: Int, size: WidgetSize) {
        appSizes = appSizes + (appWidgetId to size)
        saveSizes()
        save(stack + StackEntry.App(appWidgetId))
    }

    fun remove(entry: StackEntry) {
        save(stack - entry)
        if (entry is StackEntry.App) {
            appSizes = appSizes - entry.appWidgetId
            saveSizes()
        }
    }

    fun sizeOf(entry: StackEntry): WidgetSize = (entry as? StackEntry.App)?.let { appSizes[it.appWidgetId] } ?: WidgetSize.MEDIUM

    /** How tall this page of the stack is ([builtIn] = how tall Seelie's own widgets are right now). */
    fun heightOf(entry: StackEntry?, builtIn: Dp): Dp = if (entry is StackEntry.App) sizeOf(entry).height else builtIn

    fun resize(entry: StackEntry.App, size: WidgetSize) {
        appSizes = appSizes + (entry.appWidgetId to size)
        saveSizes()
    }

    /** Moves the widget at [from] to [to] (both positions in [stack]). Does nothing if either is out of range. */
    fun move(from: Int, to: Int) {
        if (from !in stack.indices || to !in stack.indices || from == to) return
        val list = stack.toMutableList()
        list.add(to, list.removeAt(from))
        save(list)
    }

    fun useCardStyle(style: CardStyle) {
        cardStyle = style
        prefs.edit { putString(KEY_CARD, style.id) }
    }

    fun saveNotes(text: String) {
        notes = text
        prefs.edit { putString(KEY_NOTES, text) }
    }

    private fun save(list: List<StackEntry>) {
        stack = list
        prefs.edit { putString(KEY_STACK, list.joinToString(",") { it.key }) }
    }

    private fun saveSizes() {
        prefs.edit { putString(KEY_SIZES, appSizes.entries.joinToString(",") { "${it.key}=${it.value.id}" }) }
    }

    /** Reads the saved order. Never saved before = the default stack; saved but empty = he removed them all. */
    private fun load(): List<StackEntry> {
        val saved = prefs.getString(KEY_STACK, null) ?: return DEFAULT
        return saved.split(",").filter { it.isNotBlank() }.mapNotNull { StackEntry.fromKey(it) }.distinct()
    }

    private fun loadSizes(): Map<Int, WidgetSize> =
        (prefs.getString(KEY_SIZES, "") ?: "").split(",").mapNotNull { part ->
            val (id, size) = part.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            id.toIntOrNull()?.let { it to WidgetSize.byId(size) }
        }.toMap()

    companion object {
        private val DEFAULT = listOf(WidgetKind.MONTH, WidgetKind.GAUGES, WidgetKind.NOTES).map { StackEntry.BuiltIn(it) }
        private const val KEY_STACK = "stack"
        private const val KEY_SIZES = "app_sizes"
        private const val KEY_CARD = "card_style"
        private const val KEY_NOTES = "notes"
        @Volatile private var instance: WidgetStore? = null

        fun get(context: Context): WidgetStore =
            instance ?: synchronized(this) { instance ?: WidgetStore(context.applicationContext).also { instance = it } }
    }
}
