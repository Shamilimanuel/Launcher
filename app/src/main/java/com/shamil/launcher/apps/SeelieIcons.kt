package com.shamil.launcher.apps

import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import com.shamil.launcher.ui.theme.LauncherLook

/**
 * Seelie's own icon styles (his pick, 23 Oct): Seelie redraws every app's icon itself, in the theme's colours,
 * so no icon pack is needed. They show in Seelie settings → Look → Icons next to the installed packs.
 */
object SeelieIcons {
    /** One-colour icons in the accent colour on a dark round plate, like Pixel's themed icons. */
    const val THEMED = "seelie:themed"
    /** The app's own icon on a frosted night plate with a gold (accent) ring, from the prototype. */
    const val GLASS = "seelie:glass"

    fun isStyle(id: String?) = id == THEMED || id == GLASS

    /** Name for the cache: the style plus the colours it was drawn in, so a theme switch redraws them. */
    fun cacheId(style: String, look: LauncherLook) = "$style#${look.accent.toArgb()}#${look.sheet.toArgb()}"

    fun draw(info: LauncherActivityInfo, style: String, sizePx: Int, look: LauncherLook): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val icon = info.getIcon(0)
        if (style == THEMED) drawThemed(canvas, icon, sizePx.toFloat(), look) else drawGlass(canvas, icon, sizePx.toFloat(), look)
        return bitmap
    }

    private fun drawThemed(canvas: Canvas, icon: Drawable, size: Float, look: LauncherLook) {
        val accent = look.accent.toArgb()
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.sheetTop.copy(alpha = 0.95f).toArgb() }
        canvas.drawCircle(size / 2, size / 2, size * 0.48f, plate)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; strokeWidth = size * 0.02f; color = look.accent.copy(alpha = 0.3f).toArgb()
        }
        canvas.drawCircle(size / 2, size / 2, size * 0.47f, ring)

        // Android 13+ apps can include a one-colour ("monochrome") version of their icon: tint that.
        val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) (icon as? AdaptiveIconDrawable)?.monochrome else null
        if (mono != null) {
            // Adaptive layers are 108 units with the real picture in the middle 72, so draw them 1.5× the plate.
            drawCentered(canvas, mono.mutate().apply { setTint(accent) }, size, size * 1.5f * 0.92f)
        } else {
            // No one-colour version: turn the normal icon into shades of the accent instead.
            val tint = ColorMatrix().apply { setSaturation(0f) }
            val r = ((accent shr 16) and 0xFF) / 255f
            val g = ((accent shr 8) and 0xFF) / 255f
            val b = (accent and 0xFF) / 255f
            tint.postConcat(ColorMatrix(floatArrayOf(r * 1.3f, 0f, 0f, 0f, 0f, 0f, g * 1.3f, 0f, 0f, 0f, 0f, 0f, b * 1.3f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            val d = icon.mutate().apply { colorFilter = ColorMatrixColorFilter(tint) }
            drawCentered(canvas, d, size, size * 0.62f)
        }
    }

    private fun drawGlass(canvas: Canvas, icon: Drawable, size: Float, look: LauncherLook) {
        val c = size / 2
        val r = size * 0.47f
        // The frosted night plate: lighter at the top-left, deep at the bottom.
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(c * 0.7f, c * 0.55f, r * 1.4f,
                look.sheetTop.copy(alpha = 1f).toArgb(), look.sheet.copy(alpha = 1f).toArgb(), Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(c, c, r, plate)

        // The app itself: its foreground only (no square background) when it's an adaptive icon, else the whole icon.
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(c, c, r * 0.96f, Path.Direction.CW) })
        if (icon is AdaptiveIconDrawable && icon.foreground != null) {
            drawCentered(canvas, icon.foreground.mutate(), size, size * 0.6f * 1.5f)
        } else {
            drawCentered(canvas, icon.mutate(), size, size * 0.6f)
        }
        canvas.restore()

        // Glass shine across the top half, then the gold ring.
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, c - r, 0f, c, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        canvas.drawOval(RectF(c - r * 0.82f, c - r * 0.96f, c + r * 0.82f, c + r * 0.1f), shine)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; strokeWidth = size * 0.035f
            color = look.accent.copy(alpha = 0.9f).toArgb()
        }
        canvas.drawCircle(c, c, r - size * 0.0175f, ring)
    }

    /** Draws [d] as a square of [box] pixels in the middle of a [size]-pixel canvas. */
    private fun drawCentered(canvas: Canvas, d: Drawable, size: Float, box: Float) {
        val left = ((size - box) / 2).toInt()
        d.setBounds(left, left, left + box.toInt(), left + box.toInt())
        d.draw(canvas)
    }
}
