package com.shamil.launcher.ui.rail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.core.content.edit

/*
 * Seelie's own A–Z rail (24 Oct): "we have something from Niagara like the A–Z but we don't have our very own".
 * He played 10 rails on the ideas page and picked 9, in this order. Guide on the arc is Seelie's signature (the
 * default); the Niagara wave stays as "Today". Styles not built yet are marked so settings can say "coming".
 * The ids are what gets saved, so never rename them.
 */
enum class RailStyle(val id: String, val title: String, val about: String, val built: Boolean) {
    ARC_GUIDE("arcguide", "Guide on the arc", "Letters on a curve where your thumb rests; a seelie guide rides along with its light trail.", true),
    GUIDE("guide", "Seelie guide", "The rail on the edge; a seelie rides under your thumb and lights the letters up.", true),
    THUMB_ARC("arc", "Thumb arc", "Letters on a curve at the bottom right; the ones near your thumb bulge out.", true),
    FAN("fan", "Petal fan", "Touch a letter and its apps bloom beside your thumb; slide onto one to open it.", true),
    CONSTELLATION("constellation", "Constellation", "The letters are stars; sliding draws constellation lines.", true),
    LANTERN("lantern", "Lanterns", "Letters hang like paper lanterns; the one under your thumb glows and its neighbours swing.", true),
    GLIDER("glider", "Wind glider", "Like gliding: the letter lifts and its neighbours flow out on a wind of light.", true),
    WHEEL("wheel", "Element wheel", "Hold the edge and a dial of letters opens around your thumb; turn to choose.", true),
    NIAGARA("niagara", "Today (Niagara-style)", "The wave you had before: letters bend out towards your thumb.", true);

    /** The styles drawn on the thumb arc (their touch is the home's arcRailInput). */
    val onArc: Boolean get() = this == ARC_GUIDE || this == THUMB_ARC

    companion object {
        fun byId(id: String?): RailStyle = entries.firstOrNull { it.id == id && it.built } ?: ARC_GUIDE
    }
}

class RailStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var style by mutableStateOf(RailStyle.byId(prefs.getString(KEY, null)))
        private set

    init { RailSide.left = prefs.getBoolean(KEY_LEFT, false) }

    fun use(s: RailStyle) {
        style = s
        prefs.edit { putString(KEY, s.id) }
    }

    /** Left-hand mode: the rail on the left edge. Part of a save, like the style. */
    fun useLeft(left: Boolean) {
        RailSide.left = left
        prefs.edit { putBoolean(KEY_LEFT, left) }
    }

    /** Reads it again (after a saved theme was applied). */
    fun reload() {
        style = RailStyle.byId(prefs.getString(KEY, null))
        RailSide.left = prefs.getBoolean(KEY_LEFT, false)
    }

    companion object {
        const val PREFS = "rail"
        private const val KEY = "style"
        private const val KEY_LEFT = "left"
        @Volatile private var instance: RailStore? = null

        fun get(context: Context): RailStore =
            instance ?: synchronized(this) { instance ?: RailStore(context.applicationContext).also { instance = it } }
    }
}

/**
 * Left-hand mode (his ask, 24 Oct: "sometimes I hold my phone in my left hand, use the A–Z on the left like
 * Niagara"). Every rail is drawn for the right edge; on the left it's simply mirrored (touch included, so the
 * thumb works the same from the other side), and only its letters and icons are flipped back to read normally.
 * Plain state, read while drawing, so flipping it redraws without rebuilding anything.
 */
object RailSide {
    var left by mutableStateOf(false)
        internal set
}

/** Mirrors a rail onto the left edge in left-hand mode. */
fun Modifier.railMirror(): Modifier = graphicsLayer { scaleX = if (RailSide.left) -1f else 1f }

/** Draws a rail letter (or label) that stays readable when the rail is mirrored: flipped back around its own centre. */
internal fun DrawScope.railText(
    layout: TextLayoutResult, color: Color = Color.Unspecified, topLeft: Offset = Offset.Zero,
    alpha: Float = Float.NaN, shadow: Shadow? = null,
) {
    if (!RailSide.left) drawText(layout, color = color, topLeft = topLeft, alpha = alpha, shadow = shadow)
    else scale(-1f, 1f, pivot = Offset(topLeft.x + layout.size.width / 2f, topLeft.y + layout.size.height / 2f)) {
        drawText(layout, color = color, topLeft = topLeft, alpha = alpha, shadow = shadow)
    }
}

/** Draws [block] the right way round in a mirrored rail (for app icons), flipped around [pivot]. */
internal inline fun DrawScope.upright(pivot: Offset, crossinline block: DrawScope.() -> Unit) {
    if (RailSide.left) scale(-1f, 1f, pivot = pivot) { block() } else block()
}
