package com.shamil.launcher.settings

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.shamil.launcher.R

/*
 * Everything the settings search can find (24 Oct: one entry per row of the settings mockup's inventory, so every
 * setting is findable, not just 13 pages). Each hit opens its page AND the right tab.
 * Words are English and Dutch, since his phone is Dutch.
 */

/** One setting: its name, the page and tab it lives on, and extra words that should also find it. */
internal class SettingHit(
    val title: String, val node: Node, val tab: String?, val words: String,
    @DrawableRes val icon: Int = node.icon, val tint: Color = node.tint,
) {
    /** "Clock & date · Date": where it lives, shown under the title. */
    val where: String = node.title + (tab?.let { " · $it" } ?: "")

    /** Every word he typed has to fit somewhere ("clock colour" finds the clock's colour, not every colour). */
    fun matches(query: String): Boolean = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }.all { q ->
        title.lowercase().contains(q) || where.lowercase().contains(q) || words.split(' ').any { it.startsWith(q) || q in it }
    }
}

private fun hit(title: String, node: Node, tab: String?, words: String, @DrawableRes icon: Int = node.icon, tint: Color = node.tint) =
    SettingHit(title, node, tab, words, icon, tint)

internal val SETTINGS_INDEX = listOf(
    // ---- Background
    hit("Choose a wallpaper from your gallery", Node.Background, "Wallpaper", "wallpaper achtergrond background picture foto photo gallery galerij kies pick"),
    hit("Wallpaper on home, lock screen or both", Node.Background, "Wallpaper", "lock screen vergrendelscherm home both beide put"),
    hit("Colours follow your wallpaper", Node.Background, "Wallpaper", "material you dynamic kleuren colours colors follow volgen adapt swatches keep theme"),
    hit("Seelie's own wallpapers", Node.Background, "Wallpaper", "collection collectie folders mappen achtergrond seelie own eigen", R.drawable.ic_star, Geo),
    hit("Living background on / off", Node.Background, "Living", "living background achtergrond live animated bewegend levend"),
    hit("Living background presets", Node.Background, "Living", "presets cozy gilded night sakura dusk thunder calm off start mix"),
    hit("Effects: petals, stars, rain, snow…", Node.Background, "Living", "effects effecten petals blossom sakura bloesem leaves bladeren butterflies vlinders dandelions paardenbloem lanterns lantaarns fireflies vuurvliegjes motes stars sterren aurora rain regen snow sneeuw storm onweer clouds wolken mist glints water tilt kantelen density amount hoeveelheid"),
    hit("In battery saver: light version or pause", Node.Background, "Scene", "battery batterij saver energiebesparing light pause pauze", R.drawable.ic_battery_saver),
    hit("Time of day", Node.Background, "Scene", "time tijd dawn dageraad day dag dusk schemer night nacht auto"),
    hit("Weather in the background", Node.Background, "Scene", "weather weer real echt clear helder rain regen storm snow sneeuw mist"),
    hit("Colour mood", Node.Background, "Scene", "mood sfeer colour kleur gold goud pink roze violet paars green groen"),
    hit("Calm mode", Node.Background, "Scene", "calm rustig slow langzaam lightning bliksem"),
    hit("Frame rate (30 / 60 fps)", Node.Background, "Scene", "fps frame rate smooth vloeiend battery batterij"),
    hit("Background fade on / off", Node.Background, "Fade", "fade vervagen melt gradient verloop readable"),
    hit("Fade: how far down, softness, colour", Node.Background, "Fade", "fade reach softness zachtheid colour kleur black zwart navy theme"),

    // ---- Clock & date
    hit("Clock face (12 faces)", Node.Clock, "Clock", "clock klok face wijzerplaat time tijd neon words woorden big day spirit stacked classic style stijl"),
    hit("Clock font, size, weight, colour", Node.Clock, "Clock", "clock klok font lettertype size grootte weight dik bold light colour kleur gold goud glass cinzel"),
    hit("Clock on / off", Node.Clock, "Clock", "clock klok hide verbergen show tonen off uit"),
    hit("Clock position (left, centre, right)", Node.Clock, "Clock", "position positie align uitlijnen left links centre center midden right rechts"),
    hit("Clock: how far down", Node.Clock, "Clock", "down omlaag lower lager height hoogte top thumb duim"),
    hit("Clock size", Node.Clock, "Clock", "size grootte bigger groter smaller kleiner scale"),
    hit("Date look (12 looks)", Node.Clock, "Date", "date datum day dag look stamp pill caps capitals hoofdletters badge serif style stijl"),
    hit("Date size, colour, hairline", Node.Clock, "Date", "date datum size grootte colour kleur hairline lijn rule"),
    hit("Date on / off + position", Node.Clock, "Date", "date datum hide verbergen align uitlijnen position positie"),
    hit("Line under the date: style, size, colour", Node.Clock, "Glance line", "glance line regel plain pill glow size grootte colour kleur inline one line", R.drawable.ic_sunny),
    hit("Glance line on / off + position", Node.Clock, "Glance line", "glance line regel hide verbergen position positie align uitlijnen", R.drawable.ic_sunny),
    hit("What the line shows: weather, battery %, next event", Node.Clock, "Glance line", "weather weer battery batterij percentage event afspraak agenda calendar kalender", R.drawable.ic_sunny),
    hit("Spacing: clock, date, line, widget, left edge", Node.Clock, "Spacing", "spacing ruimte afstand gap tussenruimte edge rand margin marge"),
    hit("Seelie spirit: breathing colon + sparkle", Node.Clock, "Spacing", "spirit geest colon dubbelepunt breathing ademen touch"),
    hit("Sparkles by the clock", Node.Clock, "Spacing", "sparkles sterretjes glitter twinkle fonkelen"),

    // ---- Widgets
    hit("Widget stack: order, size, remove", Node.Widgets, "Stack", "widget widgets stack stapel order volgorde remove verwijderen size grootte reset calendar kalender month maand gauges meters notes notities week"),
    hit("Add a widget (built-in or from an app)", Node.Widgets, "Stack", "add toevoegen widget app spotify agenda picker kiezen", R.drawable.ic_add_circle),
    hit("Show the widgets on the home", Node.Widgets, "Stack", "show tonen hide verbergen widgets off uit"),
    hit("Widget card style", Node.Widgets, "Card", "card kaart style stijl none geen glass glas solid outline"),
    hit("Card: see-through", Node.Widgets, "Card", "see-through doorzichtig transparent transparant opacity"),
    hit("Card: corner rounding", Node.Widgets, "Card", "corners hoeken radius rounded rond"),
    hit("Card: border, size, headings, today marker", Node.Widgets, "Card", "border rand compact full headings koppen rings ringen gold goud today vandaag marker"),
    hit("Stats card: which stats", Node.Widgets, "Stats", "stats statistieken battery batterij memory geheugen storage opslag temperature temperatuur uptime wifi", R.drawable.ic_battery_full, Hydro),
    hit("Stats card weather: hours, °C / °F", Node.Widgets, "Stats", "weather weer hours uren celsius fahrenheit temperature temperatuur", R.drawable.ic_battery_full, Hydro),
    hit("Stats rings: style, thickness, number", Node.Widgets, "Stats", "rings ringen gauge meter bars balken thickness dikte number getal", R.drawable.ic_battery_full, Hydro),

    // ---- Music card
    hit("Music: notification access", Node.Music, "Card", "access toegang notification melding permission toestemming music muziek"),
    hit("Music card on the home", Node.Music, "Card", "music muziek spotify player speler song nummer media show tonen hide verbergen"),
    hit("Music card size", Node.Music, "Card", "size grootte large groot compact"),
    hit("Music card look: home style, its own, album art", Node.Music, "Card", "look album art hoes card kaart glass solid outline see-through doorzichtig corners hoeken"),
    hit("Cover art: shape", Node.Music, "Cover", "cover hoes shape vorm rounded circle cirkel vinyl record plaat"),
    hit("Cover art: size", Node.Music, "Cover", "cover hoes size grootte small medium large"),
    hit("Cover glow + accent colour", Node.Music, "Cover", "glow gloed accent colour kleur gold goud album art"),
    hit("Music buttons: play, ⏮ ⏭, extra", Node.Music, "Controls", "buttons knoppen play afspelen skip next previous shuffle like"),
    hit("Progress bar + cover side", Node.Music, "Controls", "progress voortgang bar balk wavy golvend thin dun thick dik cover left links right rechts layout"),
    hit("Music text: title size, artist, app", Node.Music, "Controls", "text tekst title titel artist artiest app name naam"),
    hit("When nothing plays", Node.Music, "Controls", "idle nothing playing niets hide verbergen line regel keep"),

    // ---- Favourites
    hit("Edit favourites", Node.Favourites, "List", "edit bewerken favourites favorieten add toevoegen order volgorde drag slepen"),
    hit("Show the favourites on the home", Node.Favourites, "List", "favourites favorieten show tonen hide verbergen"),
    hit("Group favourites under headings", Node.Favourites, "List", "group groeperen categories categorie headings koppen smart launcher auto"),
    hit("Favourites icons: normal, small, text only", Node.Favourites, "List", "icons iconen small klein text tekst only alleen"),
    hit("Hidden apps", Node.Favourites, "Apps", "hidden verborgen hide verbergen apps show tonen", R.drawable.ic_visibility_off),
    hit("Rename an app", Node.Favourites, "Apps", "rename hernoemen name naam label", R.drawable.ic_edit),
    hit("Swipe-left action per app", Node.Favourites, "Apps", "swipe veeg gesture gebaar left links action actie shortcut", R.drawable.ic_touch_app),
    hit("Notification access", Node.Favourites, "Notifications", "access toegang notifications meldingen permission", R.drawable.ic_sticky_note_2, Pyro),
    hit("Notification lines + message text", Node.Favourites, "Notifications", "notifications meldingen messages berichten lines regels text tekst privacy", R.drawable.ic_sticky_note_2, Pyro),
    hit("Quick reply per app", Node.Favourites, "Notifications", "reply antwoorden whatsapp quick snel per app line regel", R.drawable.ic_sticky_note_2, Pyro),
    hit("Quick toggles: show + which", Node.Favourites, "Toggles", "toggles schakelaars wifi bluetooth torch zaklamp quiet stil dnd niet storen volume hotspot location locatie airplane vliegtuig nfc", R.drawable.ic_flashlight_on, Anemo),
    hit("Toggles: where they sit, line-up", Node.Favourites, "Toggles", "place plek after na favourites line up uitlijnen centred spread", R.drawable.ic_flashlight_on, Anemo),
    hit("Toggles: size, spacing, shape, names", Node.Favourites, "Toggles", "size grootte spacing ruimte shape vorm circle squircle pill names namen labels", R.drawable.ic_flashlight_on, Anemo),
    hit("Toggles: colours, glow, off look, card", Node.Favourites, "Toggles", "colour kleur glow gloed off uit card kaart backing", R.drawable.ic_flashlight_on, Anemo),

    // ---- A–Z rail
    hit("A–Z rail style (9 styles)", Node.Rail, "Style", "rail alfabet balk az a-z letters wave golf niagara guide arc boog constellation sterrenbeeld lanterns lantaarns fan waaier wheel wiel glider"),
    hit("A–Z letter colour", Node.Rail, "Style", "letters colour kleur accent text tekst"),
    hit("Left thumb / rail on the left", Node.Rail, "Feel", "left links thumb duim left-handed linkshandig side kant edge rand mirror spiegel"),
    hit("Slide to open, hold •", Node.Rail, "Feel", "slide swipe vegen open openen vault kluis hold vasthouden dot"),

    // ---- Search
    hit("Search in: calculator, apps, settings, contacts, web, Play Store", Node.Search, null, "search zoeken calculator rekenmachine apps settings instellingen contacts contacten web play store"),
    hit("Web search engine", Node.Search, null, "engine zoekmachine duckduckgo google bing brave web"),
    hit("The > commands", Node.Search, null, "commands opdrachten timer torch zaklamp calc note notitie alarm wekker"),

    // ---- Theme & style
    hit("Theme", Node.Style, "Theme", "theme thema colours colors kleuren night gold fuji inazuma sumeru match wallpaper"),
    hit("Text colour", Node.Style, "Colours", "text tekst colour color kleur letters white wit cream gold goud lavender mint"),
    hit("Colour these parts", Node.Style, "Colours", "parts onderdelen apps clock klok date datum line widgets rail menus"),
    hit("Font", Node.Style, "Fonts", "font fonts lettertype typeface nunito cinzel josefin lexend kalam manrope", R.drawable.ic_text_fields, Hydro),
    hit("Seelie icon styles (Themed, Glass)", Node.Style, "Icons", "icons iconen themed glass glas pictogram style stijl", R.drawable.ic_apps, Cryo),
    hit("Icon pack", Node.Style, "Icons", "icon pack darko pictogram original", R.drawable.ic_apps, Cryo),
    hit("Icon size", Node.Style, "Icons", "icon size grootte bigger groter smaller kleiner", R.drawable.ic_apps, Cryo),
    hit("Home style: Seelie, Poster, Radial wheel", Node.Style, "Bundles & layouts", "home style startscherm stijl poster wheel wiel radial painting schilderij mode modus", R.drawable.ic_home_filled, Geo),
    hit("Home style bundle", Node.Style, "Bundles & layouts", "bundle bundel home style stijl glass floating gold minimal spirit reset", R.drawable.ic_home_filled, Electro),
    hit("Layouts (Seelie now, Big day…)", Node.Style, "Bundles & layouts", "layout layouts indeling preset seelie now spirit hour big day calm night smart launcher", R.drawable.ic_drag_indicator, Geo),
    hit("Block order", Node.Style, "Bundles & layouts", "order volgorde blocks blokken move verplaatsen up omhoog down omlaag", R.drawable.ic_drag_indicator, Geo),

    // ---- Privacy & vault
    hit("PIN look", Node.Privacy, null, "pin look compact glass seelie dots light trail round lock slot keypad"),
    hit("Vault: hidden apps, codes, fingerprint, fun lock", Node.Privacy, null, "vault kluis hidden verborgen pin password wachtwoord fingerprint vingerafdruk fun lock game spel code"),
    hit("Lock one app with a code", Node.Privacy, null, "lock slot code app per app"),

    // ---- Saves & backup
    hit("Your saves (themes)", Node.Saves, "Saves", "save saves opslaan saved looks profile profiel preset wallpaper apply toepassen rename hernoemen delete", R.drawable.ic_star, Geo),
    hit("Save the current look", Node.Saves, "Saves", "save opslaan current huidige look theme thema", R.drawable.ic_star, Geo),
    hit("Backup & restore", Node.Saves, "Backup & share", "backup back-up restore herstellen file bestand"),
    hit("Share or import a theme", Node.Saves, "Backup & share", "share delen import importeren file bestand link"),
    hit("Import from Niagara", Node.Saves, "Backup & share", "niagara import importeren favourites favorieten names namen"),

    // ---- About
    hit("Default home app", Node.About, null, "home launcher default standaard startscherm niagara switch wissel", R.drawable.ic_home),
    hit("Rescue screen (safe mode)", Node.About, null, "rescue safe mode veilige modus crash reset herstel broken kapot", R.drawable.ic_settings),
    hit("Version", Node.About, null, "version versie about over"),
)
