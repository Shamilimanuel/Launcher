package com.shamil.launcher.ui.theme

import android.content.Context
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.compose.ui.graphics.Color
import com.shamil.launcher.apps.IconPack
import com.shamil.launcher.apps.SeelieIcons

/**
 * Remembers his chosen theme, font and icons (saved on the phone, so they survive a restart).
 * All are Compose state: change one and every screen redraws in the new look straight away.
 * There's ONE store for the whole app ([get]), so a pick in Seelie settings shows on the home screen too.
 */
class LookStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("look", Context.MODE_PRIVATE)

    /** The "Match my wallpaper" theme, rebuilt whenever his wallpaper changes (null if Android can't tell its colours). */
    var wallpaperLook by mutableStateOf(WallpaperTheme.build(context))
        private set

    init {
        // A new wallpaper = new colours: Android tells us, and the theme glides to them.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                WallpaperManager.getInstance(context).addOnColorsChangedListener(
                    { _, which -> if (which and WallpaperManager.FLAG_SYSTEM != 0) refreshWallpaper() },
                    Handler(Looper.getMainLooper()),
                )
            }
        }
    }

    /** Reads the wallpaper's colours again (also called when the home comes back, in case we missed a change). */
    fun refreshWallpaper() {
        wallpaperLook = WallpaperTheme.build(context)
    }

    /** The picked theme as a look ("wallpaper" = his wallpaper's colours, falling back to Fuji). */
    val currentTheme: LauncherLook
        get() = if (themeId == WallpaperTheme.ID) wallpaperLook ?: Fuji else themeById(themeId)

    var themeId by mutableStateOf(prefs.getString(KEY_THEME, null) ?: Fuji.id)
        private set
    /** null = use the theme's own font. */
    var fontId by mutableStateOf(prefs.getString(KEY_FONT, null))
        private set
    /** The icon pack he picked: a pack's package name, [NO_PACK] for the apps' own icons, or null = not picked yet. */
    var iconPack by mutableStateOf(prefs.getString(KEY_ICONS, null))
        private set
    /** App icon size, 0.8–1.2 (1 = Niagara's 44dp). */
    var iconScale by mutableFloatStateOf(prefs.getFloat(KEY_ICON_SCALE, 1f))
        private set

    /** The text colour he picked ([TEXT_COLOURS] id), or null = the theme's own. */
    var textColour by mutableStateOf(prefs.getString(KEY_TEXT, null))
        private set
    /** Which parts get his text colour (23 Oct: "can you choose what gets a color, not immediately everything"). */
    var colourParts by mutableStateOf(prefs.getStringSet(KEY_PARTS, null)?.toSet() ?: COLOUR_PARTS.map { it.first }.toSet())
        private set

    fun toggleColourPart(part: String) {
        colourParts = if (part in colourParts) colourParts - part else colourParts + part
        prefs.edit { putStringSet(KEY_PARTS, colourParts) }
    }

    /** The A–Z letters: "text" (same as the text) or "accent". */
    var railColour by mutableStateOf(prefs.getString(KEY_RAIL, "text") ?: "text")
        private set

    /** The look to show: the theme, with his font, icon size and text colours swapped in. */
    val look: LauncherLook
        get() {
            val theme = currentTheme
            val withFont = fontId?.let { theme.copy(font = fontById(it)) } ?: theme
            val base = withFont.copy(iconScale = iconScale)
            val text = TEXT_COLOURS[textColour]?.let { if (it == Color.Unspecified) base.accent else it }
            // "Menus & settings" decides the colour of everything that isn't one of the home's parts.
            val coloured = if (text == null || MENUS !in colourParts) base else base.copy(
                text = text,
                textDim = text.copy(alpha = 0.82f),
            )
            val parts = if (text == null) emptyMap() else colourParts.filter { it != MENUS }.associateWith { text }
            val rail = when {
                railColour == "accent" -> coloured.accent
                text != null && "rail" in colourParts -> text
                else -> base.text
            }
            return coloured.copy(partText = parts, rail = rail)
        }

    /**
     * The pack Seelie should really draw with: his pick if it's still installed, else the first pack on the phone
     * (how Seelie always worked, so his Darko keeps showing), or null for the apps' own icons.
     */
    fun activeIconPack(context: Context): String? {
        val installed = IconPack.installed(context)
        return when (val pick = iconPack) {
            NO_PACK -> null
            SeelieIcons.THEMED, SeelieIcons.GLASS -> pick
            null -> installed.firstOrNull()
            else -> pick.takeIf { it in installed } ?: installed.firstOrNull()
        }
    }

    fun setTheme(id: String) {
        themeId = id
        prefs.edit { putString(KEY_THEME, id) }
    }

    fun useTextColour(id: String?) {
        textColour = id
        prefs.edit { if (id == null) remove(KEY_TEXT) else putString(KEY_TEXT, id) }
    }

    fun useRailColour(id: String) {
        railColour = id
        prefs.edit { putString(KEY_RAIL, id) }
    }

    fun setFont(id: String?) {
        fontId = id
        prefs.edit { if (id == null) remove(KEY_FONT) else putString(KEY_FONT, id) }
    }

    fun useIconPack(packageName: String?) {
        val value = packageName ?: NO_PACK
        iconPack = value
        prefs.edit { putString(KEY_ICONS, value) }
    }

    fun useIconScale(scale: Float) {
        iconScale = scale
        prefs.edit { putFloat(KEY_ICON_SCALE, scale) }
    }

    /** Reads every choice again from the phone (after a saved look was applied). */
    fun reload() {
        themeId = prefs.getString(KEY_THEME, null) ?: Fuji.id
        fontId = prefs.getString(KEY_FONT, null)
        iconPack = prefs.getString(KEY_ICONS, null)
        iconScale = prefs.getFloat(KEY_ICON_SCALE, 1f)
        textColour = prefs.getString(KEY_TEXT, null)
        colourParts = prefs.getStringSet(KEY_PARTS, null)?.toSet() ?: COLOUR_PARTS.map { it.first }.toSet()
        railColour = prefs.getString(KEY_RAIL, "text") ?: "text"
    }

    companion object {
        /** Saved as [iconPack] when he picks "Original icons". */
        const val NO_PACK = "none"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT = "font"
        private const val KEY_ICONS = "icon_pack"
        private const val KEY_ICON_SCALE = "icon_scale"
        private const val KEY_TEXT = "text_colour"
        private const val KEY_RAIL = "rail_colour"
        private const val KEY_PARTS = "colour_parts"
        const val MENUS = "menus"

        /** The parts his text colour can go to: (id, what the chip says). */
        val COLOUR_PARTS = listOf(
            "apps" to "App names", "clock" to "Clock", "date" to "Date", "line" to "Line under the date",
            "widgets" to "Widgets", "rail" to "A–Z letters", MENUS to "Menus & settings",
        )
        const val DARK = "dark"

        /** The text colours he can pick (his ask, 23 Oct). Unspecified = the theme's accent, lightened a little. */
        val TEXT_COLOURS: Map<String, Color> = linkedMapOf(
            "white" to Color(0xFFFFFFFF),
            "cream" to Color(0xFFF6F0E0),
            "gold" to Color(0xFFF3D79C),
            "accent" to Color.Unspecified,
            "lavender" to Color(0xFFDCD0FF),
            "mint" to Color(0xFFCFF5E3),
            // Not yet: DARK (0xFF1C1F2E). Dark text would also go dark inside Seelie's dark sheets and settings;
            // it needs "on the wallpaper only" text first (noted in TASK.md).
        )
        @Volatile private var instance: LookStore? = null

        fun get(context: Context): LookStore =
            instance ?: synchronized(this) { instance ?: LookStore(context.applicationContext).also { instance = it } }
    }
}
