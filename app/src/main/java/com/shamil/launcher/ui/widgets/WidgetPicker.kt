package com.shamil.launcher.ui.widgets

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
import com.shamil.launcher.settings.SectionLabel
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

/** One widget in the picker: Android's info about it, its name, its app, a preview picture and its size in grid cells. */
private class PickerItem(val info: AppWidgetProviderInfo, val name: String, val app: String, val preview: ImageBitmap?, val cells: String)

/**
 * Seelie settings → Home screen → Widget stack → Add from an app: every widget his apps offer, grouped by app,
 * with a preview. Tap one: Android asks "Allow Seelie to create widgets?" (only the first time per app),
 * then the widget's own setup opens if it has one, then it lands at the end of his stack and [onAdded] runs.
 */
@Composable
fun ColumnScope.AppWidgetPickerContent(onAdded: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val store = remember { WidgetStore.get(context) }
    val density = context.resources.displayMetrics.density

    // Reading every app's widget list and preview pictures takes a moment, so off the main thread.
    val items by produceState<List<PickerItem>?>(null) {
        value = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            SeelieWidgetHost.providers(context).map { info ->
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                    .getOrDefault(info.provider.packageName)
                val preview = runCatching {
                    (info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0))?.let { d ->
                        // Keep pictures small: about 300 px wide is plenty for a card.
                        val w = max(1, d.intrinsicWidth).coerceAtMost(300)
                        val h = (w.toFloat() * max(1, d.intrinsicHeight) / max(1, d.intrinsicWidth)).roundToInt().coerceIn(1, 400)
                        d.toBitmap(w, h).asImageBitmap()
                    }
                }.getOrNull()
                PickerItem(info, info.loadLabel(pm), app, preview, cellsOf(info, density))
            }.sortedWith(compareBy({ it.app.lowercase() }, { it.name.lowercase() }))
        }
    }

    // Adding: the widget waiting for Android's permission answer.
    var pending by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }

    fun finish(id: Int, info: AppWidgetProviderInfo) {
        store.addApp(id, WidgetSize.forMinHeight((info.minHeight / density).roundToInt()))
        onAdded()
    }

    // Step 2: the widget's own setup screen (which calendar, which city…), if it has one and it isn't optional.
    fun setUp(id: Int, info: AppWidgetProviderInfo) {
        val optional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        val relay = context as? WidgetSetupResults
        if (info.configure == null || optional || relay == null || context !is Activity) {
            finish(id, info)
            return
        }
        relay.onWidgetSetupDone = { ok -> if (ok) finish(id, info) else SeelieWidgetHost.delete(context, id) }
        try {
            SeelieWidgetHost.host(context).startAppWidgetConfigureActivityForResult(context, id, 0, relay.widgetSetupRequestCode, null)
        } catch (e: Exception) {
            // Some widgets refuse to open their setup from a launcher; place them anyway.
            relay.onWidgetSetupDone = null
            finish(id, info)
        }
    }

    // Step 1b: Android's "Allow Seelie to create widgets?" question.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val (id, info) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (result.resultCode == Activity.RESULT_OK) setUp(id, info) else SeelieWidgetHost.delete(context, id)
    }

    // Step 1: get a number for the new widget from Android and connect it to the chosen widget.
    fun add(info: AppWidgetProviderInfo) {
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

    Hint("Real widgets from your apps. The first time, Android asks if Seelie may show them; some widgets then ask for their own setup.")
    val list = items
    when {
        list == null -> Hint("Looking for widgets…")
        list.isEmpty() -> Hint("None of your apps offer widgets.")
        else -> list.groupBy { it.app }.forEach { (app, widgets) ->
            SectionLabel(app)
            widgets.forEach { item -> PickerCard(item) { add(item.info) } }
        }
    }
}

/** One widget to pick: its preview picture on a soft card, its name and its size. */
@Composable
private fun PickerCard(item: PickerItem, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier
            .staggerIn()
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .pressFeel(press, look.accent, 20.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            item.preview?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(8.dp).heightIn(max = 140.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 16.sp))
            Spacer(Modifier.width(8.dp))
            Text(item.cells, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp))
        }
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
