package com.shamil.launcher.ui

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
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
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.apps.rememberAppTweaks
import com.shamil.launcher.apps.rememberApps
import com.shamil.launcher.apps.rememberFavourites
import com.shamil.launcher.ui.widgets.WidgetStack
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

    // His renames applied, then re-sorted so a renamed app lands under its new letter.
    val apps = remember(installed, tweaks.names) {
        val collator = Collator.getInstance()
        installed
            .map { app -> tweaks.names[app.component.packageName]?.let { app.copy(label = it) } ?: app }
            .sortedWith(compareBy(collator) { it.label })
    }
    // Hidden apps leave the A–Z list and search (they still show in favourites, and under "Hidden apps").
    val (hiddenApps, listedApps) = remember(apps, tweaks.hidden) {
        apps.partition { tweaks.isHidden(it.component.packageName) }
    }
    // His favourites, in his order, matched to the installed apps (uninstalled ones are skipped).
    val favouriteApps = remember(apps, favourites.packages) {
        favourites.packages.mapNotNull { pkg -> apps.firstOrNull { it.component.packageName == pkg } }
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
    LaunchedEffect(listLetter) { glance.open = false }

    // The app whose menu is open (long-press), or null.
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight

        KeepAliveFade(visible = listLetter == null && !state.searching && !state.editingFavourites) {
            Column(
                Modifier
                    .fillMaxSize()
                    // A tap on empty space (not an app, not the card) closes the stats card.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = glance.open) {
                        glance.open = false
                    }
            ) {
                Spacer(Modifier.height(screenH * 0.105f))
                ClockAndDate(Modifier.padding(start = 48.dp))
                Glance(glance, Modifier.padding(start = 48.dp))
                if (!isDefault) MakeDefaultButton(Modifier.padding(start = 38.dp))
                Spacer(Modifier.height(12.dp))
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
                        .padding(end = 56.dp, bottom = 32.dp)
                ) {
                    // The stats card sits at the top of the favourites and scrolls with them,
                    // so apps can't slide up behind it (his 23 Oct bug).
                    GlanceCard(glance, Modifier.padding(start = 48.dp))
                    // The widget stack: swipe sideways for the next widget. It sits at the top of the favourites
                    // and scrolls away with them, so it never takes space for good. (Pick widgets in Seelie settings.)
                    WidgetStack(Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp))
                    favouriteApps.forEach { app -> AppItem(app) { openMenu(app) } }
                }
            }
        }

        KeepAliveFade(visible = listLetter != null && !state.searching) {
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
                onToggle = { favourites.toggle(it.component.packageName) },
                onReorder = { newOrder -> favourites.setOrder(newOrder.map { it.component.packageName }) },
                onDone = { state.editingFavourites = false },
            )
        }

        if (!state.searching && !state.editingFavourites) {
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
            ) { picked -> onListLetter(if (picked == STAR) null else picked) }
        }
    }

    menuApp?.let { app ->
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
private fun ClockAndDate(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNow()
    // The clock and date in the styles he picked (Seelie settings → Look → Clock & date). Tap the clock = clock app,
    // tap the date = calendar, like Niagara.
    SeelieClock(
        time = now,
        is24h = DateFormat.is24HourFormat(context),
        onClockClick = { openSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
        onClockLongClick = { openSafely(context, DefaultHome.settingsIntent()) },
        onDateClick = {
            val today = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            ContentUris.appendId(today, System.currentTimeMillis())
            openSafely(context, Intent(Intent.ACTION_VIEW, today.build()))
        },
        modifier = modifier,
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
