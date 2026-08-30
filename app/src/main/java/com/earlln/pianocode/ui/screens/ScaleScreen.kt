package com.earlln.pianocode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earlln.pianocode.music.Chord
import com.earlln.pianocode.music.ChordLibrary
import com.earlln.pianocode.music.Key
import com.earlln.pianocode.music.Note
import com.earlln.pianocode.music.ScaleType
import com.earlln.pianocode.ui.components.ChordRow
import com.earlln.pianocode.ui.components.PianoKeyboard
import com.earlln.pianocode.ui.components.SectionHeader
import com.earlln.pianocode.ui.components.noteHighlights

/**
 * Scale reference: the notes of a key on the keyboard, and the chords that live in it.
 * The transposer's "fit to scale" mode uses exactly this harmony, so seeing it here makes
 * the conversion predictable.
 */
@Composable
fun ScaleScreen(
    contentPadding: PaddingValues,
    onChordClick: (Chord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tonicKey by rememberSaveable { mutableStateOf("0:0") }
    var scaleTypeId by rememberSaveable { mutableStateOf(ScaleType.MAJOR.id) }
    var seventh by rememberSaveable { mutableStateOf(false) }

    val tonic = remember(tonicKey) {
        val (letter, accidental) = tonicKey.split(":").map(String::toInt)
        Note(letter, accidental)
    }
    val scaleType = remember(scaleTypeId) { ScaleType.byId(scaleTypeId) ?: ScaleType.MAJOR }
    val key = remember(tonic, scaleType) { Key(tonic, scaleType) }
    val diatonic = remember(key, seventh) { key.diatonicChords(seventh) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    "으뜸음",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(ChordLibrary.ROOTS, key = { "${it.letter}:${it.accidental}" }) { note ->
                        FilterChip(
                            selected = note == tonic,
                            onClick = { tonicKey = "${note.letter}:${note.accidental}" },
                            label = { Text(note.prettyName) },
                        )
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    "스케일",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(ScaleType.entries.toList(), key = { it.id }) { type ->
                        FilterChip(
                            selected = type.id == scaleTypeId,
                            onClick = { scaleTypeId = type.id },
                            label = { Text(type.koreanName) },
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        key.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${key.keySignatureText} · ${key.notes.joinToString(" ") { it.prettyName }}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    PianoKeyboard(
                        highlights = noteHighlights(key.notes, startOctave = 4),
                        height = 130.dp,
                        showLabels = true,
                    )
                }
            }
        }

        if (diatonic.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "펜타토닉과 블루스 스케일은 7음이 아니어서 다이아토닉 코드를 만들지 않습니다. " +
                            "메이저나 마이너 스케일을 선택해 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            return@LazyColumn
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = "다이아토닉 코드",
                    subtitle = "이 조성에서 자연스럽게 쓰이는 7개의 코드",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(listOf(false, true)) { wantsSeventh ->
                    FilterChip(
                        selected = seventh == wantsSeventh,
                        onClick = { seventh = wantsSeventh },
                        label = { Text(if (wantsSeventh) "7화음" else "3화음") },
                    )
                }
            }
        }

        items(diatonic, key = { "diatonic-${it.degree}" }) { entry ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.romanNumeral,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        "${entry.degree}도",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ChordRow(chord = entry.chord, onClick = { onChordClick(entry.chord) })
            }
        }
    }
}
