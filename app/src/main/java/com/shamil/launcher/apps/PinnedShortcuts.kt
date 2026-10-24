package com.shamil.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Pinned shortcuts in the favourites (24 Oct: his Niagara "Roadmap - Fin", a website added from the browser).
 * When a browser does "Add to home screen", Android asks the home app (us) to accept it ([PinShortcutActivity]);
 * Seelie then keeps it as a favourite with the key "shortcut:<package>/<id>", next to the normal package names.
 * In the lists a shortcut is an ordinary AppEntry whose class name starts with [SHORTCUT_CLASS], so it can be
 * shown, reordered and opened like any app.
 */

/** Start of a favourite key that means a pinned shortcut instead of an app. */
const val SHORTCUT_KEY = "shortcut:"
/** Marks a shortcut's (made-up) class name inside an AppEntry. */
private const val SHORTCUT_CLASS = "#shortcut:"

/** The shortcut id if this entry is a pinned shortcut, else null. */
val AppEntry.shortcutId: String? get() = component.className.takeIf { it.startsWith(SHORTCUT_CLASS) }?.removePrefix(SHORTCUT_CLASS)

/** What the favourites list stores for this entry: a package name, or "shortcut:<package>/<id>". */
val AppEntry.favouriteKey: String get() = shortcutId?.let { "$SHORTCUT_KEY${component.packageName}/$it" } ?: component.packageName

object PinnedShortcuts {
    /** Goes up whenever a shortcut is pinned or removed, so the home reads the list again. */
    var version by mutableIntStateOf(0)
        private set

    fun changed() { version++ }

    fun keyFor(info: ShortcutInfo) = "$SHORTCUT_KEY${info.`package`}/${info.id}"

    /**
     * Every shortcut pinned to Seelie, as entries for the lists. Android only shares these with the default home app,
     * so this is empty otherwise. Off the main thread (icons are drawn here).
     */
    fun load(context: Context): List<AppEntry> = runCatching {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (!launcherApps.hasShortcutHostPermission()) return@runCatching emptyList()
        val query = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val density = context.resources.displayMetrics.densityDpi
        val px = (44 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty().map { info ->
            val icon = runCatching { launcherApps.getShortcutIconDrawable(info, density)?.toBitmap(px, px) }.getOrNull()
                ?: context.packageManager.getApplicationIcon(info.`package`).toBitmap(px, px)
            AppEntry(
                label = (info.shortLabel ?: info.longLabel ?: info.id).toString(),
                component = ComponentName(info.`package`, SHORTCUT_CLASS + info.id),
                user = info.userHandle,
                icon = icon.asImageBitmap(),
                installedAt = 0L,
                iconKey = keyFor(info),
            )
        }
    }.getOrDefault(emptyList())

    /** Opens a pinned shortcut (the website, the chat...). */
    fun open(context: Context, entry: AppEntry) {
        val id = entry.shortcutId ?: return
        try {
            context.getSystemService(LauncherApps::class.java).startShortcut(entry.component.packageName, id, null, null, entry.user)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Lets go of a shortcut (removed from the favourites): Android forgets it was pinned to Seelie. */
    fun unpin(context: Context, entry: AppEntry) {
        val id = entry.shortcutId ?: return
        runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val pkg = entry.component.packageName
            val query = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val keep = launcherApps.getShortcuts(query, entry.user).orEmpty().map { it.id }.filter { it != id }
            launcherApps.pinShortcuts(pkg, keep, entry.user)
        }
        changed()
    }
}

/** The pinned shortcuts, read again whenever one is added or removed. */
@Composable
fun rememberPinnedShortcuts(): List<AppEntry> {
    val context = LocalContext.current
    val version = PinnedShortcuts.version
    val list by produceState(emptyList<AppEntry>(), version) {
        value = withContext(Dispatchers.IO) { PinnedShortcuts.load(context.applicationContext) }
    }
    return list
}
