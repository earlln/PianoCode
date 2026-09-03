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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earlln.pianocode.music.ChordParser
import com.earlln.pianocode.settings.KeyboardSettings
import com.earlln.pianocode.ui.components.ChordKeyboard
import com.earlln.pianocode.ui.components.SectionHeader

/**
 * The keyboard drawing options, with a live example above them.
 *
 * Every switch here changes a picture, and describing a picture in words is a poor
 * substitute for showing it, so the example redraws as the switches move. It is a real
 * keyboard, not a mock-up of one, which is also how a broken option would be noticed.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val prefs = KeyboardSettings.prefs
    val sample = remember { ChordParser.parse("Cmaj7")!! }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "건반 그림",
                    subtitle = "바꾸는 대로 아래 예시에 바로 반영됩니다",
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Cmaj7",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    ChordKeyboard(
                        chord = sample,
                        height = if (prefs.showHand) 178.dp else 150.dp,
                        showLabels = prefs.showNoteNames,
                        showFingers = prefs.showFingers,
                        showHand = prefs.showHand,
                        handOpacity = prefs.handOpacity,
                        minOctaves = 2,
                    )
                }
            }
        }

        item {
            SettingSwitch(
                title = "손가락 번호",
                description = "누를 건반 위에 1(엄지)~5(새끼)를 표시합니다.",
                checked = prefs.showFingers,
                onChange = { on -> KeyboardSettings.update { it.copy(showFingers = on) } },
            )
        }

        item {
            SettingSwitch(
                title = "손 모양",
                description = "손가락이 어느 방향으로 뻗는지 실루엣으로 보여 줍니다. " +
                    "손가락 번호를 끄면 함께 사라집니다.",
                checked = prefs.showHand,
                onChange = { on -> KeyboardSettings.update { it.copy(showHand = on) } },
            )
        }

        if (prefs.showHand) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "손 모양 진하기",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(prefs.handOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "옅게 두면 건반이 비쳐 보이고, 진하게 두면 손이 또렷합니다.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = prefs.handOpacity,
                            onValueChange = { value ->
                                KeyboardSettings.update { it.copy(handOpacity = value) }
                            },
                            valueRange = 0.35f..1f,
                        )
                    }
                }
            }

            item {
                SettingSwitch(
                    title = "코드 목록에서도 손 모양",
                    description = "목록의 작은 건반에도 손을 그립니다. " +
                        "그림이 커지는 대신 한 화면에 보이는 코드 수가 줄어듭니다.",
                    checked = prefs.handInList,
                    onChange = { on -> KeyboardSettings.update { it.copy(handInList = on) } },
                )
            }
        }

        item {
            SettingSwitch(
                title = "음 이름",
                description = "건반 위에 음 이름을 씁니다. 큰 건반에만 들어갑니다.",
                checked = prefs.showNoteNames,
                onChange = { on -> KeyboardSettings.update { it.copy(showNoteNames = on) } },
            )
        }

        item {
            SettingSwitch(
                title = "밀어서 자리바꿈",
                description = "건반을 좌우로 밀면 자리바꿈(전위)이 넘어갑니다. " +
                    "끄면 상세 화면의 칩으로만 바꿉니다.",
                checked = prefs.swipeToInvert,
                onChange = { on -> KeyboardSettings.update { it.copy(swipeToInvert = on) } },
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = { KeyboardSettings.reset() }) {
                    Text("기본값으로 되돌리기")
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
