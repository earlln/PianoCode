package com.earlln.pianocode.music

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the catalogue size. The README and the CHANGELOG quote these numbers, so a change
 * here should be a deliberate one that updates the docs alongside it.
 */
class CatalogueSizeTest {

    @Test
    fun `the catalogue is the advertised size`() {
        assertEquals(55, ChordQuality.ALL.size)
        assertEquals(17, ChordLibrary.ROOTS.size)
        assertEquals(935, ChordLibrary.totalChordCount)
        assertEquals(7, ChordFamily.entries.size)
    }

    @Test
    fun `each family holds the chords the readme lists`() {
        assertEquals(13, ChordQuality.byFamily(ChordFamily.MAJOR).size)
        assertEquals(11, ChordQuality.byFamily(ChordFamily.MINOR).size)
        assertEquals(17, ChordQuality.byFamily(ChordFamily.DOMINANT).size)
        assertEquals(4, ChordQuality.byFamily(ChordFamily.DIMINISHED).size)
        assertEquals(2, ChordQuality.byFamily(ChordFamily.AUGMENTED).size)
        assertEquals(7, ChordQuality.byFamily(ChordFamily.SUSPENDED).size)
        assertEquals(1, ChordQuality.byFamily(ChordFamily.POWER).size)
    }
}
