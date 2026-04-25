package com.cardgame

import kotlin.test.Test
import kotlin.test.assertNotNull

class GameAudioTest {
    @Test
    fun playerAttackSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/playerAttack.wav")
        assertNotNull(resource)
    }

    @Test
    fun moneyPickupSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/moneyPickup.wav")
        assertNotNull(resource)
    }

    @Test
    fun potionPickupSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/potionPickup.wav")
        assertNotNull(resource)
    }

    @Test
    fun armorPickupSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/armorPickup.wav")
        assertNotNull(resource)
    }

    @Test
    fun chestUnlockSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/chestUnlock.wav")
        assertNotNull(resource)
    }

    @Test
    fun buffPickupSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/buffPickup.wav")
        assertNotNull(resource)
    }

    @Test
    fun diceShakeLoopSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/diceShakeLoop.wav")
        assertNotNull(resource)
    }

    @Test
    fun casinoWinSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/casinoWin.wav")
        assertNotNull(resource)
    }

    @Test
    fun casinoLoseSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/casinoLose.wav")
        assertNotNull(resource)
    }

    @Test
    fun slotSpinLoopSound_isBundledInResources() {
        val resource = javaClass.classLoader.getResource("sounds/slotSpinLoop.wav")
        assertNotNull(resource)
    }
}
