package com.earlln.pianocode.music

/**
 * The instruments a progression can be heard on.
 *
 * The numbers are General MIDI program numbers, which is what makes this work without
 * shipping a single sample: Android's own synthesiser knows these voices, so the app only
 * has to say which one it wants.
 */
enum class Instrument(val program: Int, val koreanName: String) {
    PIANO(0, "피아노"),
    ELECTRIC_PIANO(4, "일렉피아노"),
    VIBRAPHONE(11, "비브라폰"),
    ORGAN(19, "오르간"),
    NYLON_GUITAR(24, "어쿠스틱 기타"),
    STRINGS(48, "현악 앙상블"),
    VIOLIN(40, "바이올린"),
    FLUTE(73, "플룻"),
    ;

    companion object {
        fun byName(name: String?): Instrument =
            entries.firstOrNull { it.name == name } ?: PIANO
    }
}
