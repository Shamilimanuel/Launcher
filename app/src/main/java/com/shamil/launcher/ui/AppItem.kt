package com.shamil.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.AppShortcut
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.ui.theme.LocalLauncherLook
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An app row you can also swipe to the RIGHT to open its pop-up (its shortcuts), like Niagara.
 * Swipe left (or right again) to close it.
 */
@Composable
fun AppItem(app: AppEntry, highlighted: Boolean = false, onLongPress: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val openAt = with(density) { 56.dp.toPx() }
    val maxPull = with(density) { 90.dp.toPx() }
    var popupOpen by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }

    Column {
        AppRow(
            app = app,
            highlighted = highlighted,
            onLongPress = onLongPress,
            modifier = Modifier
                // The row follows the finger a little (drawing only, no re-layout).
                .graphicsLayer { translationX = dragX }
                // Only reacts to sideways drags, so scrolling the list up and down still works.
                .pointerInput(app.key) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val pulled = dragX
                            when {
                                pulled > openAt -> {
                                    popupOpen = !popupOpen
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                pulled < -openAt / 2 -> popupOpen = false
                            }
                            // Spring the row back into place.
                            scope.launch { animate(dragX, 0f) { value, _ -> dragX = value } }
                        },
                        onDragCancel = { dragX = 0f },
                    ) { change, dx ->
                        change.consume()
                        dragX = (dragX + dx * 0.6f).coerceIn(-maxPull / 2, maxPull)
                    }
                },
        )
        AnimatedVisibility(
            visible = popupOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ShortcutsPopup(app, onClose = { popupOpen = false })
        }
    }
}

/** The pop-up card under the row: the app's shortcuts, each opens straight into that part of the app. */
@Composable
private fun ShortcutsPopup(app: AppEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    var loaded by remember { mutableStateOf(false) }
    var shortcuts by remember { mutableStateOf<List<AppShortcut>?>(emptyList()) }
    LaunchedEffect(app.key) {
        shortcuts = withContext(Dispatchers.Default) { Apps.shortcuts(context, app) }
        loaded = true
    }

    Column(
        Modifier
            .padding(start = ROW_START, end = 8.dp, bottom = 10.dp)
            .fillMaxWidth()
            .background(look.panel, RoundedCornerShape(20.dp))
            .padding(vertical = 6.dp)
    ) {
        val list = shortcuts
        when {
            !loaded -> Spacer(Modifier.height(48.dp))
            list == null -> PopupNote("Make ${stringResource(R.string.app_name)} your default home to see shortcuts")
            list.isEmpty() -> PopupNote("${app.label} has no shortcuts")
            else -> list.forEach { shortcut ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            Apps.openShortcut(context, shortcut)
                            onClose()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = shortcut.icon
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    } else {
                        Box(Modifier.size(28.dp).background(look.text.copy(alpha = 0.2f), CircleShape))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        shortcut.label,
                        style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupNote(text: String) {
    val look = LocalLauncherLook.current
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 15.sp),
    )
}
