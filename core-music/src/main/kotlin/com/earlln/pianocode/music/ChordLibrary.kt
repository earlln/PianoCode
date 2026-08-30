package com.earlln.pianocode.music

/**
 * The catalogue the chord browser walks: twelve roots, each with a base chord per family
 * and every variation built on that base.
 */
object ChordLibrary {

    /**
     * The twelve roots offered in the picker, plus the enharmonic spellings players actually
     * read (D♭ next to C♯), so the list covers every chord symbol found on real sheet music.
     */
    val ROOTS: List<Note> = listOf(
        Note(0, 0),   // C
        Note(0, 1),   // C#
        Note(1, -1),  // Db
        Note(1, 0),   // D
        Note(1, 1),   // D#
        Note(2, -1),  // Eb
        Note(2, 0),   // E
        Note(3, 0),   // F
        Note(3, 1),   // F#
        Note(4, -1),  // Gb
        Note(4, 0),   // G
        Note(4, 1),   // G#
        Note(5, -1),  // Ab
        Note(5, 0),   // A
        Note(5, 1),   // A#
        Note(6, -1),  // Bb
        Note(6, 0),   // B
    )

    /** The twelve distinct pitch classes, one spelling each — used where a short list is wanted. */
    val PRIMARY_ROOTS: List<Note> = listOf(
        Note(0, 0), Note(0, 1), Note(1, 0), Note(2, -1), Note(2, 0), Note(3, 0),
        Note(3, 1), Note(4, 0), Note(5, -1), Note(5, 0), Note(6, -1), Note(6, 0),
    )

    /** Every family, in the order the browser lists them. */
    val FAMILIES: List<ChordFamily> = ChordFamily.entries.toList()

    /** The base chord of [family] on [root] — the "기본 코드" of that group. */
    fun baseChord(root: Note, family: ChordFamily): Chord =
        Chord(root, ChordQuality.byFamily(family).first { it.isBase })

    /** Every chord of [family] on [root]: the base chord first, then all of its variations. */
    fun chordsIn(root: Note, family: ChordFamily): List<Chord> =
        ChordQuality.byFamily(family).map { Chord(root, it) }

    /** The variations only — the base chord left out. */
    fun variationsIn(root: Note, family: ChordFamily): List<Chord> =
        ChordQuality.byFamily(family).filterNot { it.isBase }.map { Chord(root, it) }

    /** Every chord this app knows on [root], across all families. */
    fun allChords(root: Note): List<Chord> = ChordQuality.ALL.map { Chord(root, it) }

    /** The total size of the catalogue, shown on the home screen. */
    val totalChordCount: Int get() = ROOTS.size * ChordQuality.ALL.size

    /**
     * Finds chords whose symbol, name or notes match [query]. Empty query returns nothing,
     * so the search screen starts quiet instead of dumping a thousand rows.
     */
    fun search(query: String, roots: List<Note> = ROOTS): List<Chord> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        // An exact chord symbol wins outright: typing "F#m7b5" should show that chord first.
        val exact = ChordParser.parse(trimmed)
        val lowered = trimmed.lowercase()

        val matches = roots.flatMap { root ->
            ChordQuality.ALL.mapNotNull { quality ->
                val chord = Chord(root, quality)
                val haystack = listOf(
                    chord.symbol, chord.prettySymbol, quality.englishName,
                    quality.koreanName, quality.family.koreanName,
                ).joinToString(" ").lowercase()
                if (haystack.contains(lowered)) chord else null
            }
        }
        return if (exact != null) {
            listOf(exact) + matches.filterNot { it.symbol == exact.symbol }
        } else {
            matches
        }
    }

    /** Chords whose notes are exactly [pitchClasses], for the reverse "what chord is this" lookup. */
    fun identify(pitchClasses: Set<Int>): List<Chord> {
        if (pitchClasses.isEmpty()) return emptyList()
        return ROOTS.flatMap { root ->
            ChordQuality.ALL.mapNotNull { quality ->
                val chord = Chord(root, quality)
                if (chord.pitchClasses.toSet() == pitchClasses) chord else null
            }
        }
    }
}
