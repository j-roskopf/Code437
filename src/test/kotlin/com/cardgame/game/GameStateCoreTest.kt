package com.cardgame.game

import com.cardgame.scene.SceneId
import com.cardgame.scene.ShopDismissAction
import com.cardgame.testsupport.TestFixtures.withFreshState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameStateCoreTest {
    @Test
    fun resetForLevel_restoresCoreRunState() = withFreshState {
        GameState.playerHealth = 1
        GameState.playerAttack = 99
        GameState.score = 500
        GameState.money = 77
        GameState.keysBronze = 2
        GameState.keysSilver = 3
        GameState.keysGold = 4
        GameState.shopDismissAction = ShopDismissAction.SwitchTo(SceneId.GAME)
        GameState.inventoryReturnScene = SceneId.GAME
        GameState.gameOver = true
        GameState.addTemporaryShield(8)
        GameState.applyWallChipPenalty()
        GameState.setEquippedItem(EquipmentSlot.HEAD, ItemType.HELMET)

        GameState.resetForLevel(2)

        assertEquals(2, GameState.currentLevel)
        assertEquals(GameState.PLAYER_MAX_HEALTH, GameState.playerHealth)
        assertEquals(3, GameState.playerAttack)
        assertEquals(0, GameState.score)
        assertEquals(0, GameState.money)
        assertEquals(0, GameState.keysBronze)
        assertEquals(0, GameState.keysSilver)
        assertEquals(0, GameState.keysGold)
        assertEquals(ShopDismissAction.SwitchTo(SceneId.MENU), GameState.shopDismissAction)
        assertEquals(SceneId.MENU, GameState.inventoryReturnScene)
        assertFalse(GameState.gameOver)
        assertEquals(0, GameState.temporaryShield)
        assertEquals(0, GameState.wallChipAtkPenaltySteps)
        assertTrue(GameState.equippedItems.all { it == null })
    }

    @Test
    fun keyConsumption_prefersBronzeThenSilverThenGold() = withFreshState {
        GameState.keysBronze = 1
        GameState.keysSilver = 1
        GameState.keysGold = 1

        assertTrue(GameState.consumeAnyKey())
        assertEquals(0, GameState.keysBronze)
        assertEquals(1, GameState.keysSilver)
        assertEquals(1, GameState.keysGold)

        assertTrue(GameState.consumeAnyKey())
        assertEquals(0, GameState.keysSilver)
        assertEquals(1, GameState.keysGold)

        assertTrue(GameState.consumeAnyKey())
        assertEquals(0, GameState.keysGold)
        assertFalse(GameState.consumeAnyKey())
    }

    @Test
    fun tryConsumeKey_consumesOnlyRequestedTier() = withFreshState {
        GameState.keysBronze = 1
        GameState.keysSilver = 1

        assertTrue(GameState.tryConsumeKey(KeyTier.BRONZE))
        assertEquals(0, GameState.keysBronze)
        assertEquals(1, GameState.keysSilver)
        assertFalse(GameState.tryConsumeKey(KeyTier.GOLD))
    }

    @Test
    fun playerShieldDisplay_reflectsEquipmentAndTempShield() = withFreshState {
        GameState.setEquippedItem(EquipmentSlot.HEAD, ItemType.HELMET)
        GameState.setEquippedItem(EquipmentSlot.CHEST, ItemType.CHEST_ARMOR)
        assertEquals(2, GameState.playerShield())
        assertEquals("2", GameState.playerShieldDisplay())

        GameState.addTemporaryShield(4)
        assertEquals("2 (+4)", GameState.playerShieldDisplay())
    }

    @Test
    fun damageAfterEnemyAttack_depletesTemporaryShieldBeforeArmor() = withFreshState {
        GameState.setEquippedItem(EquipmentSlot.CHEST, ItemType.CHEST_ARMOR) // +1 armor
        GameState.addTemporaryShield(6)

        val dmg1 = GameState.damageAfterEnemyAttack(10)
        assertEquals(3, dmg1) // 10 - 6 temp - 1 armor
        assertEquals(0, GameState.temporaryShield)

        val dmg2 = GameState.damageAfterEnemyAttack(2)
        assertEquals(1, dmg2) // 2 - 1 armor
    }

    @Test
    fun wallChipPenalty_stacksAndDecaysPerMovementAction() = withFreshState {
        GameState.playerAttack = 3
        assertEquals(3, GameState.effectivePlayerAttack())

        GameState.applyWallChipPenalty()
        assertEquals(2, GameState.wallChipAtkPenaltySteps)
        assertEquals(2, GameState.effectivePlayerAttack())
        assertEquals("2 (-1)", GameState.playerAttackDisplay())

        GameState.applyWallChipPenalty()
        assertEquals(4, GameState.wallChipAtkPenaltySteps)

        GameState.onPlayerMovementActionStart()
        assertEquals(3, GameState.wallChipAtkPenaltySteps)
        assertEquals(2, GameState.effectivePlayerAttack())

        repeat(3) { GameState.onPlayerMovementActionStart() }
        assertEquals(0, GameState.wallChipAtkPenaltySteps)
        assertEquals(3, GameState.effectivePlayerAttack())
        assertEquals("3", GameState.playerAttackDisplay())
    }

    @Test
    fun effectivePlayerAttack_hasMinimumOfOne() = withFreshState {
        GameState.playerAttack = 1
        GameState.applyWallChipPenalty()
        assertEquals(1, GameState.effectivePlayerAttack())
    }
}
