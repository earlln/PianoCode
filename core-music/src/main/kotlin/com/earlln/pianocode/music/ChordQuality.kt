package com.earlln.pianocode.music

/**
 * One tone of a chord, written as a scale degree plus an alteration — `b7`, `#11`, `3`.
 *
 * Degrees carry the letter distance with them (a 3rd is always two letters up, a 9th is
 * an octave plus a 2nd), so building a chord from degrees spells its notes correctly
 * without any enharmonic guesswork.
 */
data class ChordTone(val degree: Int, val alteration: Int = 0) {

    /** Alphabet steps from the root; degree 9 is eight steps, i.e. an octave plus a second. */
    val letterSteps: Int get() = degree - 1

    /** Semitones from the root. */
    val semitones: Int get() = degreeSemitones(degree) + alteration

    val label: String get() = Note.accidentalText(alteration) + degree

    val prettyLabel: String
        get() = when (alteration) {
            1 -> "♯$degree"
            -1 -> "♭$degree"
            -2 -> "♭♭$degree"
            2 -> "♯♯$degree"
            else -> "$degree"
        }

    companion object {
        /** Semitones above the root for each degree of a major scale, extended past the octave. */
        fun degreeSemitones(degree: Int): Int {
            val within = ((degree - 1) % 7) + 1
            val octaves = (degree - 1) / 7
            val base = when (within) {
                1 -> 0; 2 -> 2; 3 -> 4; 4 -> 5; 5 -> 7; 6 -> 9; 7 -> 11
                else -> error("unreachable degree $within")
            }
            return base + 12 * octaves
        }

        /** Reads a formula token such as `1`, `b3`, `#11` or `bb7`. */
        fun parse(token: String): ChordTone {
            var alteration = 0
            var index = 0
            while (index < token.length) {
                when (token[index]) {
                    'b' -> alteration -= 1
                    '#' -> alteration += 1
                    else -> break
                }
                index++
            }
            val degree = token.substring(index).toIntOrNull()
                ?: error("bad chord tone token: $token")
            return ChordTone(degree, alteration)
        }
    }
}

/** The families the chord list is organised by — a base chord and everything derived from it. */
enum class ChordFamily(
    val id: String,
    val displayName: String,
    val koreanName: String,
    val description: String,
) {
    MAJOR("major", "Major", "메이저", "밝고 안정적인 울림. 장3도 + 완전5도가 기본입니다."),
    MINOR("minor", "Minor", "마이너", "어둡고 서정적인 울림. 단3도 + 완전5도가 기본입니다."),
    DOMINANT("dominant", "Dominant", "도미넌트", "장3도 + 단7도의 긴장감. 해결을 원하는 코드입니다."),
    DIMINISHED("diminished", "Diminished", "디미니쉬", "단3도 + 감5도. 불안정하고 경과적으로 쓰입니다."),
    AUGMENTED("augmented", "Augmented", "오그멘티드", "장3도 + 증5도. 붕 뜬 듯한 울림을 만듭니다."),
    SUSPENDED("suspended", "Suspended", "서스펜디드", "3음을 2음 또는 4음으로 대체해 색을 지웁니다."),
    POWER("power", "Power", "파워", "3음이 없는 1도 + 5도. 장단조가 정해지지 않습니다."),
}

/**
 * A chord type: everything about a chord except which note it starts on.
 *
 * @param id stable key used for navigation and saved state
 * @param symbol suffix appended to the root when writing the chord, e.g. `m7b5`
 * @param prettySymbol the same suffix using proper music glyphs for display
 * @param aliases other spellings the parser should accept for this quality
 */
data class ChordQuality(
    val id: String,
    val symbol: String,
    val prettySymbol: String,
    val englishName: String,
    val koreanName: String,
    val family: ChordFamily,
    val tones: List<ChordTone>,
    val aliases: List<String> = emptyList(),
    val isBase: Boolean = false,
    val note: String? = null,
) {
    /** `1 b3 5 b7` — the classic degree formula shown next to the chord. */
    val formula: String get() = tones.joinToString(" ") { it.label }

    val prettyFormula: String get() = tones.joinToString(" · ") { it.prettyLabel }

    /** Semitones above the root for every tone, root first. */
    val intervals: List<Int> get() = tones.map { it.semitones }

    val toneCount: Int get() = tones.size

    companion object {
        private fun quality(
            id: String,
            symbol: String,
            prettySymbol: String,
            english: String,
            korean: String,
            family: ChordFamily,
            formula: String,
            aliases: List<String> = emptyList(),
            isBase: Boolean = false,
            note: String? = null,
        ) = ChordQuality(
            id = id,
            symbol = symbol,
            prettySymbol = prettySymbol,
            englishName = english,
            koreanName = korean,
            family = family,
            tones = formula.split(" ").map(ChordTone::parse),
            aliases = aliases,
            isBase = isBase,
            note = note,
        )

        // --- Major ----------------------------------------------------------
        val MAJOR = quality(
            "maj", "", "", "Major", "메이저", ChordFamily.MAJOR, "1 3 5",
            aliases = listOf("M", "maj", "major", "ma"), isBase = true,
        )
        val MAJOR_6 = quality(
            "maj6", "6", "6", "Major 6th", "메이저 6th", ChordFamily.MAJOR, "1 3 5 6",
            aliases = listOf("M6", "maj6", "add6"),
        )
        val MAJOR_6_9 = quality(
            "maj69", "6/9", "6/9", "Major 6/9", "메이저 6/9", ChordFamily.MAJOR, "1 3 5 6 9",
            aliases = listOf("69", "6add9", "M6/9", "6\\9"),
        )
        val MAJOR_7 = quality(
            "maj7", "maj7", "maj7", "Major 7th", "메이저 7th", ChordFamily.MAJOR, "1 3 5 7",
            aliases = listOf("M7", "Maj7", "ma7", "Δ", "Δ7", "j7"),
        )
        val MAJOR_9 = quality(
            "maj9", "maj9", "maj9", "Major 9th", "메이저 9th", ChordFamily.MAJOR, "1 3 5 7 9",
            aliases = listOf("M9", "Δ9"),
        )
        val MAJOR_11 = quality(
            "maj11", "maj11", "maj11", "Major 11th", "메이저 11th", ChordFamily.MAJOR, "1 3 5 7 9 11",
            aliases = listOf("M11", "Δ11"),
        )
        val MAJOR_13 = quality(
            "maj13", "maj13", "maj13", "Major 13th", "메이저 13th", ChordFamily.MAJOR, "1 3 5 7 9 13",
            aliases = listOf("M13", "Δ13"),
        )
        val ADD_9 = quality(
            "add9", "add9", "add9", "Add 9", "애드 9", ChordFamily.MAJOR, "1 3 5 9",
            aliases = listOf("add2", "(add9)", "2"),
            note = "7음 없이 9음만 더해 투명한 색을 냅니다.",
        )
        val ADD_11 = quality(
            "add11", "add11", "add11", "Add 11", "애드 11", ChordFamily.MAJOR, "1 3 5 11",
            aliases = listOf("add4", "(add11)"),
        )
        val MAJOR_7_SHARP_11 = quality(
            "maj7#11", "maj7#11", "maj7♯11", "Major 7th ♯11", "메이저 7th ♯11",
            ChordFamily.MAJOR, "1 3 5 7 #11",
            aliases = listOf("M7#11", "Δ7#11", "maj7+11"),
            note = "리디안 색채. 재즈에서 토닉 대체로 자주 쓰입니다.",
        )
        val MAJOR_9_SHARP_11 = quality(
            "maj9#11", "maj9#11", "maj9♯11", "Major 9th ♯11", "메이저 9th ♯11",
            ChordFamily.MAJOR, "1 3 5 7 9 #11", aliases = listOf("M9#11"),
        )
        val MAJOR_7_SHARP_5 = quality(
            "maj7#5", "maj7#5", "maj7♯5", "Major 7th ♯5", "메이저 7th ♯5",
            ChordFamily.MAJOR, "1 3 #5 7", aliases = listOf("M7#5", "maj7+5"),
        )
        val MAJOR_7_FLAT_5 = quality(
            "maj7b5", "maj7b5", "maj7♭5", "Major 7th ♭5", "메이저 7th ♭5",
            ChordFamily.MAJOR, "1 3 b5 7", aliases = listOf("M7b5"),
        )

        // --- Minor ----------------------------------------------------------
        val MINOR = quality(
            "min", "m", "m", "Minor", "마이너", ChordFamily.MINOR, "1 b3 5",
            aliases = listOf("min", "minor", "-"), isBase = true,
        )
        val MINOR_6 = quality(
            "min6", "m6", "m6", "Minor 6th", "마이너 6th", ChordFamily.MINOR, "1 b3 5 6",
            aliases = listOf("min6", "-6"),
        )
        val MINOR_6_9 = quality(
            "min69", "m6/9", "m6/9", "Minor 6/9", "마이너 6/9", ChordFamily.MINOR, "1 b3 5 6 9",
            aliases = listOf("m69", "min6/9"),
        )
        val MINOR_7 = quality(
            "min7", "m7", "m7", "Minor 7th", "마이너 7th", ChordFamily.MINOR, "1 b3 5 b7",
            aliases = listOf("min7", "-7", "mi7"),
        )
        val MINOR_9 = quality(
            "min9", "m9", "m9", "Minor 9th", "마이너 9th", ChordFamily.MINOR, "1 b3 5 b7 9",
            aliases = listOf("min9", "-9"),
        )
        val MINOR_11 = quality(
            "min11", "m11", "m11", "Minor 11th", "마이너 11th", ChordFamily.MINOR, "1 b3 5 b7 9 11",
            aliases = listOf("min11", "-11"),
        )
        val MINOR_13 = quality(
            "min13", "m13", "m13", "Minor 13th", "마이너 13th", ChordFamily.MINOR, "1 b3 5 b7 9 13",
            aliases = listOf("min13", "-13"),
        )
        val MINOR_MAJOR_7 = quality(
            "minmaj7", "mMaj7", "mMaj7", "Minor Major 7th", "마이너 메이저 7th",
            ChordFamily.MINOR, "1 b3 5 7",
            aliases = listOf("mM7", "minmaj7", "m#7", "-Δ7", "mΔ"),
            note = "하모닉 마이너의 토닉. 미스터리한 울림을 냅니다.",
        )
        val MINOR_MAJOR_9 = quality(
            "minmaj9", "mMaj9", "mMaj9", "Minor Major 9th", "마이너 메이저 9th",
            ChordFamily.MINOR, "1 b3 5 7 9", aliases = listOf("mM9", "minmaj9"),
        )
        val MINOR_ADD_9 = quality(
            "minadd9", "m(add9)", "m(add9)", "Minor Add 9", "마이너 애드 9",
            ChordFamily.MINOR, "1 b3 5 9", aliases = listOf("madd9", "m add9", "-add9"),
        )
        val MINOR_7_SHARP_5 = quality(
            "min7#5", "m7#5", "m7♯5", "Minor 7th ♯5", "마이너 7th ♯5",
            ChordFamily.MINOR, "1 b3 #5 b7", aliases = listOf("min7#5", "m7+5"),
        )

        // --- Dominant -------------------------------------------------------
        val DOMINANT_7 = quality(
            "dom7", "7", "7", "Dominant 7th", "도미넌트 7th", ChordFamily.DOMINANT, "1 3 5 b7",
            aliases = listOf("dom7"), isBase = true,
        )
        val DOMINANT_9 = quality(
            "dom9", "9", "9", "Dominant 9th", "도미넌트 9th", ChordFamily.DOMINANT, "1 3 5 b7 9",
        )
        val DOMINANT_11 = quality(
            "dom11", "11", "11", "Dominant 11th", "도미넌트 11th",
            ChordFamily.DOMINANT, "1 3 5 b7 9 11",
            note = "실제 연주에서는 3음과 부딪히므로 3음을 생략하는 경우가 많습니다.",
        )
        val DOMINANT_13 = quality(
            "dom13", "13", "13", "Dominant 13th", "도미넌트 13th",
            ChordFamily.DOMINANT, "1 3 5 b7 9 13",
        )
        val DOMINANT_7_FLAT_5 = quality(
            "dom7b5", "7b5", "7♭5", "Dominant 7th ♭5", "도미넌트 7th ♭5",
            ChordFamily.DOMINANT, "1 3 b5 b7", aliases = listOf("7-5"),
        )
        val DOMINANT_7_SHARP_5 = quality(
            "dom7#5", "7#5", "7♯5", "Dominant 7th ♯5", "도미넌트 7th ♯5",
            ChordFamily.DOMINANT, "1 3 #5 b7",
            aliases = listOf("7+5", "aug7", "+7", "7aug"),
        )
        val DOMINANT_7_FLAT_9 = quality(
            "dom7b9", "7b9", "7♭9", "Dominant 7th ♭9", "도미넌트 7th ♭9",
            ChordFamily.DOMINANT, "1 3 5 b7 b9", aliases = listOf("7-9"),
        )
        val DOMINANT_7_SHARP_9 = quality(
            "dom7#9", "7#9", "7♯9", "Dominant 7th ♯9", "도미넌트 7th ♯9",
            ChordFamily.DOMINANT, "1 3 5 b7 #9", aliases = listOf("7+9"),
            note = "'헨드릭스 코드'로 불리는 강렬한 텐션입니다.",
        )
        val DOMINANT_7_SHARP_11 = quality(
            "dom7#11", "7#11", "7♯11", "Dominant 7th ♯11", "도미넌트 7th ♯11",
            ChordFamily.DOMINANT, "1 3 5 b7 9 #11", aliases = listOf("7+11"),
        )
        val DOMINANT_7_FLAT_13 = quality(
            "dom7b13", "7b13", "7♭13", "Dominant 7th ♭13", "도미넌트 7th ♭13",
            ChordFamily.DOMINANT, "1 3 5 b7 9 b13", aliases = listOf("7-13"),
        )
        val DOMINANT_9_FLAT_5 = quality(
            "dom9b5", "9b5", "9♭5", "Dominant 9th ♭5", "도미넌트 9th ♭5",
            ChordFamily.DOMINANT, "1 3 b5 b7 9", aliases = listOf("9-5"),
        )
        val DOMINANT_9_SHARP_5 = quality(
            "dom9#5", "9#5", "9♯5", "Dominant 9th ♯5", "도미넌트 9th ♯5",
            ChordFamily.DOMINANT, "1 3 #5 b7 9", aliases = listOf("9+5", "aug9"),
        )
        val DOMINANT_13_FLAT_9 = quality(
            "dom13b9", "13b9", "13♭9", "Dominant 13th ♭9", "도미넌트 13th ♭9",
            ChordFamily.DOMINANT, "1 3 5 b7 b9 13",
        )
        val DOMINANT_13_SHARP_11 = quality(
            "dom13#11", "13#11", "13♯11", "Dominant 13th ♯11", "도미넌트 13th ♯11",
            ChordFamily.DOMINANT, "1 3 5 b7 9 #11 13",
        )
        val DOMINANT_7_FLAT_5_FLAT_9 = quality(
            "dom7b5b9", "7b5b9", "7♭5♭9", "Dominant 7th ♭5♭9", "도미넌트 7th ♭5♭9",
            ChordFamily.DOMINANT, "1 3 b5 b7 b9",
        )
        val DOMINANT_7_SHARP_5_SHARP_9 = quality(
            "dom7#5#9", "7#5#9", "7♯5♯9", "Dominant 7th ♯5♯9", "도미넌트 7th ♯5♯9",
            ChordFamily.DOMINANT, "1 3 #5 b7 #9",
        )
        val DOMINANT_7_ALTERED = quality(
            "dom7alt", "7alt", "7alt", "Altered Dominant", "얼터드 도미넌트",
            ChordFamily.DOMINANT, "1 3 b7 b9 #9 #11 b13",
            aliases = listOf("alt", "7altered"),
            note = "모든 텐션을 변화시킨 코드. 얼터드 스케일과 함께 씁니다.",
        )

        // --- Diminished -----------------------------------------------------
        val DIMINISHED = quality(
            "dim", "dim", "°", "Diminished", "디미니쉬", ChordFamily.DIMINISHED, "1 b3 b5",
            aliases = listOf("o", "°", "mb5", "m-5"), isBase = true,
        )
        val DIMINISHED_7 = quality(
            "dim7", "dim7", "°7", "Diminished 7th", "디미니쉬 7th",
            ChordFamily.DIMINISHED, "1 b3 b5 bb7",
            aliases = listOf("o7", "°7"),
            note = "단3도 간격의 완전 대칭 코드. 어느 음이든 근음이 될 수 있습니다.",
        )
        val HALF_DIMINISHED_7 = quality(
            "m7b5", "m7b5", "m7♭5", "Half Diminished 7th", "하프 디미니쉬 (m7♭5)",
            ChordFamily.DIMINISHED, "1 b3 b5 b7",
            aliases = listOf("ø", "ø7", "min7b5", "m7-5", "half-dim"),
            note = "마이너 ii-V의 ii 자리에 오는 코드입니다.",
        )
        val HALF_DIMINISHED_9 = quality(
            "m9b5", "m9b5", "m9♭5", "Minor 9th ♭5", "마이너 9th ♭5",
            ChordFamily.DIMINISHED, "1 b3 b5 b7 9", aliases = listOf("min9b5"),
        )

        // --- Augmented ------------------------------------------------------
        val AUGMENTED = quality(
            "aug", "aug", "+", "Augmented", "오그멘티드", ChordFamily.AUGMENTED, "1 3 #5",
            aliases = listOf("+", "aug", "M#5", "maj#5"), isBase = true,
            note = "장3도 간격의 대칭 코드. 세 음 모두 근음이 될 수 있습니다.",
        )
        val AUGMENTED_MAJOR_7 = quality(
            "augmaj7", "+Maj7", "+Maj7", "Augmented Major 7th", "오그멘티드 메이저 7th",
            ChordFamily.AUGMENTED, "1 3 #5 7", aliases = listOf("+M7"),
        )

        // --- Suspended ------------------------------------------------------
        val SUS4 = quality(
            "sus4", "sus4", "sus4", "Suspended 4th", "서스포", ChordFamily.SUSPENDED, "1 4 5",
            aliases = listOf("sus"), isBase = true,
        )
        val SUS2 = quality(
            "sus2", "sus2", "sus2", "Suspended 2nd", "서스투", ChordFamily.SUSPENDED, "1 2 5",
        )
        val DOMINANT_7_SUS4 = quality(
            "7sus4", "7sus4", "7sus4", "Dominant 7th sus4", "7 서스포",
            ChordFamily.SUSPENDED, "1 4 5 b7", aliases = listOf("7sus"),
        )
        val DOMINANT_9_SUS4 = quality(
            "9sus4", "9sus4", "9sus4", "Dominant 9th sus4", "9 서스포",
            ChordFamily.SUSPENDED, "1 4 5 b7 9", aliases = listOf("9sus"),
        )
        val MAJOR_7_SUS4 = quality(
            "maj7sus4", "maj7sus4", "maj7sus4", "Major 7th sus4", "메이저 7 서스포",
            ChordFamily.SUSPENDED, "1 4 5 7", aliases = listOf("M7sus4"),
        )
        val DOMINANT_7_SUS2 = quality(
            "7sus2", "7sus2", "7sus2", "Dominant 7th sus2", "7 서스투",
            ChordFamily.SUSPENDED, "1 2 5 b7",
        )
        val SUS4_ADD_9 = quality(
            "sus4add9", "sus4add9", "sus4add9", "Sus4 Add 9", "서스포 애드 9",
            ChordFamily.SUSPENDED, "1 4 5 9", aliases = listOf("sus4(add9)"),
        )

        // --- Power ----------------------------------------------------------
        val POWER_5 = quality(
            "five", "5", "5", "Power Chord", "파워 코드", ChordFamily.POWER, "1 5",
            aliases = listOf("no3"), isBase = true,
            note = "3음이 없어 장조/단조 어디에나 얹을 수 있습니다.",
        )

        /** Every quality the app knows, in the order the chord list shows them. */
        val ALL: List<ChordQuality> = listOf(
            MAJOR, MAJOR_6, MAJOR_6_9, MAJOR_7, MAJOR_9, MAJOR_11, MAJOR_13,
            ADD_9, ADD_11, MAJOR_7_SHARP_11, MAJOR_9_SHARP_11, MAJOR_7_SHARP_5, MAJOR_7_FLAT_5,

            MINOR, MINOR_6, MINOR_6_9, MINOR_7, MINOR_9, MINOR_11, MINOR_13,
            MINOR_MAJOR_7, MINOR_MAJOR_9, MINOR_ADD_9, MINOR_7_SHARP_5,

            DOMINANT_7, DOMINANT_9, DOMINANT_11, DOMINANT_13,
            DOMINANT_7_FLAT_5, DOMINANT_7_SHARP_5, DOMINANT_7_FLAT_9, DOMINANT_7_SHARP_9,
            DOMINANT_7_SHARP_11, DOMINANT_7_FLAT_13, DOMINANT_9_FLAT_5, DOMINANT_9_SHARP_5,
            DOMINANT_13_FLAT_9, DOMINANT_13_SHARP_11, DOMINANT_7_FLAT_5_FLAT_9,
            DOMINANT_7_SHARP_5_SHARP_9, DOMINANT_7_ALTERED,

            DIMINISHED, DIMINISHED_7, HALF_DIMINISHED_7, HALF_DIMINISHED_9,

            AUGMENTED, AUGMENTED_MAJOR_7,

            SUS4, SUS2, DOMINANT_7_SUS4, DOMINANT_9_SUS4, MAJOR_7_SUS4, DOMINANT_7_SUS2,
            SUS4_ADD_9,

            POWER_5,
        )

        val BY_ID: Map<String, ChordQuality> = ALL.associateBy { it.id }

        /** The one base chord of each family, the entry point for its variations. */
        val BASE_QUALITIES: List<ChordQuality> = ALL.filter { it.isBase }

        fun byFamily(family: ChordFamily): List<ChordQuality> = ALL.filter { it.family == family }

        fun byId(id: String): ChordQuality? = BY_ID[id]
    }
}
