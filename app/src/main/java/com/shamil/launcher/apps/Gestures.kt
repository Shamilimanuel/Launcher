package com.shamil.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONObject

/**
 * What swiping an app to the LEFT does (his locked feature #5, 23 Oct): one action per app, picked by him.
 * Swiping RIGHT always opens the app's shortcuts, like Niagara.
 */
sealed interface SwipeAction {
    /** What the reveal behind the row and the chooser say: "App info", "New chat", "Open YouTube Music". */
    val label: String

    data object Info : SwipeAction { override val label = "App info" }
    data object ScreenTime : SwipeAction { override val label = "Screen time" }
    /** One of the app's own shortcuts, found again by its [id] when he swipes. */
    data class Shortcut(val id: String, override val label: String) : SwipeAction
    /** Open another app ([component] = its "package/activity" name). */
    data class OpenApp(val component: String, val appLabel: String) : SwipeAction {
        override val label get() = "Open $appLabel"
    }
}

/** Remembers each app's swipe-left action (saved on the phone, Compose state, one shared store). */
class GestureStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("gestures", Context.MODE_PRIVATE)

    /** Package name → action. */
    var actions by mutableStateOf(load())
        private set

    fun actionFor(packageName: String): SwipeAction? = actions[packageName]

    /** Sets (or with null, clears) the swipe-left action for one app. */
    fun set(packageName: String, action: SwipeAction?) {
        actions = if (action == null) actions - packageName else actions + (packageName to action)
        prefs.edit { putString(KEY, JSONObject().apply { actions.forEach { (pkg, a) -> put(pkg, a.toJson()) } }.toString()) }
    }

    private fun SwipeAction.toJson(): JSONObject = JSONObject().apply {
        when (val a = this@toJson) {
            SwipeAction.Info -> put("type", "info")
            SwipeAction.ScreenTime -> put("type", "screen_time")
            is SwipeAction.Shortcut -> { put("type", "shortcut"); put("id", a.id); put("label", a.label) }
            is SwipeAction.OpenApp -> { put("type", "open_app"); put("component", a.component); put("label", a.appLabel) }
        }
    }

    private fun load(): Map<String, SwipeAction> = try {
        val root = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        root.keys().asSequence().mapNotNull { pkg ->
            val o = root.getJSONObject(pkg)
            val action = when (o.optString("type")) {
                "info" -> SwipeAction.Info
                "screen_time" -> SwipeAction.ScreenTime
                "shortcut" -> SwipeAction.Shortcut(o.getString("id"), o.optString("label", "Shortcut"))
                "open_app" -> SwipeAction.OpenApp(o.getString("component"), o.optString("label", "app"))
                else -> null
            }
            action?.let { pkg to it }
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    companion object {
        private const val KEY = "swipe_left"
        @Volatile private var instance: GestureStore? = null

        fun get(context: Context): GestureStore =
            instance ?: synchronized(this) { instance ?: GestureStore(context.applicationContext).also { instance = it } }

        /** Does the action for [app]. Returns false if it can't any more (shortcut or app gone). */
        fun perform(context: Context, app: AppEntry, action: SwipeAction): Boolean = try {
            when (action) {
                SwipeAction.Info -> { Apps.openInfo(context, app); true }
                SwipeAction.ScreenTime -> { Apps.openScreenTime(context, app); true }
                is SwipeAction.Shortcut -> {
                    val shortcut = Apps.shortcuts(context, app)?.firstOrNull { it.info.id == action.id }
                    if (shortcut != null) { Apps.openShortcut(context, shortcut); true } else false
                }
                is SwipeAction.OpenApp -> {
                    val component = ComponentName.unflattenFromString(action.component) ?: return false
                    context.getSystemService(LauncherApps::class.java).startMainActivity(component, Process.myUserHandle(), null, null)
                    LaunchStats.get(context).record(component.packageName)
                    true
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
