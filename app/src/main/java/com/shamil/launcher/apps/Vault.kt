package com.shamil.launcher.apps

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The PIN vault (his locked feature #7, 23 Oct): apps he moves in here vanish from the A–Z, search, favourites and
 * Hidden apps until he unlocks it (hold • on the rail, then his PIN or fingerprint).
 *
 * Honest limits: this hides apps inside Seelie only. They still exist in the phone's Settings, and another launcher
 * would show them. It keeps prying eyes off his home screen; it is not a safe for data.
 *
 * The PIN itself is never stored: only a salted PBKDF2 hash (the standard way to keep passwords), so even someone
 * reading Seelie's files can't get it back.
 */
class VaultStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    /** Package names of the apps in the vault. */
    var apps by mutableStateOf(prefs.getStringSet(KEY_APPS, emptySet())?.toSet() ?: emptySet())
        private set
    var hasPin by mutableStateOf(prefs.contains(KEY_HASH))
        private set
    var fingerprint by mutableStateOf(prefs.getBoolean(KEY_FINGER, true))
        private set
    /** true = a free password (letters, symbols, any length) typed on the keyboard; false = a number PIN on the pad. */
    var passwordMode by mutableStateOf(prefs.getBoolean(KEY_MODE, false))
        private set
    /** Open right now? Only in memory: every restart, Home press or app launch locks it again. */
    var unlocked by mutableStateOf(false)
        private set
    /** After 5 wrong PINs in a row: no tries until this moment (uptime ms). */
    /** How many digits his number PIN has (0 = not known yet), so the pad checks it by itself, no OK needed. Never the PIN itself. */
    var pinLength by mutableIntStateOf(prefs.getInt(KEY_LENGTH, 0))
        private set
    var lockedUntil by mutableLongStateOf(0L)
        private set
    private var wrongTries = 0

    operator fun contains(packageName: String) = packageName in apps

    /**
     * Locked apps (his idea, 23 Oct): they stay in the list and favourites, but opening one asks for the code first.
     * Same code and fingerprint as the vault.
     */
    var locked by mutableStateOf(prefs.getStringSet(KEY_LOCKED, emptySet())?.toSet() ?: emptySet())
        private set
    fun isLocked(packageName: String) = packageName in locked
    fun setLocked(packageName: String, on: Boolean) {
        locked = if (on) locked + packageName else locked - packageName
        prefs.edit { putStringSet(KEY_LOCKED, locked) }
    }

    /** A locked app waiting for the code: its name (for the screen) and what to do once the code is right. */
    var pending by mutableStateOf<Pair<String, () -> Unit>?>(null)
        private set

    /**
     * Runs [open] straight away, or (for a locked app) first asks for the code. Every way an app opens goes through here:
     * a tap, search, a gesture, a shortcut, a notification.
     */
    fun guard(packageName: String, label: String, open: () -> Unit) {
        if (packageName !in locked || !hasPin || unlocked) open() else pending = label to open
    }

    /** The code was right: open the waiting app, and lock again straight after. */
    fun finishPending() {
        val action = pending?.second
        pending = null
        unlocked = false
        action?.invoke()
    }

    fun cancelPending() {
        pending = null
        unlocked = false
    }

    fun add(packageName: String) = saveApps(apps + packageName)
    fun remove(packageName: String) = saveApps(apps - packageName)

    private fun saveApps(set: Set<String>) {
        apps = set
        prefs.edit { putStringSet(KEY_APPS, set) }
    }

    /**
     * Sets a new code: a number PIN or (with [password]) anything he likes, letters, symbols, any length (his ask, 23 Oct).
     * A fresh random salt, then the slow hash of code + salt; the code itself is never kept.
     */
    fun setPin(pin: String, password: Boolean = false) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            putBoolean(KEY_MODE, password)
            if (password) remove(KEY_LENGTH) else putInt(KEY_LENGTH, pin.length)
        }
        pinLength = if (password) 0 else pin.length
        passwordMode = password
        hasPin = true
        unlocked = true
    }

    /** Checks a PIN; right = unlocked. Wrong 5 times in a row = 30 seconds of waiting. */
    fun tryPin(pin: String): Boolean {
        if (SystemClock.uptimeMillis() < lockedUntil) return false
        val salt = Base64.decode(prefs.getString(KEY_SALT, "") ?: "", Base64.NO_WRAP)
        val stored = Base64.decode(prefs.getString(KEY_HASH, "") ?: "", Base64.NO_WRAP)
        // A constant-time compare, so the time it takes doesn't give anything away.
        val ok = stored.isNotEmpty() && MessageDigest.isEqual(hash(pin, salt), stored)
        if (ok) {
            wrongTries = 0
            // A PIN made before this existed: learn its length now, so next time no OK is needed.
            if (!passwordMode && pinLength == 0) {
                pinLength = pin.length
                prefs.edit { putInt(KEY_LENGTH, pin.length) }
            }
            unlocked = true
        } else if (++wrongTries >= 5) {
            wrongTries = 0
            lockedUntil = SystemClock.uptimeMillis() + 30_000L
        }
        return ok
    }

    /**
     * A fun lock (24 Oct): an EXTRA way in next to the PIN, not instead of it. [gameKind] = "elements", "seelies" or
     * "lanterns" (null = none). Its code (e.g. "pyro,hydro,electro,cryo") is stored exactly like the PIN: only a salted
     * slow hash, and wrong tries share the PIN's 5-tries-then-30-seconds rule.
     */
    var gameKind by mutableStateOf(prefs.getString(KEY_GAME_KIND, null))
        private set

    fun setGameLock(kind: String, code: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KEY_GAME_KIND, kind)
            putString(KEY_GAME_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_GAME_HASH, Base64.encodeToString(hash(code, salt), Base64.NO_WRAP))
        }
        gameKind = kind
    }

    fun removeGameLock() {
        prefs.edit { remove(KEY_GAME_KIND); remove(KEY_GAME_SALT); remove(KEY_GAME_HASH) }
        gameKind = null
    }

    /** Checks a fun-lock code; right = unlocked. */
    fun tryGame(code: String): Boolean {
        if (gameKind == null || SystemClock.uptimeMillis() < lockedUntil) return false
        val salt = Base64.decode(prefs.getString(KEY_GAME_SALT, "") ?: "", Base64.NO_WRAP)
        val stored = Base64.decode(prefs.getString(KEY_GAME_HASH, "") ?: "", Base64.NO_WRAP)
        val ok = stored.isNotEmpty() && MessageDigest.isEqual(hash(code, salt), stored)
        if (ok) {
            wrongTries = 0
            unlocked = true
        } else if (++wrongTries >= 5) {
            wrongTries = 0
            lockedUntil = SystemClock.uptimeMillis() + 30_000L
        }
        return ok
    }

    /** The fingerprint check passed (Android did it): unlock. */
    fun unlockWithFingerprint() {
        wrongTries = 0
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }

    fun useFingerprint(on: Boolean) {
        fingerprint = on
        prefs.edit { putBoolean(KEY_FINGER, on) }
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 60_000, 256))
            .encoded

    companion object {
        private const val KEY_APPS = "apps"
        private const val KEY_LOCKED = "locked_apps"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_FINGER = "fingerprint"
        private const val KEY_MODE = "password_mode"
        private const val KEY_LENGTH = "pin_length"
        private const val KEY_GAME_KIND = "game_kind"
        private const val KEY_GAME_SALT = "game_salt"
        private const val KEY_GAME_HASH = "game_hash"
        @Volatile private var instance: VaultStore? = null

        fun get(context: Context): VaultStore =
            instance ?: synchronized(this) { instance ?: VaultStore(context.applicationContext).also { instance = it } }
    }
}
