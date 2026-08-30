package com.earlln.pianocode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.earlln.pianocode.music.ChordFamily
import com.earlln.pianocode.music.ChordLibrary
import com.earlln.pianocode.music.ChordQuality
import com.earlln.pianocode.music.Note
import com.earlln.pianocode.ui.components.ChordRow
import com.earlln.pianocode.ui.components.KeyboardLegend
import com.earlln.pianocode.ui.components.RootPicker
import com.earlln.pianocode.ui.components.SectionHeader

/**
 * The chord dictionary.
 *
 * Pick a root, then browse family by family: each family shows its base chord and, when
 * expanded, every variation built on it — which is exactly how the chords relate to each
 * other in practice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordListScreen(
    contentPadding: PaddingValues,
    onChordClick: (Chord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rootKey by rememberSaveable { mutableStateOf("0:0") }
    var query by rememberSaveable { mutableStateOf("") }
    // Kept as a joined string so the expanded set survives rotation through rememberSaveable.
    var expandedIds by rememberSaveable { mutableStateOf(ChordFamily.MAJOR.id) }
    val expandedFamilies = remember(expandedIds) {
        expandedIds.split(",").filter { it.isNotEmpty() }.toSet()
    }

    val root = remember(rootKey) {
        val (letter, accidental) = rootKey.split(":").map(String::toInt)
        Note(letter, accidental)
    }
    val searchResults = remember(query) {
        if (query.isBlank()) emptyList() else ChordLibrary.search(query).take(60)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("코드 검색 (예: Cmaj7, m7b5, 마이너)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "지우기")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        if (query.isNotBlank()) {
            item {
                Text(
                    "검색 결과 ${searchResults.size}개",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(searchResults, key = { "search-${it.symbol}" }) { chord ->
                ChordRow(
                    chord = chord,
                    onClick = { onChordClick(chord) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (searchResults.isEmpty()) {
                item { EmptySearchHint(Modifier.padding(horizontal = 16.dp)) }
            }
            return@LazyColumn
        }

        item {
            Column {
                Text(
                    "근음 선택",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
                RootPicker(
                    roots = ChordLibrary.ROOTS,
                    selected = root,
                    onSelect = { rootKey = "${it.letter}:${it.accidental}" },
                )
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
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${root.prettyName} 코드",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${ChordQuality.ALL.size}개의 코드 · ${ChordLibrary.FAMILIES.size}개 계열",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    KeyboardLegend()
                }
            }
        }

        ChordLibrary.FAMILIES.forEach { family ->
            val base = ChordLibrary.baseChord(root, family)
            val variations = ChordLibrary.variationsIn(root, family)
            val expanded = family.id in expandedFamilies

            item(key = "family-header-${family.id}") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(
                        title = "${base.prettySymbol} · ${family.koreanName}",
                        subtitle = family.description,
                    )
                }
            }

            item(key = "family-base-${family.id}") {
                ChordRow(
                    chord = base,
                    onClick = { onChordClick(base) },
                    highlighted = true,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (variations.isNotEmpty()) {
                item(key = "family-toggle-${family.id}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                val next = if (expanded) {
                                    expandedFamilies - family.id
                                } else {
                                    expandedFamilies + family.id
                                }
                                expandedIds = next.joinToString(",")
                            },
                        ) {
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (expanded) {
                                    "변화 코드 접기"
                                } else {
                                    "변화 코드 ${variations.size}개 모두 보기"
                                },
                            )
                        }
                    }
                }
            }

            if (expanded) {
                items(variations, key = { "var-${family.id}-${it.quality.id}" }) { chord ->
                    ChordRow(
                        chord = chord,
                        onClick = { onChordClick(chord) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearchHint(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("일치하는 코드가 없습니다", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "코드 심볼(Cmaj7, F#m7b5), 계열 이름(마이너, 도미넌트), " +
                    "영문 이름(diminished)으로 찾을 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
