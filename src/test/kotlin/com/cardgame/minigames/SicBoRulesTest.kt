package com.cardgame.minigames

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SicBoRulesTest {

    @Test
    fun sumBigSmall_tripleLoses() {
        assertEquals(20, SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumBig, 6, 5, 5))
        assertNull(SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumBig, 4, 4, 4))
        assertNull(SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumSmall, 4, 4, 4))
        assertEquals(20, SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumSmall, 2, 3, 4))
    }

    @Test
    fun sumBoundaries() {
        assertNull(SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumSmall, 6, 5, 1)) // sum 12
        assertNull(SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumBig, 2, 3, 4)) // sum 9
        assertEquals(20, SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumBig, 3, 4, 5)) // 12
    }

    @Test
    fun oddEven_tripleLoses() {
        assertEquals(20, SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumOdd, 2, 3, 6)) // 11 odd
        assertNull(SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumOdd, 3, 3, 3))
        assertEquals(20, SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumEven, 2, 3, 5)) // 10 even
        assertNull(SicBoRules.payoutOnWin(10, SicBoRules.Wager.SumEven, 5, 5, 5))
    }

    @Test
    fun singleDie_payoutByMatches() {
        assertNull(SicBoRules.payoutOnWin(10, SicBoRules.Wager.SingleDie(4), 1, 2, 3))
        assertEquals(20, SicBoRules.payoutOnWin(10, SicBoRules.Wager.SingleDie(4), 4, 1, 2))
        assertEquals(30, SicBoRules.payoutOnWin(10, SicBoRules.Wager.SingleDie(4), 4, 4, 1))
        assertEquals(40, SicBoRules.payoutOnWin(10, SicBoRules.Wager.SingleDie(4), 4, 4, 4))
    }
}
