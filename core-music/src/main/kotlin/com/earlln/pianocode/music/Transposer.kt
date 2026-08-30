package com.earlln.pianocode.music

/** How the chords on a sheet should be rewritten. */
enum class ConversionMode(val displayName: String, val description: String) {
    /** Shift every chord by the same interval — the usual "key change". */
    TRANSPOSE(
        "조옮김 (Transpose)",
        "모든 코드를 같은 간격만큼 옮깁니다. 곡의 화성 관계는 그대로 유지됩니다.",
    ),

    /** Re-harmonise onto the target scale, keeping each chord's scale degree. */
    DIATONIC(
        "스케일 맞춤 (Fit to scale)",
        "각 코드의 자리(도수)를 유지한 채 목표 스케일의 화성으로 바꿉니다. 장조↔단조 변환에 좋습니다.",
    ),
}

/** One chord rewritten, kept next to the original so the UI can show a before/after list. */
data class ChordConversion(
    val original: Chord,
    val converted: Chord,
    val isDiatonicToSource: Boolean,
) {
    val changed: Boolean get() = original.symbol != converted.symbol
}

/**
 * Rewrites chord symbols from one key or scale into another.
 *
 * The two modes answer different questions. [ConversionMode.TRANSPOSE] answers "play the
 * same song higher"; [ConversionMode.DIATONIC] answers "play this song in a different
 * scale", which changes chord qualities — a major I becomes a minor i when the target is
 * a minor scale.
 */
object Transposer {

    /** Rewrites a single chord. */
    fun convert(
        chord: Chord,
        sourceKey: Key,
        targetKey: Key,
        mode: ConversionMode,
    ): ChordConversion {
        val degree = degreeOf(chord.root, sourceKey)
        return when (mode) {
            ConversionMode.TRANSPOSE -> {
                val semitones = sourceKey.semitonesTo(targetKey)
                ChordConversion(chord, chord.transpose(semitones, targetKey), degree != null)
            }

            ConversionMode.DIATONIC -> {
                if (degree == null) {
                    // Chromatic chord: no degree to preserve, so keep the interval instead.
                    val semitones = sourceKey.semitonesTo(targetKey)
                    ChordConversion(chord, chord.transpose(semitones, targetKey), false)
                } else {
                    ChordConversion(chord, mapToDegree(chord, degree, sourceKey, targetKey), true)
                }
            }
        }
    }

    /** Rewrites a whole sheet's worth of chords. */
    fun convertAll(
        chords: List<Chord>,
        sourceKey: Key,
        targetKey: Key,
        mode: ConversionMode,
    ): List<ChordConversion> = chords.map { convert(it, sourceKey, targetKey, mode) }

    /**
     * Guesses which key a set of chords is in.
     *
     * Each candidate key scores a point per chord whose root is in the scale, another when
     * every note of the chord fits the scale, and a bonus when the first or last chord is
     * the tonic — the cadence is usually the strongest clue on a lead sheet.
     */
    fun detectKey(chords: List<Chord>): Key? {
        if (chords.isEmpty()) return null
        var best: Key? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (key in Key.COMMON_KEYS) {
            val scale = key.pitchClasses
            var score = 0.0
            for (chord in chords) {
                if (chord.root.pitchClass in scale) score += 1.0
                if (chord.pitchClasses.all { it in scale }) score += 2.0
            }
            val tonicPc = key.tonic.pitchClass
            if (chords.first().root.pitchClass == tonicPc) score += 1.5
            if (chords.last().root.pitchClass == tonicPc) score += 2.5
            // Prefer the simpler key signature when two candidates tie.
            score -= Math.abs(key.fifths) * 0.01
            if (score > bestScore) {
                bestScore = score
                best = key
            }
        }
        return best
    }

    /** The 1-based scale degree [note] sits on in [key], or null when it is chromatic. */
    fun degreeOf(note: Note, key: Key): Int? {
        val index = key.notes.indexOfFirst { it.pitchClass == note.pitchClass }
        return if (index >= 0) index + 1 else null
    }

    /**
     * Places [chord] on the same degree of [targetKey], taking the quality from the target
     * scale's own harmony while keeping the original's size (triad, seventh or extended).
     */
    private fun mapToDegree(chord: Chord, degree: Int, sourceKey: Key, targetKey: Key): Chord {
        val wantsSeventh = chord.quality.tones.any { it.degree == 7 }
        val diatonic = targetKey.diatonicChords(seventh = wantsSeventh)
        val target = diatonic.getOrNull(degree - 1)
            ?: return chord.transpose(0, targetKey)

        val quality = when {
            // Suspended and power chords have no third, so the scale cannot recolour them.
            chord.quality.family == ChordFamily.SUSPENDED -> chord.quality
            chord.quality.family == ChordFamily.POWER -> chord.quality
            else -> extendedQuality(target.chord.quality, chord.quality)
        }

        // A slash bass follows the same rule: keep its degree when it is in the source
        // scale, otherwise just move it by the interval between the two tonics.
        val bass = chord.bass?.let { bassNote ->
            val bassDegree = degreeOf(bassNote, sourceKey)
            if (bassDegree != null) {
                targetKey.notes.getOrNull(bassDegree - 1)
            } else {
                Chord.transposeNote(bassNote, sourceKey.semitonesTo(targetKey), targetKey)
            }
        }
        return Chord(target.chord.root, quality, bass)
    }

    /**
     * Widens the target's diatonic quality to match how tall the original chord was, so a
     * 9th stays a 9th and a 13th stays a 13th after the scale change.
     */
    private fun extendedQuality(base: ChordQuality, original: ChordQuality): ChordQuality {
        val topDegree = original.tones.maxOf { it.degree }
        if (topDegree <= 7) return base
        val wanted = when (base.family) {
            ChordFamily.MAJOR -> when (topDegree) {
                9 -> ChordQuality.MAJOR_9
                11 -> ChordQuality.MAJOR_11
                else -> ChordQuality.MAJOR_13
            }
            ChordFamily.MINOR -> when (topDegree) {
                9 -> ChordQuality.MINOR_9
                11 -> ChordQuality.MINOR_11
                else -> ChordQuality.MINOR_13
            }
            ChordFamily.DOMINANT -> when (topDegree) {
                9 -> ChordQuality.DOMINANT_9
                11 -> ChordQuality.DOMINANT_11
                else -> ChordQuality.DOMINANT_13
            }
            ChordFamily.DIMINISHED -> when (base) {
                ChordQuality.HALF_DIMINISHED_7 -> ChordQuality.HALF_DIMINISHED_9
                else -> base
            }
            else -> base
        }
        return wanted
    }
}
