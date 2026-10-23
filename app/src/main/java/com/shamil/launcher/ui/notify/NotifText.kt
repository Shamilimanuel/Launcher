package com.shamil.launcher.ui.notify

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.shamil.launcher.ui.theme.LauncherLook

/*
 * Message text with simple "markdown" (the little marks Discord and WhatsApp use for styling) drawn as real styles
 * instead of raw symbols (Niagara shows the raw ** and :emoji: names; shot 35):
 *   **bold**  *italic*  __underline__  ~~strike~~  `code`  ```code block```  ||spoiler||
 *   "# Heading" lines in bold, "> quote" and "-# small print" lines dimmed, "- item" lines with a bullet.
 * Discord's :emoji_names: and <:custom:123> emoji are removed, so the text stays clean.
 */

// <:name:123> and <a:name:123> = Discord's own custom emoji.
private val CUSTOM_EMOJI = Regex("<a?:\\w{2,32}:\\d+>")
// :name: = an emoji Android couldn't turn into a picture. Only removed when it has a letter, so "12:30:45" stays.
private val EMOJI_NAME = Regex(":([\\w+~-]{2,32}):")
private val SPACES = Regex("[ \\t]{2,}")

/** Removes emoji names and tidies spaces. [flat] = everything on one line (for the short line under a favourite). */
internal fun cleanMessage(raw: String, flat: Boolean = false): String {
    var s = raw.replace(CUSTOM_EMOJI, "")
    s = EMOJI_NAME.replace(s) { m -> if (m.groupValues[1].any { it.isLetter() }) "" else m.value }
    if (flat) s = s.replace('\n', ' ')
    return s.lines().joinToString("\n") { it.replace(SPACES, " ").trim() }.trim()
}

/** The message as styled text in [look]'s colours. [flat] puts it on one line and drops headings and quotes. */
internal fun richText(raw: String, look: LauncherLook, flat: Boolean = false): AnnotatedString {
    val s = cleanMessage(raw, flat)
    return buildAnnotatedString {
        var bold = false
        var italic = false
        var under = false
        var strike = false
        var spoiler = false
        var code: String? = null      // the fence we're inside ("`" or "```"), or null
        var lineBold = false
        var lineDim = false
        val run = StringBuilder()

        fun style() = SpanStyle(
            color = if (lineDim) look.textDim else Color.Unspecified,
            fontWeight = if (bold || lineBold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            fontFamily = if (code != null) FontFamily.Monospace else null,
            background = when {
                code != null -> look.text.copy(alpha = 0.12f)
                spoiler -> look.text.copy(alpha = 0.22f)
                else -> Color.Unspecified
            },
            textDecoration = TextDecoration.combine(listOfNotNull(
                if (under) TextDecoration.Underline else null,
                if (strike) TextDecoration.LineThrough else null,
            )),
        )

        // Writes the text collected so far in the style that was active for it.
        fun flush() {
            if (run.isEmpty()) return
            withStyle(style()) { append(run.toString()) }
            run.clear()
        }

        // A mark only counts as "start" if the same mark comes again later with something in between
        // (so a lone * or an empty ** stays as typed).
        fun closesLater(mark: String, from: Int) = s.indexOf(mark, from + mark.length) > from + mark.length

        var i = 0
        while (i < s.length) {
            // At the start of each line: headings, quotes, small print and list bullets.
            if (code == null && (i == 0 || s[i - 1] == '\n')) {
                flush()
                lineBold = false
                lineDim = false
                val rest = s.substring(i)
                when {
                    rest.startsWith("-# ") -> { lineDim = true; i += 3 }
                    rest.startsWith("### ") -> { lineBold = !flat; i += 4 }
                    rest.startsWith("## ") -> { lineBold = !flat; i += 3 }
                    rest.startsWith("# ") -> { lineBold = !flat; i += 2 }
                    rest.startsWith("> ") -> { lineDim = !flat; i += 2 }
                    rest.startsWith("- ") || rest.startsWith("* ") -> { run.append("• "); i += 2 }
                }
                if (i >= s.length) break
            }

            // Inside code: everything is literal until the closing fence.
            val fence = code
            if (fence != null) {
                if (s.startsWith(fence, i)) { flush(); code = null; i += fence.length } else { run.append(s[i]); i++ }
                continue
            }

            when {
                s.startsWith("```", i) && closesLater("```", i) -> {
                    flush(); code = "```"; i += 3
                    // Skip a language name right after the fence ("```kotlin").
                    val nl = s.indexOf('\n', i)
                    if (nl >= 0 && s.substring(i, nl).all { it.isLetterOrDigit() }) i = nl + 1
                }
                s[i] == '`' && closesLater("`", i) -> { flush(); code = "`"; i += 1 }
                s.startsWith("**", i) && (bold || closesLater("**", i)) -> { flush(); bold = !bold; i += 2 }
                s.startsWith("__", i) && (under || closesLater("__", i)) -> { flush(); under = !under; i += 2 }
                s.startsWith("~~", i) && (strike || closesLater("~~", i)) -> { flush(); strike = !strike; i += 2 }
                s.startsWith("||", i) && (spoiler || closesLater("||", i)) -> { flush(); spoiler = !spoiler; i += 2 }
                // A single * starts italic only right before a word ("2 * 3" stays maths).
                s[i] == '*' && (italic || (i + 1 < s.length && !s[i + 1].isWhitespace() && closesLater("*", i))) -> {
                    flush(); italic = !italic; i += 1
                }
                else -> { run.append(s[i]); i++ }
            }
        }
        flush()
    }
}
