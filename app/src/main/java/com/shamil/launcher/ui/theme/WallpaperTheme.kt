package com.shamil.launcher.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight

/**
 * "Match my wallpaper" (his idea, 23 Oct; also the Material You part of his locked feature #3):
 * a theme whose colours come from his own wallpaper. Android already works these colours out for the whole phone
 * (the same palette Samsung uses); on Android 12+ we use that palette, on older phones the wallpaper's main colours.
 */
object WallpaperTheme {
    const val ID = "wallpaper"

    /** The theme built from the current wallpaper, or null if Android can't tell its colours. */
    fun build(context: Context): LauncherLook? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The phone's own Material You palette (dark version: Seelie sits on a wallpaper, light text reads best).
            val s = dynamicDarkColorScheme(context)
            look(accent = s.primary, panel = s.primaryContainer.copy(alpha = 0.8f), sheet = s.surface, sheetTop = s.surfaceContainerHigh)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val colours = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return@runCatching null
            val main = Color(colours.primaryColor.toArgb())
            val second = colours.secondaryColor?.let { Color(it.toArgb()) } ?: main
            look(
                accent = lerp(second, Color.White, 0.45f),       // bright enough to read on the wallpaper
                panel = lerp(main, Color.Black, 0.45f).copy(alpha = 0.8f),
                sheet = lerp(main, Color.Black, 0.82f),
                sheetTop = lerp(main, Color.Black, 0.72f),
            )
        } else null
    }.getOrNull()

    private fun look(accent: Color, panel: Color, sheet: Color, sheetTop: Color) = LauncherLook(
        id = ID,
        name = "Match my wallpaper",
        text = Color.White,
        textDim = Color(0xD9FFFFFF),
        accent = accent,
        textShadow = Color(0x80000000),
        panel = panel,
        sheet = sheet.copy(alpha = 0.98f),
        sheetTop = sheetTop.copy(alpha = 0.98f),
        card = Color(0x14FFFFFF),
        font = NunitoSans,
        clockWeight = FontWeight.Medium,
    )
}
