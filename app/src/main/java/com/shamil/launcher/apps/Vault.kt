package com.shamil.launcher.apps

import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The PIN vault (his locked feature #7, 23 Oct): apps he moves in here vanish from the A–Z, search, favourites and
 * Hidden apps until he unlocks it (hold • on the rail, then his PIN or fingerprint).
 *
 * Honest limits: this hides apps inside Seelie only. They still exist in the phone's Settings, and another launcher
 * would show them. It keeps prying eyes off his home screen; it is not a safe for data.
 *
 * The PIN itself is never stored: only a salted PBKDF2 hash (the standard way to keep passwords), so even someone
 * reading Seelie's files can't get it back. The PIN's digit count is kept in plain text (so the pad checks by itself).
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
class PendingOpen(val label: String, val code: LockCode, val open: () -> Unit, val packageName: String = "")

class VaultStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    init {
        // Before 24 Oct there was one code slot; a password lived in the PIN's keys. Move it to its own slot once.
        if (prefs.getBoolean(KEY_MODE, false) && prefs.contains(KEY_HASH) && !prefs.contains(KEY_PW_HASH)) {
            prefs.edit {
                putString(KEY_PW_SALT, prefs.getString(KEY_SALT, ""))
                putString(KEY_PW_HASH, prefs.getString(KEY_HASH, ""))
                if (prefs.contains(KEY_HASH_ITER)) putInt(KEY_PW_HASH_ITER, prefs.getInt(KEY_HASH_ITER, OLD_ROUNDS))
                remove(KEY_SALT); remove(KEY_HASH); remove(KEY_LENGTH); remove(KEY_HASH_ITER)
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
    /** How many digits his number PIN has (0 = not known yet), so the pad checks it by itself, no OK needed. Never the PIN itself. */
    var pinLength by mutableIntStateOf(prefs.getInt(KEY_LENGTH, 0))
        private set

    /*
     * The wrong-tries wait (25 Oct, audit S7): 5 wrong codes in a row (any code) = a wait, and each wait after that is
     * longer: 30 seconds, then 5 minutes, then 1 hour (and it stays at 1 hour). A right code resets it all. Saved in the
     * vault's prefs with the wall clock, so Force stop or a restart no longer wipes it (before, it lived in memory).
     */
    /** After 5 wrong codes in a row: no tries until this moment (wall-clock ms, 0 = free). */
    var lockedUntil by mutableLongStateOf(prefs.getLong(KEY_WAIT_UNTIL, 0L))
        private set
    private var wrongTries = prefs.getInt(KEY_WRONG_TRIES, 0)

    /**
     * How long he still has to wait before the next try (0 = he can try). If the phone's clock was set back to skip the
     * wait (now is before the moment the wait started), the wait starts again from now, in full.
     * Changes state, so call it from a tap or a coroutine, never straight from drawing.
     */
    fun lockWaitMs(): Long {
        if (lockedUntil == 0L) return 0L
        val now = System.currentTimeMillis()
        if (now < prefs.getLong(KEY_WAIT_SET_AT, 0L)) startWait(now, waitFor(prefs.getInt(KEY_WAIT_STEP, 1)))
        else if (now >= lockedUntil) {
            // The wait is over (the step count stays, so the next wait is longer).
            lockedUntil = 0L
            prefs.edit { remove(KEY_WAIT_UNTIL); remove(KEY_WAIT_SET_AT) }
            return 0L
        }
        return (lockedUntil - now).coerceAtLeast(0L)
    }

    /** The wait for the [step]th time in a row: 30 seconds, 5 minutes, then 1 hour. */
    private fun waitFor(step: Int): Long = when {
        step <= 1 -> 30_000L
        step == 2 -> 5 * 60_000L
        else -> 60 * 60_000L
    }

    private fun startWait(now: Long, length: Long) {
        lockedUntil = now + length
        prefs.edit { putLong(KEY_WAIT_UNTIL, lockedUntil); putLong(KEY_WAIT_SET_AT, now) }
    }

    /** A wrong code: the 5th in a row starts the next (longer) wait. */
    private fun wrongCode() {
        wrongTries++
        if (wrongTries >= 5) {
            wrongTries = 0
            val step = prefs.getInt(KEY_WAIT_STEP, 0) + 1
            prefs.edit { putInt(KEY_WAIT_STEP, step) }
            startWait(System.currentTimeMillis(), waitFor(step))
        }
        prefs.edit { putInt(KEY_WRONG_TRIES, wrongTries) }
    }

    /** A right code (or fingerprint): every count and wait starts over. */
    private fun rightCode() {
        wrongTries = 0
        lockedUntil = 0L
        prefs.edit { remove(KEY_WRONG_TRIES); remove(KEY_WAIT_UNTIL); remove(KEY_WAIT_SET_AT); remove(KEY_WAIT_STEP) }
    }

    /** A code is being checked right now (the screen says "Checking…" and ignores more tries meanwhile). */
    var checking by mutableStateOf(false)
        private set

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
     * Runs [open] straight away, or (for a locked app, or an app hidden in the vault) first asks for the code. Every way
     * an app opens goes through here: a tap, search, a gesture, a shortcut, a notification, the clock and date.
     * Vault apps ask for the vault's code (25 Oct audit: before, only locked apps were checked). The vault screen still
     * opens its own apps straight away, because it does so while it's unlocked.
     */
    fun guard(packageName: String, label: String, open: () -> Unit) {
        if (!guardsFromOutside(packageName) || unlocked) { pass(packageName); open() }
        else pending = PendingOpen(label, codeFor(packageName), open, packageName)
    }

    /**
     * Asks [packageName]'s code before a change to it (25 Oct audit: Unlock, picking its code, Hide, Move to vault,
     * Uninstall, its shortcuts), then runs [action]. Unlike [guard] it does NOT mark the app as opened the right way,
     * so the lock watcher still locks it if it comes to the front. Apps without a lock just run [action].
     */
    fun askCode(packageName: String, label: String, action: () -> Unit) {
        if (!guardsFromOutside(packageName) || unlocked) action()
        else pending = PendingOpen(label, codeFor(packageName), action)
    }

    /** The code a guarded app asks for: a locked app its own pick, a vault app the vault's code. */
    private fun codeFor(packageName: String): LockCode = if (packageName in locked) lockCodeOf(packageName) else vaultCode

    /** The code was right: open the waiting app, and lock again straight after. */
    fun finishPending() {
        val waiting = pending
        pending = null
        unlocked = false
        if (waiting != null) {
            if (waiting.packageName.isNotEmpty()) pass(waiting.packageName)
            waiting.open()
        }
    }

    /*
     * The lock watcher (lock/SeelieLockService, 25 Oct): locked apps and vault apps that come to the front any other
     * way (recent apps, a notification, another app) get the lock too.
     */

    /** The guarded app he just opened the right way (memory only): the watcher leaves it alone while he's in it. */
    var passed: String? = null
    var passedAt = 0L
        private set

    fun pass(packageName: String) {
        passed = packageName
        passedAt = SystemClock.uptimeMillis()
    }

    /** Apps that need a code wherever they're opened from: locked apps and the apps hidden in the vault. */
    fun guardsFromOutside(packageName: String) = hasPin && (packageName in locked || packageName in apps)

    /** A guarded app came to the front by itself: ask for its code (a vault app: the vault's code), then [open] it. */
    fun askFromOutside(packageName: String, label: String, open: () -> Unit) {
        pending = PendingOpen(label, codeFor(packageName), open, packageName)
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
     * The slow hash runs off the main thread (25 Oct, audit S9), so call it from a coroutine.
     */
    suspend fun setPin(pin: String, password: Boolean = false, forVault: Boolean = true) {
        val slot = if (password) PASSWORD_SLOT else PIN_SLOT
        val (salt, hashed) = withContext(Dispatchers.Default) { newSalt().let { it to hash(pin, it, ROUNDS) } }
        synchronized(this) {
            prefs.edit {
                putSlot(slot, salt, hashed)
                if (!password) putInt(KEY_LENGTH, pin.length)
                if (forVault) putBoolean(KEY_MODE, password)
            }
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

    /**
     * Checks [pin] against his PIN or password; right = unlocked. Wrong 5 times in a row (any code) = a wait (see
     * [lockWaitMs]). Runs the slow hash off the main thread (25 Oct, audit S9), so call it from a coroutine; while it
     * runs [checking] is on and a second try is turned away.
     */
    suspend fun tryCode(pin: String, code: LockCode): Boolean {
        if (checking || lockWaitMs() > 0) return false
        val password = code == LockCode.PASSWORD
        checking = true
        val ok = try { matches(pin, if (password) PASSWORD_SLOT else PIN_SLOT) } finally { checking = false }
        if (ok) {
            rightCode()
            // A PIN made before this existed: learn its length now, so next time no OK is needed.
            if (!password && pinLength == 0) {
                pinLength = pin.length
                prefs.edit { putInt(KEY_LENGTH, pin.length) }
            }
            unlocked = true
        } else wrongCode()
        return ok
    }

    /*
     * The slow hash (25 Oct, audit S9): PBKDF2 with 600,000 rounds now (the OWASP advice), was 60,000. Every hash keeps
     * its own round count next to it, so codes made before still work (no count saved = 60,000). A right code on an old
     * count is quietly scrambled again at 600,000 with a fresh salt.
     */

    /** Where one code's salt, hash and round count live in the prefs. */
    private class Slot(val salt: String, val hash: String, val rounds: String)

    /** Is [input] this slot's code? The hash runs on a background thread; the compare is constant-time. */
    private suspend fun matches(input: String, slot: Slot): Boolean {
        val salt = decode(prefs.getString(slot.salt, null))
        val storedText = prefs.getString(slot.hash, null)
        val stored = decode(storedText)
        if (stored.isEmpty() || salt.isEmpty()) return false
        val rounds = prefs.getInt(slot.rounds, OLD_ROUNDS)
        // A constant-time compare, so the time it takes doesn't give anything away.
        val ok = withContext(Dispatchers.Default) { MessageDigest.isEqual(hash(input, salt, rounds), stored) }
        if (ok && rounds < ROUNDS) rehash(input, slot, storedText)
        return ok
    }

    /** Scrambles a right code again at [ROUNDS] with a fresh salt, in the background (the unlock doesn't wait for it). */
    private fun rehash(input: String, slot: Slot, oldHash: String?) {
        background.launch {
            val salt = newSalt()
            val hashed = hash(input, salt, ROUNDS)
            synchronized(this@VaultStore) {
                // Changed or removed meanwhile (he made a new code): leave the new one alone.
                if (prefs.getString(slot.hash, null) != oldHash) return@launch
                prefs.edit { putSlot(slot, salt, hashed) }
            }
        }
    }

    private fun SharedPreferences.Editor.putSlot(slot: Slot, salt: ByteArray, hashed: ByteArray) {
        putString(slot.salt, Base64.encodeToString(salt, Base64.NO_WRAP))
        putString(slot.hash, Base64.encodeToString(hashed, Base64.NO_WRAP))
        putInt(slot.rounds, ROUNDS)
    }

    private fun decode(text: String?): ByteArray =
        runCatching { Base64.decode(text ?: "", Base64.NO_WRAP) }.getOrDefault(ByteArray(0))

    private fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }

    /** For the background re-scramble only; lives as long as Seelie does. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * A fun lock (24 Oct): a little game as a code for LOCKED APPS only (25 Oct, audit S8: it has far fewer combos than a
     * PIN, so it no longer opens the vault; the vault takes the PIN or password, or the fingerprint). [gameKind] =
     * "elements", "seelies" or "lanterns" (null = none). Its code (e.g. "pyro,hydro,electro,cryo") is stored exactly
     * like the PIN: only a salted slow hash, and wrong tries share the PIN's wait.
     */
    var gameKind by mutableStateOf(prefs.getString(KEY_GAME_KIND, null))
        private set

    /** Saves a new fun-lock code (the slow hash runs off the main thread, so call it from a coroutine). */
    suspend fun setGameLock(kind: String, code: String) {
        val (salt, hashed) = withContext(Dispatchers.Default) { newSalt().let { it to hash(code, it, ROUNDS) } }
        synchronized(this) {
            prefs.edit {
                putString(KEY_GAME_KIND, kind)
                putSlot(GAME_SLOT, salt, hashed)
            }
        }
        gameKind = kind
    }

    fun removeGameLock() {
        synchronized(this) {
            prefs.edit { remove(KEY_GAME_KIND); remove(KEY_GAME_SALT); remove(KEY_GAME_HASH); remove(KEY_GAME_HASH_ITER) }
        }
        gameKind = null
    }

    /**
     * Checks a fun-lock code. Right does NOT unlock yet: the game first plays its success animation, then calls
     * [unlockAfterGame] (otherwise the app would jump open mid-sparkle). Off the main thread, like [tryCode].
     */
    suspend fun tryGame(code: String): Boolean {
        if (gameKind == null || checking || lockWaitMs() > 0) return false
        checking = true
        val ok = try { matches(code, GAME_SLOT) } finally { checking = false }
        if (ok) {
            rightCode()
            gameOk = true
        } else wrongCode()
        return ok
    }

    /** Set only by a right [tryGame], so nothing else can open a locked app through [unlockAfterGame]. */
    private var gameOk = false

    /** The fun lock's success animation has played: open up. */
    fun unlockAfterGame() {
        if (!gameOk) return
        gameOk = false
        unlocked = true
    }

    /**
     * The vault's own code screen is showing (memory only; set by the vault screen while it asks). With [pending] it
     * tells a fingerprint that is still wanted apart from a stale one.
     */
    var vaultAsking = false

    /**
     * The fingerprint check passed (Android did it): unlock. Only when something is waiting for it (25 Oct audit): the
     * vault's code screen or a locked app. A late success with nothing waiting would leave [unlocked] on, and then every
     * locked app would open without its code until Seelie stops.
     */
    fun unlockWithFingerprint() {
        if (pending == null && !vaultAsking) return
        rightCode()
        unlocked = true
    }

    /** Goes up every time the vault locks again (Seelie stops, the vault closes): open notification cards and shortcut pop-ups of locked apps close on it. */
    var lockedAgain by mutableIntStateOf(0)
        private set

    fun lock() {
        unlocked = false
        gameOk = false
        lockedAgain++
    }

    fun useFingerprint(on: Boolean) {
        fingerprint = on
        prefs.edit { putBoolean(KEY_FINGER, on) }
    }

    /** The slow hash. Slow on purpose: never call it on the main thread (see [matches]). */
    private fun hash(pin: String, salt: ByteArray, rounds: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, rounds, 256))
            .encoded

    companion object {
        /** PBKDF2 rounds for new codes (25 Oct, audit S9), and for codes saved before counts were kept. */
        private const val ROUNDS = 600_000
        private const val OLD_ROUNDS = 60_000
        private const val KEY_HASH_ITER = "pin_hash_iter"
        private const val KEY_PW_HASH_ITER = "pw_hash_iter"
        private const val KEY_GAME_HASH_ITER = "game_hash_iter"
        private const val KEY_WRONG_TRIES = "wrong_tries"
        private const val KEY_WAIT_UNTIL = "wait_until"
        private const val KEY_WAIT_SET_AT = "wait_set_at"
        private const val KEY_WAIT_STEP = "wait_step"
        private val PIN_SLOT = Slot(KEY_SALT, KEY_HASH, KEY_HASH_ITER)
        private val PASSWORD_SLOT = Slot(KEY_PW_SALT, KEY_PW_HASH, KEY_PW_HASH_ITER)
        private val GAME_SLOT = Slot(KEY_GAME_SALT, KEY_GAME_HASH, KEY_GAME_HASH_ITER)
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
