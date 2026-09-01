package com.earlln.pianocode.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earlln.pianocode.music.Chord
import com.earlln.pianocode.music.ChordLibrary
import com.earlln.pianocode.music.ChordQuality
import com.earlln.pianocode.music.Key
import com.earlln.pianocode.music.Note
import com.earlln.pianocode.music.Transposer
import com.earlln.pianocode.settings.KeyboardSettings
import com.earlln.pianocode.ui.components.ChordKeyboard
import com.earlln.pianocode.ui.components.swipeInversions
import com.earlln.pianocode.ui.components.ChordRow
import com.earlln.pianocode.ui.components.KeyRole
import com.earlln.pianocode.ui.components.KeyboardLegend
import com.earlln.pianocode.ui.components.SectionHeader

/**
 * Everything about one chord: the keyboard shape in every inversion, its notes and
 * degrees, the keys it belongs to, and the sibling chords in its family.
 */
@Composable
fun ChordDetailScreen(
    root: Note,
    qualityId: String,
    contentPadding: PaddingValues,
    onChordClick: (Chord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quality = remember(qualityId) { ChordQuality.byId(qualityId) ?: ChordQuality.MAJOR }
    val chord = remember(root, quality) { Chord(root, quality) }
    var inversion by rememberSaveable(chord.symbol) { mutableIntStateOf(0) }

    val siblings = remember(chord) {
        ChordLibrary.chordsIn(root, quality.family).filterNot { it.quality.id == quality.id }
    }
    val containingKeys = remember(chord) { keysContaining(chord) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
                        chord.prettySymbol,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${chord.root.prettyName} ${quality.koreanName} · ${quality.englishName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${chord.root.koreanName} 위에 쌓은 ${quality.family.koreanName} 계열",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val tip = quality.note
                    if (tip != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "건반 위치",
                    subtitle = chord.positionLabel(inversion) +
                        if (KeyboardSettings.prefs.showFingers) " · 숫자는 손가락 (1=엄지)" else "",
                )
                Spacer(Modifier.height(12.dp))
                val prefs = KeyboardSettings.prefs
                ChordKeyboard(
                    chord = chord,
                    inversion = inversion,
                    startOctave = 3,
                    height = if (prefs.showHand) 178.dp else 150.dp,
                    showLabels = prefs.showNoteNames,
                    showFingers = prefs.showFingers,
                    showHand = prefs.showHand,
                    handOpacity = prefs.handOpacity,
                    minOctaves = 2,
                    modifier = if (prefs.swipeToInvert) {
                        Modifier.swipeInversions(chord.positionCount, inversion) { inversion = it }
                    } else {
                        Modifier
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        if (prefs.swipeToInvert) append("건반을 좌우로 밀면 자리바꿈이 넘어갑니다. ")
                        if (prefs.showHand) append("손 모양은 손가락이 뻗는 방향을 보여 주는 실루엣입니다. ")
                        if (prefs.showFingers) append("왼손이 짚는 자리는 어두운 동그라미입니다.")
                    }.ifBlank { "표시 옵션은 설정 화면에서 바꿀 수 있습니다." },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                KeyboardLegend(
                    roles = buildList {
                        if (chord.hasSlashBass) add(KeyRole.BASS)
                        add(KeyRole.ROOT)
                        add(KeyRole.CHORD_TONE)
                        if (quality.tones.any { it.degree > 7 }) add(KeyRole.TENSION)
                    },
                )
            }
        }

        item {
            Column {
                Text(
                    "자리바꿈 (전위)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items((0 until chord.positionCount).toList()) { position ->
                        FilterChip(
                            selected = position == inversion,
                            onClick = { inversion = position },
                            label = { Text(chord.positionLabel(position)) },
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("구성음", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    chord.tonesWithNotes.forEachIndexed { index, (tone, note) ->
                        if (index > 0) {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tone.prettyLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(48.dp),
                            )
                            Text(
                                note.prettyName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.width(56.dp),
                            )
                            Text(
                                note.koreanName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(56.dp),
                            )
                            Text(
                                "근음에서 ${tone.semitones}반음",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        AssistChip(
                            onClick = {},
                            label = { Text("공식 ${quality.prettyFormula}") },
                        )
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("${quality.toneCount}화음") },
                        )
                    }
                }
            }
        }

        if (containingKeys.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(
                        title = "이 코드가 어울리는 조성",
                        subtitle = "코드의 모든 음이 스케일 안에 들어가는 조성입니다",
                    )
                }
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(containingKeys) { key ->
                        AssistChip(onClick = {}, label = { Text(key.name) })
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "${quality.family.koreanName} 계열의 다른 코드",
                    subtitle = "같은 뿌리에서 갈라져 나온 변화 코드들",
                )
            }
        }
        items(siblings, key = { "sibling-${it.quality.id}" }) { sibling ->
            ChordRow(
                chord = sibling,
                onClick = { onChordClick(sibling) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(title = "다른 근음으로 보기")
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(ChordLibrary.PRIMARY_ROOTS) { otherRoot ->
                    val other = Chord(otherRoot, quality)
                    FilterChip(
                        selected = otherRoot == root,
                        onClick = { onChordClick(other) },
                        label = { Text(other.prettySymbol) },
                    )
                }
            }
        }
    }
}

/** The major and minor keys whose scale contains every note of [chord]. */
private fun keysContaining(chord: Chord): List<Key> {
    val pitchClasses = chord.pitchClasses.toSet()
    return Key.COMMON_KEYS.filter { key ->
        pitchClasses.all { it in key.pitchClasses } &&
            Transposer.degreeOf(chord.root, key) != null
    }
}
