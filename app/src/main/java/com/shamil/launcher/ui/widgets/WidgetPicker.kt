package com.shamil.launcher.ui.widgets

import com.shamil.launcher.apps.LaunchStats
import com.shamil.launcher.apps.Favourites
import com.shamil.launcher.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Lets the picker hear back from a widget's own setup screen (opened by the activity, so the activity relays it). */
interface WidgetSetupResults {
    /** Called once with true (set up) or false (cancelled) when the widget's setup screen closes. */
    var onWidgetSetupDone: ((Boolean) -> Unit)?
    val widgetSetupRequestCode: Int
}

/** One app that offers widgets: its name, icon and every widget it has (previews load when he opens it). */
private class PickerApp(val pkg: String, val name: String, val icon: ImageBitmap?, val widgets: List<AppWidgetProviderInfo>) {
    val letter: String = name.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
}

/** One widget card: its preview picture, name, description and size in grid cells. */
private class PickerItem(val info: AppWidgetProviderInfo, val name: String, val about: String?, val preview: ImageBitmap?, val cells: String)

/**
 * Seelie settings → Home screen → Widget stack → Add from an app, laid out like Niagara's "Select a widget"
 * (his screenshots, 24 Oct): a search box, Suggestions (apps he uses), then every app A–Z as one row with
 * "N widgets"; tap an app and its widgets open under it as big preview cards. Tap a card: Android asks
 * "Allow Seelie to create widgets?" (only the first time per app), then the widget's own setup if it has one,
 * then it lands at the end of his stack and [onAdded] runs.
 */
@Composable
fun ColumnScope.AppWidgetPickerContent(onAdded: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val store = remember { WidgetStore.get(context) }
    val density = context.resources.displayMetrics.density

    // The apps and how many widgets each has. Quick: no preview pictures yet (those load when an app is opened).
    // Apps hidden in the vault are left out, so they don't show here or in Suggestions (25 Oct, audit S5).
    val vaultApps = remember { com.shamil.launcher.apps.VaultStore.get(context) }.apps
    val apps by produceState<List<PickerApp>?>(null, vaultApps) {
        value = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            SeelieWidgetHost.providers(context).filter { it.provider.packageName !in vaultApps }
                .groupBy { it.provider.packageName }.map { (pkg, widgets) ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                PickerApp(
                    pkg = pkg,
                    name = info?.let { pm.getApplicationLabel(it).toString() } ?: pkg,
                    icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    widgets = widgets,
                )
            }.sortedBy { it.name.lowercase() }
        }
    }
    // Suggestions: apps with widgets that are in his favourites or that he opens most (like Niagara's).
    val suggested = remember(apps) {
        val list = apps ?: return@remember emptyList()
        val favs = Favourites(context).packages
        val used = LaunchStats.get(context).mostOpened().map { it.first }
        (favs + used).distinct().mapNotNull { pkg -> list.firstOrNull { it.pkg == pkg } }.take(7)
    }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }   // the app whose widgets are showing

    // Connect the chosen widget (permission + its own setup), then put it at the end of his stack.
    val connect = rememberWidgetConnector { id, info ->
        store.addApp(id, WidgetSize.forMinHeight((info.minHeight / density).roundToInt()))
        onAdded()
    }

    Text("Handy things from your apps, right on your home, without opening them.",
        modifier = Modifier.staggerIn().padding(top = 6.dp, bottom = 14.dp),
        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 15.sp, lineHeight = 21.sp))
    PickerSearch(query) { query = it; open = null }

    val list = apps
    when {
        list == null -> Hint("Looking for widgets…")
        list.isEmpty() -> Hint("None of your apps offer widgets.")
        query.isNotBlank() -> {
            // Searching: apps whose name matches, or that have a widget whose name matches.
            val q = query.trim().lowercase()
            val pm = context.packageManager
            val hits = list.filter { app ->
                app.name.lowercase().contains(q) || app.widgets.any { runCatching { it.loadLabel(pm) }.getOrDefault("").lowercase().contains(q) }
            }
            PickerHeading(if (hits.isEmpty()) "Nothing found" else "Results")
            hits.forEach { app -> PickerAppRow(app, open == app.pkg, density, { open = if (open == app.pkg) null else app.pkg }) { connect(it) } }
        }
        else -> {
            // Seelie's own widgets he hasn't added yet, first: they need no permission or setup.
            if (store.notAdded.isNotEmpty()) {
                PickerHeading("Seelie")
                store.notAdded.forEach { kind -> SeelieWidgetRow(kind) { store.add(kind); onAdded() } }
            }
            if (suggested.isNotEmpty()) {
                PickerHeading("Suggestions")
                suggested.forEach { app -> PickerAppRow(app, open == "s:" + app.pkg, density, { open = if (open == "s:" + app.pkg) null else "s:" + app.pkg }) { connect(it) } }
            }
            list.groupBy { it.letter }.forEach { (letter, group) ->
                PickerHeading(letter, big = true)
                group.forEach { app -> PickerAppRow(app, open == app.pkg, density, { open = if (open == app.pkg) null else app.pkg }) { connect(it) } }
            }
        }
    }
}

/** One of Seelie's own widgets: its glyph, name and what it shows. Tap adds it to the stack. */
@Composable
private fun SeelieWidgetRow(kind: WidgetKind, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier.staggerIn().fillMaxWidth().pressFeel(press, look.accent, 16.dp).clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = press, indication = null, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).background(kind.tint.copy(alpha = 0.22f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(kind.icon), null, tint = kind.tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(kind.title, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 18.sp))
            Text(kind.about, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
        }
        Text("+", style = TextStyle(color = look.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun PickerHeading(text: String, big: Boolean = false) {
    val look = LocalLauncherLook.current
    Text(text, modifier = Modifier.staggerIn().padding(top = if (big) 18.dp else 16.dp, bottom = 6.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = if (big) 24.sp else 18.sp))
}

/** The search box: an app or widget name. */
@Composable
private fun PickerSearch(query: String, onQuery: (String) -> Unit) {
    val look = LocalLauncherLook.current
    Row(
        Modifier.staggerIn().padding(bottom = 6.dp).fillMaxWidth().height(52.dp).clip(CircleShape).background(look.card)
            .border(1.dp, if (query.isNotEmpty()) look.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = look.textDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search widgets", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 17.sp))
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp),
                cursorBrush = SolidColor(look.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { onQuery("") }, contentAlignment = Alignment.Center) {
                Text("✕", style = TextStyle(color = look.textDim, fontSize = 16.sp))
            }
        }
    }
}

/** One app: icon, name, "N widgets". Tapped, its widgets open beneath it as cards. */
@Composable
private fun PickerAppRow(app: PickerApp, opened: Boolean, density: Float, onToggle: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier.staggerIn().fillMaxWidth().pressFeel(press, look.accent, 16.dp).clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = press, indication = null, onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon != null) Image(app.icon, contentDescription = null, modifier = Modifier.size(52.dp))
        else Box(Modifier.size(52.dp).background(look.card, RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 18.sp))
            Text(if (app.widgets.size == 1) "1 widget" else "${app.widgets.size} widgets",
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
        }
        Text(if (opened) "⌃" else "⌄", style = TextStyle(color = look.textDim, fontSize = 18.sp))
    }
    AnimatedVisibility(visible = opened, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        PickerWidgets(app, density, onPick)
    }
}

/** The widgets of one app, as big centred cards (loaded now: preview pictures take a moment). */
@Composable
private fun PickerWidgets(app: PickerApp, density: Float, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val items by produceState<List<PickerItem>?>(null, app.pkg) {
        value = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            app.widgets.map { info ->
                val preview = runCatching {
                    (info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0))?.let { d ->
                        // Keep pictures small: about 600 px wide is plenty for a card.
                        val w = max(1, d.intrinsicWidth).coerceAtMost(600)
                        val h = (w.toFloat() * max(1, d.intrinsicHeight) / max(1, d.intrinsicWidth)).roundToInt().coerceIn(1, 800)
                        d.toBitmap(w, h).asImageBitmap()
                    }
                }.getOrNull()
                val about = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { info.loadDescription(context)?.toString() }.getOrNull() else null
                PickerItem(info, info.loadLabel(pm), about, preview, cellsOf(info, density))
            }.sortedBy { it.name.lowercase() }
        }
    }
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        val list = items
        if (list == null) Hint("Loading…") else list.forEach { item -> PickerCard(item) { onPick(item.info) } }
    }
}

/** One widget to pick: its preview picture, name and what it shows, centred on a dark card (Niagara's look). */
@Composable
private fun PickerCard(item: PickerItem, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(26.dp)
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .pressFeel(press, look.accent, 26.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item.preview?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 220.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(14.dp))
        }
        Text(item.name, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 18.sp))
        Text(listOfNotNull(item.about, item.cells).joinToString(" · "), textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp, lineHeight = 19.sp))
    }
}

/** The widget's size in home-screen grid cells, like "4×2" (how phones usually describe widget sizes). */
private fun cellsOf(info: AppWidgetProviderInfo, density: Float): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        return "${info.targetCellWidth}×${info.targetCellHeight}"
    }
    // Older Android: the classic formula, cells = (dp + 30) / 70.
    fun cells(px: Int) = (((px / density) + 30) / 70).roundToInt().coerceAtLeast(1)
    return "${cells(info.minWidth)}×${cells(info.minHeight)}"
}

/**
 * The steps to connect a widget from another app to Seelie, as one function you call with the widget to connect:
 * 1. ask Android for a new widget number and connect it (Android asks "Allow Seelie to create widgets?" if needed),
 * 2. open the widget's own setup screen if it has one,
 * then [onConnected] gets the new number. Cancelled anywhere = the number is given back to Android.
 * Used by the picker (add) and by Reset (a fresh start for a stuck widget).
 */
@Composable
fun rememberWidgetConnector(onConnected: (Int, AppWidgetProviderInfo) -> Unit): (AppWidgetProviderInfo) -> Unit {
    val context = LocalContext.current
    // The widget waiting for Android's permission answer.
    var pending by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }

    // Step 2: the widget's own setup screen (which calendar, which city...), if it has one and it isn't optional.
    fun setUp(id: Int, info: AppWidgetProviderInfo) {
        val optional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        val relay = context as? WidgetSetupResults
        if (info.configure == null || optional || relay == null || context !is Activity) {
            onConnected(id, info)
            return
        }
        relay.onWidgetSetupDone = { ok -> if (ok) onConnected(id, info) else SeelieWidgetHost.delete(context, id) }
        try {
            SeelieWidgetHost.host(context).startAppWidgetConfigureActivityForResult(context, id, 0, relay.widgetSetupRequestCode, null)
        } catch (e: Exception) {
            // Some widgets refuse to open their setup from a launcher; place them anyway.
            relay.onWidgetSetupDone = null
            onConnected(id, info)
        }
    }

    // Step 1b: Android's "Allow Seelie to create widgets?" question.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val (id, info) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (result.resultCode == Activity.RESULT_OK) setUp(id, info) else SeelieWidgetHost.delete(context, id)
    }

    // Step 1: a new number from Android, connected to the chosen widget.
    return { info ->
        val id = SeelieWidgetHost.host(context).allocateAppWidgetId()
        if (SeelieWidgetHost.manager(context).bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            setUp(id, info)
        } else {
            pending = id to info
            askPermission.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            )
        }
    }
}
