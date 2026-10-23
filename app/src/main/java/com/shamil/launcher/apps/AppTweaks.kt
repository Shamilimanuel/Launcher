package com.shamil.launcher.apps

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import org.json.JSONObject

/**
 * His personal changes to apps, saved on the phone: apps hidden from the A–Z list,
 * and apps he renamed (Niagara can't rename apps; we can). Stored by package name.
 */
class AppTweaks(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var hidden by mutableStateOf(prefs.getStringSet(HIDDEN, emptySet())!!.toSet())
        private set

    /** "com.google.android.youtube" -> "YT" */
    var names by mutableStateOf(readNames())
        private set

    fun isHidden(packageName: String) = packageName in hidden

    fun setHidden(packageName: String, hide: Boolean) {
        hidden = if (hide) hidden + packageName else hidden - packageName
        prefs.edit { putStringSet(HIDDEN, hidden) }
    }

    /** A blank name puts the original name back. */
    fun rename(packageName: String, newName: String) {
        names = if (newName.isBlank()) names - packageName else names + (packageName to newName.trim())
        prefs.edit { putString(NAMES, JSONObject(names).toString()) }
    }

    private fun readNames(): Map<String, String> = try {
        val json = JSONObject(prefs.getString(NAMES, "{}")!!)
        json.keys().asSequence().associateWith { json.getString(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    private companion object {
        const val HIDDEN = "hidden"
        const val NAMES = "names"
    }
}

@Composable
fun rememberAppTweaks(): AppTweaks {
    val context = LocalContext.current.applicationContext
    return remember { AppTweaks(context) }
}
