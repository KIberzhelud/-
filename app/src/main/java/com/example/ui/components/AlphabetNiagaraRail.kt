package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KeyboardSearchMatcher
import com.example.model.AlphabetMode
import com.example.model.AppEntry
import com.example.model.ContactEntry
import com.example.model.MainTab
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesAndAlphabetRail(
    activeTab: MainTab,
    alphabetMode: AlphabetMode,
    favoriteApps: List<AppEntry>,
    starredContacts: List<ContactEntry>,
    presentLetters: Set<Char>,
    activeScrubLetter: Char?,
    highlightedLetter: Char?,
    onToggleAlphabetMode: () -> Unit,
    onScrubLetter: (Char?) -> Unit,
    onTapLetter: (Char) -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onCallContact: (ContactEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val letters = remember(alphabetMode) {
        when (alphabetMode) {
            AlphabetMode.LATIN -> KeyboardSearchMatcher.latinAlphabet
            AlphabetMode.CYRILLIC -> KeyboardSearchMatcher.cyrillicAlphabet
        }
    }

    var scrubberHeightPx by remember { mutableIntStateOf(1) }
    val activeIdx = activeScrubLetter?.let { letters.indexOf(it) } ?: -1

    Box(modifier = modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(
                    if (theme.isGlassMode) theme.surface.copy(alpha = 0.88f)
                    else theme.background
                )
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(theme.chromeBorderOuter)
            )

            val showFavoritesColumn = when (activeTab) {
                MainTab.CALLS -> starredContacts.isNotEmpty()
                else -> favoriteApps.isNotEmpty()
            }

            // Favorites Column (only visible when at least one favorite app/contact exists)
            if (showFavoritesColumn) {
                Column(
                    modifier = Modifier
                        .width(66.dp)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp, horizontal = 3.dp)
                        .testTag("favorites_panel_column"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (activeTab == MainTab.CALLS) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(starredContacts, key = { "${it.id}_${it.number}" }) { contact ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onCallContact(contact) }
                                        .padding(vertical = 4.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .vistaInsetBox(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(com.example.R.drawable.ic_symbian_contacts),
                                            contentDescription = contact.name,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("favorites_rail_list"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(favoriteApps, key = { it.id }) { app ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .combinedClickable(
                                            onClick = { onLaunchApp(app) },
                                            onLongClick = { onLongPressApp(app) }
                                        )
                                        .padding(vertical = 3.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (app.icon != null) {
                                        Image(
                                            bitmap = app.icon,
                                            contentDescription = app.label,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Niagara Alphabet Scrubber Column
            Column(
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // EN / RU Alphabet Toggle Pill (Vista Aero)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .vistaAeroButton(isPrimary = false, isActive = true)
                        .clickable(onClick = onToggleAlphabetMode)
                        .padding(vertical = 3.dp)
                        .testTag("alphabet_mode_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = alphabetMode.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = theme.accent,
                        maxLines = 1
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { scrubberHeightPx = it.height.coerceAtLeast(1) }
                        .pointerInput(letters) {
                            detectTapGestures { offset ->
                                val idx = ((offset.y / scrubberHeightPx) * letters.size)
                                    .toInt()
                                    .coerceIn(0, letters.lastIndex)
                                onTapLetter(letters[idx])
                            }
                        }
                        .pointerInput(letters) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    val idx = ((offset.y / scrubberHeightPx) * letters.size)
                                        .toInt()
                                        .coerceIn(0, letters.lastIndex)
                                    onScrubLetter(letters[idx])
                                },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    val idx = ((change.position.y / scrubberHeightPx) * letters.size)
                                        .toInt()
                                        .coerceIn(0, letters.lastIndex)
                                    onScrubLetter(letters[idx])
                                },
                                onDragEnd = { onScrubLetter(null) },
                                onDragCancel = { onScrubLetter(null) }
                            )
                        }
                        .testTag("niagara_alphabet_rail")
                ) {
                    val stepDp = maxHeight / letters.size
                    val letterFontSize = if (stepDp < 14.dp) 9.sp else 10.sp

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        letters.forEachIndexed { index, ch ->
                            val isScrubbed = activeScrubLetter == ch
                            val isHighlighted = highlightedLetter == ch
                            val hasMatches = presentLetters.contains(ch)
                            val waveOffsetDp = if (activeIdx >= 0) {
                                val dist = abs(index - activeIdx)
                                when (dist) {
                                    0 -> (-14).dp
                                    1 -> (-8).dp
                                    2 -> (-3).dp
                                    else -> 0.dp
                                }
                            } else {
                                0.dp
                            }

                            Text(
                                text = ch.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (isScrubbed) 12.sp else letterFontSize,
                                    fontWeight = if (isScrubbed || isHighlighted) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = when {
                                    isScrubbed || isHighlighted -> theme.accent
                                    hasMatches -> TextPrimary.copy(alpha = 0.85f)
                                    else -> TextMuted.copy(alpha = 0.5f)
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(x = waveOffsetDp)
                            )
                        }
                    }
                }
            }
        }

        // Niagara Floating Preview Bubble during vertical drag
        if (activeScrubLetter != null && activeIdx >= 0) {
            val fraction = (activeIdx + 0.5f) / letters.size.toFloat()
            val yPx = (fraction * scrubberHeightPx).roundToInt() - 40
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(x = -120, y = yPx.coerceAtLeast(16)) }
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                theme.activeButtonTop,
                                theme.activeButtonUpperMid,
                                theme.activeButtonLowerMid,
                                theme.activeButtonBottom
                            )
                        )
                    )
                    .border(1.5.dp, theme.focusBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeScrubLetter.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    color = TextPrimary
                )
            }
        }
    }
}
