package com.shamil.launcher.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.io.File

/**
 * His wallpaper, picked through Seelie (24 Oct: "add your own wallpaper and the colours adapt to it").
 * Newer Android doesn't let a launcher READ the wallpaper, so the theme previews had only colours. Picking it here,
 * Seelie keeps its own copy: every preview and new save can show the real picture.
 */
class WallpaperStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val file = File(File(context.filesDir, "wallpaper").apply { mkdirs() }, "current.jpg")
    private val lockFile = File(file.parentFile, "lock.jpg")

    /** Goes up whenever the copy changes, so previews reload it (0 = no copy). */
    var version by mutableIntStateOf(if (file.exists() || lockFile.exists()) 1 else 0)
        private set

    /** Where a new wallpaper goes: "home", "lock" or "both". */
    var target by androidx.compose.runtime.mutableStateOf(prefs.getString(KEY_TARGET, "both") ?: "both")
        private set

    fun useTarget(t: String) {
        target = t
        prefs.edit { putString(KEY_TARGET, t) }
    }

    /** The theme he had before "Follow wallpaper", to go back to with "Keep my theme". */
    val themeBefore: String? get() = prefs.getString(KEY_THEME_BEFORE, null)

    fun rememberTheme(id: String) = prefs.edit { putString(KEY_THEME_BEFORE, id) }

    /** Seelie's copy of the wallpaper, or null if he hasn't picked one through Seelie yet. Reading [version] first keeps it live. */
    fun copyFile(): File? = file.takeIf { version > 0 && it.exists() }

    /** Seelie's copy of the lock screen picture (when he picked one through Seelie). */
    fun lockCopyFile(): File? = lockFile.takeIf { version > 0 && it.exists() }

    /** Sets the picked picture as his wallpaper (on [target]) and keeps a copy. Slow: off the main thread. */
    fun setFromUri(uri: Uri): Boolean = runCatching {
        val bitmap = decodeForScreen(context, uri) ?: return@runCatching false
        val picked = File(file.parentFile, "picked.jpg")
        picked.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val flags = when (target) {
            "home" -> WallpaperManager.FLAG_SYSTEM
            "lock" -> WallpaperManager.FLAG_LOCK
            else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        picked.inputStream().use { WallpaperManager.getInstance(context).setStream(it, null, true, flags) }
        // Keep a copy of each screen it went on (the previews show the home one; a save swaps both).
        if (target != "home") picked.copyTo(lockFile, overwrite = true)
        if (target != "lock") picked.copyTo(file, overwrite = true)
        picked.delete()
        version++
        true
    }.getOrDefault(false)

    /** A save with its own pictures was applied: those are the wallpapers now, so they become the copies. */
    fun keep(home: File?, lock: File?) {
        runCatching {
            home?.takeIf { it.exists() }?.copyTo(file, overwrite = true)
            lock?.takeIf { it.exists() }?.copyTo(lockFile, overwrite = true)
            version++
        }
    }

    /** Copies Seelie's home copy into [to] (for a save). False if there's no copy. */
    fun copyInto(to: File): Boolean = runCatching { file.exists() && file.copyTo(to, overwrite = true).exists() }.getOrDefault(false)

    /** Copies Seelie's lock screen copy into [to]. False if there's none. */
    fun copyLockInto(to: File): Boolean = runCatching { lockFile.exists() && lockFile.copyTo(to, overwrite = true).exists() }.getOrDefault(false)

    companion object {
        const val PREFS = "wallpaper"
        private const val KEY_TARGET = "target"
        private const val KEY_THEME_BEFORE = "theme_before"
        @Volatile private var instance: WallpaperStore? = null

        fun get(context: Context): WallpaperStore =
            instance ?: synchronized(this) { instance ?: WallpaperStore(context.applicationContext).also { instance = it } }

        /**
         * Reads a picked picture, shrunk to about screen size (big photos would eat memory) and turned the right way up
         * (phone photos store their rotation separately; Android 9+ reads it, older phones get it as stored).
         */
        fun decodeForScreen(context: Context, uri: Uri): Bitmap? = runCatching {
            val metrics = context.resources.displayMetrics
            val maxSide = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1080)
            fun sampleFor(w: Int, h: Int): Int {
                var sample = 1
                while (maxOf(w, h) / (sample * 2) >= maxSide) sample *= 2
                return sample
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.setTargetSampleSize(sampleFor(info.size.width, info.size.height))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE   // a normal bitmap, so it can be saved as JPEG
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight) })
                }
            }
        }.getOrNull()
    }
}
