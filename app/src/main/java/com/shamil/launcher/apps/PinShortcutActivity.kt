package com.shamil.launcher.apps

import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.shamil.launcher.ui.theme.LauncherTheme
import com.shamil.launcher.ui.theme.LookStore

/**
 * Android opens this when an app asks to put a shortcut on the home screen (a browser's "Add to home screen").
 * Seelie asks once, then keeps it at the end of the favourites (reorder it in Edit favourites).
 */
class PinShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        val info = request?.shortcutInfo
        if (request == null || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || info == null || !request.isValid) {
            finish()
            return
        }
        val label = (info.shortLabel ?: info.longLabel ?: "this shortcut").toString()
        setContent {
            LauncherTheme(look = LookStore.get(this).look) {
                val look = LookStore.get(this).look
                AlertDialog(
                    onDismissRequest = { finish() },
                    containerColor = look.sheet,
                    title = { Text("Add \"$label\" to your favourites?", color = look.text) },
                    text = { Text("It goes at the end of your favourites; move it in Edit favourites.", color = look.textDim) },
                    confirmButton = {
                        TextButton({
                            if (runCatching { request.accept() }.getOrDefault(false)) {
                                val favourites = Favourites(this)
                                val key = PinnedShortcuts.keyFor(info)
                                if (key !in favourites.packages) favourites.setOrder(favourites.packages + key)
                                Favourites.changed()
                                PinnedShortcuts.changed()
                                Toast.makeText(this, "\"$label\" added to your favourites", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Couldn't add \"$label\"", Toast.LENGTH_SHORT).show()
                            }
                            finish()
                        }) { Text("Add", color = look.accent) }
                    },
                    dismissButton = { TextButton({ finish() }) { Text("Cancel", color = look.textDim) } },
                )
            }
        }
    }
}
