package com.shamil.launcher.ui

import com.shamil.launcher.ui.widgets.SeelieWidgetHost
import com.shamil.launcher.ui.widgets.StackEntry
import com.shamil.launcher.ui.widgets.WidgetStore
import com.shamil.launcher.settings.SeelieSettings
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import com.shamil.launcher.apps.Apps
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.settings.LocalStagger
import com.shamil.launcher.settings.Stagger
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val EDIT_ROW = 60.dp

/**
 * Edit favourites, full screen: Niagara's layout (checkbox left, ⠿ handle right, Selected / Suggested / A–Z,
 * a Done pill bottom-right), in Seelie's own look and motion. Tap any row to add or remove that app;
 * drag ⠿ to reorder (the others glide out of the way). The letter rail with its wave jumps through the list.
 */
@Composable
fun EditFavouritesScreen(
    apps: List<AppEntry>,
    favouriteApps: List<AppEntry>,
    suggestions: List<AppEntry>,
    onToggle: (AppEntry) -> Unit,
    onReorder: (List<AppEntry>) -> Unit,
    onDone: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val widgets = remember { WidgetStore.get(context) }
    var removing by remember { mutableStateOf<StackEntry?>(null) }
    val haptics = LocalHapticFeedback.current
    val rowPx = with(LocalDensity.current) { EDIT_ROW.toPx() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // One working copy of the order while dragging (the handles hold on to it), synced with the saved list otherwise.
    var order by remember { mutableStateOf(favouriteApps) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(favouriteApps) { if (dragging == null) order = favouriteApps }
    val picked = remember(favouriteApps) { favouriteApps.map { it.key }.toSet() }
    val sections = remember(apps) { apps.groupBy { it.letter } }
    // Search, like Niagara's: while there's text, the A–Z part shows only the matching apps.
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) { if (query.isBlank()) emptyList() else Apps.search(apps, query) }
    val searching = query.isNotBlank()

    // Where each letter's header sits in the list, for the rail. Items: title, Selected header, the favourites,
    // (Suggested header + suggestions), then per letter a header + its apps.
    val letterIndex = remember(order.size, suggestions, sections) {
        var i = 3 + order.size + if (suggestions.isNotEmpty()) 1 + suggestions.size else 0
        sections.mapValues { (_, group) -> i.also { i += 1 + group.size } }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // The wallpaper stays faintly visible behind a wash of the theme's night colours.
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 0.86f), look.sheet.copy(alpha = 0.94f))))
    ) {
        val screenH = maxHeight
        CompositionLocalProvider(LocalStagger provides remember { Stagger() }) {
            LazyColumn(
                state = listState,
                // The keyboard pushes the list up instead of covering the results.
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 28.dp,
                    bottom = 140.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    end = 52.dp,
                ),
            ) {
                item(key = "title") { EditTitle(count = order.size) }
                item(key = "search") { EditSearch(query, onQuery = { query = it }) }

                if (searching) {
                    // Searching: just the matches, right under the box; tick them like anywhere else.
                    item(key = "h-results") { EditSection(if (results.isEmpty()) "No apps match" else "Results · ${results.size}") }
                    items(results, key = { "res-${it.key}" }) { app ->
                        EditRow(app, checked = app.key in picked, modifier = Modifier.animateItem(), onClick = { onToggle(app) })
                    }
                }

                // The widgets on his home, first (they sit above the favourites on the home): untick one to take it off (asks first), or add another.
                if (!searching) {
                    item(key = "h-widgets") { EditSection("Widgets · ${widgets.stack.size}") }
                    items(widgets.stack, key = { "w-${it.key}" }) { entry ->
                        WidgetEditRow(entry, modifier = Modifier.animateItem(), onClick = { removing = entry })
                    }
                    item(key = "w-add") { AddWidgetRow { SeelieSettings.open(context, SeelieSettings.PAGE_WIDGETS) } }
                }

                item(key = "h-selected") { EditSection("Selected · ${order.size}") }
                items(order, key = { "sel-${it.key}" }) { app ->
                    val isDragged = dragging == app.key
                    EditRow(
                        app = app,
                        checked = true,
                        lifted = isDragged,
                        modifier = Modifier
                            .animateItem(placementSpec = if (isDragged) null else spring(dampingRatio = 0.8f, stiffness = 500f))
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragged) dragOffset else 0f },
                        onClick = { onToggle(app) },
                        handle = Modifier.pointerInput(app.key) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = app.key
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    dragging = null
                                    dragOffset = 0f
                                    onReorder(order)
                                },
                                onDragCancel = {
                                    dragging = null
                                    dragOffset = 0f
                                },
                            ) { change, dy ->
                                change.consume()
                                dragOffset += dy
                                // Passed half a row? Swap with the neighbour; the others glide (animateItem).
                                val from = order.indexOfFirst { it.key == app.key }
                                val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                if (from >= 0 && to != from) {
                                    order = order.toMutableList().apply { add(to, removeAt(from)) }
                                    dragOffset -= (to - from) * rowPx
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                    )
                }

                if (suggestions.isNotEmpty() && !searching) {
                    item(key = "h-suggested") { EditSection("Suggested") }
                    items(suggestions, key = { "sug-${it.key}" }) { app ->
                        EditRow(app, checked = false, modifier = Modifier.animateItem(), onClick = { onToggle(app) })
                    }
                }

                if (!searching) sections.forEach { (letter, group) ->
                    item(key = "h-$letter") { EditSection(letter, big = true) }
                    items(group, key = { "all-${it.key}" }) { app ->
                        EditRow(app, checked = app.key in picked, modifier = Modifier.animateItem(), onClick = { onToggle(app) })
                    }
                }
            }
        }

        removing?.let { entry ->
            val name = widgetName(entry)
            AlertDialog(
                onDismissRequest = { removing = null },
                containerColor = look.sheet,
                title = { Text("Take \"$name\" off your home?", color = look.text) },
                text = { Text("You can add it again any time with + Add widget.", color = look.textDim) },
                confirmButton = { TextButton({ widgets.remove(entry); removing = null }) { Text("Remove", color = Color(0xFFFF9A9A)) } },
                dismissButton = { TextButton({ removing = null }) { Text("Keep", color = look.textDim) } },
            )
        }

        // The letter rail with its wave: ☆ = back to the top (your selection), a letter = jump there.
        LetterRail(
            present = sections.keys,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = screenH * 0.2f)
                .height(screenH * 0.66f)
                .width(46.dp),
        ) { item ->
            val index = when (item) {
                STAR -> 0
                DOT -> letterIndex.values.lastOrNull() ?: 0
                else -> letterIndex.entries.firstOrNull { it.key >= item }?.value ?: letterIndex.values.lastOrNull() ?: 0
            }
            scope.launch { listState.scrollToItem(index) }
        }

        DonePill(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 64.dp, bottom = 28.dp), onDone)
    }
}

/** The big Cinzel title with its breathing glow, and the count. */
@Composable
private fun EditTitle(count: Int) {
    val look = LocalLauncherLook.current
    val glow by rememberInfiniteTransition(label = "title glow")
        .animateFloat(0.15f, 0.55f, infiniteRepeatable(tween(2800), RepeatMode.Reverse), label = "glow")
    Column(Modifier.staggerIn().padding(start = 32.dp, end = 12.dp, bottom = 8.dp)) {
        Text("SEELIE", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent.copy(alpha = 0.85f), fontSize = 11.sp, letterSpacing = 3.sp))
        Text("Your favourites",
            style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 32.sp, lineHeight = 38.sp,
                shadow = Shadow(look.accent.copy(alpha = glow), blurRadius = 24f)))
        Spacer(Modifier.height(10.dp))
        Text(
            "$count picked · most people pick 4 to 8. Tap an app to add or remove it, drag ⠿ to change the order.",
            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp, lineHeight = 20.sp),
        )
    }
}

/** The search pill under the title: a magnifier, the text, and an ✕ to clear it. */
@Composable
private fun EditSearch(query: String, onQuery: (String) -> Unit) {
    val look = LocalLauncherLook.current
    val shape = CircleShape
    Row(
        Modifier
            .staggerIn()
            .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, if (query.isNotEmpty()) look.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = look.textDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search apps", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 17.sp))
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
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { onQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", style = TextStyle(color = look.textDim, fontSize = 16.sp))
            }
        }
    }
}

/** A section heading: small accent caps with a hairline, or a big letter for the A–Z part. */
@Composable
private fun EditSection(text: String, big: Boolean = false) {
    val look = LocalLauncherLook.current
    Row(Modifier.staggerIn().padding(start = 32.dp, top = if (big) 14.dp else 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (big) {
            Text(text, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 22.sp))
        } else {
            Text(text.uppercase(), style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 11.sp, letterSpacing = 2.4.sp))
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(look.accent.copy(alpha = 0.3f), Color.Transparent))))
    }
}

/**
 * One app: checkbox on the left (its ✓ springs in), icon, name, and on favourites a ⠿ handle on the right.
 * [lifted] = being dragged: it rises a little and glows.
 */
@Composable
private fun EditRow(
    app: AppEntry,
    checked: Boolean,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
    onClick: () -> Unit,
    handle: Modifier? = null,
) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val check by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "check")
    val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "lift")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .staggerIn()
            .fillMaxWidth()
            .height(EDIT_ROW)
            .padding(horizontal = 12.dp)
            .graphicsLayer { scaleX = 1f + 0.03f * lift; scaleY = 1f + 0.03f * lift }
            .pressFeel(press, look.accent, 18.dp)
            .clip(shape)
            .background(look.accent.copy(alpha = 0.16f * lift))
            .border(1.dp, look.accent.copy(alpha = 0.5f * lift), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(start = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditCheckbox(checked, check)
        Spacer(Modifier.width(16.dp))
        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(44.dp * look.iconScale))
        Spacer(Modifier.width(16.dp))
        Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp))
        if (handle != null) {
            Box(Modifier.size(52.dp).then(handle), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_drag_indicator), contentDescription = "Drag to reorder", tint = look.textDim)
            }
        }
    }
}

/** The floating "Done" pill, bottom-right like Niagara's, in the accent colour, with the press feel. */
@Composable
private fun DonePill(modifier: Modifier, onDone: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressFeel(press, Color.White, 28.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(look.accent, look.accent.copy(alpha = 0.82f))))
            .clickable(interactionSource = press, indication = null, onClick = onDone)
            .padding(horizontal = 26.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", style = TextStyle(color = look.sheet.copy(alpha = 1f), fontSize = 18.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.width(10.dp))
        Text("Done", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.sheet.copy(alpha = 1f), fontSize = 17.sp))
    }
}

/** The checkbox: an outlined rounded square; when checked it fills with the accent and a ✓ springs in ([check] 0–1). */
@Composable
private fun EditCheckbox(checked: Boolean, check: Float) {
    val look = LocalLauncherLook.current
    Box(
        Modifier
            .size(24.dp)
            .border(2.dp, if (checked) look.accent else look.textDim.copy(alpha = 0.7f), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (check > 0.01f) {
            Box(
                Modifier.size(24.dp).graphicsLayer { scaleX = check; scaleY = check }.background(look.accent, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", style = TextStyle(color = look.sheet.copy(alpha = 1f), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** A widget's name as he knows it: Seelie's own title, or the app widget's own label ("Shortcuts"). */
@Composable
private fun widgetName(entry: StackEntry): String {
    val context = LocalContext.current
    return remember(entry) {
        when (entry) {
            is StackEntry.BuiltIn -> entry.kind.title
            is StackEntry.App -> runCatching {
                SeelieWidgetHost.manager(context).getAppWidgetInfo(entry.appWidgetId)?.loadLabel(context.packageManager)
            }.getOrNull() ?: "Widget"
        }
    }
}

/** One widget on his home, ticked; tapping asks to take it off. Its app's icon (or Seelie's own glyph) and its name. */
@Composable
private fun WidgetEditRow(entry: StackEntry, modifier: Modifier, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val press = remember { MutableInteractionSource() }
    val name = widgetName(entry)
    val appIcon = remember(entry) {
        (entry as? StackEntry.App)?.let { app ->
            runCatching {
                val info = SeelieWidgetHost.manager(context).getAppWidgetInfo(app.appWidgetId) ?: return@runCatching null
                context.packageManager.getApplicationIcon(info.provider.packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    Row(
        modifier
            .staggerIn()
            .fillMaxWidth()
            .height(EDIT_ROW)
            .padding(horizontal = 12.dp)
            .pressFeel(press, look.accent, 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(start = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditCheckbox(checked = true, check = 1f)
        Spacer(Modifier.width(16.dp))
        val size = 44.dp * look.iconScale
        when {
            appIcon != null -> Image(appIcon, contentDescription = null, modifier = Modifier.size(size))
            entry is StackEntry.BuiltIn -> Box(
                Modifier.size(size).background(entry.kind.tint.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(entry.kind.icon), null, tint = entry.kind.tint, modifier = Modifier.size(24.dp)) }
            else -> Box(Modifier.size(size).background(look.card, RoundedCornerShape(14.dp)))
        }
        Spacer(Modifier.width(16.dp))
        Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp))
    }
}

/** "+ Add widget": opens Seelie settings on the widget stack (Seelie's own widgets and widgets from apps). */
@Composable
private fun AddWidgetRow(onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .height(EDIT_ROW)
            .padding(horizontal = 12.dp)
            .pressFeel(press, look.accent, 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(start = 58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp * look.iconScale).background(look.accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("+", style = TextStyle(color = look.accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)) }
        Spacer(Modifier.width(16.dp))
        Text("Add widget", style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp))
    }
}
