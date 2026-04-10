package com.cardgame.game

import com.cardgame.testsupport.TestFixtures.withFreshState
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
    fun hazardRate_isLowButNonZero() = withFreshState {
        val samples = 4000
        var hazards = 0
        repeat(samples) {
            val item = LevelGenerator.randomItemAt(1, 1)
            if (item.type == ItemType.SPIKES || item.type == ItemType.BOMB) hazards++
        }
        val rate = hazards.toDouble() / samples.toDouble()
        assertTrue(rate > 0.002, "Expected non-zero-ish hazard rate, got $rate")
        assertTrue(rate < 0.08, "Expected low hazard rate, got $rate")
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
        assertTrue(rate > 0.06, "Wall rate too low: $rate")
        assertTrue(rate < 0.26, "Wall rate too high: $rate")
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
    fun fillAllCellsExcept_preventsOriginDoubleClosedChestSoftlock() = withFreshState {
        repeat(200) {
            val (items, _) = LevelGenerator.fillAllCellsExcept(0 to 0)
            fun closedChestAt(x: Int, y: Int): Boolean {
                val tile = items.find { !it.collected && it.gridX == x && it.gridY == y } ?: return false
                return tile.type == ItemType.CHEST && !tile.chestOpened && !GameState.hasKey(tile.tier)
            }
            assertFalse(
                closedChestAt(1, 0) && closedChestAt(0, 1),
                "Origin got fully blocked by closed chests"
            )
        }
    }
}
