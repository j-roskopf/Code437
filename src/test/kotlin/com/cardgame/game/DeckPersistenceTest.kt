package com.cardgame.game

import com.cardgame.game.GameState.PlayerDeckCard
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckPersistenceTest {
    @Test
    fun loadDeckPersistence_restoresCharacterDecksAndSelection() {
        val tmp = File.createTempFile("code437-deck", ".txt")
        tmp.deleteOnExit()
        val prevFile = System.getProperty(DeckPersistence.PROP_FILE)
        val prevDisable = System.getProperty(DeckPersistence.PROP_DISABLE)
        System.setProperty(DeckPersistence.PROP_FILE, tmp.absolutePath)
        try {
            // Avoid [resetForLevel] → rebuild persisting into [tmp] before we install the fixture.
            System.setProperty(DeckPersistence.PROP_DISABLE, "true")
            GameState.resetForLevel(1)
            tmp.writeText(
                """
                v1
                sel=MAGE
                cd_KNIGHT=POTION,SHIELD
                cd_THIEF=BOW_ARROW
                cd_MAGE=POTION,KEY
                pd=POTION
                prd=CAMPFIRE
                ed=e,RAT,0|h,SPIKES,
                edd=h,CHEST,BRONZE
                """.trimIndent() + "\n",
            )
            if (prevDisable == null) System.clearProperty(DeckPersistence.PROP_DISABLE)
            else System.setProperty(DeckPersistence.PROP_DISABLE, prevDisable)
            GameState.loadDeckPersistenceAtStartup()
            assertEquals(PlayerCharacter.MAGE, GameState.selectedPlayerCharacter)
            assertTrue(GameState.characterDeckCards(PlayerCharacter.MAGE).contains(PlayerDeckCard.KEY))
            val snap = GameState.playerDeckSnapshot()
            assertEquals(1, snap.draw, "Expected one card in player draw pile")
            assertEquals(1, snap.discard)
            assertTrue(snap.top.contains("Potion", ignoreCase = true))
            GameState.persistDecksIfEnabled()
            val round = tmp.readText(Charsets.UTF_8)
            assertTrue(round.contains("cd_MAGE=POTION,KEY"), "Persist should include mage deck: $round")
            assertTrue(round.contains("e,RAT,0"), round)
            assertTrue(round.contains("h,CHEST,BRONZE"), round)
        } finally {
            if (prevFile == null) System.clearProperty(DeckPersistence.PROP_FILE)
            else System.setProperty(DeckPersistence.PROP_FILE, prevFile)
            if (prevDisable == null) System.clearProperty(DeckPersistence.PROP_DISABLE)
            else System.setProperty(DeckPersistence.PROP_DISABLE, prevDisable)
        }
    }
}
