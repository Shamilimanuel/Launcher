package com.shamil.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.UserManager
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers the app list and the finished icons on the phone's storage, so that:
 * - after a restart, home shows everything straight away (no waiting for 100+ icons), and
 * - icons are only drawn again for apps that are new or updated.
 *
 * Each icon is saved as a PNG named after a "key": a fingerprint of the app, its install
 * (which changes when the app updates), the icon pack and [ICON_VERSION].
 */
class AppCache(context: Context) {
    private val dir = File(context.filesDir, "app-cache").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val users = context.getSystemService(UserManager::class.java)
    private val memory = ConcurrentHashMap<String, Bitmap>()

    fun keyFor(info: LauncherActivityInfo, iconPack: String?): String {
        val fingerprint = listOf(
            info.componentName.flattenToShortString(),
            users.getSerialNumberForUser(info.user),
            info.applicationInfo.sourceDir,   // a new path every time the app is installed or updated
            iconPack ?: "-",
            ICON_VERSION,
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-1").digest(fingerprint.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }

    /** A saved icon, from memory or disk; null if it was never drawn. */
    fun icon(key: String): Bitmap? =
        memory[key] ?: File(dir, "$key.png")
            .takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.path) }
            ?.also { memory[key] = it }

    fun saveIcon(key: String, bitmap: Bitmap) {
        memory[key] = bitmap
        File(dir, "$key.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Saves the list for next start, and deletes icons no app uses any more. */
    fun saveIndex(apps: List<AppEntry>) {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(
                JSONObject()
                    .put("label", app.label)
                    .put("component", app.component.flattenToString())
                    .put("user", users.getSerialNumberForUser(app.user))
                    .put("installedAt", app.installedAt)
                    .put("icon", app.iconKey)
            )
        }
        indexFile.writeText(array.toString())

        val inUse = apps.map { it.iconKey }.toSet()
        dir.listFiles()
            ?.filter { it.name.endsWith(".png") && it.name.removeSuffix(".png") !in inUse }
            ?.forEach { it.delete() }
        memory.keys.retainAll(inUse)
    }

    /** Last time's list, ready to show. Empty if there's no saved list yet (first start) or it can't be read. */
    fun readIndex(): List<AppEntry> = try {
        val array = JSONArray(indexFile.readText())
        (0 until array.length()).mapNotNull { i ->
            val item = array.getJSONObject(i)
            val user = users.getUserForSerialNumber(item.getLong("user")) ?: return@mapNotNull null
            val component = ComponentName.unflattenFromString(item.getString("component")) ?: return@mapNotNull null
            val key = item.getString("icon")
            val icon = icon(key) ?: return@mapNotNull null
            AppEntry(
                label = item.getString("label"),
                component = component,
                user = user,
                icon = icon.asImageBitmap(),
                installedAt = item.getLong("installedAt"),
                iconKey = key,
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        /** Bump this whenever the way icons are drawn changes, so every icon is redrawn once. */
        const val ICON_VERSION = 1
    }
}
