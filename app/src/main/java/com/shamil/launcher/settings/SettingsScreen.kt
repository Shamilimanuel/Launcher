package com.shamil.launcher.settings

import com.shamil.launcher.ui.livebg.LiveBgSettingsContent
import com.shamil.launcher.ui.livebg.LiveBgPreview
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.apps.Favourites
import com.shamil.launcher.apps.IconPack
import com.shamil.launcher.apps.SeelieIcons
import com.shamil.launcher.ui.clock.ClockSettingsContent
import com.shamil.launcher.ui.search.SearchSettingsContent
import com.shamil.launcher.ui.toggles.TogglesSettingsContent
import com.shamil.launcher.ui.notify.NotifSettingsContent
import com.shamil.launcher.ui.media.MusicSettingsContent
import com.shamil.launcher.ui.media.MusicPreview
import com.shamil.launcher.ui.clock.ClockStore
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.theme.SavesStore
import com.shamil.launcher.ui.widgets.WidgetStackSettingsContent
import com.shamil.launcher.ui.widgets.AppWidgetPickerContent
import com.shamil.launcher.ui.widgets.WidgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.DefaultHome
import com.shamil.launcher.R
import com.shamil.launcher.ui.openSafely
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.theme.SeelieFonts
import com.shamil.launcher.ui.theme.SeelieThemes
import com.shamil.launcher.ui.theme.themeById
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Serenitea: the screens of Seelie settings, built from the prototype he reviewed (23 Oct).
 * Behaves like Niagara's settings (its own app, quick fade), looks like Seelie: Cinzel titles,
 * rounded cards with element-coloured icons, and a live preview of the home in his theme.
 * Groups that aren't built yet are shown with a "Soon" tag and can't be opened, so nothing pretends to work.
 */

/** The pages, in the order you can reach them. */
private enum class Page { Top, Look, Layout, LiveBg, Saves, Themes, Fonts, Icons, Clock, HomeStyle, Home, Widgets, WidgetPicker, Stats, Toggles, Notifications, Music, Search, Seelie }

// Genshin element colours for the group icons (same as the prototype).
internal val Pyro = Color(0xFFF08A5D)
internal val Hydro = Color(0xFF5BC0EB)
internal val Anemo = Color(0xFF74C2A8)
internal val Electro = Color(0xFFB58AE0)
internal val Dendro = Color(0xFFA5C83B)
internal val Cryo = Color(0xFF9FD6E3)
internal val Geo = Color(0xFFF0B232)

/**
 * Everything the settings search can find: a name, where it lives, the pages to open, and extra words
 * (English and Dutch, since his phone is Dutch) that should also find it.
 */
private class SettingHit(
    val title: String, val where: String, val path: List<Page>,
    @DrawableRes val icon: Int, val tint: Color, val words: String,
) {
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        return title.lowercase().contains(q) || where.lowercase().contains(q) || words.split(' ').any { it.startsWith(q) || q in it }
    }
}

private val SETTINGS_INDEX = listOf(
    SettingHit("Your themes (saves)", "Look · Themes", listOf(Page.Look, Page.Themes), R.drawable.ic_star, Geo, "save saves opslaan saved looks profile profiel preset wallpaper achtergrond"),
    SettingHit("Home style (bundles)", "Look", listOf(Page.Look, Page.HomeStyle), R.drawable.ic_home_filled, Electro, "home style bundle bundles glass floating gold minimal spirit transparent see-through doorzichtig card kaart spacing edge"),
    SettingHit("Layout", "Look", listOf(Page.Look, Page.Layout), R.drawable.ic_drag_indicator, Geo, "layout indeling blocks blokken move verplaatsen align uitlijnen centre midden fade vervagen trail spoor sparkles sterretjes order volgorde hide verbergen big day clock position klok positie"),
    SettingHit("Living background", "Look", listOf(Page.Look, Page.LiveBg), R.drawable.ic_wallpaper, Dendro, "living background achtergrond live animated bewegend petals blossom sakura bloesem stars sterren aurora rain regen snow sneeuw storm onweer lanterns lantaarns fireflies vuurvliegjes butterflies vlinders clouds wolken mist tilt weather weer"),
    SettingHit("Themes", "Look", listOf(Page.Look, Page.Themes), R.drawable.ic_palette, Pyro, "theme thema colours colors kleuren night gold fuji inazuma sumeru"),
    SettingHit("Fonts", "Look", listOf(Page.Look, Page.Fonts), R.drawable.ic_text_fields, Hydro, "font fonts lettertype letters text tekst nunito cinzel josefin"),
    SettingHit("Icon pack and style", "Look", listOf(Page.Look, Page.Icons), R.drawable.ic_apps, Cryo, "icons iconen pack darko themed glass pictogram"),
    SettingHit("Icon size", "Look · Icons", listOf(Page.Look, Page.Icons), R.drawable.ic_apps, Cryo, "size grootte bigger smaller groter kleiner"),
    SettingHit("Clock style", "Look · Home style", listOf(Page.Look, Page.HomeStyle), R.drawable.ic_schedule, Geo, "clock klok time tijd neon words woorden colon gold"),
    SettingHit("Date style", "Look · Home style", listOf(Page.Look, Page.HomeStyle), R.drawable.ic_schedule, Geo, "date datum day dag stamp pill capitals"),
    SettingHit("Glance line style", "Look · Home style", listOf(Page.Look, Page.HomeStyle), R.drawable.ic_sunny, Geo, "glance weather weer battery batterij line regel glow pill"),
    SettingHit("Stats card", "Home screen", listOf(Page.Home, Page.Stats), R.drawable.ic_battery_full, Hydro, "stats statistieken battery batterij memory geheugen storage opslag rings ringen uptime wifi temperature temperatuur weather fahrenheit celsius"),
    SettingHit("Notifications & quick reply", "Home screen", listOf(Page.Home, Page.Notifications), R.drawable.ic_sticky_note_2, Pyro, "notifications meldingen messages berichten reply antwoorden whatsapp quick reply access toegang"),
    SettingHit("Music card", "Home screen", listOf(Page.Home, Page.Music), R.drawable.ic_music_note, Electro, "music muziek spotify player speler song nummer album media"),
    SettingHit("Quick toggles", "Home screen", listOf(Page.Home, Page.Toggles), R.drawable.ic_flashlight_on, Anemo, "toggles wifi bluetooth torch zaklamp quiet stil dnd niet storen volume hotspot location locatie airplane vliegtuig"),
    SettingHit("Widget stack", "Home screen", listOf(Page.Home, Page.Widgets), R.drawable.ic_widgets, Pyro, "widget widgets stack calendar kalender month maand gauges notes notities week"),
    SettingHit("Add a widget from an app", "Home screen · Widget stack", listOf(Page.Home, Page.Widgets, Page.WidgetPicker), R.drawable.ic_widgets, Pyro, "app widget toevoegen add spotify agenda"),
    SettingHit("Widget card style", "Home screen · Widget stack", listOf(Page.Home, Page.Widgets), R.drawable.ic_widgets, Pyro, "card kaart glass solid outline"),
    SettingHit("Search & commands", "Seelie settings", listOf(Page.Search), R.drawable.ic_search, Hydro, "search zoeken engine zoekmachine duckduckgo google contacts contacten calculator rekenmachine commands timer torch zaklamp"),
    SettingHit("Default home app", "Seelie", listOf(Page.Seelie), R.drawable.ic_home, Geo, "home launcher default standaard startscherm niagara switch"),
    SettingHit("Version", "Seelie", listOf(Page.Seelie), R.drawable.ic_settings, Geo, "version versie about over"),
)

/** The search pill at the top of Seelie settings. */
@Composable
private fun SettingsSearch(query: String, onQuery: (String) -> Unit) {
    val look = LocalLauncherLook.current
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(look.card)
            .border(1.dp, if (query.isNotEmpty()) look.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = look.textDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search all settings", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 17.sp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp),
                cursorBrush = SolidColor(look.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { onQuery("") }, contentAlignment = Alignment.Center) {
                Text("✕", style = TextStyle(color = look.textDim, fontSize = 16.sp))
            }
        }
    }
}

/** Pages other screens can open settings on, by name ([SeelieSettings.open] with a page). */
private val START_PAGES = mapOf(
    SeelieSettings.PAGE_CLOCK to listOf(Page.Look, Page.HomeStyle),
    SeelieSettings.PAGE_WIDGETS to listOf(Page.Home, Page.Widgets),
    SeelieSettings.PAGE_WIDGET_PICKER to listOf(Page.Home, Page.Widgets, Page.WidgetPicker),
)

/** One line under each theme's name, from the prototype. */
private val TAGLINES = mapOf(
    "wallpaper" to "Colours from your own wallpaper, updates when it changes",
    "night" to "The house look",
    "fuji" to "Dusk, sakura, your wallpaper",
    "inazuma" to "Storm light, electro purple",
    "sumeru" to "Rainforest light, dendro green",
)

@Composable
fun SettingsApp(looks: LookStore, startAt: String? = null, onClose: () -> Unit) {
    val look = LocalLauncherLook.current
    // Where you are: a stack of pages, so Back steps out one page at a time.
    // [startAt] opens straight on a page (e.g. from the home's "Seelie widget" sheet); Back still steps out page by page.
    var stack by remember { mutableStateOf(listOf(Page.Top) + (START_PAGES[startAt] ?: emptyList())) }
    fun push(page: Page) { stack = stack + page }
    // Search on the top page: type a word, tap a result to jump straight to its page.
    var query by remember { mutableStateOf("") }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) else onClose() }
    BackHandler(enabled = stack.size > 1) { pop() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to look.sheetTop, 0.45f to look.sheet))
    ) {
        AnimatedContent(
            targetState = stack,
            transitionSpec = {
                // Deeper = slide in from the right a little; back = the other way. Always with a fade.
                val forward = targetState.size >= initialState.size
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(260)) { -it / 6 * dir } + fadeOut(tween(160)))
            },
            label = "settings page",
        ) { pages ->
            val current = looks.currentTheme
            val fontName = looks.fontId?.let { id -> SeelieFonts.firstOrNull { it.id == id }?.name } ?: "Theme font"
            when (pages.last()) {
                Page.Top -> PageFrame("Serenitea", "Seelie settings", onBack = ::pop) {
                    SettingsSearch(query) { query = it }
                    if (query.isNotBlank()) {
                        val hits = SETTINGS_INDEX.filter { it.matches(query) }
                        SectionLabel(if (hits.isEmpty()) "Nothing found" else "Results")
                        if (hits.isEmpty()) Hint("Try another word, like theme, font, clock, widget or icon.")
                        hits.forEach { hit ->
                            GroupCard(hit.icon, hit.tint, hit.title, hit.where) {
                                query = ""
                                stack = listOf(Page.Top) + hit.path
                            }
                        }
                        return@PageFrame
                    }
                    HeroLook(summary = "${current.name} · $fontName") { push(Page.Look) }
                    SectionLabel("Settings")
                    GroupCard(R.drawable.ic_home, Pyro, "Home screen", "Widget stack, glance & weather, music card") { push(Page.Home) }
                    GroupCard(R.drawable.ic_list, Anemo, "App list", "Rail wave, hidden apps, slide-to-open, sections", soon = true)
                    GroupCard(R.drawable.ic_search, Hydro, "Search & commands", "What Seelie searches, the > commands") { push(Page.Search) }
                    GroupCard(R.drawable.ic_touch_app, Electro, "Gestures", "Swipes and taps on apps and the home screen", soon = true)
                    GroupCard(R.drawable.ic_lock, Geo, "Privacy", "PIN vault for hidden apps", soon = true)
                    GroupCard(R.drawable.ic_settings, look.accent, "Seelie", "Default home app, version") { push(Page.Seelie) }
                }
                Page.Look -> PageFrame("Serenitea", "Look", onBack = ::pop, preview = true) {
                    // The bundles: clock, date, the line under it and the widget card as one matching set.
                    val homeStyle = HomeStyleStore.get(LocalContext.current)
                    GroupCard(R.drawable.ic_star, Geo, "Your themes", SavesStore.get(LocalContext.current).saves.size.let { if (it == 0) "Save your look as a theme, with its own wallpaper" else "$it saved · plus the built-in themes" }) { push(Page.Themes) }
                    GroupCard(R.drawable.ic_home_filled, Electro, "Home style", homeStyle.bundle.title + if (homeStyle.isCustom) " · custom" else "") { push(Page.HomeStyle) }
                    GroupCard(R.drawable.ic_drag_indicator, Geo, "Layout", "Move, line up and switch off parts · fade · Seelie touches") { push(Page.Layout) }
                    GroupCard(R.drawable.ic_palette, Pyro, "Themes", current.name) { push(Page.Themes) }
                    GroupCard(R.drawable.ic_text_fields, Hydro, "Fonts", fontName) { push(Page.Fonts) }
                    GroupCard(R.drawable.ic_apps, Cryo, "Icons", "Icon pack and icon size") { push(Page.Icons) }
                    GroupCard(R.drawable.ic_wallpaper, Dendro, "Living background", "Petals, stars, weather and more over your wallpaper") { push(Page.LiveBg) }
                }
                Page.Themes -> PageFrame("Look", "Themes", onBack = ::pop, preview = true) {
                    // Like Niagara's "your themes": his saved looks first (save the current one, apply, rename…), then the built-in ones.
                    SectionLabel(SavesStore.get(LocalContext.current).saves.size.let { if (it == 0) "Your themes" else "Your themes · $it" })
                    SavesSettingsContent(intro = false)
                    SectionLabel("Built-in themes")
                    Hint("A theme sets the colours and font of all of Seelie. Tap one and the preview changes.")
                    // First: "Match my wallpaper" (colours from his own wallpaper), then the built-in themes.
                    (listOfNotNull(looks.wallpaperLook) + SeelieThemes).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                            pair.forEach { theme ->
                                ThemeTile(theme, selected = theme.id == looks.themeId) { looks.setTheme(theme.id) }
                            }
                        }
                    }
                }
                Page.Fonts -> PageFrame("Look", "Fonts", onBack = ::pop, preview = true) {
                    TextColourSettings(looks)
                    SectionLabel("Font")
                    FontRow("Theme font", "Whatever the theme comes with", current.font, selected = looks.fontId == null) { looks.setFont(null) }
                    SeelieFonts.forEach { font ->
                        FontRow(font.name, null, font.family, selected = looks.fontId == font.id) { looks.setFont(font.id) }
                    }
                }
                Page.Icons -> PageFrame("Look", "Icons", onBack = ::pop) {
                    IconsPage(looks)
                }
                // The live preview stays pinned at the top while he scrolls through the controls (his ask, 23 Oct).
                Page.Saves -> PageFrame("Look", "My saves", onBack = ::pop) {
                    SavesSettingsContent()
                }
                Page.HomeStyle -> PageFrame("Look", "Home style", onBack = ::pop, pinned = { HomePreview() }) {
                    HomeStyleSettingsContent()
                }
                Page.Clock -> PageFrame("Look", "Clock & date", onBack = ::pop) {
                    ClockSettingsContent()
                }
                Page.Home -> PageFrame("Serenitea", "Home screen", onBack = ::pop) {
                    val widgets = WidgetStore.get(LocalContext.current).stack.size
                    GroupCard(R.drawable.ic_widgets, Pyro, "Widget stack", if (widgets == 1) "1 widget" else "$widgets widgets") { push(Page.Widgets) }
                    GroupCard(R.drawable.ic_battery_full, Hydro, "Stats card", "Which stats, the weather part, ring style") { push(Page.Stats) }
                    GroupCard(R.drawable.ic_sticky_note_2, Pyro, "Notifications", "Messages on your favourites, quick reply per app") { push(Page.Notifications) }
                    GroupCard(R.drawable.ic_flashlight_on, Anemo, "Quick toggles", "Wi-Fi, Bluetooth, Torch, Quiet between your favourites") { push(Page.Toggles) }
                    GroupCard(R.drawable.ic_sunny, Geo, "Glance & weather", "Weather city, what the line shows", soon = true)
                    GroupCard(R.drawable.ic_music_note, Electro, "Music card", "A player that keeps its spot") { push(Page.Music) }
                }
                Page.Widgets -> PageFrame("Home screen", "Widget stack", onBack = ::pop) {
                    WidgetStackSettingsContent(onAddFromApp = { push(Page.WidgetPicker) })
                }
                Page.WidgetPicker -> PageFrame("Widget stack", "Pick a widget", onBack = ::pop) {
                    // After adding, back to the stack page, where the new widget sits at the end.
                    AppWidgetPickerContent(onAdded = ::pop)
                }
                // The real card stays pinned at the top while he changes things below.
                Page.Stats -> PageFrame("Home screen", "Stats card", onBack = ::pop, pinned = { StatsPreview() }) {
                    StatsSettingsContent()
                }
                // The real card stays pinned at the top while he changes things below.
                Page.Layout -> PageFrame("Look", "Layout", onBack = ::pop, pinned = { HomePreview() }) {
                    LayoutSettingsContent(onClockFaces = { push(Page.Clock) })
                }
                Page.LiveBg -> PageFrame("Look", "Living background", onBack = ::pop, pinned = { LiveBgPreview() }) {
                    LiveBgSettingsContent(showPreview = false)
                }
                Page.Music -> PageFrame("Home screen", "Music card", onBack = ::pop, pinned = { MusicPreview() }) {
                    MusicSettingsContent(showPreview = false)
                }
                Page.Notifications -> PageFrame("Home screen", "Notifications", onBack = ::pop) {
                    NotifSettingsContent()
                }
                Page.Toggles -> PageFrame("Home screen", "Quick toggles", onBack = ::pop) {
                    TogglesSettingsContent()
                }
                Page.Search -> PageFrame("Serenitea", "Search & commands", onBack = ::pop) {
                    SearchSettingsContent()
                }
                Page.Seelie -> PageFrame("Serenitea", "Seelie", onBack = ::pop) {
                    val context = LocalContext.current
                    GroupCard(R.drawable.ic_home, look.accent, "Default home app", "Pick Seelie or another launcher") {
                        openSafely(context, DefaultHome.settingsIntent())
                    }
                    val version = remember {
                        @Suppress("DEPRECATION")   // the newer form needs Android 13; this one works everywhere
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
                    }
                    Hint("Seelie $version · made by Shamil, with Claude")
                }
            }
        }
    }
}

/** A page: back button + small gold eyebrow + big Cinzel title, an optional live preview that stays put, then the scrolling content. */
@Composable
internal fun PageFrame(
    eyebrow: String, title: String, onBack: () -> Unit, preview: Boolean = false,
    /** Something that stays at the top while the page scrolls (e.g. Home style's live preview). */
    pinned: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalLauncherLook.current
    val scroll = rememberScrollState()
    // 0 = at the top, 1 = scrolled ~140dp down: the big title shrinks and the small eyebrow fades away.
    val collapseDistance = with(LocalDensity.current) { 140.dp.toPx() }
    val collapse = { (scroll.value / collapseDistance).coerceIn(0f, 1f) }
    val back = remember { MutableInteractionSource() }
    // Stagger: every card on this page takes the next number, so they float in one after another.
    CompositionLocalProvider(LocalStagger provides remember { Stagger() }) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .pressFeel(back, look.accent, 23.dp)
                        .clip(CircleShape)
                        .border(1.dp, look.accent.copy(alpha = 0.35f), CircleShape)
                        .clickable(interactionSource = back, indication = null, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = look.text, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.graphicsLayer {
                    // Shrinks toward its left edge as you scroll, like a title settling into a top bar.
                    val c = collapse()
                    scaleX = 1f - 0.22f * c
                    scaleY = 1f - 0.22f * c
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }) {
                    Text(eyebrow.uppercase(), modifier = Modifier.graphicsLayer { alpha = 1f - collapse() },
                        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent.copy(alpha = 0.85f), fontSize = 11.sp, letterSpacing = 3.sp))
                    GlowTitle(title)
                }
            }
            // A hairline under the header that appears once the page is scrolled.
            Box(Modifier.fillMaxWidth().height(1.dp).graphicsLayer { alpha = collapse() }
                .background(Brush.horizontalGradient(listOf(Color.Transparent, look.accent.copy(alpha = 0.35f), Color.Transparent))))
            if (preview) {
                LivePreview(Modifier.padding(horizontal = 18.dp).staggerIn())
                Spacer(Modifier.height(6.dp))
            }
            if (pinned != null) {
                Box(Modifier.padding(horizontal = 18.dp)) { pinned() }
                Spacer(Modifier.height(6.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
                content = content,
            )
        }
    }
}

/** The page title in Cinzel, with a soft glow in the accent colour that slowly breathes. */
@Composable
private fun GlowTitle(title: String) {
    val look = LocalLauncherLook.current
    val glow by rememberInfiniteTransition(label = "title glow")
        .animateFloat(0.15f, 0.55f, infiniteRepeatable(tween(2800), RepeatMode.Reverse), label = "glow")
    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 27.sp, letterSpacing = 1.sp,
            shadow = Shadow(look.accent.copy(alpha = glow), blurRadius = 22f)))
}

/**
 * Look → Icons: a strip of his favourites drawn with the chosen pack (so he sees the effect at once),
 * then "Original icons" and every icon pack installed on the phone, then the icon size.
 */
@Composable
private fun IconsPage(looks: LookStore) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val active = remember(looks.iconPack) { looks.activeIconPack(context) }
    // Every installed pack: its package name, its name and its own app icon.
    val packs = remember {
        val pm = context.packageManager
        IconPack.installed(context).mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                Triple(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap())
            }.getOrNull()
        }
    }
    // His first 6 favourites, drawn with the active pack or style (off the main thread: reading a pack takes a moment).
    // Seelie's styles use the theme's colours, so they redraw when the theme changes.
    val favourites = remember { Favourites(context) }
    val px = (48 * density).toInt()
    fun favouriteInfos() = context.getSystemService(LauncherApps::class.java).let { launcherApps ->
        favourites.packages.take(6).mapNotNull { pkg -> launcherApps.getActivityList(pkg, Process.myUserHandle()).firstOrNull() }
    }
    val sample by produceState(emptyList<ImageBitmap>(), active, looks.themeId) {
        value = withContext(Dispatchers.Default) {
            val style = active?.takeIf { SeelieIcons.isStyle(it) }
            val pack = if (style == null) active?.let { IconPack.load(context, it) } else null
            favouriteInfos().map { info ->
                (if (style != null) SeelieIcons.draw(info, style, px, looks.look) else Apps.drawIcon(context, info, pack, px)).asImageBitmap()
            }
        }
    }
    // One example icon per Seelie style (his first favourite), for the style rows.
    val styleSamples by produceState(emptyMap<String, ImageBitmap>(), looks.themeId) {
        value = withContext(Dispatchers.Default) {
            val first = favouriteInfos().firstOrNull() ?: return@withContext emptyMap()
            listOf(SeelieIcons.THEMED, SeelieIcons.GLASS).associateWith { SeelieIcons.draw(first, it, px, looks.look).asImageBitmap() }
        }
    }

    // The preview strip: the icons glide to the new pack.
    val stripShape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .height(96.dp)
            .clip(stripShape)
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
            .sparkles(look.accent.copy(alpha = 1f), count = 10)
            .border(1.dp, look.accent.copy(alpha = 0.3f), stripShape),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(sample, animationSpec = tween(350), label = "icons") { icons ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                icons.forEach { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(44.dp * look.iconScale)) }
            }
        }
    }

    SectionLabel("Seelie styles")
    PackRow("Themed", "One-colour icons in your accent colour", styleSamples[SeelieIcons.THEMED], selected = active == SeelieIcons.THEMED) {
        looks.useIconPack(SeelieIcons.THEMED)
    }
    PackRow("Seelie Glass", "Frosted night plate with a gold ring", styleSamples[SeelieIcons.GLASS], selected = active == SeelieIcons.GLASS) {
        looks.useIconPack(SeelieIcons.GLASS)
    }
    Hint("Both follow your theme's colours. Apps without a one-colour icon get a tinted version of their normal one.")

    SectionLabel("Icon pack")
    PackRow("Original icons", "Each app's own icon", null, selected = active == null) { looks.useIconPack(null) }
    packs.forEach { (pkg, name, icon) ->
        PackRow(name, "Installed on this phone", icon, selected = active == pkg) { looks.useIconPack(pkg) }
    }
    if (packs.isEmpty()) Hint("No icon packs installed. Install one (like Darko) from the Play Store and it shows up here.")

    SectionLabel("Icon size")
    Column(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp))
            Text("${(looks.iconScale * 100).roundToInt()}%", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 17.sp))
        }
        Slider(
            value = looks.iconScale,
            onValueChange = { looks.useIconScale((it * 20).roundToInt() / 20f) },
            valueRange = 0.8f..1.2f,
            steps = 7,
            colors = SliderDefaults.colors(
                thumbColor = look.accent,
                activeTrackColor = look.accent,
                inactiveTrackColor = look.text.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text("100% is the same size as Niagara.", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
    }
}

/** An icon pack to pick: its own icon (or a grid icon for "Original"), its name, and a round radio button. */
@Composable
internal fun PackRow(name: String, sub: String, icon: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    val press = remember { MutableInteractionSource() }
    val dot by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "radio")
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .pressFeel(press, look.accent, 20.dp)
            .clip(shape)
            .background(if (selected) Brush.horizontalGradient(listOf(look.accent.copy(alpha = 0.18f), look.card)) else Brush.horizontalGradient(listOf(look.card, look.card)))
            .border(1.dp, if (selected) look.accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(48.dp))
        else IconTile(R.drawable.ic_apps, Cryo)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 18.sp))
            Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
        }
        Box(
            Modifier.size(24.dp).border(2.dp, if (selected) look.accent else look.textDim.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (dot > 0.01f) Box(Modifier.size(12.dp).graphicsLayer { scaleX = dot; scaleY = dot }.background(look.accent, CircleShape))
        }
    }
}

/** A small, live copy of the home screen in the current theme and font: clock, date, glance, three favourites. */
@Composable
internal fun LivePreview(modifier: Modifier = Modifier, height: Int = 200) {
    val look = LocalLauncherLook.current
    val now = remember { LocalDateTime.now() }
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
            .drawBehind {
                // A soft glow in the accent colour, like light on the wallpaper.
                drawCircle(
                    Brush.radialGradient(listOf(look.accent.copy(alpha = 0.32f), Color.Transparent), center = Offset(size.width * 0.25f, 0f), radius = size.width * 0.7f),
                    radius = size.width * 0.7f, center = Offset(size.width * 0.25f, 0f),
                )
            }
            .sparkles(look.accent.copy(alpha = 1f))
            .border(1.dp, look.accent.copy(alpha = 0.3f), shape)
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Column {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TextStyle(fontFamily = look.font, fontWeight = look.clockWeight, color = look.text, fontSize = 42.sp, shadow = look.shadow))
            Text(now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).replace(".", ""),
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp, shadow = look.shadow))
            Spacer(Modifier.height(10.dp))
            listOf("YouTube", "WhatsApp", "Spotify").forEachIndexed { i, name ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).background(look.accent.copy(alpha = 1f - i * 0.25f), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(name, style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 15.sp, shadow = look.shadow))
                }
            }
        }
        Text(
            "LIVE PREVIEW",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color(0x99000000), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp, letterSpacing = 1.5.sp),
        )
    }
}

/** The big "Look" card on the top page: the preview with what's picked underneath. */
@Composable
private fun HeroLook(summary: String, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(26.dp)
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .pressFeel(press, look.accent, 26.dp)
            .clip(shape)
            .border(1.5.dp, look.accent.copy(alpha = 0.6f), shape)
            .background(look.card)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
    ) {
        LivePreview(height = 170)
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(R.drawable.ic_palette, look.accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Look", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 19.sp))
                Text("Themes and fonts", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp))
                Text(summary, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 13.sp))
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = look.textDim)
        }
    }
}

/** A setting group: coloured icon tile, title, one line of what's inside, and a › (or a "Soon" tag). */
@Composable
internal fun GroupCard(@DrawableRes icon: Int, tint: Color, title: String, sub: String, soon: Boolean = false, onClick: () -> Unit = {}) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(22.dp)
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .pressFeel(press, tint, 22.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = if (soon) 0.05f else 0.12f), look.card)))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .clickable(enabled = !soon, interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, if (soon) tint.copy(alpha = 0.55f) else tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text.copy(alpha = if (soon) 0.6f else 1f), fontSize = 18.sp))
            Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim.copy(alpha = if (soon) 0.6f else 1f), fontSize = 13.sp))
        }
        Spacer(Modifier.width(8.dp))
        if (soon) {
            Text("SOON", modifier = Modifier.border(1.dp, look.textDim.copy(alpha = 0.4f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.textDim, fontSize = 9.sp, letterSpacing = 1.5.sp))
        } else {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = look.textDim)
        }
    }
}

/** A rounded square with a softly glowing icon in an element colour. */
@Composable
internal fun IconTile(@DrawableRes icon: Int, tint: Color) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .size(50.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), tint.copy(alpha = 0.12f))))
            .border(1.dp, tint.copy(alpha = 0.55f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** A theme to pick: a mini home in that theme's own colours and font, with a ✓ when it's the active one. */
@Composable
private fun RowScope.ThemeTile(theme: LauncherLook, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier
            .weight(1f)
            .staggerIn()
            .height(196.dp)
            .pressFeel(press, theme.accent, 22.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(theme.sheetTop.copy(alpha = 1f), theme.sheet.copy(alpha = 1f))))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(theme.accent.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.3f, 0f), radius = size.width),
                    radius = size.width, center = Offset(size.width * 0.3f, 0f),
                )
            }
            .border(if (selected) 2.dp else 1.dp, if (selected) theme.accent else Color.White.copy(alpha = 0.08f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text("19:17", modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = theme.font, fontWeight = theme.clockWeight, color = theme.text, fontSize = 28.sp))
            // The ✓ pops in with a little spring when this theme gets picked.
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn(),
                exit = scaleOut(tween(150)) + fadeOut(tween(150)),
            ) {
                Box(Modifier.size(26.dp).background(theme.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", style = TextStyle(color = theme.sheet.copy(alpha = 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        repeat(3) { i ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(theme.accent.copy(alpha = 1f - i * 0.28f), CircleShape))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width((70 - i * 14).dp).height(5.dp).background(theme.text.copy(alpha = 0.35f), CircleShape))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(theme.name, style = TextStyle(fontFamily = theme.font, fontWeight = FontWeight.Bold, color = theme.text, fontSize = 15.sp))
        Text(TAGLINES[theme.id] ?: "", maxLines = 2, style = TextStyle(fontFamily = theme.font, color = theme.textDim, fontSize = 11.sp, lineHeight = 14.sp))
    }
}

/** A font to pick: an "Aa" tile and the name, both written in that font, and a round radio button. */
@Composable
internal fun FontRow(name: String, sub: String?, family: FontFamily, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    val press = remember { MutableInteractionSource() }
    // The radio's dot springs in when picked and shrinks away when not.
    val dot by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "radio")
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .pressFeel(press, look.accent, 20.dp)
            .clip(shape)
            .background(if (selected) Brush.horizontalGradient(listOf(look.accent.copy(alpha = 0.18f), look.card)) else Brush.horizontalGradient(listOf(look.card, look.card)))
            .border(1.dp, if (selected) look.accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).background(look.accent.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", style = TextStyle(fontFamily = family, color = look.text, fontSize = 20.sp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = TextStyle(fontFamily = family, color = look.text, fontSize = 18.sp))
            if (sub != null) Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
        }
        Box(
            Modifier.size(24.dp).border(2.dp, if (selected) look.accent else look.textDim.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (dot > 0.01f) Box(Modifier.size(12.dp).graphicsLayer { scaleX = dot; scaleY = dot }.background(look.accent, CircleShape))
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    val look = LocalLauncherLook.current
    Row(Modifier.staggerIn().padding(start = 4.dp, top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 11.sp, letterSpacing = 2.4.sp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(look.accent.copy(alpha = 0.3f), Color.Transparent))))
    }
}

@Composable
internal fun Hint(text: String) {
    val look = LocalLauncherLook.current
    Text(text, modifier = Modifier.staggerIn().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 14.dp),
        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp, lineHeight = 20.sp))
}
