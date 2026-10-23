package com.shamil.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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

    // Where each letter's header sits in the list, for the rail. Items: title, Selected header, the favourites,
    // (Suggested header + suggestions), then per letter a header + its apps.
    val letterIndex = remember(order.size, suggestions, sections) {
        var i = 2 + order.size + if (suggestions.isNotEmpty()) 1 + suggestions.size else 0
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 28.dp,
                    bottom = 140.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    end = 52.dp,
                ),
            ) {
                item(key = "title") { EditTitle(count = order.size) }

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

                if (suggestions.isNotEmpty()) {
                    item(key = "h-suggested") { EditSection("Suggested") }
                    items(suggestions, key = { "sug-${it.key}" }) { app ->
                        EditRow(app, checked = false, modifier = Modifier.animateItem(), onClick = { onToggle(app) })
                    }
                }

                sections.forEach { (letter, group) ->
                    item(key = "h-$letter") { EditSection(letter, big = true) }
                    items(group, key = { "all-${it.key}" }) { app ->
                        EditRow(app, checked = app.key in picked, modifier = Modifier.animateItem(), onClick = { onToggle(app) })
                    }
                }
            }
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
        // The checkbox: an outlined rounded square; when checked it fills with the accent and a ✓ springs in.
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
