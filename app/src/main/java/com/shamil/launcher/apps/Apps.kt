package com.shamil.launcher.apps

import com.shamil.launcher.ui.theme.LookStore
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.Normalizer

/** One app in the list: its name, icon, and what Android needs to open it. */
data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** When it was first installed (milliseconds), for "Recently installed". */
    val installedAt: Long,
    /** Which saved icon file belongs to it (see AppCache). */
    val iconKey: String,
) {
    /** The letter it's filed under in the A–Z list: "É" counts as "E", numbers and symbols go under "#". */
    val letter: String = label.firstOrNull()
        ?.let { Normalizer.normalize(it.toString(), Normalizer.Form.NFD).first().uppercaseChar() }
        ?.takeIf { it in 'A'..'Z' }
        ?.toString() ?: "#"

    /** A unique id per app, so the list can track rows as apps come and go. */
    val key: String get() = "${component.flattenToShortString()}@$user"
}

/** One of an app's shortcuts (see [Apps.shortcuts]). */
class AppShortcut(val label: String, val icon: ImageBitmap?, val info: ShortcutInfo)

/** Reading, opening and watching the phone's installed apps, through Android's LauncherApps service. */
object Apps {

    /**
     * Every app that has an icon in the app list, sorted A–Z the way the phone's language sorts.
     * Icons come from [cache] when they're already drawn; only new or updated apps get drawn again.
     */
    fun load(context: Context, cache: AppCache): List<AppEntry> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val iconPx = (48 * context.resources.displayMetrics.density).toInt()
        val collator = Collator.getInstance()
        // The icons he picked in Seelie settings → Icons: an installed pack (until he picks: the first one, his Darko),
        // one of Seelie's own styles, or null for the apps' own icons.
        val looks = LookStore.get(context)
        val choice = looks.activeIconPack(context)
        val look = looks.look
        val style = choice?.takeIf { SeelieIcons.isStyle(it) }
        val iconPackName = if (style != null) SeelieIcons.cacheId(style, look) else choice
        // Reading the pack's list takes a moment, so only do it if some icon actually needs drawing.
        val iconPack by lazy { if (style == null) choice?.let { IconPack.load(context, it) } else null }
        // "profiles" = you, plus a work profile or Secure Folder if the phone has one.
        val apps = launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            // Leave out Seelie itself (its settings are a window inside Seelie, reached from the • section).
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                val key = cache.keyFor(info, iconPackName)
                val icon = cache.icon(key)
                    ?: (if (style != null) SeelieIcons.draw(info, style, iconPx, look) else drawIcon(context, info, iconPack, iconPx))
                        .also { cache.saveIcon(key, it) }
                AppEntry(
                    label = info.label.toString(),
                    component = info.componentName,
                    user = info.user,
                    icon = icon.asImageBitmap(),
                    installedAt = info.firstInstallTime,
                    iconKey = key,
                )
            }
            .sortedWith(compareBy(collator) { it.label })
        cache.saveIndex(apps)
        return apps
    }

    /**
     * The pack's hand-made icon if it has one, else the app's own icon restyled the pack's way,
     * else (no pack) the app's own icon. The work-profile badge is added back on top.
     */
    fun drawIcon(context: Context, info: LauncherActivityInfo, iconPack: IconPack?, iconPx: Int): Bitmap {
        val packIcon = iconPack?.iconFor(info.componentName)
            ?: iconPack?.restyle(info.getIcon(0), info.componentName, iconPx)
                ?.let { BitmapDrawable(context.resources, it) }
        val icon = packIcon?.let { context.packageManager.getUserBadgedIcon(it, info.user) }
            ?: info.getBadgedIcon(0)
        return icon.toBitmap(iconPx, iconPx)
    }

    /**
     * Apps whose name matches what was typed, best first: names that start with it,
     * then names with a word that starts with it, then names that contain it. Accents and capitals don't matter.
     */
    fun search(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = query.trim().simplified()
        if (q.isEmpty()) return emptyList()
        return apps
            .mapNotNull { app ->
                val name = app.label.simplified()
                val rank = when {
                    name.startsWith(q) -> 0
                    name.split(' ').any { it.startsWith(q) } -> 1
                    q in name -> 2
                    else -> null
                }
                rank?.let { it to app }
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    /** "Galerij" and "galérij" both become "galerij". */
    private fun String.simplified() =
        Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** Opens [app]; a locked one asks for the code first (see [VaultStore.guard]). */
    fun launch(context: Context, app: AppEntry) =
        VaultStore.get(context).guard(app.component.packageName, app.label) { launchNow(context, app) }

    private fun launchNow(context: Context, app: AppEntry) {
        // A pinned shortcut (a website from "Add to home screen") opens through its app.
        if (app.shortcutId != null) return PinnedShortcuts.open(context, app)
        try {
            context.getSystemService(LauncherApps::class.java)
                .startMainActivity(app.component, app.user, null, null)
            // Counted for "Suggested" in Edit favourites (stays on the phone).
            LaunchStats.get(context).record(app.component.packageName)
        } catch (e: Exception) {
            // E.g. the app was uninstalled a moment ago.
            Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** The app's page in the phone's Settings (storage, permissions, uninstall). */
    fun openInfo(context: Context, app: AppEntry) {
        context.getSystemService(LauncherApps::class.java)
            .startAppDetailsActivity(app.component, app.user, null, null)
    }

    /**
     * The app's shortcuts, like WhatsApp's "New chat" or Maps' "Navigate home" (at most 5).
     * Returns null when we aren't the default Home app: Android only shares shortcuts with that one.
     */
    fun shortcuts(context: Context, app: AppEntry): List<AppShortcut>? {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (!launcherApps.hasShortcutHostPermission()) return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.component.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        val found = runCatching { launcherApps.getShortcuts(query, app.user) }.getOrNull().orEmpty()
        val iconPx = (28 * context.resources.displayMetrics.density).toInt()
        val dpi = context.resources.displayMetrics.densityDpi
        return found
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(5)
            .map { info ->
                AppShortcut(
                    label = (info.shortLabel ?: info.longLabel ?: "").toString(),
                    icon = runCatching { launcherApps.getShortcutIconDrawable(info, dpi) }.getOrNull()
                        ?.toBitmap(iconPx, iconPx)?.asImageBitmap(),
                    info = info,
                )
            }
    }

    fun openShortcut(context: Context, shortcut: AppShortcut) =
        VaultStore.get(context).guard(shortcut.info.`package`, shortcut.label) { openShortcutNow(context, shortcut) }

    private fun openShortcutNow(context: Context, shortcut: AppShortcut) {
        try {
            context.getSystemService(LauncherApps::class.java).startShortcut(shortcut.info, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open ${shortcut.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Apps that came with the phone (like YouTube on Samsung) can't be uninstalled, only disabled. */
    fun isSystemApp(context: Context, app: AppEntry): Boolean = try {
        val info = context.getSystemService(LauncherApps::class.java)
            .getApplicationInfo(app.component.packageName, 0, app.user)
        info.flags and ApplicationInfo.FLAG_SYSTEM != 0
    } catch (e: Exception) {
        false
    }

    /** Android's own "Uninstall this app?" dialog. */
    fun uninstall(context: Context, app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            openInfo(context, app)
        }
    }

    /** Digital Wellbeing's screen-time page for this app (Android 10+); falls back to App info. */
    fun openScreenTime(context: Context, app: AppEntry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent(Settings.ACTION_APP_USAGE_SETTINGS)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, app.component.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // No screen-time app on this phone.
            }
        }
        openInfo(context, app)
    }

    /** Calls [onChange] whenever an app is installed, removed or updated. Returns a function that stops watching. */
    fun watch(context: Context, onChange: () -> Unit): () -> Unit {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()
            override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()
            override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        return { launcherApps.unregisterCallback(callback) }
    }
}

/**
 * The installed apps as live state. Shows last time's saved list straight away, then checks
 * it against the phone in the background, and again whenever an app is installed, removed or updated.
 */
@Composable
fun rememberApps(): List<AppEntry> {
    val context = LocalContext.current
    val cache = remember { AppCache(context.applicationContext) }
    // Only one load at a time, so two loads never write the same files at once.
    val oneAtATime = remember { Mutex() }
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var version by remember { mutableIntStateOf(0) }
    // Picking another icon pack in Seelie settings redraws every icon (each pack has its own cached copies).
    // Seelie's own styles are drawn in the theme's colours, so for those a theme switch redraws the icons too.
    val looks = LookStore.get(context)
    val iconPack = looks.iconPack
    val styleTheme = if (SeelieIcons.isStyle(iconPack)) looks.themeId else ""
    LaunchedEffect(version, iconPack, styleTheme) {
        if (version == 0) {
            // Instant start: the list and icons saved last time.
            val saved = withContext(Dispatchers.IO) { cache.readIndex() }
            if (saved.isNotEmpty()) apps = saved
        } else {
            // App updates arrive as a burst of events; wait for the burst to end, then reload once.
            delay(300)
        }
        // Off the main (UI) thread, so scrolling stays smooth while this runs.
        apps = oneAtATime.withLock { withContext(Dispatchers.Default) { Apps.load(context, cache) } }
    }
    DisposableEffect(Unit) {
        val stop = Apps.watch(context) { version++ }
        onDispose { stop() }
    }
    return apps
}
