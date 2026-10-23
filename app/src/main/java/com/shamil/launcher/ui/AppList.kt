package com.shamil.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.DefaultHome
import com.shamil.launcher.R
import com.shamil.launcher.settings.SeelieSettings
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.ui.theme.LocalLauncherLook

/** Sizes measured from Niagara's own layout on his phone (adb uiautomator, 23 Oct; see reference/setup-notes.md). */
val ROW_START = 46.dp

/**
 * Every app, A–Z, straight on the wallpaper. Opens at [letter] and keeps the top of the
 * screen empty (the list lives in the thumb zone, like Niagara). Long-press any app for its menu.
 */
@Composable
fun AppList(
    apps: List<AppEntry>,
    hiddenApps: List<AppEntry>,
    letter: String?,
    topPadding: Dp,
    bottomPadding: Dp,
    onAppMenu: (AppEntry) -> Unit,
    /** Where the thumb is (screen y) while sliding off the letter rail onto the list; null otherwise. */
    slideY: Float? = null,
    /** Tells home which app is under the sliding thumb (null = none), so letting go opens it. */
    onSlideTarget: (AppEntry?) -> Unit = {},
    /** While editing favourites: every row gets a + / ✓, and tapping a row adds or removes it. */
    addingFavourites: Boolean = false,
    isFavourite: (AppEntry) -> Boolean = { false },
    onToggleFavourite: (AppEntry) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // The apps grouped by letter ("A" -> [Agenda, AliExpress, ...]), plus where each letter's header sits in the list.
    val sections = remember(apps) { apps.groupBy { it.letter } }
    val headerIndex = remember(sections) {
        var index = 0
        // After the letters comes the "Seelie" section, so its header is the last stop.
        sections.mapValues { (_, group) -> index.also { index += 1 + group.size } } + (DOT to index)
    }
    // His 5 newest apps, for "Recently installed".
    val recentApps = remember(apps) { apps.sortedByDescending { it.installedAt }.take(5) }
    var showRecent by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Slide-to-open: work out which row is under the thumb while it slides in from the rail.
    var listTop by remember { mutableFloatStateOf(0f) }
    var slideKey by remember { mutableStateOf<String?>(null) }
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val currentSlideY by rememberUpdatedState(slideY)
    val currentOnSlideTarget by rememberUpdatedState(onSlideTarget)
    val topPaddingPx = with(LocalDensity.current) { topPadding.toPx() }
    LaunchedEffect(listState, byKey) {
        snapshotFlow {
            val y = currentSlideY ?: return@snapshotFlow null
            val local = y - listTop
            if (local < topPaddingPx - 20f) return@snapshotFlow null   // the faded top area doesn't count
            val info = listState.layoutInfo
            val at = local + info.viewportStartOffset
            info.visibleItemsInfo.firstOrNull { at >= it.offset && at < it.offset + it.size }?.key as? String
        }.collect { key ->
            val app = key?.let { byKey[it] }
            slideKey = app?.key
            if (app != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnSlideTarget(app)
        }
    }

    // Whenever the letter changes, jump there (or to the next letter that has apps).
    LaunchedEffect(letter, headerIndex) {
        if (letter == null) return@LaunchedEffect
        val target = headerIndex.keys.firstOrNull { it >= letter } ?: headerIndex.keys.lastOrNull() ?: return@LaunchedEffect
        listState.scrollToItem(headerIndex.getValue(target))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { listTop = it.positionInRoot().y }
            .fadeAbove(topPadding),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding, end = 56.dp),
    ) {
        sections.forEach { (sectionLetter, group) ->
            item(key = "header-$sectionLetter") { SectionHeader(sectionLetter) }
            items(group, key = { it.key }) { app ->
                if (addingFavourites) {
                    AddFavouriteRow(app, isFavourite(app)) { onToggleFavourite(app) }
                } else {
                    AppItem(app, highlighted = app.key == slideKey) { onAppMenu(app) }
                }
            }
        }
        // The launcher's own corner, reached with the • at the bottom of the rail.
        item(key = "header-launcher") { SectionHeader(stringResource(R.string.app_name)) }
        item(key = "switch-home") {
            ActionRow(R.drawable.ic_home_filled, "Switch Home app") { openSafely(context, DefaultHome.settingsIntent()) }
        }
        item(key = "recent") {
            ActionRow(R.drawable.ic_history_filled, "Recently installed") { showRecent = !showRecent }
        }
        if (showRecent) {
            items(recentApps, key = { "recent-${it.key}" }) { app -> AppRow(app) { onAppMenu(app) } }
        }
        // Hidden apps are never lost: they live here, and long-press brings them back.
        if (hiddenApps.isNotEmpty()) {
            item(key = "hidden") {
                ActionRow(R.drawable.ic_visibility_off_filled, "Hidden apps (${hiddenApps.size})") { showHidden = !showHidden }
            }
            if (showHidden) {
                items(hiddenApps, key = { "hidden-${it.key}" }) { app -> AppRow(app) { onAppMenu(app) } }
            }
        }
        // The very last row: Seelie's own settings app (Serenitea), like Niagara's settings at the end.
        item(key = "settings") {
            ActionRow(R.drawable.ic_settings_filled, "Seelie settings") { SeelieSettings.open(context) }
        }
    }
}

/** A row while adding favourites: tap anywhere on it (or the + / ✓) to add or remove it. */
@Composable
private fun AddFavouriteRow(app: AppEntry, isFavourite: Boolean, onToggle: () -> Unit) {
    val look = LocalLauncherLook.current
    AppRow(
        app = app,
        onClick = onToggle,
        onLongPress = onToggle,
        // On the left, away from the letter rail on the right edge.
        leading = {
            Icon(
                painterResource(if (isFavourite) R.drawable.ic_check_circle else R.drawable.ic_add_circle),
                contentDescription = if (isFavourite) "In favourites" else "Add to favourites",
                tint = if (isFavourite) look.accent else look.text,
                modifier = Modifier.size(28.dp),
            )
        },
    )
}

/** Rows that scroll up past [y] fade out instead of crowding the top of the wallpaper. */
private fun Modifier.fadeAbove(y: Dp) = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val end = y.toPx()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = end - 90.dp.toPx(), endY = end),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun SectionHeader(title: String) {
    val look = LocalLauncherLook.current
    Text(
        title,
        // Its own 43dp row, starting a little left of the icons (Niagara: 40dp).
        modifier = Modifier.height(43.dp).wrapContentHeight(Alignment.CenterVertically).padding(start = 40.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 23.sp, shadow = look.shadow),
    )
}

/** A row in the Launcher section: a round badge with an icon instead of an app icon. */
@Composable
private fun ActionRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(start = ROW_START),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "Blocky and full" like Niagara's (his ask, 23 Oct): a solid rounded square in a soft pastel of the accent,
        // with a dark, filled icon, instead of a see-through circle with a thin outline icon.
        Box(
            Modifier
                .size(44.dp)
                .background(lerp(look.accent, Color.White, 0.55f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = look.sheet.copy(alpha = 1f), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(20.dp))
        Text(label, style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp, shadow = look.shadow))
    }
}

/**
 * One app: 40dp icon, 20sp name, 60dp tall. Tap opens it; long-press calls [onLongPress] (the app menu).
 * [highlighted] = lit up because the thumb is sliding onto it from the letter rail.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppEntry,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    /** What a tap does; by default it opens the app. */
    onClick: (() -> Unit)? = null,
    /** Something in the left margin, before the icon, like the + / ✓ while adding favourites (28dp wide). */
    leading: (@Composable () -> Unit)? = null,
    onLongPress: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (highlighted) look.accent.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(30.dp))
            .combinedClickable(
                onClick = { if (onClick != null) onClick() else Apps.launch(context, app) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            // With something in the margin, start further left so the icon still lines up at ROW_START.
            .padding(start = if (leading != null) 8.dp else ROW_START),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(44.dp * LocalLauncherLook.current.iconScale))
        Spacer(Modifier.width(20.dp))
        Text(
            app.label,
            style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp, shadow = look.shadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
