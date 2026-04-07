package com.cardgame.quest

import com.cardgame.game.EnemyKind
import com.cardgame.game.ItemType
import kotlin.math.roundToInt
import kotlin.random.Random

enum class QuestTargetType {
    KILL_KIND,
    KILL_ANY,
    KILL_ELITE,
    COLLECT_GOLD,
}

data class QuestTemplate(
    val id: String,
    val title: String,
    val description: String,
    val targetType: QuestTargetType,
    val targetCount: Int,
    val targetKind: EnemyKind? = null,
    val rewardGold: Int,
)

/** Snapshot of run counters when a quest is accepted — progress is deltas from this baseline only. */
data class QuestCountersSnapshot(
    val killByKind: Map<EnemyKind, Int>,
    val totalKills: Int,
    val eliteKills: Int,
    val goldCollected: Int,
)

data class ActiveQuest(
    val template: QuestTemplate,
    var progress: Int = 0,
    val baselineAtAccept: QuestCountersSnapshot,
)

object QuestSystem {
    private const val QUEST_REST_BASE_WEIGHT = 100
    private const val QUEST_WEIGHT = 7
    private const val REST_WEIGHT = 9

    val templates: List<QuestTemplate> = listOf(
        QuestTemplate(
            id = "rat_hunter",
            title = "Rat Hunter",
            description = "Defeat 3 rats.",
            targetType = QuestTargetType.KILL_KIND,
            targetKind = EnemyKind.RAT,
            targetCount = 3,
            rewardGold = 35
        ),
        QuestTemplate(
            id = "slime_cleanup",
            title = "Slime Cleanup",
            description = "Defeat 4 slimes.",
            targetType = QuestTargetType.KILL_KIND,
            targetKind = EnemyKind.SLIME,
            targetCount = 4,
            rewardGold = 32
        ),
        QuestTemplate(
            id = "first_bloodline",
            title = "Elite Breaker",
            description = "Defeat 1 elite enemy.",
            targetType = QuestTargetType.KILL_ELITE,
            targetCount = 1,
            rewardGold = 55
        ),
        QuestTemplate(
            id = "skirmisher",
            title = "Skirmisher",
            description = "Defeat 6 enemies of any kind.",
            targetType = QuestTargetType.KILL_ANY,
            targetCount = 6,
            rewardGold = 40
        ),
        QuestTemplate(
            id = "coin_chaser",
            title = "Coin Chaser",
            description = "Collect 60 gold during this quest.",
            targetType = QuestTargetType.COLLECT_GOLD,
            targetCount = 60,
            rewardGold = 45
        ),
        QuestTemplate(
            id = "haunted_clearout",
            title = "Haunted Clearout",
            description = "Defeat 2 ghosts.",
            targetType = QuestTargetType.KILL_KIND,
            targetKind = EnemyKind.GHOST,
            targetCount = 2,
            rewardGold = 36
        ),
    )

    fun randomOffer(
        excludeQuestIds: Set<String> = emptySet(),
        completedQuestIds: Set<String> = emptySet()
    ): QuestTemplate? {
        val pool = templates.filter { it.id !in excludeQuestIds && it.id !in completedQuestIds }
        return if (pool.isEmpty()) null else pool.random()
    }

    /**
     * @param canSpawnQuestTile if false, [ItemType.QUEST] will not be rolled (e.g. all quests done or at cap).
     */
    fun tunedItemTypeFromPool(
        pool: List<ItemType>,
        canSpawnQuestTile: Boolean,
        allQuestsCompleted: Boolean
    ): ItemType {
        val filtered = if (allQuestsCompleted) pool.filter { it != ItemType.QUEST } else pool
        if (filtered.isEmpty()) return ItemType.HEALTH_POTION

        val nonSpecial = filtered.filter { it != ItemType.QUEST && it != ItemType.REST }
        val totalWeight = QUEST_REST_BASE_WEIGHT +
            (if (ItemType.REST in filtered) REST_WEIGHT else 0) +
            (if (ItemType.QUEST in filtered && canSpawnQuestTile && !allQuestsCompleted) QUEST_WEIGHT else 0)

        var roll = Random.nextInt(totalWeight)
        if (ItemType.REST in filtered) {
            if (roll < REST_WEIGHT) return ItemType.REST
            roll -= REST_WEIGHT
        }
        if (ItemType.QUEST in filtered && canSpawnQuestTile && !allQuestsCompleted) {
            if (roll < QUEST_WEIGHT) return ItemType.QUEST
            roll -= QUEST_WEIGHT
        }

        val basePool = if (nonSpecial.isNotEmpty()) nonSpecial else filtered
        return basePool[Random.nextInt(basePool.size)]
    }

    fun restHealAmount(currentHealth: Int, maxHealth: Int = 30): Int {
        val pct = 0.35f
        val base = (maxHealth * pct).roundToInt().coerceAtLeast(2)
        val missing = (maxHealth - currentHealth).coerceAtLeast(0)
        return base.coerceAtMost(missing)
    }
}

