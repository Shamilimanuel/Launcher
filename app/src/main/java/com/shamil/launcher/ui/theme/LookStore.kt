package com.shamil.launcher.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
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

    /** The look to show: the theme, with his font and icon size swapped in. */
    val look: LauncherLook
        get() {
            val theme = themeById(themeId)
            val withFont = fontId?.let { theme.copy(font = fontById(it)) } ?: theme
            return withFont.copy(iconScale = iconScale)
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
        @Volatile private var instance: LookStore? = null

        fun get(context: Context): LookStore =
            instance ?: synchronized(this) { instance ?: LookStore(context.applicationContext).also { instance = it } }
    }
}
