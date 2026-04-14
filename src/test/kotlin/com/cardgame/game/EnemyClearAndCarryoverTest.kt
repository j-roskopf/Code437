package com.cardgame.game

import com.cardgame.testsupport.TestFixtures.withFreshState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnemyClearAndCarryoverTest {
    @Test
    fun enemyDeck_doesNotReshuffleDiscardIntoDrawWhenDrawEmpty() = withFreshState {
        GameState.resetForLevel(1)
        val draw0 = GameState.enemyDeckSnapshot().draw
        assertTrue(draw0 > 0)
        repeat((draw0 - 1).coerceAtLeast(0)) {
            val c = GameState.drawSpawnForCell(
                1,
                1,
                emptyList(),
                emptyList(),
                spawnSourceOverride = GameState.SpawnSource.ENEMY,
            )
            c.first?.let { GameState.onSpawnedItemResolved(it) }
            c.second?.let { GameState.onSpawnedEnemyResolved(it) }
        }
        val lastCard = GameState.drawSpawnForCell(
            1,
            1,
            emptyList(),
            emptyList(),
            spawnSourceOverride = GameState.SpawnSource.ENEMY,
        )
        assertEquals(ItemType.END_LEVEL, lastCard.first?.type, "Final enemy-deck card should be END_LEVEL")
        lastCard.first?.let { GameState.onSpawnedItemResolved(it) }
        assertEquals(0, GameState.enemyDeckSnapshot().draw)
        assertTrue(GameState.enemyDeckSnapshot().discard > 0)
        val afterExhaust = GameState.drawSpawnForCell(
            1,
            1,
            emptyList(),
            emptyList(),
            spawnSourceOverride = GameState.SpawnSource.ENEMY,
        )
        assertEquals(null, afterExhaust.first)
        assertEquals(null, afterExhaust.second)
        assertEquals(0, GameState.enemyDeckSnapshot().draw, "No mid-floor recycle into draw")
        GameState.peekSpawnQueue().forEach {
            assertEquals(GameState.SpawnSource.PLAYER, it, "HUD queue should be player-only when enemy draw is empty")
        }
    }

    @Test
    fun playerDeck_doesNotRecycleDiscardIntoDrawWhenDrawEmpty() = withFreshState {
        GameState.resetForLevel(1)
        GameState.selectedPlayerCharacter = PlayerCharacter.KNIGHT
        GameState.resetForLevel(1)
        val total = GameState.playerDeckSnapshot().draw + GameState.playerDeckSnapshot().discard
        assertTrue(total > 0)
        repeat(total) {
            val sp = GameState.drawSpawnForCell(
                1,
                1,
                emptyList(),
                emptyList(),
                spawnSourceOverride = GameState.SpawnSource.PLAYER,
            )
            sp.first?.let { GameState.onSpawnedItemResolved(it) }
        }
        assertEquals(0, GameState.playerDeckSnapshot().draw)
        assertTrue(GameState.playerDeckSnapshot().discard > 0)
        val after = GameState.drawSpawnForCell(
            1,
            1,
            emptyList(),
            emptyList(),
            spawnSourceOverride = GameState.SpawnSource.PLAYER,
        )
        assertEquals(null, after.first)
        assertEquals(null, after.second)
        GameState.peekSpawnQueue().forEach {
            assertEquals(GameState.SpawnSource.ENEMY, it, "Queue should omit player when player draw is empty")
        }
    }

    @Test
    fun spawnQueue_emptyWhenBothDrawPilesExhausted() = withFreshState {
        GameState.resetForLevel(1)
        GameState.selectedPlayerCharacter = PlayerCharacter.KNIGHT
        GameState.resetForLevel(1)
        val pt = GameState.playerDeckSnapshot().draw + GameState.playerDeckSnapshot().discard
        repeat(pt) {
            val sp = GameState.drawSpawnForCell(
                1,
                1,
                emptyList(),
                emptyList(),
                spawnSourceOverride = GameState.SpawnSource.PLAYER,
            )
            sp.first?.let { GameState.onSpawnedItemResolved(it) }
        }
        val ed = GameState.enemyDeckSnapshot().draw
        repeat(ed) {
            val sp = GameState.drawSpawnForCell(
                1,
                1,
                emptyList(),
                emptyList(),
                spawnSourceOverride = GameState.SpawnSource.ENEMY,
            )
            sp.first?.let { GameState.onSpawnedItemResolved(it) }
            sp.second?.let { GameState.onSpawnedEnemyResolved(it) }
        }
        assertTrue(GameState.peekSpawnQueue().isEmpty())
    }

    @Test
    fun remainingEnemyUnits_decrementsWhenDeckSpawnedEnemyResolved() = withFreshState {
        GameState.resetForLevel(1)
        val start = GameState.remainingEnemyUnitsForFloor()
        assertTrue(start > 0)
        val e = LevelGenerator.spawnEnemyFromSpec(
            EnemyKind.RAT,
            elite = false,
            gridX = 0,
            gridY = 0,
            spawnedFromEnemyDeck = true,
        )
        GameState.onSpawnedEnemyResolved(e)
        assertEquals(start - 1, GameState.remainingEnemyUnitsForFloor())
    }

    @Test
    fun isFloorClearByEnemyElimination_requiresQuotaAndNoLiveEnemies() = withFreshState {
        GameState.resetForLevel(1)
        val rem = GameState.remainingEnemyUnitsForFloor()
        repeat(rem) {
            GameState.onSpawnedEnemyResolved(
                LevelGenerator.spawnEnemyFromSpec(
                    EnemyKind.RAT,
                    false,
                    0,
                    0,
                    spawnedFromEnemyDeck = true,
                ),
            )
        }
        assertTrue(GameState.isFloorClearByEnemyElimination(gridHasLiveEnemy = false))
        assertFalse(GameState.isFloorClearByEnemyElimination(gridHasLiveEnemy = true))
    }

    @Test
    fun advanceToNextLevel_partialHealsAndPreservesAttack() = withFreshState {
        GameState.resetForLevel(1)
        GameState.playerAttack = 9
        GameState.playerHealth = 5
        GameState.temporaryShield = 4
        GameState.advanceToNextLevel()
        assertEquals(2, GameState.currentLevel)
        assertEquals(9, GameState.playerAttack)
        assertEquals(0, GameState.temporaryShield)
        val expectedHeal = kotlin.math.max(
            4,
            kotlin.math.round(GameState.PLAYER_MAX_HEALTH * 0.25).toInt(),
        )
        assertEquals(
            kotlin.math.min(GameState.PLAYER_MAX_HEALTH, 5 + expectedHeal),
            GameState.playerHealth,
        )
    }

    @Test
    fun spawnEnemyFromSpec_laterFloorsTrendHigherCombinedStats() = withFreshState {
        val low = mutableListOf<Int>()
        val high = mutableListOf<Int>()
        repeat(60) {
            GameState.resetForLevel(1)
            val e = LevelGenerator.spawnEnemyFromSpec(EnemyKind.RAT, false, 0, 0)
            low.add(e.health + e.attack)
        }
        repeat(60) {
            GameState.resetForLevel(6)
            val e = LevelGenerator.spawnEnemyFromSpec(EnemyKind.RAT, false, 0, 0)
            high.add(e.health + e.attack)
        }
        assertTrue(high.average() > low.average() - 0.01, "Expected higher floors to roll stronger enemies on average")
    }
}
