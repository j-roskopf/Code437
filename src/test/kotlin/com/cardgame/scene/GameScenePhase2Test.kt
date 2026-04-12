package com.cardgame.scene

import com.cardgame.game.ItemType
import com.cardgame.game.KeyTier
import com.cardgame.testsupport.TestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameScenePhase2Test {
    @Test
    fun computeHudLayout_positionsRowsConsistently() {
        val layout = GameScene.computeHudLayout(canvasHeight = 58, questLineCount = 1)
        assertEquals(8, layout.totalLineCount)
        assertEquals(5, layout.questBlockStartLine)
        assertEquals(6, layout.keysLineIndex)
        assertEquals(7, layout.hpBarLineIndex)
    }

    @Test
    fun computeHudLayout_expandsForMultipleQuestLines() {
        val single = GameScene.computeHudLayout(canvasHeight = 58, questLineCount = 1)
        val multi = GameScene.computeHudLayout(canvasHeight = 58, questLineCount = 4)
        assertEquals(8, single.totalLineCount)
        assertEquals(11, multi.totalLineCount)
        assertEquals(5, single.questBlockStartLine)
        assertEquals(5, multi.questBlockStartLine)
        assertEquals(multi.questBlockStartLine + 4, multi.keysLineIndex)
        assertEquals(6, single.keysLineIndex)
        assertEquals(9, multi.keysLineIndex)
    }

    @Test
    fun statsLineFormatting_isStable() {
        assertEquals(
            "LV 2 HP:17 ATK:3 (-1) SHD:5 (+4)",
            GameScene.hudStatsLine(level = 2, hp = 17, atkDisplay = "3 (-1)", shieldDisplay = "5 (+4)")
        )
        assertEquals(
            "ATK:4  HP:20  SHD:10",
            GameScene.playerCardStatsLine(atkDisplay = "4", hp = 20, shieldDisplay = "10")
        )
    }

    @Test
    fun resolveWallChip_decrementsAndDestroysAtZero() {
        val first = GameScene.resolveWallChip(2)
        assertEquals(1, first.remainingHp)
        assertFalse(first.destroyed)

        val second = GameScene.resolveWallChip(1)
        assertEquals(0, second.remainingHp)
        assertTrue(second.destroyed)
    }

    @Test
    fun resolveWallChip_bootstrapsMissingHpToDefaultDurability() {
        val fromZero = GameScene.resolveWallChip(0)
        assertEquals(1, fromZero.remainingHp)
        assertFalse(fromZero.destroyed)
    }

    @Test
    fun resolveMoveCollision_chestLockedWithoutKey() {
        val chest = TestFixtures.item(ItemType.CHEST, x = 2, y = 1, chestOpened = false, tier = KeyTier.SILVER)
        val res = GameScene.resolveMoveCollision(
            newX = 2,
            newY = 1,
            items = listOf(chest),
            hasKeyForTier = { false }
        )
        assertEquals(GameScene.MoveCollision.CHEST_LOCKED, res.collision)
    }

    @Test
    fun resolveMoveCollision_chestUnlockedWhenKeyAvailable() {
        val chest = TestFixtures.item(ItemType.CHEST, x = 1, y = 1, chestOpened = false, tier = KeyTier.BRONZE)
        val res = GameScene.resolveMoveCollision(
            newX = 1,
            newY = 1,
            items = listOf(chest),
            hasKeyForTier = { it == KeyTier.BRONZE }
        )
        assertEquals(GameScene.MoveCollision.CHEST_UNLOCKED, res.collision)
    }

    @Test
    fun resolveMoveCollision_bombAndWallBlockers() {
        val bomb = TestFixtures.item(ItemType.BOMB, x = 3, y = 0)
        val wall = TestFixtures.item(ItemType.WALL, x = 0, y = 2)
        val bombRes = GameScene.resolveMoveCollision(3, 0, listOf(bomb), hasKeyForTier = { false })
        val wallRes = GameScene.resolveMoveCollision(0, 2, listOf(wall), hasKeyForTier = { false })
        assertEquals(GameScene.MoveCollision.BOMB, bombRes.collision)
        assertEquals(GameScene.MoveCollision.WALL, wallRes.collision)
    }

    @Test
    fun resolveMoveCollision_noneWhenTileIsPassable() {
        val potion = TestFixtures.item(ItemType.HEALTH_POTION, x = 4, y = 2)
        val res = GameScene.resolveMoveCollision(4, 2, listOf(potion), hasKeyForTier = { false })
        assertEquals(GameScene.MoveCollision.NONE, res.collision)
    }

    @Test
    fun resolveItemEffect_mapsConsumablesAndEquipment() {
        val hp = GameScene.resolveItemEffect(ItemType.HEALTH_POTION, value = 5, tier = KeyTier.BRONZE)
        assertEquals(5, hp.healthDelta)
        val atk = GameScene.resolveItemEffect(ItemType.ATTACK_BOOST, value = 2, tier = KeyTier.BRONZE)
        assertEquals(2, atk.attackDelta)
        val shd = GameScene.resolveItemEffect(ItemType.SHIELD, value = 4, tier = KeyTier.BRONZE)
        assertEquals(4, shd.tempShieldDelta)
        val key = GameScene.resolveItemEffect(ItemType.KEY, value = 0, tier = KeyTier.GOLD)
        assertEquals(KeyTier.GOLD, key.keyTierToAdd)
        val equip = GameScene.resolveItemEffect(ItemType.CHEST_ARMOR, value = 0, tier = KeyTier.BRONZE)
        assertEquals(ItemType.CHEST_ARMOR, equip.equipType)
    }

    @Test
    fun resolveCombatExchange_appliesDamagePipelineResult() {
        val exchange = GameScene.resolveCombatExchange(
            playerHealth = 20,
            enemyHealth = 7,
            enemyAttack = 9,
            playerAttack = 3,
            damageAfterEnemyAttack = { atk -> (atk - 5).coerceAtLeast(0) }
        )
        assertEquals(4, exchange.playerDamageTaken)
        assertEquals(3, exchange.enemyDamageTaken)
        assertFalse(exchange.enemyDefeated)
        assertFalse(exchange.playerDefeated)
    }

    @Test
    fun resolveCombatExchange_flagsDefeatStates() {
        val exchange = GameScene.resolveCombatExchange(
            playerHealth = 2,
            enemyHealth = 2,
            enemyAttack = 10,
            playerAttack = 5,
            damageAfterEnemyAttack = { 3 }
        )
        assertTrue(exchange.enemyDefeated)
        assertTrue(exchange.playerDefeated)
    }

    @Test
    fun planPostMoveFlow_noMovementSkipsSlideAndBombTick() {
        val plan = GameScene.planPostMoveFlow(
            prevX = 1, prevY = 1, curX = 1, curY = 1,
            inputDx = 0, inputDy = 0
        )
        assertFalse(plan.moved)
        assertEquals(GameScene.SlideKind.NONE, plan.slideKind)
        assertFalse(plan.shouldTickBombs)
        assertFalse(plan.shouldCheckShop)
        assertTrue(plan.shouldCheckLevelComplete)
    }

    @Test
    fun planPostMoveFlow_horizontalMoveUsesColumnSlide() {
        val plan = GameScene.planPostMoveFlow(
            prevX = 1, prevY = 1, curX = 2, curY = 1,
            inputDx = 1, inputDy = 0
        )
        assertTrue(plan.moved)
        assertEquals(GameScene.SlideKind.COLUMN_UP, plan.slideKind)
        assertTrue(plan.shouldTickBombs)
        assertTrue(plan.shouldCheckShop)
    }

    @Test
    fun planPostMoveFlow_verticalMoveUsesRowSlide() {
        val plan = GameScene.planPostMoveFlow(
            prevX = 1, prevY = 1, curX = 1, curY = 2,
            inputDx = 0, inputDy = 1
        )
        assertTrue(plan.moved)
        assertEquals(GameScene.SlideKind.ROW_LEFT, plan.slideKind)
        assertTrue(plan.shouldTickBombs)
        assertTrue(plan.shouldCheckShop)
        assertTrue(plan.shouldCheckLevelComplete)
    }

    @Test
    fun resolvePostMoveSceneRoute_prioritizesGamblingOverShopAndLevelComplete() {
        val route = GameScene.resolvePostMoveSceneRoute(
            moved = true,
            onGamblingTile = true,
            onShopTile = true,
            reachedLevelTarget = true
        )
        assertEquals(GameScene.PostMoveSceneRoute.MINIGAMES, route)
    }

    @Test
    fun resolvePostMoveSceneRoute_prioritizesShopOverLevelComplete() {
        val route = GameScene.resolvePostMoveSceneRoute(
            moved = true,
            onGamblingTile = false,
            onShopTile = true,
            reachedLevelTarget = true
        )
        assertEquals(GameScene.PostMoveSceneRoute.SHOP, route)
    }

    @Test
    fun resolvePostMoveSceneRoute_levelCompleteWhenNoShop() {
        val route = GameScene.resolvePostMoveSceneRoute(
            moved = true,
            onGamblingTile = false,
            onShopTile = false,
            reachedLevelTarget = true
        )
        assertEquals(GameScene.PostMoveSceneRoute.LEVEL_COMPLETE, route)
    }

    @Test
    fun resolvePostMoveSceneRoute_noneWhenNoGatesTriggered() {
        val route = GameScene.resolvePostMoveSceneRoute(
            moved = false,
            onGamblingTile = false,
            onShopTile = false,
            reachedLevelTarget = false
        )
        assertEquals(GameScene.PostMoveSceneRoute.NONE, route)
    }
}
