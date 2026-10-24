package com.shamil.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.shamil.launcher.settings.SeelieSettings
import com.shamil.launcher.ui.home.HomeLayoutStore
import com.shamil.launcher.ui.livebg.LiveBgStore
import com.shamil.launcher.ui.rail.RailStore
import com.shamil.launcher.ui.theme.SavesStore
import com.shamil.launcher.ui.vault.PinLookStore

/**
 * Safe mode (his pick, 24 Oct): Seelie is his Home app, so a build that crashes on start would loop and trap him.
 * Every crash leaves a short note (the error and where, nothing personal). Two crashes within a minute, and the
 * next start shows the rescue screen instead of the home. It can also be opened by hand (settings → Seelie).
 */
object SafeMode {
    const val EXTRA_RESCUE = "seelie.rescue"
    private const val PREFS = "safe_mode"
    private const val KEY_TIMES = "crash_times"
    private const val KEY_NOTE = "crash_note"
    /** Two crashes this close together count as "it keeps crashing". */
    private const val WINDOW_MS = 60_000L

    @Volatile private var installed = false

    /** Notes every crash before Android shows "Seelie keeps stopping". Once per process. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val times = (prefs.getString(KEY_TIMES, "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.takeLast(2) + System.currentTimeMillis()
                val where = error.stackTrace.take(8).joinToString("\n") { "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
                // commit, not apply: the process is about to die, so it must be written now.
                prefs.edit(commit = true) {
                    putString(KEY_TIMES, times.joinToString(","))
                    putString(KEY_NOTE, "${error.javaClass.name}: ${error.message.orEmpty().take(300)}\n$where")
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** It crashed twice within a minute, the last one just now: show the rescue screen. */
    fun shouldRescue(context: Context): Boolean {
        val times = (prefs(context).getString(KEY_TIMES, "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
        if (times.size < 2) return false
        val (a, b) = times.takeLast(2)
        return b - a < WINDOW_MS && System.currentTimeMillis() - b < WINDOW_MS
    }

    fun note(context: Context): String? = prefs(context).getString(KEY_NOTE, null)

    /** Back to normal: forget the recent crashes (the last note stays, for the rescue screen next time). */
    fun clear(context: Context) = prefs(context).edit { remove(KEY_TIMES) }

    /**
     * Resets the home's look to the defaults: layout, A–Z rail, living background, clock, home style, PIN look.
     * Theme, font, icons, favourites, apps, vault and saves are kept, so a save can be applied again afterwards.
     */
    fun resetLook(context: Context) {
        for (file in listOf(HomeLayoutStore.PREFS, RailStore.PREFS, LiveBgStore.PREFS, "clock", "home_style", PinLookStore.PREFS)) {
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        }
        SavesStore.get(context).reloadStores()
    }

    /** Opens the rescue screen from anywhere (Seelie settings). */
    fun open(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java).putExtra(EXTRA_RESCUE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

private val Night = Color(0xFF0E1226)
private val NightTop = Color(0xFF1B2140)
private val Gold = Color(0xFFE8C77A)
private val Dim = Color(0xCCFFFFFF)

/**
 * The rescue screen. Deliberately plain: no theme, no saved look, no living background, no widgets, so whatever
 * broke the home can't break this too.
 */
@Composable
fun SafeModeScreen(byHand: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val note = remember { SafeMode.note(context) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightTop, Night)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text("Seelie rescue", style = TextStyle(color = Gold, fontSize = 30.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Text(
            if (byHand) "Nothing crashed. This is the screen you'd get if Seelie kept crashing on start."
            else "Seelie crashed twice in a row, so it opened this instead of your home. Pick a way out.",
            style = TextStyle(color = Dim, fontSize = 15.sp, lineHeight = 21.sp),
        )
        Spacer(Modifier.height(22.dp))
        RescueButton("Back to my home", "Try the normal home again") { SafeMode.clear(context); onDone() }
        RescueButton("Reset the look", "Layout, A–Z rail, living background, clock and home style back to the defaults. Theme, apps and saves stay.") { confirmReset = true }
        RescueButton("Seelie settings", "Change or switch off whatever broke") { SeelieSettings.open(context) }
        RescueButton("Switch home app", "Use another launcher for now") {
            runCatching { context.startActivity(DefaultHome.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        if (note != null) {
            Spacer(Modifier.height(18.dp))
            Text("Last crash note (send this to Claude):", style = TextStyle(color = Dim, fontSize = 13.sp))
            Spacer(Modifier.height(6.dp))
            Text(
                note,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)).padding(12.dp),
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp),
            )
            RescueButton("Copy crash note", "To paste it in our chat") {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Seelie crash", note))
                Toast.makeText(context, "Crash note copied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        containerColor = NightTop,
        title = { Text("Reset the look?", color = Color.White) },
        text = { Text("Your saves stay, so you can apply one again afterwards.", color = Dim) },
        confirmButton = {
            TextButton({
                SafeMode.resetLook(context)
                SafeMode.clear(context)
                confirmReset = false
                onDone()
            }) { Text("Reset", color = Gold) }
        },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel", color = Dim) } },
    )
}

@Composable
private fun RescueButton(title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(shape).background(Color(0x14FFFFFF))
            .border(1.dp, Gold.copy(alpha = 0.35f), shape).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(title, style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium))
        Text(sub, style = TextStyle(color = Dim, fontSize = 13.sp, lineHeight = 18.sp))
    }
}
