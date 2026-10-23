package com.shamil.launcher.ui.search

import android.app.Activity
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

/*
 * Small helpers the search results share: open something without crashing, copy text, search the web.
 */

/**
 * Opens [intent]; returns false (instead of crashing) when nothing on the phone can open it.
 * From outside an Activity Android needs NEW_TASK, so that's added only then.
 */
internal fun startSafely(context: Context, intent: Intent): Boolean {
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        // ActivityNotFoundException (no app for it) or SecurityException (a screen we may not open).
        false
    }
}

internal fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

/** Puts [text] on the clipboard. Android 13+ shows its own "Copied" bubble, older phones get our toast. */
internal fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Seelie", text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(context, "Copied $text")
}

/** "Search the web for …" and "Search Play Store for …". */
object WebSearch {

    /** Opens the engine's results page in the default browser; no browser = Android's own web search. */
    fun search(context: Context, engine: WebEngine, query: String) {
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse(engine.urlFor(query)))
        if (startSafely(context, browser)) return
        val webSearch = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (!startSafely(context, webSearch)) toast(context, "No browser found")
    }

    /** The Play Store app's search (market://), or the Play website when the Store app isn't there. */
    fun playStore(context: Context, query: String) {
        val q = Uri.encode(query)
        if (startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps")))) return
        if (!startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps")))) {
            toast(context, "No Play Store or browser found")
        }
    }
}
