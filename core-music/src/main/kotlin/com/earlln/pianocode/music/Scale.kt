package com.earlln.pianocode.music

/**
 * A scale shape — the interval pattern, without a tonic.
 *
 * [semitones] holds the steps above the tonic; degrees advance one letter each, which is
 * what gives every mode a correctly spelled seven-note version.
 */
enum class ScaleType(
    val id: String,
    val englishName: String,
    val koreanName: String,
    val semitones: List<Int>,
    val isHeptatonic: Boolean = true,
) {
    MAJOR("major", "Major (Ionian)", "메이저 (아이오니안)", listOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR("minor", "Natural Minor (Aeolian)", "내추럴 마이너 (에올리안)", listOf(0, 2, 3, 5, 7, 8, 10)),
    HARMONIC_MINOR("harmonic-minor", "Harmonic Minor", "하모닉 마이너", listOf(0, 2, 3, 5, 7, 8, 11)),
    MELODIC_MINOR("melodic-minor", "Melodic Minor", "멜로딕 마이너", listOf(0, 2, 3, 5, 7, 9, 11)),
    DORIAN("dorian", "Dorian", "도리안", listOf(0, 2, 3, 5, 7, 9, 10)),
    PHRYGIAN("phrygian", "Phrygian", "프리지안", listOf(0, 1, 3, 5, 7, 8, 10)),
    LYDIAN("lydian", "Lydian", "리디안", listOf(0, 2, 4, 6, 7, 9, 11)),
    MIXOLYDIAN("mixolydian", "Mixolydian", "믹솔리디안", listOf(0, 2, 4, 5, 7, 9, 10)),
    LOCRIAN("locrian", "Locrian", "로크리안", listOf(0, 1, 3, 5, 6, 8, 10)),
    MAJOR_PENTATONIC("major-pentatonic", "Major Pentatonic", "메이저 펜타토닉", listOf(0, 2, 4, 7, 9), isHeptatonic = false),
    MINOR_PENTATONIC("minor-pentatonic", "Minor Pentatonic", "마이너 펜타토닉", listOf(0, 3, 5, 7, 10), isHeptatonic = false),
    BLUES("blues", "Blues", "블루스", listOf(0, 3, 5, 6, 7, 10), isHeptatonic = false),
    ;

    /** True for the two shapes the transposer treats as ordinary keys with a key signature. */
    val isKeyMode: Boolean get() = this == MAJOR || this == NATURAL_MINOR

    companion object {
        /** The shapes offered as transposition targets — ones a key signature exists for. */
        val TRANSPOSE_TARGETS: List<ScaleType> =
            listOf(MAJOR, NATURAL_MINOR, DORIAN, PHRYGIAN, LYDIAN, MIXOLYDIAN, LOCRIAN,
                HARMONIC_MINOR, MELODIC_MINOR)

        fun byId(id: String): ScaleType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A key: a tonic plus a scale shape. Knows how many sharps or flats it carries, which is
 * what decides whether a transposed chord is written `A♯m` or `B♭m`.
 */
data class Key(val tonic: Note, val type: ScaleType = ScaleType.MAJOR) {

    val name: String get() = "${tonic.prettyName} ${type.koreanName}"

    val shortName: String
        get() = tonic.prettyName + if (type == ScaleType.NATURAL_MINOR) "m" else ""

    /** The scale notes, correctly spelled — one per letter for the seven-note shapes. */
    val notes: List<Note>
        get() = type.semitones.mapIndexed { index, semis ->
            if (type.isHeptatonic) {
                tonic.transposeBy(index, semis)
            } else {
                Note.fromPitchClass(tonic.pitchClass + semis, preferFlats = fifths < 0)
            }
        }

    val pitchClasses: Set<Int> get() = type.semitones.map {
        Math.floorMod(tonic.pitchClass + it, 12)
    }.toSet()

    /**
     * Position on the circle of fifths: positive counts sharps, negative counts flats.
     * Derived from the parallel major so every mode inherits a sensible accidental bias.
     */
    val fifths: Int
        get() {
            val majorTonicFifths = LETTER_FIFTHS[tonic.letter] + 7 * tonic.accidental
            val modeOffset = when (type) {
                ScaleType.MAJOR -> 0
                ScaleType.NATURAL_MINOR, ScaleType.HARMONIC_MINOR, ScaleType.MELODIC_MINOR -> -3
                ScaleType.DORIAN -> -2
                ScaleType.PHRYGIAN -> -4
                ScaleType.LYDIAN -> 1
                ScaleType.MIXOLYDIAN -> -1
                ScaleType.LOCRIAN -> -5
                ScaleType.MAJOR_PENTATONIC -> 0
                ScaleType.MINOR_PENTATONIC, ScaleType.BLUES -> -3
            }
            return majorTonicFifths + modeOffset
        }

    val prefersFlats: Boolean get() = fifths < 0

    /** `♯ 2개`, `♭ 3개`, `조표 없음` — shown next to the key picker. */
    val keySignatureText: String
        get() = when {
            fifths > 0 -> "♯ ${fifths}개"
            fifths < 0 -> "♭ ${-fifths}개"
            else -> "조표 없음"
        }

    /** Writes [pitchClass] the way this key would write it. */
    fun spell(pitchClass: Int): Note {
        val pc = Math.floorMod(pitchClass, 12)
        notes.firstOrNull { it.pitchClass == pc }?.let { return it }
        // Outside the key: fall back to the accidental the key signature already leans on.
        return Note.fromPitchClass(pc, preferFlats = prefersFlats)
    }

    /** Semitones from this key's tonic up to [other]'s tonic, 0..11. */
    fun semitonesTo(other: Key): Int = Math.floorMod(other.tonic.pitchClass - tonic.pitchClass, 12)

    /** The seven chords built on this scale, as triads or sevenths. */
    fun diatonicChords(seventh: Boolean = false): List<DiatonicChord> {
        if (!type.isHeptatonic) return emptyList()
        val degrees = notes
        val size = degrees.size
        return degrees.indices.map { degreeIndex ->
            val stackedPitchClasses = (0 until if (seventh) 4 else 3).map { step ->
                val index = degreeIndex + step * 2
                val octaves = index / size
                Math.floorMod(degrees[index % size].pitchClass, 12) + 12 * octaves
            }
            val rootPc = stackedPitchClasses.first()
            val intervals = stackedPitchClasses.map { Math.floorMod(it - rootPc, 12) }.sorted()
            val quality = matchQuality(intervals, seventh)
            DiatonicChord(
                degree = degreeIndex + 1,
                chord = Chord(degrees[degreeIndex], quality),
                romanNumeral = romanNumeral(degreeIndex + 1, quality),
            )
        }
    }

    companion object {
        /** Circle-of-fifths position of each natural letter: F=-1, C=0, G=1, … */
        private val LETTER_FIFTHS = intArrayOf(0, 2, 4, -1, 1, 3, 5)

        private val ROMAN = arrayOf("I", "II", "III", "IV", "V", "VI", "VII")

        /** Every major and natural-minor key, in circle-of-fifths order, for the key pickers. */
        val COMMON_KEYS: List<Key> by lazy {
            val majors = listOf(
                Note(0, 0),  // C
                Note(4, 0),  // G
                Note(1, 0),  // D
                Note(5, 0),  // A
                Note(2, 0),  // E
                Note(6, 0),  // B
                Note(3, 1),  // F#
                Note(0, 1),  // C#
                Note(3, 0),  // F
                Note(6, -1), // Bb
                Note(2, -1), // Eb
                Note(5, -1), // Ab
                Note(1, -1), // Db
                Note(4, -1), // Gb
                Note(0, -1), // Cb
            )
            majors.map { Key(it, ScaleType.MAJOR) } +
                majors.map { Key(it.transposeBy(5, 9), ScaleType.NATURAL_MINOR) }
        }

        private fun matchQuality(intervals: List<Int>, seventh: Boolean): ChordQuality {
            val set = intervals.toSet()
            return when {
                seventh && set == setOf(0, 4, 7, 11) -> ChordQuality.MAJOR_7
                seventh && set == setOf(0, 3, 7, 10) -> ChordQuality.MINOR_7
                seventh && set == setOf(0, 4, 7, 10) -> ChordQuality.DOMINANT_7
                seventh && set == setOf(0, 3, 6, 10) -> ChordQuality.HALF_DIMINISHED_7
                seventh && set == setOf(0, 3, 6, 9) -> ChordQuality.DIMINISHED_7
                seventh && set == setOf(0, 3, 7, 11) -> ChordQuality.MINOR_MAJOR_7
                seventh && set == setOf(0, 4, 8, 11) -> ChordQuality.AUGMENTED_MAJOR_7
                seventh && set == setOf(0, 4, 8, 10) -> ChordQuality.DOMINANT_7_SHARP_5
                set.containsAll(setOf(0, 4, 7)) -> ChordQuality.MAJOR
                set.containsAll(setOf(0, 3, 7)) -> ChordQuality.MINOR
                set.containsAll(setOf(0, 3, 6)) -> ChordQuality.DIMINISHED
                set.containsAll(setOf(0, 4, 8)) -> ChordQuality.AUGMENTED
                set.containsAll(setOf(0, 5, 7)) -> ChordQuality.SUS4
                set.containsAll(setOf(0, 2, 7)) -> ChordQuality.SUS2
                else -> ChordQuality.MAJOR
            }
        }

        private fun romanNumeral(degree: Int, quality: ChordQuality): String {
            val base = ROMAN[degree - 1]
            return when (quality.family) {
                ChordFamily.MINOR -> base.lowercase() + quality.symbol.removePrefix("m")
                ChordFamily.DIMINISHED -> base.lowercase() + when (quality) {
                    ChordQuality.HALF_DIMINISHED_7 -> "ø7"
                    ChordQuality.DIMINISHED_7 -> "°7"
                    else -> "°"
                }
                ChordFamily.AUGMENTED -> base + "+"
                ChordFamily.DOMINANT -> base + quality.symbol
                else -> base + quality.symbol
            }
        }
    }
}

/** One chord of a key's harmony, with the roman numeral it is usually labelled with. */
data class DiatonicChord(
    val degree: Int,
    val chord: Chord,
    val romanNumeral: String,
)
