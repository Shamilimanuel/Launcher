package com.shamil.launcher.ui.rail

import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * The left thumb (his ask, 24 Oct: "it works when I use my thumb on the left empty side, like my thumb was there,
 * and it would move the A–Z"). The rail stays on the right; a slide along the LEFT edge is handed to it as if it
 * happened on the right (the touch is mirrored), so every rail style works from both sides without knowing.
 *
 * A touch on the left edge waits a moment first: an up-and-down slide (or, for the arc styles, a slide along the
 * mirrored curve) goes to the rail; a tap, a sideways swipe or a hold goes through unchanged, a few ms late, so
 * favourites and the list on the left keep working. Lives in MainActivity.dispatchTouchEvent.
 */
class RailRelay(private val deliver: (MotionEvent) -> Boolean) {
    private enum class Mode { NONE, WAITING, RELAY, PASS }
    private var mode = Mode.NONE
    private val held = ArrayList<MotionEvent>()
    private var down = Offset.Zero
    private var width = 0f
    private val handler = Handler(Looper.getMainLooper())
    /** Held still for a while: a long-press on whatever is there, not the rail. */
    private val holdTimeout = Runnable { if (mode == Mode.WAITING) release(mirror = false) }

    fun dispatch(ev: MotionEvent, width: Int, height: Int, density: Float, slop: Int): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            clear()
            this.width = width.toFloat()
            down = Offset(ev.x, ev.y)
            if (startsHere(down, width.toFloat(), height.toFloat(), density)) {
                mode = Mode.WAITING
                held += MotionEvent.obtain(ev)
                handler.postDelayed(holdTimeout, HOLD_MS)
                return true
            }
            mode = Mode.NONE
            return deliver(ev)
        }
        return when (mode) {
            Mode.NONE, Mode.PASS -> deliver(ev).also { if (ev.isEnd()) mode = Mode.NONE }
            Mode.RELAY -> deliverMirrored(ev).also { if (ev.isEnd()) mode = Mode.NONE }
            Mode.WAITING -> {
                held += MotionEvent.obtain(ev)
                val d = Offset(ev.x - down.x, ev.y - down.y)
                when {
                    ev.isEnd() -> release(mirror = false)
                    d.getDistance() > slop -> release(mirror = isRailMove(d))
                }
                if (ev.isEnd()) mode = Mode.NONE
                true
            }
        }
    }

    /** Does a touch at [p] start in the left-thumb zone? */
    private fun startsHere(p: Offset, w: Float, h: Float, density: Float): Boolean {
        if (!enabled || !RailSide.edge || RailSide.left) return false
        val arc = arcGeo
        return if (arc != null) arc.near(Offset(w - p.x, p.y))
        else p.x < EDGE_DP * density && p.y > h * 0.22f && p.y < h * 0.96f
    }

    /** A move [d] from the start: mostly up-and-down for the edge rails, along the curve for the arc ones. */
    private fun isRailMove(d: Offset): Boolean {
        val arc = arcGeo
        return if (arc != null) arc.along(Offset(width - down.x, down.y), Offset(-d.x, d.y))
        else abs(d.y) > abs(d.x)
    }

    /** Sends everything held so far, mirrored to the rail or as it was, and keeps going that way. */
    private fun release(mirror: Boolean) {
        handler.removeCallbacks(holdTimeout)
        mode = if (mirror) Mode.RELAY else Mode.PASS
        val events = held.toList()
        held.clear()
        events.forEach { if (mirror) deliverMirrored(it) else deliver(it); it.recycle() }
    }

    private fun deliverMirrored(ev: MotionEvent): Boolean {
        val copy = MotionEvent.obtain(ev)
        copy.transform(Matrix().apply { setScale(-1f, 1f, width / 2f, 0f) })
        return deliver(copy).also { copy.recycle() }
    }

    private fun clear() {
        handler.removeCallbacks(holdTimeout)
        held.forEach { it.recycle() }
        held.clear()
    }

    private fun MotionEvent.isEnd() = actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL

    companion object {
        /** The home is showing a rail right now (set by the home; off while searching, editing, in the vault). */
        @Volatile var enabled = false
        /** The arc while an arc style is on (null = an edge rail); its shape is read at touch time. */
        @Volatile var arc: ArcRailState? = null
        private val arcGeo: ArcGeometry? get() = arc?.geo
        /** How wide the left-thumb strip is, for the edge rails. */
        private const val EDGE_DP = 32f
        private const val HOLD_MS = 280L
    }
}
