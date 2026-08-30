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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earlln.pianocode.music.Chord
import com.earlln.pianocode.music.ChordLibrary
import com.earlln.pianocode.music.ChordQuality
import com.earlln.pianocode.music.Note
import com.earlln.pianocode.ui.Destination
import com.earlln.pianocode.ui.components.ChordKeyboard
import com.earlln.pianocode.ui.components.KeyboardLegend

/** Landing screen: what the app holds, and one tap into each part of it. */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        "피아노 코드, 한 화면에",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${ChordLibrary.ROOTS.size}개 근음 × ${ChordQuality.ALL.size}개 코드 = " +
                            "${ChordLibrary.totalChordCount}개의 코드를 건반 그림과 함께 확인하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    ChordKeyboard(
                        chord = Chord(Note(0, 0), ChordQuality.MAJOR_9),
                        height = 108.dp,
                        showLabels = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "예시: Cmaj9",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    KeyboardLegend()
                }
            }
        }

        items(
            Destination.entries.filter { it != Destination.HOME },
            key = { it.route },
        ) { destination ->
            MenuCard(
                destination = destination,
                onClick = { onNavigate(destination) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuCard(
    destination: Destination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(destination.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    destination.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
