package com.shamil.launcher.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.shamil.launcher.DefaultHome
import com.shamil.launcher.R
import com.shamil.launcher.settings.SeelieSettings
import com.shamil.launcher.ui.clock.ClockStore
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.widgets.WidgetStore
import kotlinx.coroutines.launch

/** What the glance line under the date shows (saved on the phone, Compose state, one shared store). */
class HomePrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    var showWeather by mutableStateOf(prefs.getBoolean(KEY_WEATHER, true))
        private set
    var showBattery by mutableStateOf(prefs.getBoolean(KEY_BATTERY, true))
        private set

    fun useWeather(on: Boolean) { showWeather = on; prefs.edit { putBoolean(KEY_WEATHER, on) } }
    fun useBattery(on: Boolean) { showBattery = on; prefs.edit { putBoolean(KEY_BATTERY, on) } }

    companion object {
        private const val KEY_WEATHER = "show_weather"
        private const val KEY_BATTERY = "show_battery"
        @Volatile private var instance: HomePrefs? = null

        fun get(context: Context): HomePrefs =
            instance ?: synchronized(this) { instance ?: HomePrefs(context.applicationContext).also { instance = it } }
    }
}

/**
 * The "Seelie widget" sheet: long-press the empty space right of the clock (or the clock itself), like Niagara's.
 * Quick ways to the clock style, what the glance line shows, the widget stack, and Seelie's other places.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeWidgetSheet(onEditFavourites: () -> Unit, onDismiss: () -> Unit) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val home = remember { HomePrefs.get(context) }
    val clock = remember { ClockStore.get(context) }
    val widgets = remember { WidgetStore.get(context) }

    /** Slide the sheet away, then do [action]. */
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = look.sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(0f to look.sheetTop, 0.4f to look.sheet))
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 4.dp)
                    .background(look.text.copy(alpha = 0.28f), CircleShape)
            )
            Spacer(Modifier.size(16.dp))
            Text("Seelie widget", modifier = Modifier.padding(start = 8.dp),
                style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 26.sp, letterSpacing = 1.sp))
            Text("Your clock, the line under it and your widgets.", modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp))

            GroupLabel("Clock")
            Card(R.drawable.ic_schedule, "Clock, date & home style", HomeStyleStore.get(context).bundle.title + " · " + clock.clockStyle.label) {
                closeThen { SeelieSettings.open(context, SeelieSettings.PAGE_CLOCK) }
            }

            GroupLabel("The line under the date")
            ToggleCard(R.drawable.ic_sunny, "Weather", "Temperature and sky, from your rough location", home.showWeather) { home.useWeather(it) }
            ToggleCard(R.drawable.ic_battery_full, "Battery percentage", "With a bolt while charging", home.showBattery) { home.useBattery(it) }

            GroupLabel("Widgets")
            val count = widgets.stack.size
            Card(R.drawable.ic_widgets, "Widget stack", if (count == 1) "1 widget · swipe sideways under the clock" else "$count widgets · swipe sideways under the clock") {
                closeThen { SeelieSettings.open(context, SeelieSettings.PAGE_WIDGETS) }
            }
            Card(R.drawable.ic_add_circle, "Add a widget from an app", "Spotify, Calendar, weather…") {
                closeThen { SeelieSettings.open(context, SeelieSettings.PAGE_WIDGET_PICKER) }
            }

            GroupLabel("Seelie")
            Card(R.drawable.ic_star, "Edit favourites", "Pick, search and reorder") { closeThen(onEditFavourites) }
            Card(R.drawable.ic_settings, "Seelie settings", "Themes, fonts, icons and more") { closeThen { SeelieSettings.open(context) } }
            Card(R.drawable.ic_home, "Switch Home app", "Pick Seelie or another launcher") { closeThen { openSafely(context, DefaultHome.settingsIntent()) } }
        }
    }
}

/** A card with a switch on the right, in the accent colour. Tapping anywhere on it flips the switch. */
@Composable
private fun ToggleCard(@DrawableRes icon: Int, title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .padding(bottom = 7.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable { onChange(!on) }
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = look.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp))
            Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.5.sp))
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = look.sheet.copy(alpha = 1f),
                checkedTrackColor = look.accent,
                uncheckedThumbColor = look.textDim,
                uncheckedTrackColor = look.card,
                uncheckedBorderColor = look.textDim.copy(alpha = 0.5f),
            ),
        )
    }
}
