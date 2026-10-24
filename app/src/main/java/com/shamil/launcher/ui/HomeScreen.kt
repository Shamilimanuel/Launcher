package com.shamil.launcher.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import com.shamil.launcher.ui.home.GroupHeading
import com.shamil.launcher.apps.AppCategories
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import com.shamil.launcher.ui.home.WallpaperFade
import com.shamil.launcher.ui.home.currentHomeLayout
import com.shamil.launcher.ui.home.BLOCK_FAVS
import com.shamil.launcher.ui.home.BLOCK_MUSIC
import com.shamil.launcher.ui.home.BLOCK_WIDGETS
import com.shamil.launcher.ui.home.BLOCK_TOP
import com.shamil.launcher.ui.livebg.rememberLiveBgController
import com.shamil.launcher.ui.livebg.LivingBackground
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.content.IntentFilter
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shamil.launcher.DefaultHome
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.LaunchStats
import com.shamil.launcher.apps.VaultStore
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.apps.rememberAppTweaks
import com.shamil.launcher.apps.rememberApps
import com.shamil.launcher.apps.PinnedShortcuts
import com.shamil.launcher.apps.SHORTCUT_KEY
import com.shamil.launcher.apps.favouriteKey
import com.shamil.launcher.apps.rememberPinnedShortcuts
import com.shamil.launcher.apps.shortcutId
import androidx.compose.material3.AlertDialog
import com.shamil.launcher.apps.rememberFavourites
import com.shamil.launcher.ui.widgets.WidgetStack
import com.shamil.launcher.ui.toggles.QuickTogglesRow
import com.shamil.launcher.ui.media.MusicCard
import com.shamil.launcher.ui.toggles.TogglePrefs
import com.shamil.launcher.ui.home.HomeTop
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.clock.SeelieClock
import com.shamil.launcher.ui.theme.LocalLauncherLook
import java.text.Collator
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the home screen is showing. MainActivity owns one, so pressing Home can [reset] it.
 * - [listLetter]: null = favourites; a letter = the A–Z list, opened at that letter.
 * - [searching] / [query]: the search box and what's typed in it.
 */
class HomeState {
    var listLetter by mutableStateOf<String?>(null)
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    /** "Edit favourites" mode: drag handles to reorder. */
    var editingFavourites by mutableStateOf(false)

    fun closeSearch() {
        searching = false
        query = ""
    }

    /** Back from an app he opened: the A–Z stays where it was, only the search box closes. */
    fun returnFromApp() {
        closeSearch()
    }

    fun reset() {
        listLetter = null
        editingFavourites = false
        closeSearch()
    }
}

/**
 * The home screen, Niagara-style: clock, date and favourites on the wallpaper, the letter rail on the right.
 * Touching a letter swaps the favourites for the full A–Z list; ☆, Back or Home swaps back.
 * While the list shows, a round search button sits bottom-right.
 */
@Composable
fun HomeScreen(state: HomeState, isDefault: Boolean) {
    val listLetter = state.listLetter
    val onListLetter = { letter: String? -> state.listLetter = letter }
    val installed = rememberApps()
    val favourites = rememberFavourites()
    val tweaks = rememberAppTweaks()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // His renames applied, then re-sorted so a renamed app lands under its new letter.
    val apps = remember(installed, tweaks.names) {
        val collator = Collator.getInstance()
        installed
            .map { app -> tweaks.names[app.component.packageName]?.let { app.copy(label = it) } ?: app }
            .sortedWith(compareBy(collator) { it.label })
    }
    // Apps in the PIN vault vanish everywhere (list, search, favourites, Hidden apps) until he unlocks it.
    val vault = remember { VaultStore.get(context) }
    val outsideVault = remember(apps, vault.apps) { apps.filter { it.component.packageName !in vault } }
    // Hidden apps leave the A–Z list and search (they still show in favourites, and under "Hidden apps").
    val (hiddenApps, listedApps) = remember(outsideVault, tweaks.hidden) {
        outsideVault.partition { tweaks.isHidden(it.component.packageName) }
    }
    // His favourites, in his order, matched to the installed apps (uninstalled ones are skipped).
    // Pinned shortcuts (websites from "Add to home screen") can be favourites too, stored as "shortcut:<package>/<id>".
    val pinned = rememberPinnedShortcuts()
    val favouriteApps = remember(outsideVault, favourites.packages, pinned) {
        favourites.packages.mapNotNull { key ->
            if (key.startsWith(SHORTCUT_KEY)) pinned.firstOrNull { it.favouriteKey == key }
            else outsideVault.firstOrNull { it.component.packageName == key }
        }
    }
    val lettersWithApps = remember(listedApps) { listedApps.map { it.letter }.toSet() }
    // "Suggested" in Edit favourites: the apps he opened most in the last 2 weeks that aren't favourites yet;
    // until Seelie has counted some opens, his newest apps.
    val suggestions = remember(listedApps, favourites.packages, state.editingFavourites) {
        val notPicked = listedApps.filter { it.component.packageName !in favourites }
        val often = LaunchStats.get(context).mostOpened().mapNotNull { (pkg, _) -> notPicked.firstOrNull { it.component.packageName == pkg } }
        (often.ifEmpty { notPicked.sortedByDescending { it.installedAt } }).take(4)
    }

    // Coming back to the favourites (☆, Back or Home) always starts at the top, like Niagara.
    val favouritesScroll = rememberScrollState()
    LaunchedEffect(listLetter) {
        if (listLetter == null) favouritesScroll.scrollTo(0)
    }

    // The weather/stats line and its card. The card closes when you tap away, press Back or open the list.
    val glance = rememberGlanceState()
    // The layout editor's choices (Look → Layout).
    val layout = currentHomeLayout()
    // Grouped layout (Smart Launcher-ish): each favourite's heading, worked out off the main thread.
    val groupOf by produceState(emptyMap<String, String>(), favouriteApps, layout.group) {
        value = if (!layout.group) emptyMap()
        else withContext(Dispatchers.IO) { favouriteApps.associate { it.favouriteKey to AppCategories.of(context, it.component.packageName) } }
    }
    // The living background (Look → Living background): drawn behind everything, fed with the real weather.
    val liveBg = rememberLiveBgController()
    // His home style: how the clock, date, glance line and widget card look together (one bundle + tweaks).
    val homeStyle = HomeStyleStore.get(context).style
    LaunchedEffect(listLetter) { glance.open = false }
    // The swipe-right pop-up: one at a time, closed by a tap outside it, by Back, or by switching list/favourites.
    val popups = remember { PopupHolder() }
    LaunchedEffect(listLetter, state.searching) { popups.close() }
    BackHandler(enabled = popups.openKey != null) { popups.close() }

    // The app whose menu is open (long-press), or null.
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    // The PIN vault (hold • on the rail).
    var vaultOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = vaultOpen) { vault.lock(); vaultOpen = false }
    // The "swipe left" chooser for one app (or null).
    var gestureApp by remember { mutableStateOf<AppEntry?>(null) }
    // The "Seelie widget" sheet (long-press beside the clock).
    var widgetSheet by remember { mutableStateOf(false) }
    val openMenu = { app: AppEntry -> menuApp = app }

    // Slide-to-open: where the thumb is while it slides off the rail onto the list, and the app under it.
    var slideY by remember { mutableStateOf<Float?>(null) }
    var slideTarget by remember { mutableStateOf<AppEntry?>(null) }

    // Back steps out one level: search → list → editing favourites → favourites.
    // (The handler registered LAST wins, so they're listed from the outermost level to the innermost.)
    BackHandler(enabled = glance.open) { glance.open = false }
    BackHandler(enabled = state.editingFavourites) { state.editingFavourites = false }
    BackHandler(enabled = listLetter != null) { onListLetter(null) }
    BackHandler(enabled = state.searching) { state.closeSearch() }

    // BoxWithConstraints tells us the screen size, so positions can be a share of the height like in the notes.
    // Rows anywhere below can open the chooser (a left swipe on an app with no action yet).
    CompositionLocalProvider(LocalGestureChooser provides { app: AppEntry -> gestureApp = app }, LocalPopupHolder provides popups) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // A tap (not a drag) outside the open pop-up closes it. Watching only, so the tap still reaches what it hit.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                    val moved = (up.position - down.position).getDistance() > viewConfiguration.touchSlop
                    // A tap anywhere: a little sparkle there, if "Touch sparkle" is on.
                    if (!moved) liveBg.onTap(up.position.x, up.position.y)
                    val inside = popups.bounds?.contains(down.position) == true
                    if (popups.openKey != null && !moved && !inside) popups.close()
                }
            }
    ) {
        val screenH = maxHeight

        // Background fade (Look → Layout): the wallpaper only at the top, melting into a colour below.
        if (layout.fade) WallpaperFade(layout)

        // First child = drawn first = behind the clock, apps and rail. Paused under the vault and Edit favourites.
        LivingBackground(
            Modifier.fillMaxSize(),
            weather = glance.weather,
            active = !vaultOpen && !state.editingFavourites,
            controller = liveBg,
        )

        KeepAliveFade(visible = listLetter == null && !state.searching && !state.editingFavourites) {
            Column(
                Modifier
                    .fillMaxSize()
                    // A tap on empty space (not an app, not the card) closes the stats card.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = glance.open) {
                        glance.open = false
                    }
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Once scrolled, apps fade out softly at the top edge instead of being cut off.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (favouritesScroll.value > 0) {
                                drawRect(
                                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = 28.dp.toPx()),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                        .verticalScroll(favouritesScroll)
                        // Room at the bottom for the navigation buttons, so the last favourite never hides behind them.
                        .padding(bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) {
                    // The clock, date and glance line scroll away with the favourites too, like Niagara (his 23 Oct ask).
                    // How far down the clock starts is his (Look → Layout; his mockup pick: lower, in thumb reach).
                    Spacer(Modifier.height(screenH * layout.clockTop))
                    // The blocks in his order (Look → Layout): the top (clock, date, glance), widgets, music, favourites.
                    layout.order.forEach { block ->
                        when (block) {
                            BLOCK_TOP -> {
                                // The clock area, the full width of the screen: holding the empty space right of the clock
                                // opens the "Seelie widget" sheet, like Niagara's.
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .pointerInput(Unit) {
                                            detectTapGestures(onLongPress = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                widgetSheet = true
                                            })
                                        }
                                ) {
                                    // Clock, date and glance line in his home style (Look → Home style) and layout.
                                    ClockAndDate(glance, onLongPress = { widgetSheet = true })
                                }
                                if (!isDefault) MakeDefaultButton(Modifier.padding(start = 38.dp))
                                Spacer(Modifier.height(12.dp))
                                // The stats card sits under the top and scrolls with it, so apps can't slide up behind it.
                                GlanceCard(glance, Modifier.padding(start = 48.dp, end = 56.dp))
                            }
                            // The widget stack: swipe sideways for the next widget; it scrolls away with the rest.
                            // Widgets and the music card in his "Widgets" text colour (Look → Fonts → Colour these).
                            BLOCK_WIDGETS -> if (layout.widgets) {
                                CompositionLocalProvider(LocalLauncherLook provides LocalLauncherLook.current.forPart("widgets")) {
                                    WidgetStack(Modifier.padding(start = homeStyle.edge.dp, end = 56.dp, top = (homeStyle.gap3 - 12).coerceAtLeast(0).dp, bottom = 8.dp))
                                }
                            }
                            // The music card (Home screen → Music card; it hides itself when it's off).
                            BLOCK_MUSIC -> CompositionLocalProvider(LocalLauncherLook provides LocalLauncherLook.current.forPart("widgets")) {
                                MusicCard(Modifier.padding(start = homeStyle.edge.dp, end = 56.dp, bottom = 8.dp))
                            }
                            BLOCK_FAVS -> if (layout.favs) Column(Modifier.padding(end = 56.dp)) {
                                // The quick toggles row sits between his favourites, after as many as he chose (Home screen → Quick toggles).
                                val toggleSlot = remember { TogglePrefs.get(context) }.rowSlot(favouriteApps.size)
                                // His favourites in his "App names" text colour, and his icon style (Layout: normal, small, text only).
                                val appsLook = LocalLauncherLook.current.forPart("apps").let {
                                    if (layout.icons == "small") it.copy(iconScale = it.iconScale * 0.7f) else it
                                }
                                CompositionLocalProvider(LocalLauncherLook provides appsLook, LocalAppIcons provides (layout.icons != "text")) {
                                    if (layout.group && groupOf.isNotEmpty()) {
                                        // Grouped (Smart Launcher-ish): under small headings, groups in the order his favourites
                                        // first reach them, his own order kept inside each group. Toggles go first or last.
                                        if (toggleSlot == 0) QuickTogglesRow()
                                        favouriteApps.groupBy { groupOf[it.favouriteKey] ?: AppCategories.OTHER }.forEach { (heading, apps) ->
                                            GroupHeading(heading)
                                            apps.forEach { app -> AppItem(app, showNotifications = app.shortcutId == null) { openMenu(app) } }
                                        }
                                        if (toggleSlot != 0) QuickTogglesRow()
                                    } else {
                                        favouriteApps.forEachIndexed { i, app ->
                                            if (i == toggleSlot) QuickTogglesRow()
                                            // A website shortcut doesn't show its browser's notifications.
                                            AppItem(app, showNotifications = app.shortcutId == null) { openMenu(app) }
                                        }
                                        if (toggleSlot == favouriteApps.size) QuickTogglesRow()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        KeepAliveFade(visible = listLetter != null && !state.searching) {
          CompositionLocalProvider(LocalLauncherLook provides LocalLauncherLook.current.forPart("apps")) {
            AppList(
                apps = listedApps,
                hiddenApps = hiddenApps,
                letter = listLetter,
                topPadding = screenH * 0.30f,
                // Just room for the search button: a short section at the end (Z, •) then sits with the
                // list's end at the bottom, like Niagara, instead of floating high with empty space under it.
                bottomPadding = screenH * 0.12f + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                onAppMenu = openMenu,
                slideY = slideY,
                onSlideTarget = { slideTarget = it },
            )
          }
        }

        AnimatedVisibility(
            visible = listLetter != null && !state.searching,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 58.dp, bottom = 36.dp),
        ) {
            SearchButton(onClick = { state.searching = true })
        }

        AnimatedVisibility(visible = state.searching, enter = fadeIn(), exit = fadeOut()) {
            SearchPanel(
                apps = listedApps,
                query = state.query,
                onQuery = { state.query = it },
                onAppMenu = openMenu,
                // Opening an app, setting a timer or saving a note closes search.
                onClose = { state.closeSearch() },
            )
        }

        // Edit favourites: its own full screen (with its own rail), fading in over the home.
        AnimatedVisibility(
            visible = state.editingFavourites,
            enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.97f),
            exit = fadeOut(tween(180)) + scaleOut(tween(200), targetScale = 0.97f),
        ) {
            EditFavouritesScreen(
                apps = listedApps,
                favouriteApps = favouriteApps,
                suggestions = suggestions,
                onToggle = {
                    // Taking a website shortcut out of the favourites also lets go of it (it was only there).
                    if (it.shortcutId != null && it.favouriteKey in favourites) PinnedShortcuts.unpin(context, it)
                    favourites.toggle(it.favouriteKey)
                },
                onReorder = { newOrder -> favourites.setOrder(newOrder.map { it.favouriteKey }) },
                onDone = { state.editingFavourites = false },
            )
        }

        // The vault, over everything (hold • on the rail).
        AnimatedVisibility(visible = vaultOpen, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
            VaultScreen(allApps = apps, onClose = { vaultOpen = false })
        }
        // A locked app asking for the code (from a tap, search, a gesture, a shortcut or a notification).
        AnimatedVisibility(visible = vault.pending != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            var label by remember { mutableStateOf("") }
            vault.pending?.first?.let { label = it }
            LockGate(label)
        }

        if (!state.searching && !state.editingFavourites && !vaultOpen) {
            LetterRail(
                present = lettersWithApps,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = screenH * 0.27f)
                    .height(screenH * 0.64f)
                    // The touch strip runs to the screen edge; letters sit 32dp in, like his Niagara.
                    .width(46.dp),
                onSlide = { slideY = it },
                onRelease = { slid ->
                    // Let go on a lit-up app after sliding off the letters: open it (Niagara can't do this).
                    if (slid) slideTarget?.let { Apps.launch(context, it) }
                    slideY = null
                    slideTarget = null
                },
                onHoldDot = { vaultOpen = true },
            ) { picked -> onListLetter(if (picked == STAR) null else picked) }
        }
    }

    }

    gestureApp?.let { app -> GestureSheet(app, listedApps) { gestureApp = null } }

    if (widgetSheet) {
        HomeWidgetSheet(
            onEditFavourites = {
                onListLetter(null)
                state.editingFavourites = true
            },
            onDismiss = { widgetSheet = false },
        )
    }

    // A website shortcut gets a small menu of its own (the app menu's rename/hide/uninstall would hit its browser).
    menuApp?.takeIf { it.shortcutId != null }?.let { app ->
        AlertDialog(
            onDismissRequest = { menuApp = null },
            containerColor = LocalLauncherLook.current.sheet,
            title = { Text(app.label, color = LocalLauncherLook.current.text) },
            text = { Text("A website shortcut in your favourites.", color = LocalLauncherLook.current.textDim) },
            confirmButton = {
                TextButton({
                    favourites.toggle(app.favouriteKey)
                    PinnedShortcuts.unpin(context, app)
                    menuApp = null
                }) { Text("Remove from favourites", color = Color(0xFFFF9A9A)) }
            },
            dismissButton = {
                TextButton({ menuApp = null; onListLetter(null); state.editingFavourites = true }) {
                    Text("Edit favourites", color = LocalLauncherLook.current.accent)
                }
            },
        )
    }
    menuApp?.takeIf { it.shortcutId == null }?.let { app ->
        val pkg = app.component.packageName
        AppMenuSheet(
            app = app,
            originalName = installed.firstOrNull { it.key == app.key }?.label ?: app.label,
            isFavourite = pkg in favourites,
            isHidden = tweaks.isHidden(pkg),
            onToggleFavourite = { favourites.toggle(pkg) },
            onEditFavourites = {
                onListLetter(null)
                state.editingFavourites = true
            },
            onToggleHidden = { tweaks.setHidden(pkg, !tweaks.isHidden(pkg)) },
            onRename = { tweaks.rename(pkg, it) },
            onDismiss = { menuApp = null },
            onSwipeLeft = { gestureApp = app },
            onMoveToVault = {
                vault.add(pkg)
                // First time: open the vault so he makes his PIN right away.
                if (!vault.hasPin) vaultOpen = true
                else Toast.makeText(context, "${app.label} is in the vault · hold • to open it", Toast.LENGTH_SHORT).show()
            },
            isLocked = vault.isLocked(pkg),
            onToggleLock = {
                val on = !vault.isLocked(pkg)
                vault.setLocked(pkg, on)
                // No code yet: make one first (the vault's setup screen).
                if (on && !vault.hasPin) vaultOpen = true
                else Toast.makeText(context, if (on) "${app.label} is locked" else "${app.label} is unlocked", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/**
 * Fades its content in and out, but keeps it built while hidden, so showing it again is instant
 * (no stutter on the first touch of the rail). While hidden it's moved off-screen so it can't catch touches.
 */
@Composable
private fun KeepAliveFade(visible: Boolean, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "fade")
    Box(
        Modifier
            .fillMaxSize()
            // Read in the layout/draw phases (lambdas), so the fade itself never rebuilds the content.
            .offset { if (!visible && alpha == 0f) IntOffset(100_000, 0) else IntOffset.Zero }
            .graphicsLayer { this.alpha = alpha }
    ) {
        content()
    }
}

/**
 * Clock + date. Tap the clock for your alarms, tap the date for your calendar,
 * and long-press the clock to switch Home app (the way back to Niagara).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockAndDate(glance: GlanceState, onLongPress: () -> Unit = {}) {
    val context = LocalContext.current
    val now = rememberNow()
    val noRipple = remember { MutableInteractionSource() }
    // Tap the clock = clock app, tap the date = calendar, like Niagara. Holding the clock opens the Seelie widget sheet
    // ("Switch Home app" is in there, the old escape hatch).
    HomeTop(
        time = now,
        glance = glance,
        clockModifier = Modifier.combinedClickable(
            interactionSource = noRipple,
            indication = null,
            onClick = { openSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
            onLongClick = onLongPress,
        ),
        dateModifier = Modifier.clickable(interactionSource = noRipple, indication = null) {
            val today = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            ContentUris.appendId(today, System.currentTimeMillis())
            openSafely(context, Intent(Intent.ACTION_VIEW, today.build()))
        },
    )
}

/**
 * The current time as state. Android's "time tick" broadcast nudges it every minute, but only
 * while home is on screen (saves battery); coming back to home refreshes it straight away.
 */
@Composable
private fun rememberNow(): LocalDateTime {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                now = LocalDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        var listening = false
        // "Lifecycle" = Android telling us when home becomes visible (START) or hidden (STOP).
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!listening) {
                    now = LocalDateTime.now()
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                    listening = true
                }
                Lifecycle.Event.ON_STOP -> if (listening) {
                    context.unregisterReceiver(receiver)
                    listening = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (listening) context.unregisterReceiver(receiver)
        }
    }
    return now
}

/** Only shows while this isn't the default Home app yet. */
@Composable
private fun MakeDefaultButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    // MainActivity re-checks "am I default?" every time it comes back to the front.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    TextButton(onClick = { ask.launch(DefaultHome.requestIntent(context)) }, modifier = modifier) {
        Text("Make me your default home", style = TextStyle(fontFamily = look.font, color = look.accent, fontSize = 16.sp, shadow = look.shadow))
    }
}

/** Opens another app's screen; quietly does nothing if no app on the phone can handle it. */
internal fun openSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // No app on the phone handles this (e.g. no calendar app installed).
    }
}
