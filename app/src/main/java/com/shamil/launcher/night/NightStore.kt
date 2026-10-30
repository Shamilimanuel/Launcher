package com.shamil.launcher.night

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.shamil.launcher.R
import com.shamil.launcher.ui.theme.WallpaperStore
import java.io.File

/*
 * The night screen's choices (25 Oct, the full version of the screen saver he tested on a phone).
 * Same pattern as the other stores: Compose state, ONE store for the whole app, saved on the phone in its own settings
 * file "night_screen" (so a saved look carries the night screen too, see Saves.kt). The ids are saved names: never
 * rename them.
 */

/** One swipe page of the night screen (his "pages" pick from the mock-up). */
enum class NightPage(val id: String, val title: String, val about: String) {
    STANDBY("standby", "StandBy", "Big gold clock, date, three widget cards, music when it plays"),
    TRAIL("trail", "Seelie trail", "The little light wanders, then sleeps above the clock"),
    WINDOW("window", "Rainy window", "Rain on the glass, a steaming mug, music like a radio"),
    MUSIC("music", "Music", "Big cover, title and the controls"),
    PHOTO("photo", "Photo", "One photo you pick, slowly moving, with the clock"),
    CLOCK("clock", "Just the clock", "Only the clock and date, as big as it fits");

    companion object {
        fun byId(id: String): NightPage? = entries.firstOrNull { it.id == id }
    }
}

/** The sky behind StandBy, Music and Just the clock. [swatch] = top, middle, bottom colour, for the settings tiles. */
enum class NightSky(val id: String, val title: String, val swatch: List<Long>) {
    CONSTELLATION("constel", "Constellation", listOf(0xFF04071A, 0xFF0C1633, 0xFF1D1A47)),
    STARFALL("starfall", "Starfall", listOf(0xFF04071A, 0xFF0C1633, 0xFF2A2458)),
    AURORA("aurora", "Aurora", listOf(0xFF030815, 0xFF0A1A33, 0xFF1E6A5A)),
    DUSK("dusk", "Gold dusk", listOf(0xFF15123F, 0xFF3E2470, 0xFFB2507F)),
    FIREFLIES("fireflies", "Fireflies", listOf(0xFF03100B, 0xFF0A2216, 0xFF2E5A22)),
    LANTERNS("lanterns", "Lanterns", listOf(0xFF08061C, 0xFF1E1241, 0xFFC8703A)),
    PETALS("petals", "Petals", listOf(0xFF120A24, 0xFF2E1646, 0xFFD27A9A)),
    RAIN("rain", "Rain window", listOf(0xFF08111C, 0xFF122338, 0xFF3A4A5A));

    companion object {
        fun byId(id: String?): NightSky = entries.firstOrNull { it.id == id } ?: CONSTELLATION
    }
}

/** What one StandBy widget slot shows. */
enum class NightWidget(val id: String, val title: String, @DrawableRes val icon: Int) {
    WEATHER("weather", "Weather", R.drawable.ic_partly_cloudy_night),
    EVENT("event", "Next event", R.drawable.ic_calendar_month),
    ALARM("alarm", "Next alarm", R.drawable.ic_schedule),
    BATTERY("battery", "Battery", R.drawable.ic_bolt),
    MUSIC("music", "Music", R.drawable.ic_music_note),
    SUNRISE("sunrise", "Sunrise", R.drawable.ic_sunny),
    TOMORROW("tomorrow", "Tomorrow", R.drawable.ic_star);

    companion object {
        fun byId(id: String?, fallback: NightWidget): NightWidget = entries.firstOrNull { it.id == id } ?: fallback
    }
}

/** How the night screen dims: with the room's light (the phone's light sensor), after his bedtime, or not. */
enum class NightDim(val id: String, val title: String) {
    ROOM("room", "Room light"),
    BEDTIME("bedtime", "Bedtime"),
    OFF("off", "Off");

    companion object {
        fun byId(id: String?): NightDim = entries.firstOrNull { it.id == id } ?: ROOM
    }
}

class NightStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Every page in his order (on and off ones). */
    var order by mutableStateOf(readOrder())
        private set
    /** The pages he switched off. */
    var off by mutableStateOf(readOff())
        private set
    var sky by mutableStateOf(NightSky.byId(prefs.getString(KEY_SKY, null)))
        private set
    /** The three StandBy slots. */
    var slots by mutableStateOf(readSlots())
        private set
    var dim by mutableStateOf(NightDim.byId(prefs.getString(KEY_DIM, null)))
        private set
    /** Bedtime as minutes after midnight (22:30 = 1350). Used by "Bedtime" dimming. */
    var bedtime by mutableIntStateOf(prefs.getInt(KEY_BEDTIME, DEFAULT_BEDTIME))
        private set
    /** Late mode: deep red and very dim, from [lateAt] until the morning. */
    var late by mutableStateOf(prefs.getBoolean(KEY_LATE, false))
        private set
    var lateAt by mutableIntStateOf(prefs.getInt(KEY_LATE_AT, DEFAULT_LATE_AT))
        private set
    /** Changes whenever he picks or removes the photo, so the photo page loads the new one. */
    var photoStamp by mutableLongStateOf(prefs.getLong(KEY_PHOTO, 0L))
        private set

    /** Seelie's own copy of his photo (the picker's link stops working later, so it's copied in). */
    val photoFile: File get() = File(File(context.filesDir, "night"), "photo.jpg")
    fun hasPhoto(): Boolean = photoFile.exists()

    /** The pages that really show, in order. Photo only once he picked one; never nothing (then StandBy). */
    fun shownPages(): List<NightPage> {
        val photo = photoStamp >= 0 && hasPhoto()   // reading the stamp makes Compose look again after a new pick
        return order.filter { it !in off && (it != NightPage.PHOTO || photo) }.ifEmpty { listOf(NightPage.STANDBY) }
    }

    fun setOn(page: NightPage, on: Boolean) {
        off = if (on) off - page else off + page
        prefs.edit { putString(KEY_OFF, off.joinToString(",") { it.id }) }
    }

    /** Moves [page] one step up (-1) or down (+1) in the order. */
    fun move(page: NightPage, by: Int) {
        val list = order.toMutableList()
        val from = list.indexOf(page)
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        list.removeAt(from)
        list.add(to, page)
        order = list
        prefs.edit { putString(KEY_ORDER, list.joinToString(",") { it.id }) }
    }

    fun useSky(s: NightSky) { sky = s; prefs.edit { putString(KEY_SKY, s.id) } }

    fun useSlot(index: Int, w: NightWidget) {
        slots = slots.toMutableList().also { it[index] = w }
        prefs.edit { putString(KEY_SLOTS, slots.joinToString(",") { it.id }) }
    }

    fun useDim(d: NightDim) { dim = d; prefs.edit { putString(KEY_DIM, d.id) } }
    fun useBedtime(minutes: Int) { bedtime = minutes.mod(DAY); prefs.edit { putInt(KEY_BEDTIME, bedtime) } }
    fun useLate(on: Boolean) { late = on; prefs.edit { putBoolean(KEY_LATE, on) } }
    fun useLateAt(minutes: Int) { lateAt = minutes.mod(DAY); prefs.edit { putInt(KEY_LATE_AT, lateAt) } }

    /**
     * Copies the photo he picked into Seelie's own files, shrunk to about screen size (run off the main thread).
     * Switches the Photo page on. False when the picture couldn't be read.
     */
    fun setPhoto(uri: Uri): Boolean = runCatching {
        val bitmap = WallpaperStore.decodeForScreen(context, uri) ?: return@runCatching false
        val file = photoFile
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "photo.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (!temp.renameTo(file)) { temp.copyTo(file, overwrite = true); temp.delete() }
        stamp()
        if (NightPage.PHOTO in off) setOn(NightPage.PHOTO, true)
        true
    }.getOrDefault(false)

    fun removePhoto() {
        photoFile.delete()
        stamp()
    }

    private fun stamp() {
        val now = System.currentTimeMillis()
        prefs.edit { putLong(KEY_PHOTO, now) }
        photoStamp = now
    }

    /** Reads everything again (after a saved look was applied). */
    fun reload() {
        order = readOrder()
        off = readOff()
        sky = NightSky.byId(prefs.getString(KEY_SKY, null))
        slots = readSlots()
        dim = NightDim.byId(prefs.getString(KEY_DIM, null))
        bedtime = prefs.getInt(KEY_BEDTIME, DEFAULT_BEDTIME)
        late = prefs.getBoolean(KEY_LATE, false)
        lateAt = prefs.getInt(KEY_LATE_AT, DEFAULT_LATE_AT)
        photoStamp = prefs.getLong(KEY_PHOTO, 0L)
    }

    /** His order; pages added in a later version join at the end. */
    private fun readOrder(): List<NightPage> {
        val saved = prefs.getString(KEY_ORDER, null).orEmpty().split(',').mapNotNull { NightPage.byId(it.trim()) }.distinct()
        return saved + NightPage.entries.filter { it !in saved }
    }

    private fun readOff(): Set<NightPage> =
        prefs.getString(KEY_OFF, null).orEmpty().split(',').mapNotNull { NightPage.byId(it.trim()) }.toSet()

    private fun readSlots(): List<NightWidget> {
        val saved = prefs.getString(KEY_SLOTS, null).orEmpty().split(',')
        return DEFAULT_SLOTS.mapIndexed { i, d -> NightWidget.byId(saved.getOrNull(i)?.trim(), d) }
    }

    companion object {
        /** Its settings file (Saves.kt copies it into a saved look). */
        const val PREFS = "night_screen"
        private const val KEY_ORDER = "order"
        private const val KEY_OFF = "off"
        private const val KEY_SKY = "sky"
        private const val KEY_SLOTS = "slots"
        private const val KEY_DIM = "dim"
        private const val KEY_BEDTIME = "bedtime"
        private const val KEY_LATE = "late"
        private const val KEY_LATE_AT = "late_at"
        private const val KEY_PHOTO = "photo_stamp"
        private const val DAY = 24 * 60
        const val DEFAULT_BEDTIME = 22 * 60 + 30
        const val DEFAULT_LATE_AT = 23 * 60 + 30
        /** Morning: dimming and Late mode stop at 07:00. */
        const val MORNING = 7 * 60
        /** The mock-up's StandBy: weather, next event, next alarm. */
        private val DEFAULT_SLOTS = listOf(NightWidget.WEATHER, NightWidget.EVENT, NightWidget.ALARM)
        @Volatile private var instance: NightStore? = null

        fun get(context: Context): NightStore =
            instance ?: synchronized(this) { instance ?: NightStore(context.applicationContext).also { instance = it } }
    }
}
