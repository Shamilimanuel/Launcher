package com.shamil.launcher.ui.widgets

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
 * Remembers which widgets are in his stack (in order), the card style and his quick notes,
 * saved on the phone. Like LookStore it's Compose state and there's ONE store for the whole app ([get]),
 * so a change in Seelie settings shows on the home screen straight away.
 */
class WidgetStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    /** The widgets in the stack, first one = the page you see first. Saved as "month,gauges,notes". */
    var stack by mutableStateOf(load())
        private set

    var cardStyle by mutableStateOf(CardStyle.byId(prefs.getString(KEY_CARD, null)))
        private set

    /** The quick notes: plain text, one note per line. */
    var notes by mutableStateOf(prefs.getString(KEY_NOTES, "") ?: "")
        private set

    /** Widgets he hasn't added yet (shown as "Add a widget" cards). */
    val notAdded: List<WidgetKind> get() = WidgetKind.entries.filter { it !in stack }

    fun add(kind: WidgetKind) {
        if (kind in stack) return   // each widget once is enough
        save(stack + kind)
    }

    fun remove(kind: WidgetKind) = save(stack - kind)

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

    private fun save(list: List<WidgetKind>) {
        stack = list
        prefs.edit { putString(KEY_STACK, list.joinToString(",") { it.id }) }
    }

    /** Reads the saved order. Never saved before = the default stack; saved but empty = he removed them all. */
    private fun load(): List<WidgetKind> {
        val saved = prefs.getString(KEY_STACK, null) ?: return DEFAULT
        return saved.split(",").filter { it.isNotBlank() }.mapNotNull { WidgetKind.byId(it) }.distinct()
    }

    companion object {
        private val DEFAULT = listOf(WidgetKind.MONTH, WidgetKind.GAUGES, WidgetKind.NOTES)
        private const val KEY_STACK = "stack"
        private const val KEY_CARD = "card_style"
        private const val KEY_NOTES = "notes"
        @Volatile private var instance: WidgetStore? = null

        fun get(context: Context): WidgetStore =
            instance ?: synchronized(this) { instance ?: WidgetStore(context.applicationContext).also { instance = it } }
    }
}
