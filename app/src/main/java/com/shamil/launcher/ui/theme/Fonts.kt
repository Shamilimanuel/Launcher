package com.shamil.launcher.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.shamil.launcher.R

/*
 * The fonts he can pick in settings. Like NunitoSans: one fixed (static) file per weight,
 * never a single variable file (that rendered hairline-thin on his phone).
 * Families with fewer weights only list what really exists; Android picks the nearest one.
 * Licences and sources: FONTS.md in the project folder.
 */

/** One font choice in settings. [id] is what gets saved, so never rename it. */
data class SeelieFont(val id: String, val name: String, val family: FontFamily)

// Niagara's own fonts.

/** Niagara calls this one "Niagara Neo". */
val JosefinSans = FontFamily(
    Font(R.font.josefin_sans_regular, FontWeight.Normal),
    Font(R.font.josefin_sans_medium, FontWeight.Medium),
    Font(R.font.josefin_sans_semibold, FontWeight.SemiBold),
    Font(R.font.josefin_sans_bold, FontWeight.Bold),
)

val FuzzyBubbles = FontFamily(
    Font(R.font.fuzzy_bubbles_regular, FontWeight.Normal),
    Font(R.font.fuzzy_bubbles_bold, FontWeight.Bold),
)

/** Latin letters only (the full Korean file was 3 MB per weight). */
val Gaegu = FontFamily(
    Font(R.font.gaegu_regular, FontWeight.Normal),
    Font(R.font.gaegu_bold, FontWeight.Bold),
)

val Kalam = FontFamily(
    Font(R.font.kalam_regular, FontWeight.Normal),
    Font(R.font.kalam_bold, FontWeight.Bold),
)

val Lexend = FontFamily(
    Font(R.font.lexend_regular, FontWeight.Normal),
    Font(R.font.lexend_medium, FontWeight.Medium),
    Font(R.font.lexend_semibold, FontWeight.SemiBold),
    Font(R.font.lexend_bold, FontWeight.Bold),
)

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

val Merienda = FontFamily(
    Font(R.font.merienda_regular, FontWeight.Normal),
    Font(R.font.merienda_medium, FontWeight.Medium),
    Font(R.font.merienda_semibold, FontWeight.SemiBold),
    Font(R.font.merienda_bold, FontWeight.Bold),
)

val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

/** Handwriting, one weight only. */
val NothingYouCouldDo = FontFamily(
    Font(R.font.nothing_you_could_do_regular, FontWeight.Normal),
)

/** The rounder cousin of Nunito Sans. */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

/** One weight only. */
val Pangolin = FontFamily(
    Font(R.font.pangolin_regular, FontWeight.Normal),
)

// Seelie's Genshin-style fonts.

/** Carved-stone capitals, like Genshin's titles. */
val Cinzel = FontFamily(
    Font(R.font.cinzel_regular, FontWeight.Normal),
    Font(R.font.cinzel_medium, FontWeight.Medium),
    Font(R.font.cinzel_semibold, FontWeight.SemiBold),
    Font(R.font.cinzel_bold, FontWeight.Bold),
)

/** Soft rounded, Inazuma feel. Latin letters only (the full Japanese file was 4 MB per weight). */
val ZenMaruGothic = FontFamily(
    Font(R.font.zen_maru_gothic_regular, FontWeight.Normal),
    Font(R.font.zen_maru_gothic_medium, FontWeight.Medium),
    Font(R.font.zen_maru_gothic_bold, FontWeight.Bold),
)

val Fredoka = FontFamily(
    Font(R.font.fredoka_regular, FontWeight.Normal),
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)

/** Elegant serif. */
val CormorantGaramond = FontFamily(
    Font(R.font.cormorant_garamond_regular, FontWeight.Normal),
    Font(R.font.cormorant_garamond_medium, FontWeight.Medium),
    Font(R.font.cormorant_garamond_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_garamond_bold, FontWeight.Bold),
)

/** Tall and narrow, good for a big clock. */
val Oswald = FontFamily(
    Font(R.font.oswald_regular, FontWeight.Normal),
    Font(R.font.oswald_medium, FontWeight.Medium),
    Font(R.font.oswald_semibold, FontWeight.SemiBold),
    Font(R.font.oswald_bold, FontWeight.Bold),
)

/** All fonts, in the order the settings show them: standard, Niagara Neo, system, then A to Z. */
val SeelieFonts = listOf(
    SeelieFont("nunito_sans", "Standard (Nunito Sans)", NunitoSans),
    SeelieFont("josefin_sans", "Niagara Neo (Josefin Sans)", JosefinSans),
    SeelieFont("system", "System", FontFamily.Default),
    SeelieFont("cinzel", "Cinzel", Cinzel),
    SeelieFont("cormorant_garamond", "Cormorant Garamond", CormorantGaramond),
    SeelieFont("fredoka", "Fredoka", Fredoka),
    SeelieFont("fuzzy_bubbles", "Fuzzy Bubbles", FuzzyBubbles),
    SeelieFont("gaegu", "Gaegu", Gaegu),
    SeelieFont("kalam", "Kalam", Kalam),
    SeelieFont("lexend", "Lexend", Lexend),
    SeelieFont("manrope", "Manrope", Manrope),
    SeelieFont("merienda", "Merienda", Merienda),
    SeelieFont("montserrat", "Montserrat", Montserrat),
    SeelieFont("nothing_you_could_do", "Nothing You Could Do", NothingYouCouldDo),
    SeelieFont("nunito", "Nunito", Nunito),
    SeelieFont("oswald", "Oswald", Oswald),
    SeelieFont("pangolin", "Pangolin", Pangolin),
    SeelieFont("zen_maru_gothic", "Zen Maru Gothic", ZenMaruGothic),
)

/** The font saved under [id]; an unknown id falls back to Nunito Sans. */
fun fontById(id: String): FontFamily = SeelieFonts.firstOrNull { it.id == id }?.family ?: NunitoSans
