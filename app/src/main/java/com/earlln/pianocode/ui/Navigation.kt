package com.earlln.pianocode.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.earlln.pianocode.music.Note

/** Every screen the drawer can reach. */
enum class Destination(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    HOME("home", "홈", "PianoCode 한눈에 보기", Icons.Filled.Home),
    CHORDS("chords", "코드 목록", "기본 코드와 모든 변화 코드", Icons.Filled.LibraryMusic),
    SCALES("scales", "스케일 & 다이아토닉", "조성별 구성음과 어울리는 코드", Icons.Filled.GraphicEq),
    CONVERTER("converter", "악보 코드 변환", "악보 사진의 코드를 원하는 스케일로", Icons.Filled.PhotoFilter),
    SETTINGS("settings", "설정", "건반 그림과 손가락 표시", Icons.Filled.Tune),
    ABOUT("about", "정보", "버전과 사용법", Icons.Filled.Info),
}

/** Route helpers for the chord detail screen, which carries its chord in the path. */
object Routes {
    const val CHORD_DETAIL = "chord/{letter}/{accidental}/{qualityId}"

    fun chordDetail(root: Note, qualityId: String): String =
        "chord/${root.letter}/${root.accidental + ACCIDENTAL_OFFSET}/$qualityId"

    /** Navigation arguments cannot be negative, so accidentals are stored shifted. */
    const val ACCIDENTAL_OFFSET = 2
}
