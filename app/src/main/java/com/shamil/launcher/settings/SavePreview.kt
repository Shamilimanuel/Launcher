package com.shamil.launcher.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.shamil.launcher.apps.SeelieIcons
import com.shamil.launcher.ui.clock.ClockStyle
import com.shamil.launcher.ui.home.DATE_LOOKS
import com.shamil.launcher.ui.home.HomeBundle
import com.shamil.launcher.ui.home.HomeLayout
import com.shamil.launcher.ui.home.HomeLayoutStore
import com.shamil.launcher.ui.home.HomeMode
import com.shamil.launcher.ui.home.HomeModeStore
import com.shamil.launcher.ui.home.HomeStyle
import com.shamil.launcher.ui.home.LayoutPreset
import com.shamil.launcher.ui.home.StatKind
import com.shamil.launcher.ui.home.StatsStyle
import com.shamil.launcher.ui.livebg.LIVE_BG_PRESETS
import com.shamil.launcher.ui.livebg.LiveBgStore
import com.shamil.launcher.ui.livebg.LiveBgStyle
import com.shamil.launcher.ui.livebg.LiveFx
import com.shamil.launcher.ui.media.MusicStyle
import com.shamil.launcher.ui.rail.RailStore
import com.shamil.launcher.ui.rail.RailStyle
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.theme.SavesStore
import com.shamil.launcher.ui.theme.SeelieFonts
import com.shamil.launcher.ui.theme.WallpaperTheme
import com.shamil.launcher.ui.theme.themeById
import com.shamil.launcher.ui.toggles.QuickToggle
import com.shamil.launcher.ui.vault.PinLook
import com.shamil.launcher.ui.vault.PinLookStore
import org.json.JSONObject
import kotlin.math.roundToInt

/*
 * The full save preview, part 1: what a save holds, in plain values (his 24 Oct ask: "that save preview needs to be
 * fixed, it doesn't show enough, it needs everything"; a save is his whole setup, like swapping a Linux desktop with
 * one script). Everything is read from the save's snapshot JSON, the same way each store reads its own file, so
 * previewing never touches his real settings. The drawing is in SavePhone.kt, the screen in ThemePreview.kt.
 */

/** The parts a save holds, as the phone, the chips and the receipt show them. */
internal class SaveParts(
    val look: LauncherLook,
    val themeName: String,
    val fontName: String,
    val textColourName: String,
    /** Where his text colour goes ("App names", "Clock"…). */
    val colourPartNames: List<String>,
    val railAccent: Boolean,
    val iconsName: String,
    val iconScale: Float,
    val home: HomeStyle,
    val bundle: HomeBundle,
    val clock: ClockStyle,
    val layout: HomeLayout,
    val live: LiveBgStyle,
    val music: MusicStyle,
    val toggles: ToggleLook,
    val stats: StatsStyle,
    val rail: RailStyle,
    val railLeft: Boolean,
    val pin: PinLook,
    /** Settings files an older save doesn't hold: applying it keeps his own for those, so the preview shows his own. */
    val notInSave: Set<String>,
    /** Which home: Seelie's tall page, Poster or the Radial wheel (25 Oct, audit P3: the preview now draws all three). */
    val mode: HomeMode = HomeMode.SEELIE,
)

/** The quick toggles row as a save describes it (the store reads the same JSON; this copy never touches the store). */
internal class ToggleLook(
    val show: Boolean = false,
    val toggles: List<QuickToggle> = QuickToggle.DEFAULT,
    val after: Int = 3,
    val pills: Boolean = false,
    val size: String = "medium",
    val gap: Int = 12,
    val shape: String = "circle",
    val onColour: String = "accent",
    val glow: Boolean = true,
    val offLook: String = "glass",
    val card: Boolean = false,
    val cardLabel: Boolean = true,
    val align: String = "start",
    val labels: Boolean = false,
)

/** The phone's layers, back to front: (key, name). The chips use the same keys, plus "type" for fonts & colours. */
internal val SAVE_LAYERS = listOf(
    "wall" to "Wallpaper", "live" to "Living background", "clock" to "Clock & date", "widgets" to "Widgets",
    "music" to "Music card", "favs" to "Favourites", "rail" to "A–Z rail", "pin" to "PIN screen",
)

/**
 * Reads a save. [now] = his settings right now (for parts an older save doesn't hold). Nothing here writes anything.
 */
internal fun readSaveParts(context: Context, snapshot: JSONObject, now: JSONObject = SavesStore.get(context).currentSnapshot()): SaveParts =
    runCatching { readSavePartsOrThrow(context, snapshot, now) }.getOrElse { readSavePartsOrThrow(context, now, now) }

private fun readSavePartsOrThrow(context: Context, snapshot: JSONObject, now: JSONObject): SaveParts {
    val missing = now.keys().asSequence().filter { !snapshot.has(it) }.toSet()
    fun file(f: String): JSONObject? = snapshot.optJSONObject(f) ?: now.optJSONObject(f)
    fun entry(f: String, key: String): JSONObject? = file(f)?.optJSONObject(key)
    fun str(f: String, key: String): String? = entry(f, key)?.optString("v")?.takeIf { it.isNotEmpty() }
    fun bool(f: String, key: String): Boolean? = entry(f, key)?.opt("v") as? Boolean
    fun num(f: String, key: String): Float? = entry(f, key)?.optDouble("v")?.toFloat()?.takeIf { !it.isNaN() }
    fun set(f: String, key: String): Set<String>? =
        entry(f, key)?.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }

    // Look: theme, font, text colours, icons (the same sums LookStore does).
    val themeId = str("look", "theme")
    val wallpaperTheme = themeId == WallpaperTheme.ID
    val theme = if (wallpaperTheme) LookStore.get(context).wallpaperLook ?: themeById(null) else themeById(themeId)
    val fontId = str("look", "font")
    val textColour = str("look", "text_colour")
    val parts = set("look", "colour_parts") ?: LookStore.COLOUR_PARTS.map { it.first }.toSet()
    val railColour = str("look", "rail_colour") ?: "text"
    val iconScale = num("look", "icon_scale") ?: 1f
    val look = LookStore.build(theme, fontId, iconScale, textColour, parts, railColour)
    val iconPack = str("look", "icon_pack")
    val iconsName = when (iconPack) {
        null -> "the first icon pack on the phone"
        LookStore.NO_PACK -> "the apps' own icons"
        SeelieIcons.THEMED -> "Seelie themed"
        SeelieIcons.GLASS -> "Seelie glass"
        else -> runCatching { context.packageManager.run { getApplicationLabel(getApplicationInfo(iconPack, 0)).toString() } }
            .getOrDefault("an icon pack that isn't installed")
    }

    val bundle = HomeBundle.byId(str("home_style", "bundle"))
    // Music: the first version saved four loose switches; those still count until a style was saved (like MusicPrefs).
    val oldMusic = MusicStyle(
        show = bool("music_card", "show") ?: false,
        size = str("music_card", "size") ?: "large",
        bar = if (bool("music_card", "progress") ?: true) "thin" else "none",
        idle = if (bool("music_card", "reserve_spot") ?: true) "keep" else "hide",
    )
    return SaveParts(
        look = look,
        themeName = if (wallpaperTheme) "Match my wallpaper" else theme.name,
        fontName = fontId?.let { id -> SeelieFonts.firstOrNull { it.id == id }?.name } ?: "the theme's own",
        textColourName = textColour?.replaceFirstChar { it.uppercase() } ?: "the theme's own",
        colourPartNames = LookStore.COLOUR_PARTS.filter { it.first in parts }.map { it.second },
        railAccent = railColour == "accent",
        iconsName = iconsName,
        iconScale = iconScale,
        home = HomeStyle.fromJson(str("home_style", "style"), bundle.style),
        bundle = bundle,
        clock = ClockStyle.byId(str("clock", "clock_style")),
        // The store's own starting point (clock lower, a touch smaller) for a save made before the layout editor.
        layout = HomeLayout.fromJson(str(HomeLayoutStore.PREFS, "layout"), HomeLayout(clockTop = 0.29f, clockScale = 0.95f)),
        live = LiveBgStyle.fromJson(str(LiveBgStore.PREFS, "style"), LiveBgStyle()),
        music = MusicStyle.fromJson(str("music_card", "style"), oldMusic),
        toggles = readToggles(str("quick_toggles", "setup")),
        stats = readStats(str("stats_card", "style")),
        rail = RailStyle.byId(str(RailStore.PREFS, "style")),
        railLeft = bool(RailStore.PREFS, "left") ?: false,
        pin = PinLook.byId(str(PinLookStore.PREFS, "look")),
        notInSave = missing,
        mode = HomeMode.byId(str(HomeModeStore.PREFS, "mode")),
    )
}

/** The toggles setup JSON, read like TogglePrefs does (a setup from the first version counts as hidden). */
private fun readToggles(json: String?): ToggleLook = try {
    val o = JSONObject(json ?: return ToggleLook())
    val d = ToggleLook()
    val list = o.optJSONArray("toggles")
    fun pick(key: String, fallback: String, vararg allowed: String) = o.optString(key, fallback).takeIf { it in allowed } ?: fallback
    ToggleLook(
        show = o.optInt("v", 1) >= 2 && o.optBoolean("show", d.show),
        toggles = if (list == null) d.toggles else (0 until list.length()).mapNotNull { QuickToggle.byId(list.optString(it)) }.distinct(),
        after = o.optInt("after", d.after).coerceIn(0, 10),
        pills = o.optString("style") == "pills",
        size = pick("size", d.size, "small", "medium", "large"),
        gap = o.optInt("gap", d.gap).coerceIn(4, 28),
        shape = pick("shape", d.shape, "circle", "squircle", "pill"),
        onColour = pick("onColour", d.onColour, "accent", "gold", "white", "home"),
        glow = o.optBoolean("glow", d.glow),
        offLook = pick("offLook", d.offLook, "glass", "soft", "bare"),
        card = o.optString("backing") == "card",
        cardLabel = o.optBoolean("cardLabel", d.cardLabel),
        align = pick("align", d.align, "start", "center", "spread"),
        labels = o.optBoolean("labels", d.labels),
    )
} catch (e: Exception) {
    ToggleLook()
}

/** The stats card JSON, read like StatsStore does. */
private fun readStats(json: String?): StatsStyle = try {
    val o = JSONObject(json ?: return StatsStyle())
    val list = o.optJSONArray("stats")
    StatsStyle(
        stats = if (list == null) StatsStyle().stats else (0 until list.length()).mapNotNull { StatKind.byId(list.getString(it)) },
        showWeather = o.optBoolean("showWeather", true),
        hours = o.optInt("hours", 3),
        ringStyle = o.optString("ringStyle", "circle"),
    )
} catch (e: Exception) {
    StatsStyle()
}

/** Which wallpaper pictures a page has (for the words; the pictures themselves load in SavePhone). */
internal class WallInfo(val home: Boolean, val lock: Boolean, val current: Boolean)

/** Said after a part an older save doesn't hold. */
private const val KEEPS_YOURS = " (not in this save: applying it keeps yours)"

private fun pct(f: Float) = "${(f * 100).roundToInt()}%"

private fun align(a: String) = when (a) { "centre" -> "centred"; "right" -> "on the right"; else -> "on the left" }

private fun liveWords(l: LiveBgStyle, full: Boolean): String {
    if (!l.anythingOn()) return "Off"
    val on = LiveFx.ALL.filter { it.index != LiveFx.TOUCH && it.index != LiveFx.TILT && l.fx(it.id).on }
    val names = if (full) on.joinToString(", ") { "${it.name} ${l.fx(it.id).density}" }
    else on.take(3).joinToString(", ") { it.name } + if (on.size > 3) " +${on.size - 3}" else ""
    val preset = LIVE_BG_PRESETS.firstOrNull { it.id == l.preset }?.name
    return buildString {
        append(names.ifEmpty { "Only touch & tilt" })
        append(" · ${l.mood} mood")
        if (l.calm) append(" · calm")
        if (full) {
            append(" · time: ${l.time}, weather: ${l.weather}")
            if (l.fx("touch").on) append(" · touch sparkle")
            if (l.fx("tilt").on) append(" · tilt ${l.fx("tilt").density}")
            if (l.fps60) append(" · 60 fps")
        }
        if (preset != null) append(" · preset $preset")
    }
}

private fun wallWords(p: SaveParts, w: WallInfo): String = buildString {
    append(
        when {
            w.home && w.current -> "Your wallpaper (the copy you picked in Seelie)"
            w.home -> "Its own picture"
            w.current -> "Android doesn't let Seelie see your wallpaper, so it shows the theme's colours"
            else -> "No picture: it keeps your wallpaper and shows the theme's colours here"
        }
    )
    if (w.lock) append(" + a lock screen picture")
    if (p.layout.fade) append(" · fades from ${p.layout.fadeReach}% into ${p.layout.fadeColour}")
}

private fun dateWords(p: SaveParts): String {
    val dateOff = !p.layout.date || p.clock == ClockStyle.BigDay
    val date = if (dateOff) "date off" else "${DATE_LOOKS.firstOrNull { it.first == p.home.dateStyle }?.second ?: p.home.dateStyle} date"
    val line = if (p.layout.glance) "${p.home.glanceStyle} glance line" else "glance line off"
    return "$date · $line"
}

private fun cardWords(h: HomeStyle) =
    if (h.cardStyle == "none") "no card" else "${h.cardStyle} card ${h.cardOpacity}%, ${h.cardRadius} dp corners"

private fun musicWords(m: MusicStyle): String =
    if (!m.show) "Off (its look is saved for when you switch it on)"
    else "${m.size} · ${if (m.artBackground) "album art card" else if (m.followHome) "home style card" else "${m.cardStyle} card"} · " +
        "${m.artShape} cover · ${m.bar} bar"

private fun favWords(p: SaveParts): String = buildString {
    append(when (p.layout.icons) { "small" -> "Small icons"; "text" -> "Text only"; else -> "Normal icons" })
    append(if (p.layout.group) " · grouped" else " · one list")
    if (p.toggles.show) append(" · quick toggles after ${p.toggles.after}")
    append(" · icons: ${p.iconsName}")
    if (!p.layout.favs) { clear(); append("Hidden on the home") }
}

/** The home style in a few words (25 Oct, audit P3), for the "Home style" chip and the receipt. */
private fun modeWords(p: SaveParts): String = when (p.mode) {
    HomeMode.SEELIE -> "Seelie: the tall page with your clock, widgets, music card and favourites"
    HomeMode.POSTER -> "Poster: your wallpaper as a painting, a big clock and four apps (no widgets, music card or toggles)"
    HomeMode.WHEEL -> "Radial wheel: a calm clock, apps in the " + (if (p.railLeft) "bottom-left" else "bottom-right") +
        " corner wheel (no widgets, music card or toggles)"
}

/** One short line per chip (keys as in [SAVE_LAYERS], plus "type" and "mode"). */
internal fun partDetails(p: SaveParts, w: WallInfo): Map<String, String> {
    fun keeps(vararg files: String) = if (files.any { it in p.notInSave }) KEEPS_YOURS else ""
    return mapOf(
        "mode" to modeWords(p) + keeps(HomeModeStore.PREFS),
        "wall" to wallWords(p, w) + keeps(HomeLayoutStore.PREFS),
        "live" to liveWords(p.live, full = false) + keeps(LiveBgStore.PREFS),
        "clock" to "${p.clock.label} clock · ${pct(p.layout.clockScale)} · ${pct(p.layout.clockTop)} down · ${align(p.layout.alignClock)}. " +
            dateWords(p) + keeps("clock", HomeLayoutStore.PREFS),
        "widgets" to (if (!p.layout.widgets) "Off" else "Shown, in a ${cardWords(p.home)}") +
            ". Which widgets are in your stack isn't part of a save: you keep your own." + keeps("home_style"),
        "music" to musicWords(p.music) + keeps("music_card"),
        "favs" to favWords(p) + ". Your favourite apps stay yours." + keeps("quick_toggles", HomeLayoutStore.PREFS),
        "rail" to "${p.rail.title} · letters ${if (p.railAccent) "in the accent" else "like the text"}" +
            (if (p.railLeft) " · on the left" else "") + keeps(RailStore.PREFS),
        "type" to "${p.fontName} · text ${p.textColourName.lowercase()} · ${p.themeName} theme" + keeps("look"),
        "pin" to "${p.pin.title}. The fun lock lives in the vault, not in a save." + keeps(PinLookStore.PREFS),
    )
}

/** The receipt: every value the save holds, as (what, value). Colour swatches are drawn next to "Fonts & colours". */
internal fun receiptRows(p: SaveParts, w: WallInfo): List<Pair<String, String>> {
    val l = p.layout
    val h = p.home
    val m = p.music
    val t = p.toggles
    val order = l.order.joinToString(" → ") {
        when (it) { "top" -> "Clock"; "widgets" -> "Widgets"; "music" -> "Music"; else -> "Favourites" }
    }
    val rows = listOf(
        "Home style" to modeWords(p),
        "Theme" to p.themeName,
        "Wallpaper" to wallWords(p, w),
        "Living bg" to liveWords(p.live, full = true),
        "Clock" to "${p.clock.label} · ${pct(l.clockScale)} size · ${pct(l.clockTop)} down · ${align(l.alignClock)}" +
            (if (!l.clock) " · hidden" else "") + (if (l.sparkles) " · sparkles" else "") +
            " · ${h.clockColor} ${if (h.clockFont == "cinzel") "Cinzel" else "theme font"}, weight ${h.clockWeight}",
        "Date" to (if (!l.date) "Off" else "${DATE_LOOKS.firstOrNull { it.first == h.dateStyle }?.second ?: h.dateStyle} · " +
            "${h.dateSize.roundToInt()} sp · ${h.dateColor}${if (h.dateRule) " · hairline" else ""} · ${align(l.alignDate)}"),
        "Glance line" to (if (!l.glance) "Off" else "${h.glanceStyle} · ${h.glanceColor} · ${align(l.alignGlance)}" +
            if (h.inline) " · beside the date" else ""),
        // "Home style" above is the home itself (Seelie, Poster, wheel); this row is the bundle of clock and card looks.
        "Style bundle" to "${p.bundle.title}${if (h != p.bundle.style) " (tweaked)" else ""} · ${cardWords(h)}" +
            (if (h.spirit) " · Seelie spirit" else "") + " · edge ${h.edge} dp",
        "Layout" to "${LayoutPreset.entries.firstOrNull { it.id == l.preset }?.title ?: "Your own"} · $order",
        "Widgets" to (if (l.widgets) "Shown · ${h.cardSize} · today in ${h.cardToday}" else "Off") +
            " · the stack's widgets aren't in a save",
        "Music card" to musicWords(m) + if (m.show) " · ${m.accent} colour · ${m.buttons} buttons" +
            (if (m.skipButtons) " · ⏮ ⏭" else "") + " · when idle: ${m.idle}" else "",
        "Favourites" to favWords(p) + " · icon size ${pct(p.iconScale)}",
        "Quick toggles" to (if (!t.show) "Off" else "${t.toggles.joinToString(", ") { it.short }} · after ${t.after} · " +
            "${if (t.pills) "pills" else "icons"}, ${t.size} ${t.shape}${if (t.card) " · on a card" else ""}"),
        "Stats card" to "${p.stats.stats.joinToString(", ") { it.title }.ifEmpty { "no stats" }} · ${p.stats.ringStyle}" +
            if (p.stats.showWeather) " · weather" else "",
        "A–Z rail" to "${p.rail.title} · letters ${if (p.railAccent) "in the accent" else "like the text"} · " +
            if (p.railLeft) "rail on the left" else "rail on the right",
        "Fonts & colours" to "${p.fontName} · text ${p.textColourName.lowercase()} on ${p.colourPartNames.joinToString(", ").lowercase().ifEmpty { "nothing" }}",
        "PIN look" to "${p.pin.title} · the fun lock stays in the vault",
    )
    return rows + if (p.notInSave.isEmpty()) emptyList()
    else listOf("Not in this save" to "${p.notInSave.joinToString(", ")}: an older save, so applying it keeps your own for these")
}

/** The swatches for "Fonts & colours": accent, text, dim text, and the sheet behind menus. */
internal fun SaveParts.swatches(): List<Color> = listOf(look.accent, look.text, look.textDim, look.sheet.copy(alpha = 1f))
