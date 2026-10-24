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
/**
 * The three kinds of code (24 Oct): he makes each once, and every locked app picks which one opens it
 * (his ask: "one has a pin code and the other has a letter code or a fun code"). The ids are saved: never rename.
 */
enum class LockCode(val id: String, val title: String) {
    PIN("pin", "PIN"),
    PASSWORD("password", "Password"),
    FUN("fun", "Fun lock");

    companion object {
        fun byId(id: String?): LockCode? = entries.firstOrNull { it.id == id }
    }
}

/** A locked app waiting for its code: its name (for the screen), which code opens it, and what to do once it's right. */
class PendingOpen(val label: String, val code: LockCode, val open: () -> Unit)

class VaultStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    init {
        // Before 24 Oct there was one code slot; a password lived in the PIN's keys. Move it to its own slot once.
        if (prefs.getBoolean(KEY_MODE, false) && prefs.contains(KEY_HASH) && !prefs.contains(KEY_PW_HASH)) {
            prefs.edit {
                putString(KEY_PW_SALT, prefs.getString(KEY_SALT, ""))
                putString(KEY_PW_HASH, prefs.getString(KEY_HASH, ""))
                remove(KEY_SALT); remove(KEY_HASH); remove(KEY_LENGTH)
            }
        }
    }

    /** Package names of the apps in the vault. */
    var apps by mutableStateOf(prefs.getStringSet(KEY_APPS, emptySet())?.toSet() ?: emptySet())
        private set
    /** A number PIN is made. */
    var hasPinCode by mutableStateOf(prefs.contains(KEY_HASH))
        private set
    /** A password is made. */
    var hasPassword by mutableStateOf(prefs.contains(KEY_PW_HASH))
        private set
    /** Any code at all (the vault needs one before it can be used). */
    val hasPin: Boolean get() = hasPinCode || hasPassword
    var fingerprint by mutableStateOf(prefs.getBoolean(KEY_FINGER, true))
        private set
    /** What opens the VAULT: true = his password, false = his number PIN. Locked apps each pick their own (see [lockCodeOf]). */
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
    fun setLocked(packageName: String, on: Boolean, code: LockCode? = null) {
        locked = if (on) locked + packageName else locked - packageName
        lockCodes = if (on && code != null) lockCodes + (packageName to code) else lockCodes - packageName
        prefs.edit {
            putStringSet(KEY_LOCKED, locked)
            putStringSet(KEY_LOCK_CODES, lockCodes.map { (pkg, c) -> pkg + "|" + c.id }.toSet())
        }
    }

    /** Which code each locked app asked for (apps not in here use the vault's code). */
    private var lockCodes by mutableStateOf(
        prefs.getStringSet(KEY_LOCK_CODES, emptySet()).orEmpty().mapNotNull { e ->
            val parts = e.split('|', limit = 2)
            if (parts.size != 2) null else LockCode.byId(parts[1])?.let { parts[0] to it }
        }.toMap()
    )

    /** The vault's own code. */
    val vaultCode: LockCode get() = if (passwordMode) LockCode.PASSWORD else LockCode.PIN

    fun hasCode(code: LockCode) = when (code) {
        LockCode.PIN -> hasPinCode
        LockCode.PASSWORD -> hasPassword
        LockCode.FUN -> gameKind != null
    }

    /** The codes he has made, in order: the ones a locked app can pick from. */
    val madeCodes: List<LockCode> get() = LockCode.entries.filter { hasCode(it) }

    /** The code that opens [packageName]: its own pick, or the vault's code if it has none (or that code was removed). */
    fun lockCodeOf(packageName: String): LockCode = lockCodes[packageName]?.takeIf { hasCode(it) } ?: vaultCode

    /** A locked app waiting for its code. */
    var pending by mutableStateOf<PendingOpen?>(null)
        private set

    /**
     * Runs [open] straight away, or (for a locked app) first asks for the code. Every way an app opens goes through here:
     * a tap, search, a gesture, a shortcut, a notification.
     */
    fun guard(packageName: String, label: String, open: () -> Unit) {
        if (packageName !in locked || !hasPin || unlocked) open() else pending = PendingOpen(label, lockCodeOf(packageName), open)
    }

    /** The code was right: open the waiting app, and lock again straight after. */
    fun finishPending() {
        val action = pending?.open
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
     * [forVault] = it also becomes the code that opens the vault (and opens it now); false = only made, for locked apps.
     */
    fun setPin(pin: String, password: Boolean = false, forVault: Boolean = true) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            if (password) {
                putString(KEY_PW_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                putString(KEY_PW_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            } else {
                putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
                putInt(KEY_LENGTH, pin.length)
            }
            if (forVault) putBoolean(KEY_MODE, password)
        }
        if (password) hasPassword = true else { hasPinCode = true; pinLength = pin.length }
        if (forVault) {
            passwordMode = password
            unlocked = true
        }
    }

    /** The vault opens with [code] from now on (only a PIN or password, and only if it's made). */
    fun useForVault(code: LockCode) {
        if (code == LockCode.FUN || !hasCode(code)) return
        passwordMode = code == LockCode.PASSWORD
        prefs.edit { putBoolean(KEY_MODE, passwordMode) }
    }

    /** Checks the vault's code; right = unlocked. */
    fun tryPin(pin: String): Boolean = tryCode(pin, vaultCode)

    /** Checks [pin] against his PIN or password; right = unlocked. Wrong 5 times in a row (any code) = 30 seconds of waiting. */
    fun tryCode(pin: String, code: LockCode): Boolean {
        if (SystemClock.uptimeMillis() < lockedUntil) return false
        val password = code == LockCode.PASSWORD
        val salt = Base64.decode(prefs.getString(if (password) KEY_PW_SALT else KEY_SALT, "") ?: "", Base64.NO_WRAP)
        val stored = Base64.decode(prefs.getString(if (password) KEY_PW_HASH else KEY_HASH, "") ?: "", Base64.NO_WRAP)
        // A constant-time compare, so the time it takes doesn't give anything away.
        val ok = stored.isNotEmpty() && MessageDigest.isEqual(hash(pin, salt), stored)
        if (ok) {
            wrongTries = 0
            // A PIN made before this existed: learn its length now, so next time no OK is needed.
            if (!password && pinLength == 0) {
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

    /**
     * Checks a fun-lock code. Right does NOT unlock yet: the game first plays its success animation, then calls
     * [unlockAfterGame] (otherwise the vault would jump open mid-sparkle).
     */
    fun tryGame(code: String): Boolean {
        if (gameKind == null || SystemClock.uptimeMillis() < lockedUntil) return false
        val salt = Base64.decode(prefs.getString(KEY_GAME_SALT, "") ?: "", Base64.NO_WRAP)
        val stored = Base64.decode(prefs.getString(KEY_GAME_HASH, "") ?: "", Base64.NO_WRAP)
        val ok = stored.isNotEmpty() && MessageDigest.isEqual(hash(code, salt), stored)
        if (ok) {
            wrongTries = 0
            gameOk = true
        } else if (++wrongTries >= 5) {
            wrongTries = 0
            lockedUntil = SystemClock.uptimeMillis() + 30_000L
        }
        return ok
    }

    /** Set only by a right [tryGame], so nothing else can open the vault through [unlockAfterGame]. */
    private var gameOk = false

    /** The fun lock's success animation has played: open up. */
    fun unlockAfterGame() {
        if (!gameOk) return
        gameOk = false
        unlocked = true
    }

    /** The fingerprint check passed (Android did it): unlock. */
    fun unlockWithFingerprint() {
        wrongTries = 0
        unlocked = true
    }

    fun lock() {
        unlocked = false
        gameOk = false
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
        private const val KEY_PW_HASH = "pw_hash"
        private const val KEY_PW_SALT = "pw_salt"
        private const val KEY_LOCK_CODES = "lock_codes"
        private const val KEY_GAME_KIND = "game_kind"
        private const val KEY_GAME_SALT = "game_salt"
        private const val KEY_GAME_HASH = "game_hash"
        @Volatile private var instance: VaultStore? = null

        fun get(context: Context): VaultStore =
            instance ?: synchronized(this) { instance ?: VaultStore(context.applicationContext).also { instance = it } }
    }
}
