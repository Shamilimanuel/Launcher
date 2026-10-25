package com.shamil.launcher.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject

/*
 * Seelie's own wallpapers (25 Oct: "just put in your recommendations"). 30 real pictures, each licence-checked:
 * space photos from ESO, ESA/Webb, ESA/Hubble and NASA, and old prints and paintings from museums' open collections.
 * They live in the app's assets (wallpapers/<id>.jpg, phone-shaped 1080 × 2340 where the original allowed it, plus a
 * small copy in wallpapers/thumbs/), described by wallpapers/catalog.json: the folder, title, artist, date, museum,
 * licence and the exact credit line the source asks for.
 */

/** One bundled wallpaper and where it comes from. [credit] is the source's own credit line: shown exactly as written. */
class SeelieWallpaper(
    val id: String,
    val folder: String,
    val title: String,
    val artist: String,
    val date: String,
    val institution: String,
    val credit: String,
    /** The museum's or agency's page for this picture. */
    val record: String,
    /** The licence in a few words ("CC BY 4.0", "Public domain (CC0)"). */
    val licence: String,
    /** True when the licence asks for the credit to be shown (CC BY): it then sits on the preview too. */
    val attribution: Boolean,
) {
    val asset: String get() = "wallpapers/$id.jpg"
    val thumbAsset: String get() = "wallpapers/thumbs/$id.jpg"
}

/** A folder of wallpapers: its name, the three pictures fanned on its cover, and what's inside, in order. */
class WallpaperFolder(val id: String, val name: String, val cover: List<String>, val items: List<SeelieWallpaper>) {
    /** A short name for the page title (the long one doesn't fit the big Cinzel heading). */
    val shortName: String get() = SHORT_NAMES[id] ?: name
}

private val SHORT_NAMES = mapOf(
    SeelieWallpapers.NIGHT_SKY to "Night sky",
    SeelieWallpapers.FANTASY to "Fantasy",
    SeelieWallpapers.NATURE to "Nature & art",
    SeelieWallpapers.LOFI_RAIN to "Lo-fi & rain",
)

object SeelieWallpapers {
    /** The folder ids (also used by the settings search to open a folder straight away). */
    const val NIGHT_SKY = "night-sky"
    const val FANTASY = "fantasy"
    const val NATURE = "nature"
    const val LOFI_RAIN = "lofi-rain"

    @Volatile private var folders: List<WallpaperFolder>? = null

    /** Every folder, in his order. Read from the catalog once (it's small: 14 KB). */
    fun folders(context: Context): List<WallpaperFolder> = folders ?: synchronized(this) {
        folders ?: read(context).also { folders = it }
    }

    fun folder(context: Context, id: String): WallpaperFolder? = folders(context).firstOrNull { it.id == id }

    fun all(context: Context): List<SeelieWallpaper> = folders(context).flatMap { it.items }

    fun find(context: Context, id: String?): SeelieWallpaper? = id?.let { i -> all(context).firstOrNull { it.id == i } }

    private fun read(context: Context): List<WallpaperFolder> = runCatching {
        val json = JSONObject(context.assets.open("wallpapers/catalog.json").bufferedReader().use { it.readText() })
        val walls = json.getJSONArray("wallpapers").let { a ->
            List(a.length()) { i ->
                val w = a.getJSONObject(i)
                SeelieWallpaper(
                    id = w.getString("id"), folder = w.getString("folder"), title = w.getString("title"),
                    artist = w.getString("artist"), date = w.getString("date"), institution = w.getString("institution"),
                    credit = w.getString("credit"), record = w.getString("record"), licence = w.getString("licence"),
                    attribution = w.optBoolean("attribution"),
                )
            }
        }
        json.getJSONArray("folders").let { a ->
            List(a.length()) { i ->
                val f = a.getJSONObject(i)
                val id = f.getString("id")
                val cover = f.getJSONArray("cover").let { c -> List(c.length()) { c.getString(it) } }
                WallpaperFolder(id, f.getString("name"), cover, walls.filter { it.folder == id })
            }
        }
    }.getOrDefault(emptyList())

    // The small copies for the folder covers and grids, kept while settings is open (30 × ~0.25 MB as RGB_565).
    private val thumbs = LruCache<String, ImageBitmap>(40)

    /** A thumbnail already in memory (so a grid that opens again shows it at once), or null. */
    fun cachedThumb(id: String): ImageBitmap? = thumbs.get(id)

    /** The small copy (240 px wide). Slow the first time: off the main thread. */
    fun thumb(context: Context, wallpaper: SeelieWallpaper): ImageBitmap? = thumbs.get(wallpaper.id) ?: runCatching {
        context.assets.open(wallpaper.thumbAsset).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
        }?.asImageBitmap()?.also { thumbs.put(wallpaper.id, it) }
    }.getOrNull()

    /** The full picture, shrunk to about [width] pixels wide (for the preview). Off the main thread. */
    fun picture(context: Context, wallpaper: SeelieWallpaper, width: Int): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(wallpaper.asset).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        context.assets.open(wallpaper.asset).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }?.asImageBitmap()
    }.getOrNull()
}
