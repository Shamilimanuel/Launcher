package com.shamil.launcher.ui.search

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.DrawableRes
import com.shamil.launcher.R
import java.text.Normalizer

/**
 * One screen of the phone's Settings app that search can open.
 * [keywords] are extra words that find it, in English and Dutch ("batterij", "helderheid"...).
 * [actions] are tried in order: some phones don't have every screen, so there's a fallback.
 */
class PhoneSetting(
    val name: String,
    val sub: String,
    @DrawableRes val icon: Int,
    val keywords: List<String>,
    val actions: List<String>,
)

/** A hand-picked list of the Settings screens people look for most, found by name or keyword. */
object PhoneSettings {

    val all = listOf(
        PhoneSetting("Wi-Fi", "Network & internet", R.drawable.ic_settings,
            listOf("wifi", "wlan", "internet", "netwerk", "draadloos", "network"),
            listOf(Settings.ACTION_WIFI_SETTINGS)),
        PhoneSetting("Bluetooth", "Connected devices", R.drawable.ic_settings,
            listOf("bt", "headphones", "earbuds", "koptelefoon", "oordopjes", "apparaten", "devices"),
            listOf(Settings.ACTION_BLUETOOTH_SETTINGS)),
        PhoneSetting("Mobile network", "SIM, mobile data, roaming", R.drawable.ic_settings,
            listOf("mobile data", "mobiele data", "mobiele netwerken", "sim", "4g", "5g", "roaming"),
            listOf(Settings.ACTION_DATA_ROAMING_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)),
        PhoneSetting("Data usage", "How much data apps use", R.drawable.ic_settings,
            listOf("dataverbruik", "data gebruik", "data saver", "databesparing"),
            listOf("android.settings.DATA_USAGE_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)),
        PhoneSetting("Hotspot & tethering", "Share your internet", R.drawable.ic_settings,
            listOf("hotspot", "tethering", "mobiele hotspot", "delen", "share internet"),
            listOf("android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)),
        PhoneSetting("Airplane mode", "Network & internet", R.drawable.ic_settings,
            listOf("flight mode", "vliegtuigmodus", "vliegtuigstand"),
            listOf(Settings.ACTION_AIRPLANE_MODE_SETTINGS)),
        PhoneSetting("Connections", "All network settings", R.drawable.ic_settings,
            listOf("network", "internet", "verbindingen", "netwerk"),
            listOf(Settings.ACTION_WIRELESS_SETTINGS)),
        PhoneSetting("NFC & payments", "Tap to pay", R.drawable.ic_settings,
            listOf("nfc", "contactloos", "betalen", "pay", "tap"),
            listOf(Settings.ACTION_NFC_SETTINGS)),
        PhoneSetting("VPN", "Private networks", R.drawable.ic_lock,
            listOf("vpn", "proxy"),
            listOf(Settings.ACTION_VPN_SETTINGS)),
        PhoneSetting("Battery", "Usage and battery life", R.drawable.ic_battery_full,
            listOf("batterij", "accu", "power", "energie", "opladen", "charging"),
            listOf(Intent.ACTION_POWER_USAGE_SUMMARY, Settings.ACTION_BATTERY_SAVER_SETTINGS)),
        PhoneSetting("Battery saver", "Make the battery last longer", R.drawable.ic_battery_full,
            listOf("energiebesparing", "power saving", "spaarstand", "batterijbesparing"),
            listOf(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
        PhoneSetting("Display", "Brightness, dark mode, screen timeout", R.drawable.ic_visibility,
            listOf("scherm", "helderheid", "brightness", "dark mode", "donkere modus", "donker", "screen", "timeout",
                "font size", "lettergrootte", "night light", "blue light", "oogcomfort"),
            listOf(Settings.ACTION_DISPLAY_SETTINGS)),
        PhoneSetting("Wallpaper", "Home and lock screen background", R.drawable.ic_wallpaper,
            listOf("achtergrond", "background"),
            listOf(Intent.ACTION_SET_WALLPAPER, Settings.ACTION_DISPLAY_SETTINGS)),
        PhoneSetting("Sound & vibration", "Volume, ringtone", R.drawable.ic_settings,
            listOf("sound", "geluid", "geluiden", "volume", "ringtone", "beltoon", "trillen", "vibration", "vibrate"),
            listOf(Settings.ACTION_SOUND_SETTINGS)),
        PhoneSetting("Do not disturb", "Silence calls and alerts", R.drawable.ic_clear_night,
            listOf("dnd", "niet storen", "focus", "quiet", "stil", "silent"),
            listOf(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, Settings.ACTION_SOUND_SETTINGS)),
        PhoneSetting("Notifications", "Which apps may alert you", R.drawable.ic_settings,
            listOf("meldingen", "notificaties", "alerts", "badges"),
            listOf("android.settings.ALL_APPS_NOTIFICATION_SETTINGS", "android.settings.NOTIFICATION_SETTINGS",
                Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)),
        PhoneSetting("Apps", "All installed apps", R.drawable.ic_apps,
            listOf("applicaties", "toepassingen", "apps beheren", "uninstall", "verwijderen", "permissions", "machtigingen"),
            listOf(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, Settings.ACTION_APPLICATION_SETTINGS)),
        PhoneSetting("Default apps", "Home app, browser, phone", R.drawable.ic_home,
            listOf("standaardapps", "standaard apps", "default", "launcher", "browser", "home app", "startscherm"),
            listOf(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_HOME_SETTINGS)),
        PhoneSetting("Storage", "Free up space", R.drawable.ic_settings,
            listOf("opslag", "opslagruimte", "ruimte", "geheugen", "space", "memory"),
            listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        PhoneSetting("Location", "GPS and location access", R.drawable.ic_settings,
            listOf("locatie", "gps", "plaats", "where"),
            listOf(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
        PhoneSetting("Security & lock screen", "PIN, fingerprint, face", R.drawable.ic_lock,
            listOf("beveiliging", "biometrie", "vingerafdruk", "fingerprint", "face", "gezicht", "pin", "password",
                "wachtwoord", "lock screen", "vergrendelscherm", "vergrendeling"),
            listOf(Settings.ACTION_SECURITY_SETTINGS)),
        PhoneSetting("Privacy", "Permissions and data", R.drawable.ic_lock,
            listOf("privacy", "machtigingen", "permissions", "toestemmingen"),
            listOf(Settings.ACTION_PRIVACY_SETTINGS, Settings.ACTION_SECURITY_SETTINGS)),
        PhoneSetting("Accounts & sync", "Google and other accounts", R.drawable.ic_settings,
            listOf("accounts", "google", "sync", "synchroniseren", "back-up", "backup"),
            listOf(Settings.ACTION_SYNC_SETTINGS)),
        PhoneSetting("Accessibility", "Vision, hearing, interaction", R.drawable.ic_touch_app,
            listOf("toegankelijkheid", "talkback", "magnify", "vergroten", "zoom"),
            listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        PhoneSetting("Language", "Phone language and region", R.drawable.ic_text_fields,
            listOf("taal", "talen", "locale", "regio", "region"),
            listOf(Settings.ACTION_LOCALE_SETTINGS)),
        PhoneSetting("Keyboard", "On-screen keyboard", R.drawable.ic_text_fields,
            listOf("toetsenbord", "invoer", "input", "typing", "typen", "gboard", "autocorrect"),
            listOf(Settings.ACTION_INPUT_METHOD_SETTINGS)),
        PhoneSetting("Date & time", "Time zone, 24-hour clock", R.drawable.ic_schedule,
            listOf("datum", "tijd", "tijdzone", "time zone", "clock", "klok"),
            listOf(Settings.ACTION_DATE_SETTINGS)),
        PhoneSetting("Cast", "Show your screen on a TV", R.drawable.ic_settings,
            listOf("casten", "smart view", "scherm delen", "screen mirroring", "chromecast", "tv"),
            listOf(Settings.ACTION_CAST_SETTINGS)),
        PhoneSetting("Developer options", "USB debugging and more", R.drawable.ic_settings,
            listOf("ontwikkelaarsopties", "ontwikkelaar", "developer", "usb debugging", "adb", "animation scale"),
            listOf(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)),
        PhoneSetting("About phone", "Model, Android version", R.drawable.ic_info,
            listOf("over de telefoon", "telefoon info", "info", "android version", "versie", "model", "software"),
            listOf(Settings.ACTION_DEVICE_INFO_SETTINGS)),
        PhoneSetting("All settings", "The Settings app", R.drawable.ic_settings,
            listOf("settings", "instellingen"),
            listOf(Settings.ACTION_SETTINGS)),
    )

    /**
     * Settings that match what was typed, best first: names starting with it, then a word in the name,
     * then a keyword. Needs 2+ letters, so one letter doesn't fill the screen with settings.
     */
    fun search(query: String): List<PhoneSetting> {
        val q = query.trim().searchable()
        if (q.length < 2) return emptyList()
        return all
            .mapNotNull { setting ->
                val name = setting.name.searchable()
                val words = setting.keywords.map { it.searchable() }
                val rank = when {
                    name.startsWith(q) || name.compact().startsWith(q.compact()) -> 0
                    name.split(' ', '-', '&').any { it.startsWith(q) } -> 1
                    words.any { it.startsWith(q) } -> 2
                    words.any { word -> word.split(' ').any { it.startsWith(q) } } -> 3
                    else -> null
                }
                rank?.let { it to setting }
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    /** Opens the first screen this phone has; if none of them work, the main Settings app. */
    fun open(context: Context, setting: PhoneSetting) {
        val opened = setting.actions.any { action -> startSafely(context, Intent(action)) }
        if (!opened && !startSafely(context, Intent(Settings.ACTION_SETTINGS))) toast(context, "Couldn't open ${setting.name}")
    }
}

/** "Batterij" and "bátterij" both become "batterij", so accents and capitals don't matter. */
internal fun String.searchable(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** Only letters and digits: "wi-fi" → "wifi", so it doesn't matter if you type the dash. */
private fun String.compact(): String = filter { it.isLetterOrDigit() }
