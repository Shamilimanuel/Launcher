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
import com.shamil.launcher.apps.GestureStore
import com.shamil.launcher.ui.notify.NotificationCard
import com.shamil.launcher.ui.notify.NotificationLine
import com.shamil.launcher.ui.notify.rememberAppNotifications
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.shamil.launcher.apps.VaultStore
import com.shamil.launcher.ui.theme.LocalLauncherLook
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which app's swipe-right pop-up is open (only one at a time) and where it is on screen, so a tap anywhere
 * outside it closes it (his ask, 23 Oct: "when you swipe right and click away it should also go away").
 */
class PopupHolder {
    var openKey by mutableStateOf<String?>(null)
    var bounds: Rect? = null

    fun close() {
        openKey = null
        bounds = null
    }
}
val LocalPopupHolder = staticCompositionLocalOf { PopupHolder() }

/** Opens the "swipe left" chooser for an app (the home screen provides it). */
val LocalGestureChooser = staticCompositionLocalOf<(AppEntry) -> Unit> { {} }

/**
 * An app row you can swipe to the RIGHT to open its pop-up (its shortcuts), like Niagara, and to the LEFT for the
 * action he picked for this app (his locked feature #5): the row slides away revealing the action, a tick when it's
 * far enough, and letting go does it. No action yet = the chooser opens, so it's easy to find.
 */
@Composable
fun AppItem(
    app: AppEntry,
    highlighted: Boolean = false,
    /** Favourites show their notifications under the name (+ the full card with quick reply); the A–Z list doesn't. */
    showNotifications: Boolean = false,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val openAt = with(density) { 56.dp.toPx() }
    val maxPull = with(density) { 90.dp.toPx() }
    val chooseGesture = LocalGestureChooser.current
    val leftAction = GestureStore.get(context).actionFor(app.component.packageName)
    val popups = LocalPopupHolder.current
    val popupOpen = popups.openKey == app.key
    var dragX by remember { mutableFloatStateOf(0f) }
    // His new notifications for this app (shared, parsed once per change), and whether their card is open.
    val notifs = if (showNotifications) rememberAppNotifications()[app.component.packageName].orEmpty() else emptyList()
    var notifOpen by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val vault = remember { VaultStore.get(context) }
    // Seelie stopped (or the vault locked again): a locked app's open card (full messages + reply) closes, so the
    // next person to pick up the phone doesn't find it open (25 Oct, audit S10).
    LaunchedEffect(vault.lockedAgain) { if (vault.guardsFromOutside(app.component.packageName)) notifOpen = false }

    Column {
      Box(contentAlignment = Alignment.CenterEnd) {
        // What a left swipe will do, revealed behind the row as it slides away.
        Row(
            Modifier
                .padding(end = 16.dp)
                .graphicsLayer {
                    val p = (-dragX / openAt).coerceIn(0f, 1f)
                    alpha = p
                    scaleX = 0.85f + 0.15f * p
                    scaleY = 0.85f + 0.15f * p
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                leftAction?.label ?: "Choose an action",
                maxLines = 1,
                style = TextStyle(fontFamily = look.font, color = if (armed) look.accent else look.textDim, fontSize = 15.sp, shadow = look.shadow),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(28.dp).background(if (armed) look.accent else look.text.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                Text("‹", style = TextStyle(color = if (armed) look.sheet.copy(alpha = 1f) else look.text, fontSize = 18.sp))
            }
        }
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
                                    // A locked app's shortcuts (often chat names) show only after its code (25 Oct, audit S6).
                                    if (popups.openKey == app.key) popups.close()
                                    else vault.askCode(app.component.packageName, app.label) { popups.openKey = app.key }
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                popupOpen && pulled < 0 -> popups.close()
                                pulled < -openAt -> {
                                    // Swipe left: his action for this app, or the chooser when there's none (or it broke).
                                    val action = GestureStore.get(context).actionFor(app.component.packageName)
                                    if (action == null || !GestureStore.perform(context, app, action)) chooseGesture(app)
                                }
                            }
                            armed = false
                            // Spring the row back into place.
                            scope.launch { animate(dragX, 0f) { value, _ -> dragX = value } }
                        },
                        onDragCancel = { dragX = 0f; armed = false },
                    ) { change, dx ->
                        change.consume()
                        dragX = (dragX + dx * 0.6f).coerceIn(-maxPull, maxPull)
                        // A tick the moment a left swipe is far enough to count.
                        val nowArmed = dragX < -openAt
                        if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        armed = nowArmed
                    }
                },
        )
      }
        // One or two lines of the newest message under the name; › opens the full card.
        if (notifs.isNotEmpty()) {
            NotificationLine(
                app.component.packageName, notifs,
                modifier = Modifier.graphicsLayer { translationX = dragX },
                onExpand = { notifOpen = !notifOpen; popups.close() },
            )
        }
        AnimatedVisibility(
            visible = notifOpen && notifs.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            NotificationCard(app, notifs, onClose = { notifOpen = false })
        }
        AnimatedVisibility(
            visible = popupOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ShortcutsPopup(app, onClose = { popups.close() }, modifier = Modifier.onGloballyPositioned { popups.bounds = it.boundsInRoot() })
        }
    }
}

/** The pop-up card under the row: the app's shortcuts, each opens straight into that part of the app. */
@Composable
private fun ShortcutsPopup(app: AppEntry, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    var loaded by remember { mutableStateOf(false) }
    var shortcuts by remember { mutableStateOf<List<AppShortcut>?>(emptyList()) }
    // A locked app's pop-up only opens after its code (see AppItem) and closes when Seelie stops: its shortcuts open
    // without asking a second time.
    val codeGiven = remember(app.key) { VaultStore.get(context).guardsFromOutside(app.component.packageName) }
    LaunchedEffect(app.key) {
        shortcuts = withContext(Dispatchers.Default) { Apps.shortcuts(context, app) }
        loaded = true
    }

    Column(
        modifier
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
                            Apps.openShortcut(context, shortcut, codeGiven)
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
