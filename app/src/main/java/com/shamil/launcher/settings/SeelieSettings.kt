package com.shamil.launcher.settings

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import com.shamil.launcher.R

/** Opens "Seelie settings" (Serenitea), a window inside Seelie, on top of the home screen. */
object SeelieSettings {
    /** Pages settings can open straight on. */
    const val PAGE_CLOCK = "clock"
    const val PAGE_WIDGETS = "widgets"
    const val PAGE_WIDGET_PICKER = "widget_picker"
    internal const val EXTRA_PAGE = "page"

    /** Opens settings on its top page, or straight on [page] (one of the PAGE_ names). */
    fun open(context: Context, page: String? = null) {
        val intent = Intent(context, SettingsActivity::class.java)
        if (page != null) intent.putExtra(EXTRA_PAGE, page)
        // Opened from the home screen it simply goes on top of it; from anywhere else Android needs a new task.
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // A quick fade over the home instead of the usual app animation (Niagara does the same, ~0.2 s).
        val options = ActivityOptions.makeCustomAnimation(context, R.anim.seelie_fade_in, R.anim.seelie_hold)
        runCatching { context.startActivity(intent, options.toBundle()) }
    }
}
