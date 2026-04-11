package com.cardgame.game

import com.cardgame.testsupport.TestFixtures.withFreshState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
                        (item.type == ItemType.CHEST || item.type == ItemType.GAMBLING) && item.spawnedFromEnemyDeck
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
            ItemType.KEY,
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
    fun armor_consumedDoesNotReturnToDeckCycle() = withFreshState {
        GameState.selectedPlayerCharacter = PlayerCharacter.MAGE
        GameState.resetForLevel(1)
        GameState.addCardToPlayerDeck(GameState.PlayerDeckCard.ARMOR)

        val before = GameState.characterDeckCards(PlayerCharacter.MAGE).count { it == GameState.PlayerDeckCard.ARMOR }
        assertTrue(before >= 1)

        val armorItem = GridItem(ItemType.HAND_ARMOR, value = 0, gridX = 0, gridY = 0, collected = false)
        GameState.onSpawnedItemResolved(armorItem)

        val after = GameState.characterDeckCards(PlayerCharacter.MAGE).count { it == GameState.PlayerDeckCard.ARMOR }
        assertEquals(before - 1, after)
    }
}

