package com.shamil.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shamil.launcher.ui.theme.LocalLauncherLook

/**
 * Real widgets from his other apps (Spotify, Google Calendar, a weather app…), shown in the widget stack.
 * Android calls an app that shows other apps' widgets a widget "host"; this is Seelie's host.
 * Each placed widget gets a number from Android, its app widget id, which the [WidgetStore] saves.
 */
object SeelieWidgetHost {
    /** Seelie's own number for its host (any fixed number; it must never change, or his widgets are lost). */
    private const val HOST_ID = 2026

    @Volatile private var host: AppWidgetHost? = null
    private var listeners = 0

    fun host(context: Context): AppWidgetHost =
        host ?: synchronized(this) { host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it } }

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)

    /**
     * Widgets only update while the host "listens". The home screen and Seelie settings both call [start] when they
     * come on screen and [stop] when they go away; it listens while at least one of them is visible.
     */
    @Synchronized
    fun start(context: Context) {
        if (listeners++ == 0) runCatching { host(context).startListening() }
    }

    @Synchronized
    fun stop(context: Context) {
        if (listeners > 0 && --listeners == 0) runCatching { host(context).stopListening() }
    }

    /** Every widget his apps offer, grouped later by app in the picker. */
    fun providers(context: Context): List<AppWidgetProviderInfo> =
        manager(context).installedProviders.filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }

    /** Forgets a placed widget (Android frees its number and the app stops updating it). */
    fun delete(context: Context, appWidgetId: Int) {
        runCatching { host(context).deleteAppWidgetId(appWidgetId) }
    }
}

/**
 * Shows one placed app widget at the size of its stack page. If its app was uninstalled, a short note instead
 * (he can remove it in Seelie settings).
 */
@Composable
fun AppWidgetPage(appWidgetId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val info = remember(appWidgetId) { SeelieWidgetHost.manager(context).getAppWidgetInfo(appWidgetId) }
    BoxWithConstraints(modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
        if (info == null) {
            Text(
                "This widget's app is gone. Remove it in Seelie settings → Widgets → Stack.",
                modifier = Modifier.padding(16.dp),
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp),
            )
            return@BoxWithConstraints
        }
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        AndroidView(
            factory = { ctx ->
                SeelieWidgetHost.host(ctx).createView(ctx.applicationContext, appWidgetId, info).apply { setPadding(0, 0, 0, 0) }
            },
            modifier = Modifier.fillMaxSize(),
            // Tell the widget how much room it has, so it can pick its own layout for that size.
            update = { view -> view.tellSize(widthDp, heightDp) },
        )
    }
}

private fun AppWidgetHostView.tellSize(widthDp: Float, heightDp: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
    } else {
        @Suppress("DEPRECATION")
        updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
    }
}
