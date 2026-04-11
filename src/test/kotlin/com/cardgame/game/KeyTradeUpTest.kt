package com.cardgame.game

import com.cardgame.testsupport.TestFixtures.withFreshState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyTradeUpTest {
    @Test
    fun tradeBronzeForSilver_spendsThreeBronze_addsOneSilver() = withFreshState {
        GameState.keysBronze = 3
        GameState.keysSilver = 0
        assertTrue(GameState.tryTradeBronzeKeysForSilver())
        assertEquals(0, GameState.keysBronze)
        assertEquals(1, GameState.keysSilver)
    }

    @Test
    fun tradeBronzeForSilver_failsWithFewerThanCost() = withFreshState {
        GameState.keysBronze = 2
        assertFalse(GameState.tryTradeBronzeKeysForSilver())
        assertEquals(2, GameState.keysBronze)
    }

    @Test
    fun tradeSilverForGold_spendsThreeSilver_addsOneGold() = withFreshState {
        GameState.keysSilver = 3
        GameState.keysGold = 0
        assertTrue(GameState.tryTradeSilverKeysForGold())
        assertEquals(0, GameState.keysSilver)
        assertEquals(1, GameState.keysGold)
    }
}
