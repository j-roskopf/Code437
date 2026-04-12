package com.cardgame.game

import com.cardgame.game.GameState.DeckCardInstance
import com.cardgame.game.GameState.PlayerDeckCard
import com.cardgame.game.PlayerCharacter
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
                cd_MAGE=POTION,KEY_BRONZE
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
            assertTrue(GameState.characterDeckCards(PlayerCharacter.MAGE).any { it.card == PlayerDeckCard.KEY_BRONZE })
            val snap = GameState.playerDeckSnapshot()
            assertEquals(1, snap.draw, "Expected one card in player draw pile")
            assertEquals(1, snap.discard)
            assertTrue(snap.top.contains("Potion", ignoreCase = true))
            GameState.persistDecksIfEnabled()
            val round = tmp.readText(Charsets.UTF_8)
            assertTrue(round.contains("\"format\": \"code437.decks\""), round)
            assertTrue(round.contains("\"schemaVersion\": 4"), round)
            assertTrue(round.contains("\"MAGE\": ["), round)
            assertTrue(round.contains("\"KEY_BRONZE\""), round)
            assertTrue(round.contains("\"e,RAT,0\""), round)
            assertTrue(round.contains("\"h,CHEST,BRONZE\""), round)
        } finally {
            if (prevFile == null) System.clearProperty(DeckPersistence.PROP_FILE)
            else System.setProperty(DeckPersistence.PROP_FILE, prevFile)
            if (prevDisable == null) System.clearProperty(DeckPersistence.PROP_DISABLE)
            else System.setProperty(DeckPersistence.PROP_DISABLE, prevDisable)
        }
    }

    @Test
    fun loadDeckPersistence_mapsLegacyKeyTokenToBronzeKeyCard() {
        val tmp = File.createTempFile("code437-deck-legacy", ".txt")
        tmp.deleteOnExit()
        val prevFile = System.getProperty(DeckPersistence.PROP_FILE)
        val prevDisable = System.getProperty(DeckPersistence.PROP_DISABLE)
        System.setProperty(DeckPersistence.PROP_FILE, tmp.absolutePath)
        try {
            System.setProperty(DeckPersistence.PROP_DISABLE, "true")
            GameState.resetForLevel(1)
            tmp.writeText(
                """
                v1
                sel=MAGE
                cd_MAGE=POTION,KEY
                pd=
                prd=
                ed=
                edd=
                """.trimIndent() + "\n",
            )
            if (prevDisable == null) System.clearProperty(DeckPersistence.PROP_DISABLE)
            else System.setProperty(DeckPersistence.PROP_DISABLE, prevDisable)
            GameState.loadDeckPersistenceAtStartup()
            assertEquals(
                listOf(DeckCardInstance(PlayerDeckCard.POTION), DeckCardInstance(PlayerDeckCard.KEY_BRONZE)),
                GameState.characterDeckCards(PlayerCharacter.MAGE),
            )
        } finally {
            if (prevFile == null) System.clearProperty(DeckPersistence.PROP_FILE)
            else System.setProperty(DeckPersistence.PROP_FILE, prevFile)
            if (prevDisable == null) System.clearProperty(DeckPersistence.PROP_DISABLE)
            else System.setProperty(DeckPersistence.PROP_DISABLE, prevDisable)
        }
    }

    @Test
    fun loadDeckPersistence_restoresEquippedGear() {
        val tmp = File.createTempFile("code437-deck-eq", ".txt")
        tmp.deleteOnExit()
        val prevFile = System.getProperty(DeckPersistence.PROP_FILE)
        val prevDisable = System.getProperty(DeckPersistence.PROP_DISABLE)
        System.setProperty(DeckPersistence.PROP_FILE, tmp.absolutePath)
        try {
            System.setProperty(DeckPersistence.PROP_DISABLE, "true")
            GameState.resetForLevel(1)
            tmp.writeText(
                """
                v2
                sel=MAGE
                cd_MAGE=POTION
                pd=
                prd=
                ed=
                edd=
                sq=
                eq_NECK=NECKLACE+2
                """.trimIndent() + "\n",
            )
            if (prevDisable == null) System.clearProperty(DeckPersistence.PROP_DISABLE)
            else System.setProperty(DeckPersistence.PROP_DISABLE, prevDisable)
            GameState.loadDeckPersistenceAtStartup()
            assertEquals(ItemType.NECKLACE, GameState.equippedItems[EquipmentSlot.NECK.ordinal])
            GameState.persistDecksIfEnabled()
            val text = tmp.readText(Charsets.UTF_8)
            assertTrue(text.contains("\"NECK\": \"NECKLACE+2\""), text)
            assertTrue(text.contains("\"schemaVersion\": 4"), text)
        } finally {
            if (prevFile == null) System.clearProperty(DeckPersistence.PROP_FILE)
            else System.setProperty(DeckPersistence.PROP_FILE, prevFile)
            if (prevDisable == null) System.clearProperty(DeckPersistence.PROP_DISABLE)
            else System.setProperty(DeckPersistence.PROP_DISABLE, prevDisable)
        }
    }

    @Test
    fun loadDeckPersistence_migratesJsonSchemaV2ToCurrentV3() {
        val tmp = File.createTempFile("code437-deck-json-v2", ".txt")
        tmp.deleteOnExit()
        val prevFile = System.getProperty(DeckPersistence.PROP_FILE)
        val prevDisable = System.getProperty(DeckPersistence.PROP_DISABLE)
        System.setProperty(DeckPersistence.PROP_FILE, tmp.absolutePath)
        try {
            System.setProperty(DeckPersistence.PROP_DISABLE, "true")
            GameState.resetForLevel(1)
            tmp.writeText(
                """
                {
                  "schemaVersion": 2,
                  "selectedCharacter": "MAGE",
                  "characterDecks": {
                    "MAGE": ["POTION", "KEY_BRONZE"]
                  },
                  "playerDeckDraw": ["POTION"],
                  "playerDeckDiscard": [],
                  "enemyDeckDraw": ["e,RAT,0"],
                  "enemyDeckDiscard": [],
                  "spawnQueue": ["E", "P"]
                }
                """.trimIndent(),
            )
            if (prevDisable == null) System.clearProperty(DeckPersistence.PROP_DISABLE)
            else System.setProperty(DeckPersistence.PROP_DISABLE, prevDisable)
            GameState.loadDeckPersistenceAtStartup()
            assertEquals(PlayerCharacter.MAGE, GameState.selectedPlayerCharacter)
            assertTrue(GameState.characterDeckCards(PlayerCharacter.MAGE).any { it.card == PlayerDeckCard.KEY_BRONZE })
            val rewritten = tmp.readText(Charsets.UTF_8)
            assertTrue(rewritten.contains("\"format\": \"code437.decks\""), rewritten)
            assertTrue(rewritten.contains("\"schemaVersion\": 4"), rewritten)
        } finally {
            if (prevFile == null) System.clearProperty(DeckPersistence.PROP_FILE)
            else System.setProperty(DeckPersistence.PROP_FILE, prevFile)
            if (prevDisable == null) System.clearProperty(DeckPersistence.PROP_DISABLE)
            else System.setProperty(DeckPersistence.PROP_DISABLE, prevDisable)
        }
    }
}
