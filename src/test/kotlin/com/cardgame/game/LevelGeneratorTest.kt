package com.cardgame.game

import com.cardgame.testsupport.TestFixtures.withFreshState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LevelGeneratorTest {
    @Test
    fun keyTierForLevel_usesLevelBands() {
        assertEquals(KeyTier.BRONZE, LevelGenerator.keyTierForLevel(1))
        assertEquals(KeyTier.BRONZE, LevelGenerator.keyTierForLevel(3))
        assertEquals(KeyTier.SILVER, LevelGenerator.keyTierForLevel(4))
        assertEquals(KeyTier.SILVER, LevelGenerator.keyTierForLevel(6))
        assertEquals(KeyTier.GOLD, LevelGenerator.keyTierForLevel(7))
    }

    @Test
    fun buildEnemyDeck_includesQuestHazardCards() = withFreshState {
        val deck = LevelGenerator.buildEnemyDeckForLevel(1, 120)
        val quests = deck.filter { it.hazardType == ItemType.QUEST }
        assertTrue(quests.size >= 3, "Enemy deck should include at least MIN_QUEST_CARDS_PER_ENEMY_DECK quest tiles when quests remain")
    }

    @Test
    fun buildEnemyDeck_chestCardsMatchLevelTier() = withFreshState {
        val bronze = LevelGenerator.buildEnemyDeckForLevel(2, 3000).filter { it.hazardType == ItemType.CHEST }
        assertTrue(bronze.isNotEmpty(), "Expected some chest cards on low levels")
        assertTrue(bronze.all { it.hazardTier == KeyTier.BRONZE })
        val silver = LevelGenerator.buildEnemyDeckForLevel(5, 3000).filter { it.hazardType == ItemType.CHEST }
        assertTrue(silver.isNotEmpty())
        assertTrue(silver.all { it.hazardTier == KeyTier.SILVER })
        val gold = LevelGenerator.buildEnemyDeckForLevel(10, 3000).filter { it.hazardType == ItemType.CHEST }
        assertTrue(gold.isNotEmpty())
        assertTrue(gold.all { it.hazardTier == KeyTier.GOLD })
    }

    @Test
    fun gambling_neverRollsWithZeroMoney() = withFreshState {
        repeat(800) {
            val item = LevelGenerator.randomItemAt(1, 1)
            assertFalse(item.type == ItemType.GAMBLING)
        }
    }

    @Test
    fun gambling_canRollWhenPlayerHasGold() = withFreshState {
        GameState.addMoney(100)
        var saw = false
        repeat(4000) {
            val item = LevelGenerator.randomItemAt(1, 1)
            if (item.type == ItemType.GAMBLING) saw = true
        }
        assertTrue(saw, "Expected GAMBLING to appear at least once with gold in pool")
    }

    @Test
    fun enemyDeck_buildExcludesGamblingWhenPlayerHasNoMoney() = withFreshState {
        GameState.resetForLevel(1)
        val deck = LevelGenerator.buildEnemyDeckForLevel(1, 800)
        assertTrue(deck.none { it.hazardType == ItemType.GAMBLING })
    }

    @Test
    fun enemyDeck_buildIncludesGamblingWhenPlayerHasMoney() = withFreshState {
        GameState.resetForLevel(1)
        GameState.addMoney(50)
        val deck = LevelGenerator.buildEnemyDeckForLevel(1, 2000)
        assertTrue(deck.any { it.hazardType == ItemType.GAMBLING })
    }

    @Test
    fun hazardRate_isLowButNonZero() = withFreshState {
        val samples = 4000
        var hazards = 0
        repeat(samples) {
            val item = LevelGenerator.randomItemAt(1, 1)
            if (item.type == ItemType.SPIKES || item.type == ItemType.BOMB) hazards++
        }
        val rate = hazards.toDouble() / samples.toDouble()
        assertTrue(rate > 0.02, "Expected ~5% hazard rate (shared spawner), got $rate")
        assertTrue(rate < 0.10, "Expected low hazard rate, got $rate")
    }

    @Test
    fun wallRate_isRarerButPresent() = withFreshState {
        val samples = 6000
        var wall = 0
        var nonHazard = 0
        repeat(samples) {
            val item = LevelGenerator.randomItemAt(1, 1)
            if (item.type == ItemType.SPIKES || item.type == ItemType.BOMB) return@repeat
            nonHazard++
            if (item.type == ItemType.WALL) wall++
        }
        val rate = wall.toDouble() / nonHazard.toDouble()
        assertTrue(rate > 0.03, "Wall rate too low (~5% of non-hazard rolls): $rate")
        assertTrue(rate < 0.10, "Wall rate too high (~5% of non-hazard rolls): $rate")
    }

    @Test
    fun equipmentDoesNotSpawnIfAlreadyEquipped_samePiece() = withFreshState {
        GameState.setEquippedItem(EquipmentSlot.HEAD, ItemType.HELMET)
        repeat(3000) {
            val item = LevelGenerator.randomItemAt(2, 1)
            assertFalse(item.type == ItemType.HELMET, "Generated already-equipped helmet")
        }
    }

    @Test
    fun equipmentCap_onBoardIsAtMostOneVisible() = withFreshState {
        val existing = mutableListOf<GridItem>()
        repeat(300) { i ->
            val item = LevelGenerator.randomItemAt(i % GridConfig.COLS, (i / GridConfig.COLS) % GridConfig.ROWS, existing)
            val hasEquipmentOnGrid = existing.any { !it.collected && it.type.equipmentSlot() != null }
            if (hasEquipmentOnGrid) {
                assertFalse(item.type.equipmentSlot() != null, "Generated second visible equipment item")
            }
            existing.add(item)
            // simulate collection occasionally so equipment can appear again later
            if (i % 10 == 0) {
                existing.firstOrNull { !it.collected }?.collected = true
            }
        }
    }

    @Test
    fun spawnItemFromType_chestSpawnsRealChestNotRandomLoot() = withFreshState {
        GameState.resetForLevel(1)
        repeat(80) {
            val item = LevelGenerator.spawnItemFromType(
                ItemType.CHEST,
                1,
                1,
                existingItems = emptyList(),
                existingEnemies = emptyList(),
                spawnedFromEnemyDeck = false,
                spawnedFromPlayerDeck = true,
            )
            assertEquals(ItemType.CHEST, item.type, "Expected a gold chest tile, not a random loot type")
            assertTrue(item.spawnedFromPlayerDeck)
        }
    }

    @Test
    fun initialBoardSpawnSources_hasTenEnemyAndFourPlayer() {
        val sources = LevelGenerator.initialBoardSpawnSources(Random(0L))
        assertEquals(GridConfig.COLS * GridConfig.ROWS - 1, sources.size)
        assertEquals(GameState.INITIAL_BOARD_ENEMY_DRAWS, sources.count { it == GameState.SpawnSource.ENEMY })
        assertEquals(GameState.INITIAL_BOARD_PLAYER_DRAWS, sources.count { it == GameState.SpawnSource.PLAYER })
    }

    @Test
    fun fillAllCellsExcept_drawsExactlyTenEnemyDeckCards() = withFreshState {
        GameState.resetForLevel(1)
        assertEquals(25, GameState.enemyDeckSnapshot().draw)
        assertEquals(0, GameState.enemyDeckSnapshot().discard)
        LevelGenerator.fillAllCellsExcept(0 to 0)
        assertEquals(15, GameState.enemyDeckSnapshot().draw, "Enemy deck should lose exactly 10 cards")
        assertEquals(0, GameState.enemyDeckSnapshot().discard)
    }

}
