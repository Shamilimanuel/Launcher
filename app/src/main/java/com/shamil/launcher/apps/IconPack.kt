package com.shamil.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import kotlin.math.abs

/**
 * An installed icon pack (like Darko). Every pack ships an "appfilter.xml" that says:
 * - "for app X, use my drawing called Y" (the hand-made icons), and
 * - how to restyle apps it has no drawing for: a back plate, the app's own icon shrunk
 *   to [scale], a mask cut out of it, and an optional overlay on top.
 */
class IconPack private constructor(
    private val res: Resources,
    private val packageName: String,
    /** "com.whatsapp/com.whatsapp.Main" -> "whatsapp" (the pack's drawing name). */
    private val icons: Map<String, String>,
    private val backs: List<String>,
    private val masks: List<String>,
    private val overlays: List<String>,
    private val scale: Float,
) {
    /** The pack's hand-made icon for this app, or null if it doesn't have one. */
    fun iconFor(component: ComponentName): Drawable? =
        icons[component.flattenToString()]?.let { drawable(it) }

    /**
     * The app's own icon restyled the pack's way, e.g. Darko: dark round plate + icon at half size.
     * Returns null if the pack has no restyling rules.
     */
    fun restyle(original: Drawable, component: ComponentName, sizePx: Int): Bitmap? {
        if (backs.isEmpty() && masks.isEmpty() && overlays.isEmpty()) return null
        // The same app always gets the same plate (when a pack offers several).
        val pick = abs(component.packageName.hashCode())
        val result = createBitmap(sizePx, sizePx)
        val canvas = Canvas(result)

        // 1. The back plate.
        backs.pickFrom(pick)?.let { drawable(it) }?.let { canvas.drawFull(it, sizePx) }

        // 2. The app's icon, shrunk and centred, on its own layer so the mask only cuts the icon (not the plate).
        val layer = createBitmap(sizePx, sizePx)
        val layerCanvas = Canvas(layer)
        val inner = (sizePx * scale).toInt().coerceAtLeast(1)
        val offset = (sizePx - inner) / 2f
        layerCanvas.drawBitmap(original.toBitmap(inner, inner), offset, offset, null)
        // 3. The mask: wherever it's solid, the icon is erased ("DST_OUT" = cut out).
        masks.pickFrom(pick)?.let { drawable(it) }?.let { mask ->
            val cut = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            layerCanvas.drawBitmap(mask.toBitmap(sizePx, sizePx), 0f, 0f, cut)
        }
        canvas.drawBitmap(layer, 0f, 0f, null)

        // 4. The overlay on top (shine, frame), if the pack has one.
        overlays.pickFrom(pick)?.let { drawable(it) }?.let { canvas.drawFull(it, sizePx) }
        return result
    }

    private fun List<String>.pickFrom(seed: Int): String? = if (isEmpty()) null else this[seed % size]

    private fun Canvas.drawFull(d: Drawable, sizePx: Int) {
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(this)
    }

    private fun drawable(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", packageName)
            .takeIf { it != 0 } ?: res.getIdentifier(name, "mipmap", packageName)
        if (id == 0) return null
        return try {
            ResourcesCompat.getDrawable(res, id, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    companion object {
        /** The "I'm an icon pack" labels packs put on themselves (ADW and Nova are the common standards). */
        private val PACK_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")

        /** Package names of every icon pack on the phone. */
        fun installed(context: Context): List<String> =
            PACK_ACTIONS
                .flatMap { context.packageManager.queryIntentActivities(Intent(it), PackageManager.GET_META_DATA) }
                .map { it.activityInfo.packageName }
                .distinct()

        /** Reads a pack's appfilter.xml. Returns null if the pack is gone or has no list. */
        fun load(context: Context, packageName: String): IconPack? {
            val res = try {
                context.packageManager.getResourcesForApplication(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                return null
            }
            // The list lives either in res/xml or in assets, depending on the pack.
            val parser: XmlPullParser = res.getIdentifier("appfilter", "xml", packageName)
                .takeIf { it != 0 }
                ?.let { res.getXml(it) }
                ?: try {
                    Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), null) }
                } catch (e: Exception) {
                    return null
                }

            val icons = HashMap<String, String>()
            val backs = mutableListOf<String>()
            val masks = mutableListOf<String>()
            val overlays = mutableListOf<String>()
            var scale = 1f
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    // e.g. <item component="ComponentInfo{com.whatsapp/com.whatsapp.Main}" drawable="whatsapp"/>
                    "item" -> {
                        val component = parser.getAttributeValue(null, "component") ?: continue
                        val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                        val inner = component.removePrefix("ComponentInfo{").removeSuffix("}")
                        ComponentName.unflattenFromString(inner)?.let { icons[it.flattenToString()] = drawable }
                    }
                    // e.g. <iconback img1="iconback" img2="iconback2"/>: every attribute is one image.
                    "iconback" -> backs += parser.allAttributeValues()
                    "iconmask" -> masks += parser.allAttributeValues()
                    "iconupon" -> overlays += parser.allAttributeValues()
                    "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                }
            }
            // Packs sometimes list images they don't actually include (Darko lists 11 plates, ships 1), so keep only real ones.
            fun exists(name: String) =
                res.getIdentifier(name, "drawable", packageName) != 0 || res.getIdentifier(name, "mipmap", packageName) != 0
            return IconPack(
                res, packageName, icons,
                backs.filter(::exists), masks.filter(::exists), overlays.filter(::exists), scale,
            )
        }

        private fun XmlPullParser.allAttributeValues() = (0 until attributeCount).map { getAttributeValue(it) }
    }
}
