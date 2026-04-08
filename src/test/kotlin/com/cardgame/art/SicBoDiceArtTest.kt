package com.cardgame.art

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SicBoDiceArtTest {

    private val expectedW = 30
    private val expectedH = 16

    @Test
    fun eachFace_isSixteenByThirty() {
        for (face in 1..6) {
            val lines = SicBoDiceArt.linesFor(face)
            assertEquals(expectedH, lines.size, "face $face line count")
            lines.forEachIndexed { i, row ->
                assertEquals(expectedW, row.length, "face $face row $i width")
            }
        }
    }

    @Test
    fun faces_haveDistinctArt_notOneGraphicForAll() {
        val f1 = SicBoDiceArt.linesFor(1).joinToString("\n")
        val f2 = SicBoDiceArt.linesFor(2).joinToString("\n")
        val f6 = SicBoDiceArt.linesFor(6).joinToString("\n")
        assertNotEquals(f1, f6)
        assertNotEquals(f1, f2)
    }

    @Test
    fun linesFor_clampsToValidRange() {
        val low = SicBoDiceArt.linesFor(0)
        val high = SicBoDiceArt.linesFor(99)
        assertEquals(SicBoDiceArt.linesFor(1), low)
        assertEquals(SicBoDiceArt.linesFor(6), high)
    }
}
