package com.shamil.launcher.apps

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
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
    /** Open right now? Only in memory: every restart, Home press or app launch locks it again. */
    var unlocked by mutableStateOf(false)
        private set
    /** After 5 wrong PINs in a row: no tries until this moment (uptime ms). */
    var lockedUntil by mutableLongStateOf(0L)
        private set
    private var wrongTries = 0

    operator fun contains(packageName: String) = packageName in apps

    fun add(packageName: String) = saveApps(apps + packageName)
    fun remove(packageName: String) = saveApps(apps - packageName)

    private fun saveApps(set: Set<String>) {
        apps = set
        prefs.edit { putStringSet(KEY_APPS, set) }
    }

    /** Sets a new PIN (4-6 digits): a fresh random salt, then the slow hash of PIN + salt. */
    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
        }
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
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_FINGER = "fingerprint"
        @Volatile private var instance: VaultStore? = null

        fun get(context: Context): VaultStore =
            instance ?: synchronized(this) { instance ?: VaultStore(context.applicationContext).also { instance = it } }
    }
}
