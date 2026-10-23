package com.shamil.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.ui.theme.LocalLauncherLook

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

/**
 * Type to find an app. Matches appear just above the box, best match nearest your thumb;
 * Enter (the keyboard's "Go") opens the best match.
 */
@Composable
fun SearchPanel(
    apps: List<AppEntry>,
    query: String,
    onQuery: (String) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val results = remember(apps, query) { Apps.search(apps, query).take(6) }
    // Put the cursor in the box straight away, which also brings up the keyboard.
    LaunchedEffect(Unit) { focus.requestFocus() }

    // imePadding = push everything up above the keyboard.
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(end = 56.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        results.asReversed().forEach { app -> AppRow(app) { onAppMenu(app) } }
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(fontFamily = look.font, color = look.text, fontSize = 18.sp),
            cursorBrush = SolidColor(look.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let { Apps.launch(context, it) } }),
            modifier = Modifier
                .padding(start = 38.dp)
                .fillMaxWidth()
                .focusRequester(focus),
            decorationBox = { field ->
                Row(
                    Modifier
                        .background(look.panel, RoundedCornerShape(28.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchGlyph(look.textDim)
                    Spacer(Modifier.width(12.dp))
                    Box {
                        if (query.isEmpty()) {
                            Text("Search apps", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 18.sp))
                        }
                        field()
                    }
                }
            },
        )
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
