package com.earlln.pianocode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earlln.pianocode.BuildConfig
import com.earlln.pianocode.music.ChordLibrary
import com.earlln.pianocode.music.ChordQuality

@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
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
            InfoCard(title = "PianoCode ${BuildConfig.VERSION_NAME}") {
                Text(
                    "빌드 ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_TYPE}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "피아노 코드 사전과 악보 코드 변환기를 하나로 묶은 앱입니다. " +
                        "${ChordLibrary.ROOTS.size}개 근음 위에 ${ChordQuality.ALL.size}종류의 코드, " +
                        "모두 ${ChordLibrary.totalChordCount}개를 건반 그림과 함께 담고 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            InfoCard(title = "코드 목록 사용법") {
                Bullet("근음을 고르면 7개 계열(메이저·마이너·도미넌트·디미니쉬·오그멘티드·서스펜디드·파워)의 기본 코드가 나옵니다.")
                Bullet("‘변화 코드 모두 보기’를 누르면 그 계열에서 파생된 모든 코드를 볼 수 있습니다.")
                Bullet("코드를 누르면 자리바꿈별 건반 위치, 구성음, 어울리는 조성을 확인할 수 있습니다.")
            }
        }

        item {
            InfoCard(title = "악보 코드 변환 사용법") {
                Bullet("악보 사진을 고르면 글자 인식으로 코드 심볼을 찾아냅니다.")
                Bullet("원래 조성은 찾아낸 코드로 자동 추정되며, 직접 바꿀 수도 있습니다.")
                Bullet("‘조옮김’은 모든 코드를 같은 간격으로 옮기고, ‘스케일 맞춤’은 도수를 유지한 채 목표 스케일의 화성으로 바꿉니다.")
                Bullet("변환된 이미지는 갤러리에 저장하거나 바로 공유할 수 있습니다.")
                Bullet("글자 인식은 기기 안에서만 처리되며 사진이 서버로 전송되지 않습니다.")
            }
        }

        item {
            InfoCard(title = "정확도에 대해") {
                Text(
                    "손글씨 악보나 화질이 낮은 사진에서는 코드 인식이 빗나갈 수 있습니다. " +
                        "변환 화면의 목록에서 인식된 코드를 하나씩 켜고 끌 수 있으니, " +
                        "저장하기 전에 확인해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text(
        "· $text",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
