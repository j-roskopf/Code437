package com.cardgame.game

import com.cardgame.testsupport.TestFixtures.withFreshState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckLoopTest {
    @Test
    fun playerDeck_persistsAcrossLevelAdvance_andAcceptsPurchases() = withFreshState {
        val before = GameState.playerDeckSnapshot()
        val totalBefore = before.draw + before.discard
        GameState.addCardToPlayerDeck(GameState.PlayerDeckCard.SHIELD)
        val afterBuy = GameState.playerDeckSnapshot()
        assertEquals(totalBefore + 1, afterBuy.draw + afterBuy.discard)

        GameState.advanceToNextLevel()
        val afterAdvance = GameState.playerDeckSnapshot()
        assertEquals(totalBefore + 1, afterAdvance.draw + afterAdvance.discard)
    }

    @Test
    fun purchase_appliesOnlyToSelectedCharacterDeck() = withFreshState {
        GameState.selectedPlayerCharacter = PlayerCharacter.KNIGHT
        val kBefore = GameState.characterDeckCards(PlayerCharacter.KNIGHT).size
        val tBefore = GameState.characterDeckCards(PlayerCharacter.THIEF).size
        GameState.addCardToPlayerDeck(GameState.PlayerDeckCard.KEY_BRONZE)
        val kAfter = GameState.characterDeckCards(PlayerCharacter.KNIGHT).size
        val tAfter = GameState.characterDeckCards(PlayerCharacter.THIEF).size
        assertEquals(kBefore + 1, kAfter)
        assertEquals(tBefore, tAfter)
    }

    @Test
    fun drawSpawnForCell_usesPlayerDeckForGoodItemSpawns() = withFreshState {
        val seenGood = mutableSetOf<ItemType>()
        repeat(400) {
            val spawn = GameState.drawSpawnForCell(1, 1, emptyList(), emptyList())
            spawn.first?.let { item ->
                if (item.type != ItemType.SPIKES && item.type != ItemType.BOMB && item.type != ItemType.WALL) {
                    val skipEnemyOnlyTile =
                        (item.type == ItemType.CHEST || item.type == ItemType.GAMBLING || item.type == ItemType.END_LEVEL) &&
                            item.spawnedFromEnemyDeck
                    if (!skipEnemyOnlyTile) {
                        seenGood.add(item.type)
                    }
                }
                GameState.onSpawnedItemResolved(item)
            }
            spawn.second?.let { enemy ->
                GameState.onSpawnedEnemyResolved(enemy)
            }
        }
        assertTrue(seenGood.isNotEmpty(), "Expected at least one player-deck item spawn")
        val allowed = setOf(
            ItemType.HEALTH_POTION,
            ItemType.ATTACK_BOOST,
            ItemType.SHIELD,
            ItemType.REST,
            ItemType.SHOP,
            ItemType.QUEST,
            ItemType.HAND_ARMOR,
            ItemType.HELMET,
            ItemType.NECKLACE,
            ItemType.CHEST_ARMOR,
            ItemType.LEGGINGS,
            ItemType.BOOTS_ARMOR,
            ItemType.CHEST,
        )
        assertTrue(seenGood.all { it in allowed }, "Unexpected player-deck item in spawns: $seenGood")
    }

    @Test
    fun enemyDeck_snapshotTracksDrawAndDiscard() = withFreshState {
        GameState.rebuildEnemyDeckForCurrentLevel()
        val start = GameState.enemyDeckSnapshot()
        assertTrue(start.draw > 0)
        repeat(120) {
            val spawn = GameState.drawSpawnForCell(2, 1, emptyList(), emptyList())
            spawn.first?.let { if (it.type == ItemType.SPIKES || it.type == ItemType.BOMB || it.type == ItemType.WALL) GameState.onSpawnedItemResolved(it) }
            spawn.second?.let { GameState.onSpawnedEnemyResolved(it) }
        }
        val now = GameState.enemyDeckSnapshot()
        assertTrue(now.draw >= 0)
        assertTrue(now.discard >= 0)
    }

    @Test
    fun playerDeck_spawnDraw_stripsNonGridKeysToDiscard_soDrawCountMatchesSpawns() = withFreshState {
        val ch = PlayerCharacter.KNIGHT
        GameState.selectedPlayerCharacter = ch
        GameState.resetForLevel(1)
        GameState.addCardToPlayerDeck(GameState.PlayerDeckCard.KEY_BRONZE)
        while (true) {
            val cards = GameState.characterDeckCards(ch)
            if (cards.size == 1 && cards[0].card == GameState.PlayerDeckCard.KEY_BRONZE) break
            val idx = cards.indexOfFirst { it.card != GameState.PlayerDeckCard.KEY_BRONZE }
            assertTrue(idx >= 0, "Expected a non-key card to remove while trimming deck")
            assertTrue(GameState.removeSelectedCharacterBuildCardAt(idx))
        }
        GameState.resetForLevel(1)
        assertEquals(1, GameState.playerDeckSnapshot().draw)

        val spawn = GameState.drawSpawnForCell(
            0,
            0,
            emptyList(),
            emptyList(),
            spawnSourceOverride = GameState.SpawnSource.PLAYER,
        )
        assertNull(spawn.first)
        assertNull(spawn.second)
        assertEquals(0, GameState.playerDeckSnapshot().draw)
        assertEquals(1, GameState.playerDeckSnapshot().discard)
    }

    @Test
    fun playerDeck_spawnDraw_flushesWhenOnlyEquipmentBlockedCardsRemain() = withFreshState {
        val ch = PlayerCharacter.MAGE
        GameState.selectedPlayerCharacter = ch
        GameState.resetForLevel(1)
        while (true) {
            val cards = GameState.characterDeckCards(ch)
            if (cards.size == 1 && cards[0].card == GameState.PlayerDeckCard.ARMOR) break
            val idx = cards.indexOfFirst { it.card != GameState.PlayerDeckCard.ARMOR }
            assertTrue(idx >= 0, "Expected a non-armor card to remove while trimming deck")
            assertTrue(GameState.removeSelectedCharacterBuildCardAt(idx))
        }
        GameState.resetForLevel(1)
        assertEquals(1, GameState.playerDeckSnapshot().draw)
        val existingEquip = GridItem(ItemType.HAND_ARMOR, value = 0, gridX = 0, gridY = 0, collected = false)

        val spawn = GameState.drawSpawnForCell(
            1,
            1,
            listOf(existingEquip),
            emptyList(),
            spawnSourceOverride = GameState.SpawnSource.PLAYER,
        )
        assertNull(spawn.first)
        assertEquals(0, GameState.playerDeckSnapshot().draw)
        assertEquals(1, GameState.playerDeckSnapshot().discard)
    }

    @Test
    fun armor_doesNotSpawnAsSecondVisibleEquipmentTile() = withFreshState {
        // Force selected character with one armor card baseline.
        GameState.selectedPlayerCharacter = PlayerCharacter.MAGE
        GameState.resetForLevel(1)

        val existingEquip = GridItem(ItemType.HAND_ARMOR, value = 0, gridX = 0, gridY = 0, collected = false)
        repeat(120) {
            val spawn = GameState.drawSpawnForCell(1, 1, listOf(existingEquip), emptyList())
            val item = spawn.first
            if (item != null) {
                assertFalse(item.type.equipmentSlot() != null, "Should not spawn second equipment while one is visible")
                GameState.onSpawnedItemResolved(item)
            }
            spawn.second?.let { GameState.onSpawnedEnemyResolved(it) }
        }
    }

    @Test
    fun armor_equipped_staysInCharacterDeck_butSkipsOneCopyWhenRebuildingDrawPile() = withFreshState {
        GameState.selectedPlayerCharacter = PlayerCharacter.THIEF
        GameState.resetForLevel(1)
        GameState.addCardToPlayerDeck(GameState.PlayerDeckCard.ARMOR)

        val buildSize = GameState.characterDeckCards(PlayerCharacter.THIEF).size
        assertTrue(GameState.characterDeckCards(PlayerCharacter.THIEF).count { it.card == GameState.PlayerDeckCard.ARMOR } >= 1)

        val armorItem = GridItem(
            ItemType.HAND_ARMOR,
            value = 0,
            gridX = 0,
            gridY = 0,
            collected = false,
            spawnedFromPlayerDeck = true,
        )
        GameState.onSpawnedItemResolved(armorItem, consumePlayerArmorFromBuild = true)

        assertEquals(
            buildSize,
            GameState.characterDeckCards(PlayerCharacter.THIEF).size,
            "Saved build still lists the armor card after equip",
        )

        GameState.advanceToNextLevel()
        val piles = GameState.playerDeckSnapshot().let { it.draw + it.discard }
        assertEquals(
            buildSize - 1,
            piles,
            "This run’s draw pile omits one equipped armor copy until resetForLevel",
        )

        GameState.resetForLevel(1)
        val afterReset = GameState.playerDeckSnapshot().let { it.draw + it.discard }
        assertEquals(buildSize, afterReset, "New attempt rebuilds the full deck from the saved build")
    }

    @Test
    fun armor_clearedWithoutEquip_returnsToDiscard_andKeepsCharacterDeck() = withFreshState {
        GameState.selectedPlayerCharacter = PlayerCharacter.MAGE
        GameState.resetForLevel(1)
        val beforeBuild = GameState.characterDeckCards(PlayerCharacter.MAGE).count { it.card == GameState.PlayerDeckCard.ARMOR }
        assertTrue(beforeBuild >= 1)
        val discardBefore = GameState.playerDeckSnapshot().discard
        val armor = GridItem(
            ItemType.HAND_ARMOR,
            value = 1,
            gridX = 0,
            gridY = 0,
            collected = false,
            spawnedFromPlayerDeck = true,
            playerDeckPlus = 0,
        )
        GameState.onSpawnedItemResolved(armor, consumePlayerArmorFromBuild = false)
        assertEquals(
            beforeBuild,
            GameState.characterDeckCards(PlayerCharacter.MAGE).count { it.card == GameState.PlayerDeckCard.ARMOR },
        )
        assertEquals(discardBefore + 1, GameState.playerDeckSnapshot().discard)
    }

    @Test
    fun characterDeckBuildIndicesDisplayOrder_matchesSortedDeckSize() = withFreshState {
        val h = PlayerCharacter.KNIGHT
        val deck = GameState.characterDeckCards(h)
        val order = GameState.characterDeckBuildIndicesDisplayOrder(h)
        assertEquals(deck.size, order.size)
        assertEquals(deck.indices.toSet(), order.toSet())
    }

    @Test
    fun removeSelectedCharacterBuildCardAtDisplayIndex_reducesBuild() = withFreshState {
        GameState.selectedPlayerCharacter = PlayerCharacter.KNIGHT
        val before = GameState.characterDeckCards(PlayerCharacter.KNIGHT).size
        assertTrue(before >= 2, "Fixture should include at least two build cards")
        assertTrue(GameState.removeSelectedCharacterBuildCardAtDisplayIndex(0))
        assertEquals(before - 1, GameState.characterDeckCards(PlayerCharacter.KNIGHT).size)
    }
}

