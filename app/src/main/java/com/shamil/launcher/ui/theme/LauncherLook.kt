package com.shamil.launcher.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.shamil.launcher.R

/**
 * Everything the home screen's look comes from. Screens read colours and fonts from here,
 * never hard-code them, so swapping themes later is just swapping this object.
 * The prototype's LOOKS object lists the fields still to come (icon style, background, ...).
 */
data class LauncherLook(
    /** Stable id, saved in settings ("night", "fuji", ...). Never rename one: his saved choice would be lost. */
    val id: String,
    val name: String,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val textShadow: Color,
    /** Fill for floating bits on the wallpaper: the round search button, the search box. */
    val panel: Color,
    /** Background of sheets that slide up, like the app menu. */
    val sheet: Color,
    /** The top of those sheets: a little lighter, fading into [sheet] (a gradient, like the prototype). */
    val sheetTop: Color,
    /** Fill of the rounded cards inside sheets. */
    val card: Color,
    val font: FontFamily,
    val clockWeight: FontWeight,
    /** App icon size, 1 = Niagara's 44dp. Set in Seelie settings → Look → Icons, not by the theme. */
    val iconScale: Float = 1f,
    /** The A–Z letters' colour; Unspecified = the same as [text]. Set in Look → Fonts. */
    val rail: Color = Color.Unspecified,
    /** His text colour for each part he picked ("apps", "clock", "date", "line", "widgets"); parts not in here keep [text]. */
    val partText: Map<String, Color> = emptyMap(),
) {
    /** This look with the text colour of one part swapped in (used around that part of the home). */
    fun forPart(part: String): LauncherLook {
        val c = partText[part] ?: return this
        return copy(text = c, textDim = c.copy(alpha = c.alpha * 0.82f))
    }

    /** The soft shadow behind all text, so it stays readable on any wallpaper. */
    val shadow: Shadow get() = Shadow(color = textShadow, blurRadius = 8f)
}

/**
 * Nunito Sans, Niagara's default font: one fixed file per weight.
 * (23 Oct: a single "variable" font file rendered every weight hairline-thin on his phone.)
 */
val NunitoSans = FontFamily(
    Font(R.font.nunito_sans_regular, FontWeight.Normal),
    Font(R.font.nunito_sans_medium, FontWeight.Medium),
    Font(R.font.nunito_sans_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_sans_bold, FontWeight.Bold),
)

/** His current look: white text on his anime wallpaper, pink accent. */
val Fuji = LauncherLook(
    id = "fuji",
    name = "Fuji",
    text = Color(0xFFFFFFFF),
    textDim = Color(0xD9FFFFFF),
    accent = Color(0xFFFFA8CC),
    textShadow = Color(0x73000000),
    panel = Color(0xCC4F5E92),
    // Sheet colours from the prototype's Fuji theme (he marked the themes and the card sheet Good, 23 Oct).
    sheet = Color(0xFA150F33),
    sheetTop = Color(0xFA231646),
    card = Color(0x14FFFFFF),
    font = NunitoSans,
    clockWeight = FontWeight.Medium,
)

/*
 * The other three themes, colours copied from the prototype's THEMES (he marked "4 themes" Good, 23 Oct).
 * Their own backgrounds, icons and clock styles come later; for now a theme sets colours and font.
 */

/** The brand theme: night and gold. */
val NightGold = LauncherLook(
    id = "night",
    name = "Night & Gold",
    text = Color(0xFFF6F0E0),
    textDim = Color(0xFFBDB6A3),
    accent = Color(0xFFE8C26B),
    textShadow = Color(0x99000000),
    panel = Color(0xAD161B2A),
    sheet = Color(0xFA0E121E),
    sheetTop = Color(0xFA161C2E),
    card = Color(0x0EFFFFFF),
    font = NunitoSans,
    clockWeight = FontWeight.Normal,
)

/** Storm light, electro purple. */
val InazumaViolet = LauncherLook(
    id = "inazuma",
    name = "Inazuma Violet",
    text = Color(0xFFF4EEFF),
    textDim = Color(0xFFC2B5E0),
    accent = Color(0xFFC9A0FF),
    textShadow = Color(0x99000000),
    panel = Color(0xB3241646),
    sheet = Color(0xFA110A26),
    sheetTop = Color(0xFA1C1240),
    card = Color(0x0FFFFFFF),
    font = ZenMaruGothic,   // the prototype gives this theme Zen Maru Gothic
    clockWeight = FontWeight.Normal,
)

/** Rainforest light, dendro green. */
val SumeruGreen = LauncherLook(
    id = "sumeru",
    name = "Sumeru Green",
    text = Color(0xFFF0F9EA),
    textDim = Color(0xFFB5CDB0),
    accent = Color(0xFFA8DA72),
    textShadow = Color(0x99000000),
    panel = Color(0xB30C2822),
    sheet = Color(0xFA08170F),
    sheetTop = Color(0xFA0F2A20),
    card = Color(0x0FFFFFFF),
    font = ZenMaruGothic,   // the prototype gives this theme Zen Maru Gothic
    clockWeight = FontWeight.Medium,
)

/** All themes, in the order the settings show them. */
val SeelieThemes = listOf(NightGold, Fuji, InazumaViolet, SumeruGreen)

fun themeById(id: String?): LauncherLook = SeelieThemes.firstOrNull { it.id == id } ?: Fuji

/** The active look, available to every screen via `LocalLauncherLook.current`. */
val LocalLauncherLook = staticCompositionLocalOf { Fuji }
