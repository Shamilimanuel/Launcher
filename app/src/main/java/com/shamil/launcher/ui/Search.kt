package com.shamil.launcher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.ui.search.ActionCard
import com.shamil.launcher.ui.search.Avatar
import com.shamil.launcher.ui.search.Calculator
import com.shamil.launcher.ui.search.ContactHit
import com.shamil.launcher.ui.search.ContactSearch
import com.shamil.launcher.ui.search.EmptySearch
import com.shamil.launcher.ui.search.Glyph
import com.shamil.launcher.ui.search.GlyphIcon
import com.shamil.launcher.ui.search.ParsedCommand
import com.shamil.launcher.ui.search.PhoneSetting
import com.shamil.launcher.ui.search.PhoneSettings
import com.shamil.launcher.ui.search.QuickNotes
import com.shamil.launcher.ui.search.ResultBadge
import com.shamil.launcher.ui.search.ResultGroup
import com.shamil.launcher.ui.search.ResultRow
import com.shamil.launcher.ui.search.SearchCommand
import com.shamil.launcher.ui.search.SearchCommands
import com.shamil.launcher.ui.search.SearchGroup
import com.shamil.launcher.ui.search.SearchStore
import com.shamil.launcher.ui.search.Torch
import com.shamil.launcher.ui.search.WebSearch
import com.shamil.launcher.ui.search.copyText
import com.shamil.launcher.ui.search.toast
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The round search button that floats bottom-right while the app list is showing. */
@Composable
fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLauncherLook.current
    Box(
        modifier
            .size(56.dp)
            .background(look.panel, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SearchGlyph(look.text)
    }
}

/** What Enter (the keyboard's Search key) opens: the row that's lit up. */
private sealed interface Best {
    data object Sum : Best
    data class App(val app: AppEntry) : Best
    data class Contact(val contact: ContactHit) : Best
    data class Setting(val setting: PhoneSetting) : Best
    data object Web : Best
}

/**
 * Seelie's universal search. Type to find apps, phone settings, contacts and the web, grouped in cards.
 * The best app match sits at the BOTTOM, nearest your thumb; Enter (the keyboard's Search key) opens the lit-up row.
 * A plain sum ("12*7") shows its answer on top, and "> …" turns the box into the command bar.
 * [onClose] is called after something opened (an app, a timer…), so the home can close the search.
 */
@Composable
fun SearchPanel(
    apps: List<AppEntry>,
    query: String,
    onQuery: (String) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onClose: () -> Unit = {},
) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val store = remember { SearchStore.get(context) }
    val focus = remember { FocusRequester() }
    // Put the cursor in the box straight away, which also brings up the keyboard.
    LaunchedEffect(Unit) { focus.requestFocus() }

    // The box keeps its own copy (text + cursor), so when a chip fills in "> timer ", the cursor lands at the end.
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    if (field.text != query) field = TextFieldValue(query, TextRange(query.length))

    val trimmed = query.trim()
    val command = SearchCommands.parse(query)   // not null = command mode (it starts with ">")
    val searching = command == null && trimmed.isNotEmpty()

    // These are quick and run as you type, so apps stay instant.
    val sum = if (searching && store.isOn(SearchGroup.Calc)) Calculator.answer(trimmed) else null
    val appsOn = store.isOn(SearchGroup.Apps)
    val appHits = remember(apps, trimmed, appsOn, searching) {
        if (searching && appsOn) Apps.search(apps, trimmed).take(5) else emptyList()
    }
    val settingHits = if (searching && store.isOn(SearchGroup.Settings)) PhoneSettings.search(trimmed).take(3) else emptyList()

    // Contacts come from the phone's database: asked off the main thread, 120 ms after you stop typing.
    val contactsOn = searching && store.isOn(SearchGroup.Contacts)
    var contactsAllowed by remember { mutableStateOf(ContactSearch.allowed(context)) }
    var contactsRefused by remember { mutableStateOf(false) }
    var contactHits by remember { mutableStateOf(emptyList<ContactHit>()) }
    // Android's own "Allow Seelie to access your contacts?" pop-up.
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsAllowed = granted
        contactsRefused = !granted
    }
    LaunchedEffect(trimmed, contactsOn, contactsAllowed) {
        // Checked again each time, in case he allowed it in the phone's settings in the meantime.
        contactsAllowed = ContactSearch.allowed(context)
        if (!contactsOn || !contactsAllowed || trimmed.length < 2) {
            contactHits = emptyList()
            return@LaunchedEffect
        }
        delay(120)   // "debounce": wait until typing pauses, so fast typing doesn't ask the database every letter
        contactHits = withContext(Dispatchers.IO) { ContactSearch.find(context, trimmed) }
    }

    val webOn = store.isOn(SearchGroup.Web)
    val playOn = store.isOn(SearchGroup.Play)
    val best: Best? = when {
        !searching -> null
        sum != null -> Best.Sum
        appHits.isNotEmpty() -> Best.App(appHits.first())
        contactHits.isNotEmpty() -> Best.Contact(contactHits.first())
        settingHits.isNotEmpty() -> Best.Setting(settingHits.first())
        webOn -> Best.Web
        else -> null
    }

    fun runCommand(parsed: ParsedCommand) {
        if (SearchCommands.run(context, parsed)) {
            onQuery("")
            onClose()
        }
    }
    fun openBest() {
        when (val b = best) {
            Best.Sum -> sum?.let { copyText(context, it) }
            is Best.App -> { Apps.launch(context, b.app); onClose() }
            is Best.Contact -> { ContactSearch.open(context, b.contact); onClose() }
            is Best.Setting -> { PhoneSettings.open(context, b.setting); onClose() }
            Best.Web -> { WebSearch.search(context, store.engine, trimmed); onClose() }
            null -> if (command != null) runCommand(command) else if (trimmed.isNotEmpty()) toast(context, "Nothing found")
        }
    }

    // imePadding = push everything up above the keyboard.
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(end = 56.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        // The results scroll, but start at the bottom (reverseScrolling), right above the box.
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState(), reverseScrolling = true)
                .padding(start = 38.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                command != null -> CommandResults(command, onFill = onQuery, onRun = { runCommand(command) })
                trimmed.isEmpty() -> EmptySearch(commandsOn = true, onPick = onQuery)
                else -> {
                    // Top to bottom: answer, web, settings, contacts, apps (apps last = nearest the thumb).
                    if (sum != null) {
                        ActionCard(Glyph.Calc, "$trimmed =", sum, big = true, toggle = null, keyHint = "Copy") { copyText(context, sum) }
                    }
                    if (webOn || playOn) {
                        ResultGroup("Web", note = if (webOn) store.engine.label else "") {
                            if (playOn) {
                                ResultRow("Search Play Store for “$trimmed”", leading = { ResultBadge(Glyph.Store) }) {
                                    WebSearch.playStore(context, trimmed)
                                    onClose()
                                }
                            }
                            if (webOn) {
                                ResultRow("Search the web for “$trimmed”", best = best == Best.Web, leading = { ResultBadge(Glyph.Globe) }) {
                                    WebSearch.search(context, store.engine, trimmed)
                                    onClose()
                                }
                            }
                        }
                    }
                    if (settingHits.isNotEmpty()) {
                        ResultGroup("Settings") {
                            settingHits.asReversed().forEach { setting ->
                                ResultRow(setting.name, sub = setting.sub, best = best == Best.Setting(setting),
                                    leading = { ResultBadge(icon = setting.icon) }) {
                                    PhoneSettings.open(context, setting)
                                    onClose()
                                }
                            }
                        }
                    }
                    if (contactsOn && (contactHits.isNotEmpty() || !contactsAllowed)) {
                        ResultGroup("Contacts") {
                            if (!contactsAllowed) {
                                // Not allowed yet: one row that asks. Said "Don't allow"? Then it opens Seelie's App info.
                                val text = if (contactsRefused) "Search contacts: allow in App info" else "Search contacts: allow access"
                                ResultRow(text, leading = { ResultBadge(Glyph.Person) }) {
                                    if (contactsRefused) ContactSearch.openPermissionSettings(context)
                                    else askContacts.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                            contactHits.asReversed().forEach { contact ->
                                ResultRow(contact.name, best = best == Best.Contact(contact), leading = { Avatar(contact.name) }) {
                                    ContactSearch.open(context, contact)
                                    onClose()
                                }
                            }
                        }
                    }
                    if (appHits.isNotEmpty()) {
                        ResultGroup("Apps", note = "best match nearest your thumb") {
                            appHits.asReversed().forEach { app ->
                                ResultRow(
                                    app.label,
                                    best = best == Best.App(app),
                                    leading = { Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp * look.iconScale)) },
                                    onLongClick = { onAppMenu(app) },
                                ) {
                                    Apps.launch(context, app)
                                    onClose()
                                }
                            }
                        }
                    }
                    if (sum == null && appHits.isEmpty() && settingHits.isEmpty() && contactHits.isEmpty()) {
                        Text(
                            "Nothing on this phone called “$trimmed”",
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp, shadow = look.shadow),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val commandMode = command != null
        NoKeyboardLearning {
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                if (it.text != query) onQuery(it.text)
            },
            singleLine = true,
            // In command mode the letters turn into typewriter letters, like his Terminal widget.
            textStyle = TextStyle(
                fontFamily = if (commandMode) FontFamily.Monospace else look.font,
                color = look.text,
                fontSize = if (commandMode) 16.sp else 18.sp,
            ),
            cursorBrush = SolidColor(look.accent),
            // No autocorrect: it would "fix" > commands and sums. The Search key opens the best match.
            // 25 Oct (audit S22): no capitals either, and NoKeyboardLearning around it asks the keyboard not to learn.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { openBest() }),
            modifier = Modifier
                .padding(start = 38.dp)
                .fillMaxWidth()
                .focusRequester(focus),
            decorationBox = { inner ->
                val shape = RoundedCornerShape(28.dp)
                Row(
                    Modifier
                        .background(look.panel, shape)
                        // Command mode: an accent ring and a faint accent tint, so you see you're in the command bar.
                        .background(if (commandMode) look.accent.copy(alpha = 0.10f) else Color.Transparent, shape)
                        .border(1.5.dp, if (commandMode) look.accent else Color.Transparent, shape)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (commandMode) GlyphIcon(Glyph.Command, look.accent) else SearchGlyph(look.textDim)
                    Spacer(Modifier.width(12.dp))
                    Box {
                        if (query.isEmpty()) {
                            Text("Search, or type >", maxLines = 1,
                                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 18.sp))
                        }
                        inner()
                    }
                }
            },
        )
        }
    }
}

/**
 * Asks the keyboard not to learn from the text box inside (25 Oct, audit S22): Android's
 * IME_FLAG_NO_PERSONALIZED_LEARNING, the "incognito" flag. KeyboardOptions has no field for it, so this catches the
 * moment the text box starts the keyboard and adds the flag. Most keyboards (Gboard, Samsung) respect it; it's a
 * request, not a lock.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NoKeyboardLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = object : PlatformTextInputInterceptor {
            override suspend fun interceptStartInputMethod(
                request: PlatformTextInputMethodRequest,
                nextHandler: PlatformTextInputSession,
            ): Nothing {
                val noLearning = object : PlatformTextInputMethodRequest {
                    override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                        request.createInputConnection(outAttributes).also {
                            outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        }
                }
                nextHandler.startInputMethod(noLearning)
            }
        },
        content = content,
    )
}

/** Command mode: the card for a complete command, or the commands that match what's typed so far. */
@Composable
private fun ColumnScope.CommandResults(parsed: ParsedCommand, onFill: (String) -> Unit, onRun: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    // Follow the torch's real state while its card could show.
    LaunchedEffect(parsed.command) { if (parsed.command == SearchCommand.Torch) Torch.watch(context) }
    val preview = SearchCommands.preview(context, parsed)
    if (preview != null) {
        ActionCard(preview.glyph, preview.small, preview.title, big = preview.big, toggle = preview.toggle,
            keyHint = if (preview.ready) "Enter" else "…", onClick = onRun)
        return
    }
    // "> note" on its own: show the saved notes too. Tap = copy, hold = delete.
    if (parsed.command == SearchCommand.Note) {
        val notes = QuickNotes.get(context)
        val latest = notes.notes.take(5)
        if (latest.isNotEmpty()) {
            ResultGroup("Your notes", note = "tap to copy, hold to delete") {
                latest.asReversed().forEach { note ->
                    ResultRow(note.text, leading = { ResultBadge(Glyph.Note) }, onLongClick = {
                        notes.remove(note)
                        toast(context, "Note deleted")
                    }) { copyText(context, note.text) }
                }
            }
        }
    }
    val matching = SearchCommands.suggestions(parsed.word)
    if (matching.isEmpty()) {
        Text(
            "No command “${parsed.word}”. Try one of these:",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp, shadow = look.shadow),
        )
    }
    ResultGroup("Commands", note = "tap to fill in") {
        matching.ifEmpty { SearchCommand.entries }.asReversed().forEach { c ->
            ResultRow("> ${c.example}", sub = c.what, monospace = true, leading = { ResultBadge(c.glyph) }) {
                // Commands that need more (a time, a sum) get "> timer " so he can type the rest.
                onFill(if (c.needsText) "> ${c.word} " else "> ${c.word}")
            }
        }
    }
}

/** A magnifying glass, drawn in code: a circle plus a short handle. */
@Composable
private fun SearchGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.2.dp.toPx()
        val r = size.minDimension * 0.32f
        val centre = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color, radius = r, center = centre, style = Stroke(stroke))
        drawLine(
            color,
            start = centre + Offset(r * 0.72f, r * 0.72f),
            end = Offset(size.width * 0.9f, size.height * 0.9f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
