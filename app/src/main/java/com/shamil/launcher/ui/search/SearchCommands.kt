package com.shamil.launcher.ui.search

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The > commands, typed in the search box like his Terminal widget: "> timer 5m", "> calc 12*7" …
 * [word] is what you type, [aliases] also work (some Dutch), [example] is shown as a suggestion.
 */
enum class SearchCommand(
    val word: String,
    val aliases: List<String>,
    val example: String,
    val what: String,
    val glyph: Glyph,
    /** True when it needs something after the word (a sum, a time, a note). */
    val needsText: Boolean,
) {
    Calc("calc", listOf("reken", "math", "="), "calc 12*7", "Calculate", Glyph.Calc, true),
    Timer("timer", listOf("countdown"), "timer 5m", "Start a timer in your clock app", Glyph.Timer, true),
    Alarm("alarm", listOf("wekker", "wake"), "alarm 7:30", "Set an alarm", Glyph.Alarm, true),
    Wifi("wifi", listOf("wi-fi", "wlan"), "wifi", "Wi-Fi panel", Glyph.Wifi, false),
    Bluetooth("bluetooth", listOf("bt"), "bluetooth", "Bluetooth settings", Glyph.Bluetooth, false),
    Torch("torch", listOf("flash", "flashlight", "zaklamp", "lamp"), "torch", "Flashlight on / off", Glyph.Torch, false),
    Note("note", listOf("notes", "notitie"), "note buy milk", "Save a quick note", Glyph.Note, true),
}

/** "> timer 5m" split up: word "timer", text "5m", and the command it names (null while you're still typing it). */
class ParsedCommand(val word: String, val text: String, val command: SearchCommand?)

/** What the card above the search box says about the command you typed. */
class CommandPreview(
    val glyph: Glyph,
    /** The small grey line on top, like "12*7 =". */
    val small: String,
    /** The main line, like "Timer for 5:00", or the answer. */
    val title: String,
    /** Show [title] big (the calculator's answer). */
    val big: Boolean = false,
    /** A switch on the right for on/off things (the torch): its current state. */
    val toggle: Boolean? = null,
    /** False = Enter does nothing useful yet (like "3+" while you're still typing). */
    val ready: Boolean = true,
)

object SearchCommands {

    /** Null unless it starts with ">". "> Timer 5m" → word "timer", text "5m". */
    fun parse(query: String): ParsedCommand? {
        val t = query.trimStart()
        if (!t.startsWith(">")) return null
        val rest = t.drop(1).trimStart()
        val word = rest.substringBefore(' ').lowercase()
        val text = rest.substringAfter(' ', "").trim()
        val command = SearchCommand.entries.firstOrNull { it.word == word || word in it.aliases }
        return ParsedCommand(word, text, command)
    }

    /** The commands that start with what's typed so far (all of them for a bare ">"). */
    fun suggestions(word: String): List<SearchCommand> =
        SearchCommand.entries.filter { c -> word.isEmpty() || c.word.startsWith(word) || c.aliases.any { it.startsWith(word) } }

    /** What to show for a complete command, or null when it still needs its text (then suggestions show). */
    fun preview(context: Context, parsed: ParsedCommand): CommandPreview? {
        val command = parsed.command ?: return null
        if (command.needsText && parsed.text.isEmpty()) return null
        return when (command) {
            SearchCommand.Calc -> {
                val answer = Calculator.answerAnything(parsed.text)
                CommandPreview(Glyph.Calc, "${parsed.text} =", answer ?: "…", big = true, ready = answer != null)
            }
            SearchCommand.Timer -> {
                val seconds = parseDuration(parsed.text)
                if (seconds > 0) CommandPreview(Glyph.Timer, "Starts in your clock app", "Timer for ${formatDuration(seconds)}")
                else CommandPreview(Glyph.Timer, "Try 5m, 90s, 1h30m or 2:30", "How long?", ready = false)
            }
            SearchCommand.Alarm -> {
                val time = parseTime(parsed.text)
                if (time != null) CommandPreview(Glyph.Alarm, "Set in your clock app", "Alarm at %d:%02d".format(time.first, time.second))
                else CommandPreview(Glyph.Alarm, "Try 7:30, 730 or 7pm", "What time?", ready = false)
            }
            SearchCommand.Wifi -> CommandPreview(Glyph.Wifi, "Opens the Wi-Fi panel", "Wi-Fi")
            SearchCommand.Bluetooth -> CommandPreview(Glyph.Bluetooth, "Opens Bluetooth settings", "Bluetooth")
            SearchCommand.Torch ->
                if (Torch.hasFlash(context)) CommandPreview(Glyph.Torch, "Tap or press Enter to switch", if (Torch.isOn) "Torch is on" else "Torch is off", toggle = Torch.isOn)
                else CommandPreview(Glyph.Torch, "Torch", "This phone has no flash", ready = false)
            SearchCommand.Note -> CommandPreview(Glyph.Note, "Quick note, saved on this phone", "Save: “${parsed.text}”")
        }
    }

    /**
     * Runs it. Returns true when it's done and the search can close (a timer was set, a panel opened),
     * false when the search should stay (the torch switched, an answer was copied, or it wasn't ready).
     */
    fun run(context: Context, parsed: ParsedCommand): Boolean {
        val command = parsed.command
        if (command == null) {
            toast(context, "Unknown command. Try > calc 12*7")
            return false
        }
        if (preview(context, parsed)?.ready != true) return false
        return when (command) {
            SearchCommand.Calc -> {
                Calculator.answerAnything(parsed.text)?.let { copyText(context, it) }
                false
            }
            SearchCommand.Timer -> startTimer(context, parseDuration(parsed.text))
            SearchCommand.Alarm -> parseTime(parsed.text)?.let { (hour, minute) -> setAlarm(context, hour, minute) } ?: false
            SearchCommand.Wifi -> {
                // Since Android 10 apps can't switch Wi-Fi themselves; the panel is the closest thing (one tap away).
                val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
                startSafely(context, panel) || startSafely(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            SearchCommand.Bluetooth -> startSafely(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            SearchCommand.Torch -> {
                if (!Torch.toggle(context)) toast(context, "Couldn't switch the torch (is the camera in use?)")
                false
            }
            SearchCommand.Note -> {
                QuickNotes.get(context).add(parsed.text)
                toast(context, "Note saved")
                true
            }
        }
    }

    private fun startTimer(context: Context, seconds: Int): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Seelie timer")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)   // start it straight away, no clock screen in between
        val ok = startSafely(context, intent)
        toast(context, if (ok) "Timer set for ${formatDuration(seconds)}" else "No clock app for timers found")
        return ok
    }

    private fun setAlarm(context: Context, hour: Int, minute: Int): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Seelie alarm")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val ok = startSafely(context, intent)
        toast(context, if (ok) "Alarm set for %d:%02d".format(hour, minute) else "No clock app for alarms found")
        return ok
    }

    /**
     * "5m" → 300 seconds. Also "90s", "1h30m", "1.5h", "2:30" (2 min 30 s), "1:00:00", and a bare "5" = 5 minutes.
     * 0 = not a duration. At most 24 hours (Android's limit for timers).
     */
    fun parseDuration(text: String): Int {
        val t = text.trim().lowercase().replace(',', '.')
        if (t.isEmpty()) return 0
        if (':' in t) {
            val parts = t.split(':').map { it.trim().toIntOrNull() ?: return 0 }
            val seconds = when (parts.size) {
                2 -> parts[0] * 60 + parts[1]
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                else -> 0
            }
            return seconds.coerceIn(0, MAX_TIMER)
        }
        // Pairs of "number + unit": "1h 30m" → (1, h), (30, m).
        val piece = Regex("(\\d+(?:\\.\\d+)?)\\s*([a-z]*)")
        if (t.replace(piece, "").isNotBlank()) return 0   // something that isn't a number+unit
        var total = 0.0
        for (match in piece.findAll(t)) {
            val amount = match.groupValues[1].toDouble()
            val unit = match.groupValues[2]
            val perUnit = when {
                unit.isEmpty() || unit.startsWith("m") -> 60          // m, min, minutes
                unit.startsWith("h") || unit.startsWith("u") -> 3600  // h, hour, uur, u
                unit.startsWith("s") -> 1                              // s, sec, seconds
                else -> return 0
            }
            total += amount * perUnit
        }
        return total.toInt().coerceIn(0, MAX_TIMER)
    }

    /** 300 → "5:00", 5400 → "1:30:00". */
    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "7:30", "7.30", "730", "7" and "7pm" → (hour, minute) in 24-hour time; null when it isn't a time. */
    fun parseTime(text: String): Pair<Int, Int>? {
        val t = text.lowercase().replace(" ", "")
        val match = Regex("^(\\d{1,2})(?:[:.h]?(\\d{2}))?(am|pm)?$").find(t) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        when (match.groupValues[3]) {
            "pm" -> if (hour in 1..11) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        if (hour > 23 || minute > 59) return null
        return hour to minute
    }

    private const val MAX_TIMER = 24 * 3600
}

/**
 * The phone's flashlight. Switching it needs no permission (CameraManager.setTorchMode).
 * [isOn] is Compose state, kept right by Android's torch callback (so it's also right if he used the quick tile).
 */
object Torch {
    var isOn by mutableStateOf(false)
        private set
    private var cameraId: String? = null
    private var looked = false
    private var watching = false

    /** The back camera with a flash (or any camera with one); null when the phone has no flash. */
    private fun flashCamera(context: Context): String? {
        if (looked) return cameraId
        val manager = context.getSystemService(CameraManager::class.java) ?: return null
        cameraId = runCatching {
            val withFlash = manager.cameraIdList.filter {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            withFlash.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: withFlash.firstOrNull()
        }.getOrNull()
        looked = true
        return cameraId
    }

    fun hasFlash(context: Context): Boolean = flashCamera(context) != null

    /** Start following the torch's real state. Safe to call often: it only signs up once. */
    fun watch(context: Context) {
        if (watching) return
        val id = flashCamera(context) ?: return
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        manager.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) isOn = enabled
            }
        }, Handler(Looper.getMainLooper()))
        watching = true
    }

    /** On → off or off → on. False when it couldn't (no flash, or the camera app is using it). */
    fun toggle(context: Context): Boolean {
        val id = flashCamera(context) ?: return false
        val manager = context.getSystemService(CameraManager::class.java) ?: return false
        watch(context)
        return try {
            manager.setTorchMode(id, !isOn)
            isOn = !isOn   // the callback confirms it a moment later
            true
        } catch (e: Exception) {
            false
        }
    }
}
