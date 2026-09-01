package com.earlln.pianocode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earlln.pianocode.music.Chord
import com.earlln.pianocode.music.Note
import com.earlln.pianocode.settings.KeyboardSettings

/** A row in the chord list: the symbol, what it is, and the keyboard shape beside it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordRow(
    chord: Chord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    var inversion by remember(chord.symbol) { mutableIntStateOf(0) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        chord.prettySymbol,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        chord.quality.koreanName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        chord.quality.prettyFormula,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        chord.notes.joinToString(" ") { it.prettyName },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val prefs = KeyboardSettings.prefs
            val handHere = prefs.showHand && prefs.handInList
            ChordKeyboard(
                chord = chord,
                inversion = inversion,
                // The hand needs headroom above the keys that a row-sized picture does not
                // otherwise have.
                height = if (handHere) 104.dp else 74.dp,
                // Note names do not fit at this size, which is why the rows never had them,
                // but the finger numbers do — and the names are in the line above anyway.
                showLabels = false,
                showFingers = prefs.showFingers,
                showHand = handHere,
                handOpacity = prefs.handOpacity,
                minOctaves = 2,
                modifier = if (prefs.swipeToInvert) {
                    Modifier.swipeInversions(chord.positionCount, inversion) { inversion = it }
                } else {
                    Modifier
                },
            )
            if (prefs.swipeToInvert && chord.positionCount > 1 && inversion != 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    chord.positionLabel(inversion),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Horizontal picker for the twelve roots, plus the enharmonic spellings. */
@Composable
fun RootPicker(
    roots: List<Note>,
    selected: Note,
    onSelect: (Note) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(roots, key = { "${it.letter}:${it.accidental}" }) { root ->
            FilterChip(
                selected = root == selected,
                onClick = { onSelect(root) },
                label = {
                    Text(root.prettyName, style = MaterialTheme.typography.titleMedium)
                },
            )
        }
    }
}

/** Explains what each highlight colour on the keyboard means. */
@Composable
fun KeyboardLegend(
    modifier: Modifier = Modifier,
    roles: List<KeyRole> = listOf(KeyRole.ROOT, KeyRole.CHORD_TONE, KeyRole.TENSION),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        roles.forEach { role ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(role.color),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    role.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A titled section break used throughout the browsing screens. */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(if (subtitle == null) 20.dp else 34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
