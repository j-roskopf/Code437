package com.cardgame.testsupport

import com.cardgame.game.EnemyCard
import com.cardgame.game.EnemyKind
import com.cardgame.game.GameState
import com.cardgame.game.GridItem
import com.cardgame.game.ItemType
import com.cardgame.game.KeyTier

object TestFixtures {
    fun reset(level: Int = 1) {
        GameState.resetForLevel(level)
    }

    fun withFreshState(level: Int = 1, block: () -> Unit) {
        reset(level)
        block()
        reset(level)
    }

    fun sample(count: Int, block: () -> Unit) {
        repeat(count) { block() }
    }

    fun item(
        type: ItemType,
        x: Int,
        y: Int,
        value: Int = 0,
        tier: KeyTier = KeyTier.BRONZE,
        collected: Boolean = false,
        chestOpened: Boolean = false,
        chestGoldClaimed: Boolean = false,
        bombTicks: Int = 0,
        wallHp: Int = 0
    ): GridItem =
        GridItem(
            type = type,
            value = value,
            gridX = x,
            gridY = y,
            collected = collected,
            tier = tier,
            chestOpened = chestOpened,
            chestGoldClaimed = chestGoldClaimed,
            bombTicks = bombTicks,
            wallHp = wallHp,
        )

    fun enemy(
        kind: EnemyKind,
        x: Int,
        y: Int,
        health: Int = 5,
        attack: Int = 2,
        defeated: Boolean = false,
        elite: Boolean = false
    ): EnemyCard =
        EnemyCard(
            kind = kind,
            health = health,
            attack = attack,
            gridX = x,
            gridY = y,
            defeated = defeated,
            isElite = elite,
        )
}
