package com.shamil.launcher.apps

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/**
 * The favourites list, in order, saved on the phone (SharedPreferences = a small
 * settings file only this app can read). Apps are stored by package name, e.g. "com.whatsapp".
 */
class Favourites(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** The current favourites. It's Compose state, so the screen redraws when it changes. */
    var packages by mutableStateOf(read())
        private set

    operator fun contains(packageName: String) = packageName in packages

    fun toggle(packageName: String) =
        save(if (packageName in packages) packages - packageName else packages + packageName)

    /** Saves a new order (after dragging favourites around). His order is never re-sorted automatically. */
    fun setOrder(newOrder: List<String>) = save(newOrder)

    private fun read(): List<String> =
        prefs.getString(KEY, null)?.split("\n")?.filter { it.isNotBlank() } ?: STARTING_SET

    private fun save(list: List<String>) {
        packages = list
        prefs.edit { putString(KEY, list.joinToString("\n")) }
    }

    private companion object {
        const val KEY = "favourites"

        /** First-run favourites: his Niagara list, in his order. */
        val STARTING_SET = listOf(
            "com.google.android.youtube",
            "com.whatsapp",
            "com.spotify.music",
            "com.snapchat.android",
            "com.google.android.gm",          // Gmail
            "com.sec.android.gallery3d",      // Galerij
            "com.discord",
            "com.opera.browser",
            "com.android.vending",            // Play Store
            "com.samsung.android.dialer",     // Telefoon
            "com.google.android.apps.maps",
            "nl.devolksbank.asn.bankieren",   // ASN Bank
        )
    }
}

@Composable
fun rememberFavourites(): Favourites {
    val context = LocalContext.current.applicationContext
    return remember { Favourites(context) }
}
