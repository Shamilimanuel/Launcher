package com.shamil.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.AppShortcut
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.apps.GestureStore
import com.shamil.launcher.apps.SwipeAction
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Swipe left on <app>": pick what a left swipe on this app does. Nothing, App info, Screen time,
 * one of the app's own shortcuts, or opening another app (with a search box, since the list is long).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSheet(app: AppEntry, apps: List<AppEntry>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val store = remember { GestureStore.get(context) }
    val pkg = app.component.packageName
    val current = store.actionFor(pkg)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun pick(action: SwipeAction?) {
        store.set(pkg, action)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    // The app's own shortcuts (null = Seelie isn't the default home, so Android won't share them).
    val shortcuts by produceState<List<AppShortcut>?>(emptyList(), app.key) {
        value = withContext(Dispatchers.Default) { Apps.shortcuts(context, app) }
    }
    var query by remember { mutableStateOf("") }
    val others = remember(apps, query) {
        val list = apps.filter { it.component.packageName != pkg }
        if (query.isBlank()) list else Apps.search(list, query)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = look.sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        LazyColumn(
            Modifier
                .fillMaxHeight(0.9f)
                .background(Brush.verticalGradient(0f to look.sheetTop, 0.4f to look.sheet))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 42.dp, height = 4.dp).background(look.text.copy(alpha = 0.28f), CircleShape))
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Swipe left", style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 24.sp))
                            Text("on ${app.label}: pick what it does", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp))
                        }
                    }
                    GroupLabel("Basics")
                    ChoiceCard(R.drawable.ic_remove_circle, "Nothing", "The row just springs back", selected = current == null) { pick(null) }
                    ChoiceCard(R.drawable.ic_info, "App info", "Android's page for this app", selected = current == SwipeAction.Info) { pick(SwipeAction.Info) }
                    ChoiceCard(R.drawable.ic_hourglass, "Screen time", "How long you used it today", selected = current == SwipeAction.ScreenTime) { pick(SwipeAction.ScreenTime) }
                    GroupLabel("${app.label}'s shortcuts")
                }
            }
            when (val list = shortcuts) {
                null -> item { Note("Make Seelie your default home to use an app's shortcuts here.") }
                else -> {
                    if (list.isEmpty()) item { Note("${app.label} has no shortcuts.") }
                    items(list, key = { "s-" + it.info.id }) { sc ->
                        val action = SwipeAction.Shortcut(sc.info.id, sc.label)
                        ChoiceCard(null, sc.label, "Opens straight into ${app.label}", selected = current == action, image = sc.icon) { pick(action) }
                    }
                }
            }
            item {
                GroupLabel("Open another app")
                // A small search box, because the list of apps is long.
                Row(
                    Modifier.padding(bottom = 8.dp).fillMaxWidth().height(48.dp).clip(CircleShape).background(look.card)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_search), null, tint = look.textDim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search apps", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 16.sp))
                        BasicTextField(query, { query = it }, singleLine = true, cursorBrush = SolidColor(look.accent),
                            textStyle = TextStyle(fontFamily = look.font, color = look.text, fontSize = 16.sp), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            items(others, key = { "a-" + it.key }) { other ->
                val action = SwipeAction.OpenApp(other.component.flattenToString(), other.label)
                ChoiceCard(null, other.label, null, selected = current == action, image = other.icon) { pick(action) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** One choice: an icon (or the app's/shortcut's own picture), title, small line, and a ✓ when it's the current one. */
@Composable
private fun ChoiceCard(
    @DrawableRes icon: Int?, title: String, sub: String?, selected: Boolean,
    image: androidx.compose.ui.graphics.ImageBitmap? = null, onClick: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .padding(bottom = 7.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (selected) look.accent.copy(alpha = 0.16f) else look.card)
            .border(1.dp, if (selected) look.accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            image != null -> Image(image, contentDescription = null, modifier = Modifier.size(30.dp))
            icon != null -> Icon(painterResource(icon), contentDescription = null, tint = look.text, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp))
            if (sub != null) Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.5.sp))
        }
        if (selected) Text("✓", style = TextStyle(color = look.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Note(text: String) {
    val look = LocalLauncherLook.current
    Text(text, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp), style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
}
