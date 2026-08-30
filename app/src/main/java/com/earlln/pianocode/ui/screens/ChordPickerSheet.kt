package com.earlln.pianocode.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earlln.pianocode.music.Chord
import com.earlln.pianocode.music.ChordFamily
import com.earlln.pianocode.music.ChordLibrary
import com.earlln.pianocode.music.ChordQuality
import com.earlln.pianocode.music.Note

/**
 * Asks which chord belongs in a spot the user marked on the sheet.
 *
 * Recognition will always leave a few symbols behind, and chasing them by tightening the
 * reader costs correct readings elsewhere. Placing the last few by hand is quicker and
 * certain — so when the reader saw something it could not use, its transposition is offered
 * straight away and the whole catalogue sits underneath for everything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordPickerSheet(
    originalText: String?,
    suggestion: Chord?,
    onDismiss: () -> Unit,
    onPick: (Chord) -> Unit,
) {
    var root by remember { mutableStateOf(suggestion?.root ?: Note(0, 0)) }
    var family by remember {
        mutableStateOf(suggestion?.quality?.family ?: ChordFamily.MAJOR)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "이 자리에 넣을 코드",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (originalText != null) {
                Text(
                    "악보에서 읽은 글자: $originalText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            if (suggestion != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onPick(suggestion) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Text("${suggestion.prettySymbol} 넣기")
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                "근음",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, bottom = 6.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                items(ChordLibrary.ROOTS, key = { "${it.letter}:${it.accidental}" }) { note ->
                    FilterChip(
                        selected = note == root,
                        onClick = { root = note },
                        label = { Text(note.prettyName) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "계열",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, bottom = 6.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                items(ChordLibrary.FAMILIES, key = { it.id }) { entry ->
                    FilterChip(
                        selected = entry == family,
                        onClick = { family = entry },
                        label = { Text(entry.koreanName) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(ChordQuality.byFamily(family), key = { it.id }) { quality ->
                    val chord = Chord(root, quality)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(chord) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            chord.prettySymbol,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.width(110.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(quality.koreanName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                chord.notes.joinToString(" ") { it.prettyName },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}