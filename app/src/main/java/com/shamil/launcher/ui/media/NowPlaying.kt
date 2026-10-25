package com.shamil.launcher.ui.media

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.notify.NotifyHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import android.graphics.Color as AndroidColor

/*
 * What's playing right now, read from Android's "media sessions" (the same thing the lock screen's player uses).
 * Android only shows us the sessions while Notification access is on for Seelie (see NotifyHub).
 * Everything stays on the phone.
 */

/**
 * One song that's playing (or paused), ready for the music card.
 * [positionMs] moves on by itself about once a second while it plays and the home is on screen.
 */
class NowPlaying internal constructor(
    val title: String,
    val artist: String,
    val album: String,
    /** The cover, already shrunk to card size. Null when the app gives none. */
    val art: ImageBitmap?,
    /** A soft colour taken from the cover, for the card's glow. Null without a cover. */
    val artColor: Color?,
    /** The music app (e.g. "com.spotify.music"), its name and small icon. */
    val packageName: String,
    val appLabel: String,
    val appIcon: ImageBitmap?,
    /** Playing (or about to, like buffering) = true; paused or stopped = false. */
    val isPlaying: Boolean,
    val positionMs: Long,
    /** 0 when the app doesn't say how long the song is (e.g. live radio). */
    val durationMs: Long,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val canSeek: Boolean,
    /** Extra buttons the app offers (Spotify's heart, shuffle, …), shuffle first when the app supports it. */
    val extras: List<MediaExtra>,
    private val controller: MediaController,
) {
    // "Transport controls" = the remote control Android hands us for that app's player.
    private val controls get() = controller.transportControls

    fun play() { runCatching { controls.play() } }
    fun pause() { runCatching { controls.pause() } }
    fun playPause() = if (isPlaying) pause() else play()
    fun next() { runCatching { controls.skipToNext() } }
    fun previous() { runCatching { controls.skipToPrevious() } }
    fun seekTo(ms: Long) { runCatching { controls.seekTo(ms.coerceAtLeast(0L)) } }

    /**
     * The cover at up to [maxPx] pixels (25 Oct, for the night screen's big Music page: [art] is shrunk for the home
     * card and looks soft that big). Slow-ish: call it off the main thread. Null when the app gives no picture.
     */
    fun bigArt(context: Context, maxPx: Int): ImageBitmap? = runCatching {
        val meta = controller.metadata ?: return@runCatching null
        val source = meta.artBitmap() ?: meta.artUri()?.let { loadArtUri(context, it, maxPx) } ?: return@runCatching null
        val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else source
        if (readable == null || readable.isRecycled || readable.width <= 0 || readable.height <= 0) return@runCatching null
        val biggest = max(readable.width, readable.height)
        val sized = if (biggest > maxPx) {
            Bitmap.createScaledBitmap(readable, readable.width * maxPx / biggest, readable.height * maxPx / biggest, true)
        } else readable
        sized.asImageBitmap()
    }.getOrNull()

    /**
     * Opens the music app, on its own "now playing" screen when it offers one. Through the lock (25 Oct, audit S2):
     * a locked or vault music app asks for its code first.
     */
    fun open(context: Context) =
        com.shamil.launcher.apps.VaultStore.get(context).guard(packageName, appLabel) { openNow(context) }

    private fun openNow(context: Context) {
        val screen = controller.sessionActivity
        if (screen != null) {
            val opened = runCatching {
                // Android 14+ asks the app that sends a PendingIntent to say "yes, it may open a screen".
                val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle()
                } else null
                screen.send(context, 0, null, null, null, null, options)
            }.isSuccess
            if (opened) return
        }
        context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/**
 * One extra button from the music app: a "custom action" (its own icon from the app, e.g. ♥ like or + add),
 * or the standard shuffle. [isOn] lights it up (only known for shuffle, see below).
 */
class MediaExtra internal constructor(
    val label: String,
    /** The app's own icon; null = draw Seelie's own (the shuffle arrows, or a small star). */
    val icon: ImageBitmap?,
    val isShuffle: Boolean,
    val isOn: Boolean,
    private val action: () -> Unit,
) {
    fun perform() { runCatching { action() } }
}

/*
 * Shuffle the "standard" way: Android's own media API has no shuffle switch, so apps built on the AndroidX media
 * library (most music apps) understand this special message instead. These names come from that library;
 * never change them. Android doesn't tell us whether shuffle is on, so Seelie remembers what it last asked for.
 */
private const val SHUFFLE_ACTION = "android.support.v4.media.session.action.SET_SHUFFLE_MODE"
private const val SHUFFLE_ARGUMENT = "android.support.v4.media.session.action.ARGUMENT_SHUFFLE_MODE"
/** The "can shuffle" flags of that library (the new one and the old one). */
private const val CAN_SHUFFLE = (1L shl 21) or (1L shl 19)

/**
 * Watches the phone's media sessions and gives back the one to show: the playing one, else the most recent one
 * that has a song. Null when nothing has a song, or when Notification access is off.
 * Cheap: it only wakes up when a player changes, and the position ticks once a second only while playing on screen.
 */
@Composable
fun rememberNowPlaying(): NowPlaying? {
    val app = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val connected by NotifyHub.connected.collectAsState()
    val resumes = rememberResumeCount()
    val watcher = remember { SessionWatcher(app) }
    // Look again when the listener (dis)connects or he comes back to the home (e.g. from switching access on).
    LaunchedEffect(watcher, connected, resumes) { watcher.start() }

    val picked = watcher.picked
    val meta = picked?.metadata
    val title = meta?.textOf(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
    val artist = meta?.textOf(
        MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
        MediaMetadata.METADATA_KEY_AUTHOR, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
    ).orEmpty()
    val album = meta?.textOf(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()

    // The cover: shrunk and its colour measured off the main thread, only when the song (or its cover) changes.
    val artBitmap = meta?.artBitmap()
    val artUri = if (artBitmap == null) meta?.artUri() else null
    val art by produceState<ArtInfo?>(null, title, album, artBitmap?.width, artBitmap?.height, artUri) {
        value = if (artBitmap == null && artUri == null) null
        else withContext(Dispatchers.Default) { prepareArt(app, artBitmap, artUri) }
    }

    // The music app's name and icon, loaded once per app.
    val pkg = picked?.controller?.packageName
    val density = LocalDensity.current
    val iconPx = with(density) { 20.dp.roundToPx() }
    val appBits by produceState<AppBits?>(null, pkg) {
        value = pkg?.let { withContext(Dispatchers.IO) { loadApp(app, it, iconPx) } }
    }

    // The app's extra buttons ("custom actions") and their icons from the app itself, loaded off the main thread.
    // Icons are kept by "app/icon number", so a ♥ that switches between empty and full doesn't load twice.
    val state = picked?.state
    val customs: List<PlaybackState.CustomAction> = state?.customActions.orEmpty()
    val iconIds = customs.map { it.icon }.filter { it != 0 }
    val actionPx = with(density) { 22.dp.roundToPx() }
    val actionIcons by produceState<Map<String, ImageBitmap>>(emptyMap(), pkg, iconIds) {
        val missing = iconIds.filter { "$pkg/$it" !in value }
        if (pkg != null && missing.isNotEmpty()) {
            val loaded = withContext(Dispatchers.IO) { loadActionIcons(app, pkg, missing, actionPx) }
            value = value + loaded
        }
    }
    // What Seelie last asked shuffle to be (Android can't tell us), per app.
    var shuffleOn by remember(pkg) { mutableStateOf(false) }

    // The clock that moves the position on: only while really playing, and only while the screen is visible.
    val ticking = state?.state == PlaybackState.STATE_PLAYING
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(owner, ticking) {
        if (!ticking) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(1_000L)
            }
        }
    }

    if (picked == null || title.isBlank()) return null
    val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
    val position = livePosition(state, if (ticking) now else 0L, duration)
    // Some apps leave the "what can you do" list empty; then assume everything works.
    val actions = state?.actions ?: 0L
    fun can(action: Long) = actions == 0L || actions and action != 0L

    val controller = picked.controller
    val extras = buildList {
        // Standard shuffle, unless the app already offers its own shuffle button.
        val ownShuffle = customs.any { "shuffle" in it.action.lowercase() || "shuffle" in it.name?.toString().orEmpty().lowercase() }
        if (actions and CAN_SHUFFLE != 0L && !ownShuffle) {
            add(MediaExtra("Shuffle", icon = null, isShuffle = true, isOn = shuffleOn) {
                val turnOn = !shuffleOn
                controller.transportControls.sendCustomAction(SHUFFLE_ACTION, Bundle().apply { putInt(SHUFFLE_ARGUMENT, if (turnOn) 1 else 0) })
                shuffleOn = turnOn
            })
        }
        customs.forEach { custom ->
            add(MediaExtra(custom.name?.toString().orEmpty(), icon = actionIcons["$pkg/${custom.icon}"], isShuffle = false, isOn = false) {
                controller.transportControls.sendCustomAction(custom, custom.extras)
            })
        }
    }

    return NowPlaying(
        title = title,
        artist = artist,
        album = album,
        art = art?.image,
        artColor = art?.color,
        packageName = picked.controller.packageName,
        appLabel = appBits?.label ?: "",
        appIcon = appBits?.icon,
        isPlaying = state.isPlayingNow(),
        positionMs = position,
        durationMs = duration,
        canSkipNext = can(PlaybackState.ACTION_SKIP_TO_NEXT),
        canSkipPrevious = can(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
        canSeek = duration > 0 && state != null && state.actions and PlaybackState.ACTION_SEEK_TO != 0L,
        extras = extras,
        controller = controller,
    )
}

/** Is Notification access on? Checked again whenever the screen comes back and when the listener (dis)connects. */
@Composable
internal fun rememberMusicAccess(): Boolean {
    val context = LocalContext.current
    val connected by NotifyHub.connected.collectAsState()
    val resumes = rememberResumeCount()
    return remember(connected, resumes) { NotifyHub.isEnabled(context) }
}

/** A number that goes up every time the screen comes back to the front ("resumed"). */
@Composable
private fun rememberResumeCount(): Int {
    val owner = LocalLifecycleOwner.current
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumes
}

/** Opens Android's page to switch Notification access on for Seelie. */
internal fun openNotificationAccess(context: Context) {
    runCatching { context.startActivity(NotifyHub.accessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
}

/**
 * ▶ when nothing is playing: presses the phone's "media play" key. Android hands it to the app that played last,
 * so it picks up where he left off (works even after that app closed its player).
 */
fun resumeLastMedia(context: Context) {
    val audio = context.getSystemService(AudioManager::class.java) ?: return
    val time = SystemClock.uptimeMillis()
    audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
    audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
}

// ---------------------------------------------------------------------------------------------------------------
// The watcher

/** The session we chose, with its details at that moment. A new one each time something changes. */
private class Picked(val controller: MediaController, val metadata: MediaMetadata?, val state: PlaybackState?)

/**
 * Listens to Android's list of media sessions and to each session's changes, and keeps [picked] up to date.
 * It's a "RememberObserver": Compose tells it when the card appears (start listening) and goes away (stop).
 */
private class SessionWatcher(private val context: Context) : RememberObserver {
    private val manager: MediaSessionManager? = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val watched = mutableListOf<Pair<MediaController, MediaController.Callback>>()
    private var listening = false

    var picked by mutableStateOf<Picked?>(null)
        private set

    /** Android calls this when a player appears or goes away. */
    private val onSessions = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }

    init {
        // A quick first look, so the card shows the song straight away instead of "Nothing playing" for a moment.
        picked = choose(readSessions())
    }

    /** Starts listening (once) and reads the sessions again. Safe to call often. */
    fun start() {
        val m = manager ?: return
        try {
            if (!listening) {
                m.addOnActiveSessionsChangedListener(onSessions, NotifyHub.component(context), handler)
                listening = true
            }
            watch(m.getActiveSessions(NotifyHub.component(context)))
        } catch (e: SecurityException) {
            // Notification access is off: Android won't show us any players.
            stop()
        }
    }

    fun stop() {
        if (listening) {
            runCatching { manager?.removeOnActiveSessionsChangedListener(onSessions) }
            listening = false
        }
        unwatchAll()
        picked = null
    }

    override fun onRemembered() = start()
    override fun onForgotten() = stop()
    override fun onAbandoned() = stop()

    private fun readSessions(): List<MediaController> = try {
        manager?.getActiveSessions(NotifyHub.component(context)).orEmpty()
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Follows every session in [list] (there are only a few), so we notice when another app starts playing. */
    private fun watch(list: List<MediaController>) {
        unwatchAll()
        list.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                override fun onSessionDestroyed() {
                    watched.removeAll { it.first === controller }
                    refresh()
                }
            }
            runCatching { controller.registerCallback(callback, handler) }
            watched += controller to callback
        }
        refresh()
    }

    private fun unwatchAll() {
        watched.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        watched.clear()
    }

    private fun refresh() {
        picked = choose(watched.map { it.first })
    }

    /** The playing session with a song, else the first (= most recent) one with a song. */
    private fun choose(list: List<MediaController>): Picked? {
        val best = list.firstOrNull { it.playbackState.isPlayingNow() && it.metadata.hasTitle() }
            ?: list.firstOrNull { it.metadata.hasTitle() }
            ?: return null
        return Picked(best, best.metadata, best.playbackState)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Small helpers

private fun PlaybackState?.isPlayingNow(): Boolean = when (this?.state) {
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
    PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
    else -> false
}

private fun MediaMetadata?.hasTitle(): Boolean =
    this != null && textOf(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNotBlank()

/** The first of [keys] that has text. */
private fun MediaMetadata.textOf(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key -> getText(key)?.toString()?.takeIf { it.isNotBlank() } }.orEmpty()

private fun MediaMetadata.artBitmap(): Bitmap? =
    getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

private fun MediaMetadata.artUri(): String? =
    listOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        .firstNotNullOfOrNull { key -> getString(key)?.takeIf { it.isNotBlank() } }

/**
 * Where the song is now. The app tells us a position and when it said so; while playing we add the time since
 * (times the speed, for 1.5× podcasts). [now] = 0 means "not ticking": just use the app's own number.
 */
private fun livePosition(state: PlaybackState?, now: Long, duration: Long): Long {
    if (state == null) return 0L
    var position = state.position
    if (now > 0L && state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
        val since = max(now, state.lastPositionUpdateTime) - state.lastPositionUpdateTime
        position += (since * state.playbackSpeed).toLong()
    }
    position = position.coerceAtLeast(0L)
    return if (duration > 0L) position.coerceAtMost(duration) else position
}

/** A cover shrunk to card size, plus its colour. */
private class ArtInfo(val image: ImageBitmap, val color: Color?)

/** The music app's name and icon. */
private class AppBits(val label: String, val icon: ImageBitmap?)

/** Card size: big enough for a 72dp cover on a sharp screen, small enough to stay light. */
private const val ART_PX = 288

/** Runs off the main thread: shrinks the cover and measures its colour. */
private fun prepareArt(context: Context, bitmap: Bitmap?, uri: String?): ArtInfo? = runCatching {
    val source = bitmap ?: uri?.let { loadArtUri(context, it) } ?: return@runCatching null
    // "Hardware" bitmaps live on the graphics chip and can't be read pixel by pixel, so copy them first.
    val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else source
    if (readable == null || readable.isRecycled || readable.width <= 0 || readable.height <= 0) return@runCatching null
    val biggest = max(readable.width, readable.height)
    val small = if (biggest > ART_PX) {
        Bitmap.createScaledBitmap(readable, readable.width * ART_PX / biggest, readable.height * ART_PX / biggest, true)
    } else readable
    ArtInfo(small.asImageBitmap(), glowColour(small))
}.getOrNull()

/** Some apps give a link to the cover instead of the picture. Only links on the phone itself, never the internet. */
private fun loadArtUri(context: Context, text: String, targetPx: Int = ART_PX): Bitmap? {
    val uri = Uri.parse(text)
    if (uri.scheme !in setOf("content", "android.resource", "file")) return null
    val resolver = context.contentResolver
    // First only read the size, then load it shrunk ("inSampleSize"), so a huge cover doesn't eat memory.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/**
 * The cover's main colour, our own small version of Android's "Palette": shrink it to 16×16, then average the
 * pixels, counting colourful ones much more than grey, black or white ones. Then make it bright enough to glow.
 */
private fun glowColour(bitmap: Bitmap): Color? {
    val side = 16
    val tiny = Bitmap.createScaledBitmap(bitmap, side, side, true)
    val pixels = IntArray(side * side)
    tiny.getPixels(pixels, 0, side, 0, 0, side, side)
    if (tiny !== bitmap) tiny.recycle()

    val hsv = FloatArray(3)
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var weight = 0.0
    var saturationSum = 0.0
    for (pixel in pixels) {
        if (AndroidColor.alpha(pixel) < 128) continue
        AndroidColor.colorToHSV(pixel, hsv)
        val saturation = hsv[1]
        val brightness = hsv[2]
        // Colourful and not too dark counts most; near-black and washed-out pixels count a little.
        val w = 0.08 + saturation * saturation * (if (brightness < 0.18f) 0.15 else 1.0) * (0.4 + brightness)
        red += AndroidColor.red(pixel) * w
        green += AndroidColor.green(pixel) * w
        blue += AndroidColor.blue(pixel) * w
        weight += w
        saturationSum += saturation
    }
    if (weight <= 0.0) return null

    val average = AndroidColor.rgb((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
    AndroidColor.colorToHSV(average, hsv)
    val greyCover = saturationSum / pixels.size < 0.12
    // A grey cover keeps a soft grey-white glow; a colourful one gets a clear, rich colour.
    hsv[1] = if (greyCover) hsv[1].coerceAtMost(0.15f) else hsv[1].coerceIn(0.45f, 0.85f)
    hsv[2] = hsv[2].coerceIn(0.7f, 0.95f)
    return Color(AndroidColor.HSVToColor(hsv))
}

/** Runs off the main thread: the extra buttons' icons from the music app's own files, as "app/icon number" → picture. */
private fun loadActionIcons(context: Context, packageName: String, ids: List<Int>, px: Int): Map<String, ImageBitmap> {
    val resources = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull() ?: return emptyMap()
    return ids.mapNotNull { id ->
        runCatching { resources.getDrawable(id, null)?.toBitmap(px, px)?.asImageBitmap() }.getOrNull()?.let { "$packageName/$id" to it }
    }.toMap()
}

/** Runs off the main thread: the app's name and its icon at [px] pixels. */
private fun loadApp(context: Context, packageName: String, px: Int): AppBits = runCatching {
    val pm = context.packageManager
    val info = pm.getApplicationInfo(packageName, 0)
    AppBits(pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toBitmap(px, px).asImageBitmap())
}.getOrElse { AppBits("", null) }
