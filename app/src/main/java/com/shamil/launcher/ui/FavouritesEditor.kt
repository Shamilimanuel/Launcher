package com.shamil.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.roundToInt

private val ROW_HEIGHT = 56.dp

/**
 * "Edit favourites" mode, like Niagara's: each favourite gets a ⋮⋮ handle to drag it to a new
 * place, and a ⊖ to remove it. The new order is saved when you let go, and never re-sorted for you.
 */
@Composable
fun FavouritesEditor(
    apps: List<AppEntry>,
    onReorder: (List<AppEntry>) -> Unit,
    onRemove: (AppEntry) -> Unit,
    onDone: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    // One working copy for the whole edit session (the drag handles hold on to it), kept in step
    // with the saved list whenever that changes and nothing is being dragged.
    var order by remember { mutableStateOf(apps) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(apps) { if (dragging == null) order = apps }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = ROW_START, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Drag ⋮⋮ to reorder · letters to add",
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 15.sp, shadow = look.shadow),
            )
            TextButton(onClick = onDone) {
                Text("Done", style = TextStyle(fontFamily = look.font, color = look.accent, fontSize = 17.sp, shadow = look.shadow))
            }
        }

        order.forEach { app ->
            // key() keeps each row (and its drag gesture) attached to its app while the rows swap places.
            key(app.key) {
                val isDragged = dragging == app.key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .background(
                            if (isDragged) look.panel else look.panel.copy(alpha = 0f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(start = ROW_START),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(44.dp * LocalLauncherLook.current.iconScale))
                    Spacer(Modifier.width(20.dp))
                    Text(
                        app.label,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp, shadow = look.shadow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        painterResource(R.drawable.ic_remove_circle),
                        contentDescription = "Remove ${app.label}",
                        tint = look.textDim,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onRemove(app) }
                            .padding(10.dp),
                    )
                    // The ⋮⋮ handle: drag it up or down.
                    Box(
                        Modifier
                            .size(48.dp)
                            .pointerInput(app.key) {
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
                                    // Passed half a row? Swap places with the neighbour, one slot at a time.
                                    val from = order.indexOfFirst { it.key == app.key }
                                    val steps = (dragOffset / rowPx).roundToInt()
                                    val to = (from + steps).coerceIn(0, order.lastIndex)
                                    if (from >= 0 && to != from) {
                                        order = order.toMutableList().apply { add(to, removeAt(from)) }
                                        dragOffset -= (to - from) * rowPx
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_drag_indicator), contentDescription = "Drag to reorder", tint = look.text)
                    }
                }
            }
        }
    }
}
