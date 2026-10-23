package com.shamil.launcher.ui.theme

import android.content.Context
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
class LookStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("look", Context.MODE_PRIVATE)

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
    /** The A–Z letters: "text" (same as the text) or "accent". */
    var railColour by mutableStateOf(prefs.getString(KEY_RAIL, "text") ?: "text")
        private set

    /** The look to show: the theme, with his font, icon size and text colours swapped in. */
    val look: LauncherLook
        get() {
            val theme = themeById(themeId)
            val withFont = fontId?.let { theme.copy(font = fontById(it)) } ?: theme
            val base = withFont.copy(iconScale = iconScale)
            val text = TEXT_COLOURS[textColour]?.let { if (it == Color.Unspecified) base.accent else it }
            val coloured = if (text == null) base else base.copy(
                text = text,
                textDim = text.copy(alpha = if (textColour == DARK) 0.75f else 0.82f),
                // Dark text on a bright wallpaper reads better with a soft light glow than a dark shadow.
                textShadow = if (textColour == DARK) Color(0x80FFFFFF) else base.textShadow,
            )
            return if (railColour == "accent") coloured.copy(rail = coloured.accent) else coloured
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

    companion object {
        /** Saved as [iconPack] when he picks "Original icons". */
        const val NO_PACK = "none"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT = "font"
        private const val KEY_ICONS = "icon_pack"
        private const val KEY_ICON_SCALE = "icon_scale"
        private const val KEY_TEXT = "text_colour"
        private const val KEY_RAIL = "rail_colour"
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
