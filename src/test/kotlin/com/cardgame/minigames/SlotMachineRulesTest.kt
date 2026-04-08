package com.cardgame.minigames

import com.cardgame.art.SlotMachineArt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlotMachineRulesTest {

    @Test
    fun spinPayout_tripleUsesSymbolMultiplier() {
        val bet = 10
        val q = SlotMachineArt.Symbol.QUESTION
        assertEquals(bet * 6, SlotMachineRules.spinPayout(bet, q, q, q))
        val dice = SlotMachineArt.Symbol.DICE
        assertEquals(bet * 55, SlotMachineRules.spinPayout(bet, dice, dice, dice))
    }

    @Test
    fun spinPayout_anyPairPaysBet() {
        val bet = 50
        val a = SlotMachineArt.Symbol.APPLE
        val b = SlotMachineArt.Symbol.PEAR
        assertEquals(bet, SlotMachineRules.spinPayout(bet, a, a, b))
        assertEquals(bet, SlotMachineRules.spinPayout(bet, a, b, b))
        assertEquals(bet, SlotMachineRules.spinPayout(bet, a, b, a))
    }

    @Test
    fun spinPayout_threeDistinct_noWin() {
        val bet = 5
        assertEquals(
            0,
            SlotMachineRules.spinPayout(
                bet,
                SlotMachineArt.Symbol.APPLE,
                SlotMachineArt.Symbol.PEAR,
                SlotMachineArt.Symbol.CARROT
            )
        )
    }

    @Test
    fun collectPaylineHits_middleRowOnly() {
        val bet = 10
        val mid = SlotMachineArt.Symbol.TOILET
        // Middle row triple; other cells chosen so diagonals are not wins (no triple / pair on those lines).
        val grid = arrayOf(
            arrayOf(SlotMachineArt.Symbol.APPLE, SlotMachineArt.Symbol.PEAR, SlotMachineArt.Symbol.CARROT),
            arrayOf(mid, mid, mid),
            arrayOf(SlotMachineArt.Symbol.FOSSIL, SlotMachineArt.Symbol.ROCKET, SlotMachineArt.Symbol.QUESTION),
        )
        val symAt: (Int, Int) -> SlotMachineArt.Symbol = { col, row -> grid[row][col] }
        val (total, hits) = SlotMachineRules.collectPaylineHits(bet, symAt)
        assertEquals(bet * 9, total)
        assertEquals(1, hits.size)
        assertEquals(SlotMachineRules.PaylineKind.MIDDLE, hits[0].kind)
        assertEquals("${mid.label}|${mid.label}|${mid.label}", hits[0].symbolsPipe())
    }

    @Test
    fun collectPaylineHits_diagonalUsesThoseCells_notMiddleRow() {
        val bet = 5
        val rocket = SlotMachineArt.Symbol.ROCKET
        val grid = Array(3) { Array(3) { SlotMachineArt.Symbol.QUESTION } }
        grid[0][0] = rocket
        grid[1][1] = rocket
        grid[2][2] = rocket
        val symAt: (Int, Int) -> SlotMachineArt.Symbol = { col, row -> grid[row][col] }
        val (_, hits) = SlotMachineRules.collectPaylineHits(bet, symAt)
        val diag = hits.single { it.kind == SlotMachineRules.PaylineKind.DIAG_TL_BR }
        assertEquals("Top-left to bottom-right", diag.lineTitle)
        assertEquals("${rocket.label}|${rocket.label}|${rocket.label}", diag.symbolsPipe())
    }

    @Test
    fun collectPaylineHits_stacksMultipleLines() {
        val bet = 2
        val s = SlotMachineArt.Symbol.CARROT
        val grid = Array(3) { Array(3) { s } }
        val symAt: (Int, Int) -> SlotMachineArt.Symbol = { col, row -> grid[row][col] }
        val (total, hits) = SlotMachineRules.collectPaylineHits(bet, symAt)
        assertEquals(3, hits.size)
        assertTrue(hits.any { it.kind == SlotMachineRules.PaylineKind.MIDDLE })
        assertTrue(hits.any { it.kind == SlotMachineRules.PaylineKind.DIAG_TL_BR })
        assertTrue(hits.any { it.kind == SlotMachineRules.PaylineKind.DIAG_BL_TR })
        val expectedPerLine = bet * 12
        assertEquals(expectedPerLine * 3, total)
    }
}
