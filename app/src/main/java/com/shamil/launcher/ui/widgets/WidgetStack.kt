package com.shamil.launcher.ui.widgets

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The widget stack under the clock: his chosen widgets side by side, swipe sideways to flip through them,
 * with small dots underneath (the current one is a wider pill in the accent colour).
 * Empty stack = nothing at all (no height). Width: whatever [modifier] gives it (it fills the width).
 *
 * Cheap on the home screen: the swipe itself moves in the layout step (no redraw of the widgets per frame),
 * and the dots only change when the page changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetStack(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { WidgetStore.get(context) }
    val stack = store.stack
    if (stack.isEmpty()) return

    val today = rememberToday()
    // Opens on the widget he was last on, and remembers every page he settles on.
    val pager = rememberPagerState(initialPage = store.lastPage.coerceIn(0, (stack.size - 1).coerceAtLeast(0))) { store.stack.size }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { store.rememberPage(it) } }
    // App widgets can be small, medium or large: the stack's height glides to the widget you're on.
    val builtIn = widgetHeight(HomeStyleStore.get(context).style)
    val height by animateDpAsState(store.heightOf(store.stack.getOrNull(pager.currentPage), builtIn), spring(dampingRatio = 0.85f, stiffness = 400f), label = "stack height")
    Column(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(height),
            pageSpacing = 12.dp,
            // Each page is known by its widget, so moving widgets around in settings keeps their state.
            key = { index -> store.stack.getOrNull(index)?.key ?: index },
        ) { page ->
            when (val entry = store.stack.getOrNull(page)) {
                // A real widget from another app draws its own background, so no Seelie card around it.
                is StackEntry.App -> AppWidgetPage(entry.appWidgetId)
                is StackEntry.BuiltIn -> WidgetCard {
                    when (entry.kind) {
                        WidgetKind.MONTH -> MonthWidget(today)
                        WidgetKind.GAUGES -> GaugesWidget()
                        WidgetKind.WEEK -> WeekWidget(today)
                        WidgetKind.NOTES -> NotesWidget(store)
                    }
                }
                null -> {}
            }
        }
        // One widget = nothing to flip through, so no dots.
        if (stack.size > 1) StackDots(pager, stack.size)
    }
}

/** The dots under the stack. Tap one to glide to that widget. */
@Composable
private fun StackDots(pager: PagerState, count: Int) {
    val look = LocalLauncherLook.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val on = pager.currentPage == i
            // The current dot stretches into a pill with a little bounce.
            val width by animateDpAsState(if (on) 16.dp else 6.dp, spring(dampingRatio = 0.55f, stiffness = 500f), label = "dot")
            Box(
                Modifier
                    // The tap area is bigger than the dot itself, so it's easy to hit.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { pager.animateScrollToPage(i) }
                    }
                    .padding(horizontal = 3.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width, 6.dp)
                        .background(if (on) look.accent else look.text.copy(alpha = 0.35f), CircleShape)
                )
            }
        }
    }
}

/**
 * Today's date, which turns over at midnight while the home is on screen, and is checked again
 * every time the home comes back (a sleeping phone can make a timer late, coming back fixes that).
 */
@Composable
private fun rememberToday(): LocalDate {
    val lifecycleOwner = LocalLifecycleOwner.current
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                today = LocalDate.now()
                val untilMidnight = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay()).toMillis()
                delay(untilMidnight.coerceAtLeast(1_000L) + 500L)
            }
        }
    }
    return today
}
