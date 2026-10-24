package com.shamil.launcher.ui.theme

import com.shamil.launcher.ui.vault.PinLookStore
import com.shamil.launcher.ui.rail.RailStore
import com.shamil.launcher.ui.home.HomeLayoutStore
import com.shamil.launcher.ui.livebg.LiveBgStore
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.shamil.launcher.ui.clock.ClockStore
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.StatsStore
import com.shamil.launcher.ui.media.MusicPrefs
import com.shamil.launcher.ui.toggles.TogglePrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Saved looks (his idea, 23 Oct: "add saves, so you can save that style and have a wallpaper for a different save").
 * A save is a snapshot of the look settings (theme, font, text colours, icons, home style, clock, stats card,
 * music card and toggles styling), plus optionally a wallpaper picture. Applying one restores it all at once.
 * Which widgets are in the stack is NOT part of a save (swapping app widgets around could break them).
 */
data class SavedLook(
    val id: String,
    val name: String,
    val createdAt: Long,
    /** What the look settings files held: file name → (key → typed value). */
    val snapshot: JSONObject,
    /** For the preview card. */
    val accent: Int,
    val sheet: Int,
    val themeName: String,
)

class SavesStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("saves", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "saves").apply { mkdirs() }

    var saves by mutableStateOf(load())
        private set

    /** Saves the current look under [name]. */
    fun saveCurrent(name: String): SavedLook {
        val look = LookStore.get(context)
        val current = look.look
        val save = SavedLook(
            id = UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "My look" },
            createdAt = System.currentTimeMillis(),
            snapshot = snapshot(),
            accent = current.accent.toArgb(),
            sheet = current.sheet.toArgb(),
            themeName = current.name,
        )
        persist(saves + save)
        // The wallpapers he picked through Seelie (Android won't let us read them otherwise), else try the phone's own.
        Thread {
            val wallpapers = WallpaperStore.get(context)
            wallpapers.copyLockInto(lockWallpaperFile(save))
            if (wallpapers.copyInto(wallpaperFile(save)) || copySystemWallpaper(save)) persist(saves)
        }.start()
        return save
    }

    /**
     * Copies the phone's current wallpaper into a save, if Android lets Seelie read it (newer Android versions often
     * don't; then the save shows its colours until he adds a picture). Off the main thread.
     */
    private fun copySystemWallpaper(save: SavedLook): Boolean = runCatching {
        val drawable = WallpaperManager.getInstance(context).drawable ?: return@runCatching false
        val metrics = context.resources.displayMetrics
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: metrics.widthPixels
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: metrics.heightPixels
        val k = minOf(1f, 1440f / maxOf(w, h))
        val bitmap = Bitmap.createBitmap((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(android.graphics.Canvas(bitmap))
        wallpaperFile(save).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    }.getOrDefault(false)

    /**
     * Overwrites a save with the current look, wallpapers included (the ones he picked through Seelie; a save keeps
     * its own pictures if there's no copy). Keeps its name.
     */
    fun update(save: SavedLook) {
        Thread {
            val wallpapers = WallpaperStore.get(context)
            val home = wallpapers.copyInto(wallpaperFile(save))
            val lock = wallpapers.copyLockInto(lockWallpaperFile(save))
            if (home || lock) persist(saves)
        }.start()
        val current = LookStore.get(context).look
        persist(saves.map {
            if (it.id == save.id) it.copy(snapshot = snapshot(), accent = current.accent.toArgb(), sheet = current.sheet.toArgb(), themeName = current.name)
            else it
        })
    }

    fun rename(save: SavedLook, name: String) = persist(saves.map { if (it.id == save.id) it.copy(name = name.ifBlank { it.name }) else it })

    fun delete(save: SavedLook) {
        wallpaperFile(save).delete()
        lockWallpaperFile(save).delete()
        persist(saves.filter { it.id != save.id })
    }

    /** The picture kept for this save (may not exist). */
    fun wallpaperFile(save: SavedLook) = File(dir, "${save.id}.jpg")
    fun hasWallpaper(save: SavedLook) = wallpaperFile(save).exists()
    /** The lock screen picture kept for this save (may not exist). */
    fun lockWallpaperFile(save: SavedLook) = File(dir, "${save.id}-lock.jpg")

    /**
     * Keeps a copy of the picked picture for this save, shrunk to about screen size (run off the main thread).
     * Returns false if it couldn't read the picture.
     */
    fun setWallpaper(save: SavedLook, uri: Uri): Boolean = runCatching {
        val bitmap = WallpaperStore.decodeForScreen(context, uri) ?: return@runCatching false
        wallpaperFile(save).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        persist(saves)   // nudges the screen to show the new thumbnail
        true
    }.getOrDefault(false)

    fun removeWallpaper(save: SavedLook) {
        wallpaperFile(save).delete()
        lockWallpaperFile(save).delete()
        persist(saves)
    }

    /**
     * Applies a save: writes its settings back, tells every part to reload (the look glides over), and sets its
     * wallpaper if it has one ([withWallpaper]). The wallpaper part is slow-ish: call this off the main thread.
     */
    fun apply(save: SavedLook, withWallpaper: Boolean = true) {
        restore(save.snapshot)
        // A save swaps the wallpapers too (his ask: like switching a whole setup with one script).
        if (withWallpaper) {
            val home = wallpaperFile(save).takeIf { it.exists() }
            val lock = lockWallpaperFile(save).takeIf { it.exists() }
            runCatching {
                val manager = WallpaperManager.getInstance(context)
                home?.inputStream()?.use { manager.setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) }
                lock?.inputStream()?.use { manager.setStream(it, null, true, WallpaperManager.FLAG_LOCK) }
            }
            if (home != null || lock != null) WallpaperStore.get(context).keep(home, lock)
        }
    }

    /** Reloads every look store from its settings file (on the main thread: they're Compose state). */
    fun reloadStores() {
        LookStore.get(context).reload()
        HomeStyleStore.get(context).reload()
        ClockStore.get(context).reload()
        StatsStore.get(context).reload()
        MusicPrefs.get(context).reload()
        TogglePrefs.get(context).reload()
        LiveBgStore.get(context).reload()
        HomeLayoutStore.get(context).reload()
        RailStore.get(context).reload()
        PinLookStore.get(context).reload()
    }

    /** What the look settings hold right now, in a save's snapshot shape (read only: the previews compare against it). */
    fun currentSnapshot(): JSONObject = snapshot()

    /** Every look settings file, entry by entry, with its type so it can be put back exactly. */
    private fun snapshot(): JSONObject = JSONObject().apply {
        for (file in FILES) {
            val entries = JSONObject()
            context.getSharedPreferences(file, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                val typed = when (value) {
                    is String -> JSONObject().put("t", "s").put("v", value)
                    is Boolean -> JSONObject().put("t", "b").put("v", value)
                    is Int -> JSONObject().put("t", "i").put("v", value)
                    is Long -> JSONObject().put("t", "l").put("v", value)
                    is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                    is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.map { it.toString() }))
                    else -> null
                }
                if (typed != null) entries.put(key, typed)
            }
            put(file, entries)
        }
    }

    private fun restore(snapshot: JSONObject) {
        for (file in FILES) {
            val entries = snapshot.optJSONObject(file) ?: continue
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit(commit = true) {
                clear()
                entries.keys().forEach { key ->
                    val o = entries.getJSONObject(key)
                    when (o.getString("t")) {
                        "s" -> putString(key, o.getString("v"))
                        "b" -> putBoolean(key, o.getBoolean("v"))
                        "i" -> putInt(key, o.getInt("v"))
                        "l" -> putLong(key, o.getLong("v"))
                        "f" -> putFloat(key, o.getDouble("v").toFloat())
                        "set" -> {
                            val arr = o.getJSONArray("v")
                            putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                        }
                    }
                }
            }
        }
    }

    private fun persist(list: List<SavedLook>) {
        saves = list.toList()
        prefs.edit {
            putString(KEY, JSONArray(list.map {
                JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt).put("snapshot", it.snapshot)
                    .put("accent", it.accent).put("sheet", it.sheet).put("themeName", it.themeName)
            }).toString())
        }
    }

    private fun load(): List<SavedLook> = try {
        val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedLook(o.getString("id"), o.getString("name"), o.optLong("createdAt"), o.optJSONObject("snapshot") ?: JSONObject(),
                o.optInt("accent"), o.optInt("sheet"), o.optString("themeName"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val KEY = "list"
        /** The settings files a save captures (only looks, never his apps, notes, vault or widgets). */
        private val FILES = listOf("look", "home_style", "clock", "stats_card", "music_card", "quick_toggles", LiveBgStore.PREFS, HomeLayoutStore.PREFS, RailStore.PREFS, PinLookStore.PREFS)
        @Volatile private var instance: SavesStore? = null

        fun get(context: Context): SavesStore =
            instance ?: synchronized(this) { instance ?: SavesStore(context.applicationContext).also { instance = it } }
    }
}
