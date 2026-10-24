package com.shamil.launcher.ui.theme

import com.shamil.launcher.ui.livebg.LiveBgStore
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        Thread { if (copySystemWallpaper(save)) persist(saves) }.start()
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

    /** Overwrites a save with the current look (keeps its name and wallpaper). */
    fun update(save: SavedLook) {
        val current = LookStore.get(context).look
        persist(saves.map {
            if (it.id == save.id) it.copy(snapshot = snapshot(), accent = current.accent.toArgb(), sheet = current.sheet.toArgb(), themeName = current.name)
            else it
        })
    }

    fun rename(save: SavedLook, name: String) = persist(saves.map { if (it.id == save.id) it.copy(name = name.ifBlank { it.name }) else it })

    fun delete(save: SavedLook) {
        wallpaperFile(save).delete()
        persist(saves.filter { it.id != save.id })
    }

    /** The picture kept for this save (may not exist). */
    fun wallpaperFile(save: SavedLook) = File(dir, "${save.id}.jpg")
    fun hasWallpaper(save: SavedLook) = wallpaperFile(save).exists()

    /**
     * Keeps a copy of the picked picture for this save, shrunk to about screen size (run off the main thread).
     * Returns false if it couldn't read the picture.
     */
    fun setWallpaper(save: SavedLook, uri: Uri): Boolean = runCatching {
        val metrics = context.resources.displayMetrics
        val maxSide = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1080)
        // First only the size, then decode just big enough (big photos would eat memory).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching false
        wallpaperFile(save).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        persist(saves)   // nudges the screen to show the new thumbnail
        true
    }.getOrDefault(false)

    fun removeWallpaper(save: SavedLook) {
        wallpaperFile(save).delete()
        persist(saves)
    }

    /**
     * Applies a save: writes its settings back, tells every part to reload (the look glides over), and sets its
     * wallpaper if it has one ([withWallpaper]). The wallpaper part is slow-ish: call this off the main thread.
     */
    fun apply(save: SavedLook, withWallpaper: Boolean = true) {
        restore(save.snapshot)
        if (withWallpaper && hasWallpaper(save)) {
            runCatching {
                wallpaperFile(save).inputStream().use {
                    WallpaperManager.getInstance(context).setStream(it, null, true, WallpaperManager.FLAG_SYSTEM)
                }
            }
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
    }

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
        private val FILES = listOf("look", "home_style", "clock", "stats_card", "music_card", "quick_toggles", LiveBgStore.PREFS)
        @Volatile private var instance: SavesStore? = null

        fun get(context: Context): SavesStore =
            instance ?: synchronized(this) { instance ?: SavesStore(context.applicationContext).also { instance = it } }
    }
}
