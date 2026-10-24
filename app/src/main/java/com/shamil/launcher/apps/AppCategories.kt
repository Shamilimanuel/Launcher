package com.shamil.launcher.apps

import android.content.Context
import android.content.pm.ApplicationInfo

/*
 * Auto categories for the "Smart Launcher-ish" layout (his pick, 24 Oct): favourites grouped under small headings.
 * Android lets apps say what they are (ApplicationInfo.category: game, audio, social, maps…), but many don't, so a
 * few well-known names fill the gaps. Anything still unknown lands under "Other".
 */
object AppCategories {
    const val SOCIAL = "Social"
    const val MEDIA = "Media"
    const val GAMES = "Games"
    const val WORK = "Productivity"
    const val MONEY = "Money"
    const val TRAVEL = "Travel"
    const val NEWS = "News"
    const val TOOLS = "Tools"
    const val OTHER = "Other"

    // Hints from the package name, checked first (Android's own label is often missing or too vague).
    private val HINTS = listOf(
        MONEY to listOf("bank", "paypal", "wallet", "pay.", ".pay", "revolut", "tikkie"),
        SOCIAL to listOf("whatsapp", "snapchat", "discord", "instagram", "telegram", "dialer", "messaging", "android.gm", "outlook",
            "facebook", "twitter", "reddit", "signal", "contacts", "courier"),
        MEDIA to listOf("youtube", "spotify", "crunchyroll", "netflix", "gallery", "camera", "photos", "music", "twitch", "podcast", "video"),
        GAMES to listOf("game", "mihoyo", "hoyolab", "nintendo.znca", "playgames", "roblox", "minecraft"),
        TRAVEL to listOf("maps", "ns.", "9292", "uber", "booking"),
        WORK to listOf("chatgpt", "openai", "anthropic", "bard", "gemini", "canva", "tasks", "calendar", "docs", "keep", "notes",
            "somtoday", "duolingo", "office", "drive", "notion", "yazio"),
        TOOLS to listOf("settings", "authenticator", "files", "fichero", "vending", "znma", "clock", "calculator", "browser", "opera", "chrome"),
    )

    /** The heading this app goes under. Quick enough to call for a few dozen favourites (off the main thread). */
    fun of(context: Context, packageName: String): String {
        val p = packageName.lowercase()
        HINTS.firstOrNull { (_, words) -> words.any { it in p } }?.let { return it.first }
        val category = runCatching { context.packageManager.getApplicationInfo(packageName, 0).category }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> GAMES
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> MEDIA
            ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> NEWS
            ApplicationInfo.CATEGORY_MAPS -> TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> WORK
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> TOOLS
            else -> OTHER
        }
    }
}
