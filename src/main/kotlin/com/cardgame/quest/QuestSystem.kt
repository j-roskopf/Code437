package com.cardgame.quest

import com.cardgame.game.EnemyKind
import com.cardgame.game.ItemType
import com.cardgame.game.LevelConfig
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
        QuestTemplate(
            id = "goblin_trouble",
            title = "Goblin Trouble",
            description = "Defeat 3 goblins.",
            targetType = QuestTargetType.KILL_KIND,
            targetKind = EnemyKind.GOBLIN,
            targetCount = 3,
            rewardGold = 38
        ),
        QuestTemplate(
            id = "imp_pest",
            title = "Imp Pest",
            description = "Defeat 3 imps.",
            targetType = QuestTargetType.KILL_KIND,
            targetKind = EnemyKind.IMP,
            targetCount = 3,
            rewardGold = 38
        ),
    )

    /**
     * Quests that can be completed on [level] (enemy kinds exist on that floor, or target is not a specific kind).
     */
    fun templatesBeatableOnFloor(candidates: List<QuestTemplate>, level: Int): List<QuestTemplate> {
        val onFloor = LevelConfig.enemyKindsForLevel(level).toSet()
        return candidates.filter { t ->
            when (t.targetType) {
                QuestTargetType.KILL_KIND -> (t.targetKind ?: return@filter false) in onFloor
                QuestTargetType.KILL_ELITE,
                QuestTargetType.KILL_ANY,
                QuestTargetType.COLLECT_GOLD,
                -> true
            }
        }
    }

    /**
     * Picks a random quest offer. Prefers templates not in [completedQuestIds] (new this run); if none remain,
     * cycles among any template not in [excludeQuestIds] (re-runs allowed).
     *
     * Only offers quests [templatesBeatableOnFloor] for [currentLevel]. If the “prefer incomplete” pool has
     * no floor-beatable quests (e.g. only ghost/goblin/imp left while floor 1 still has open slots), falls back
     * to any floor-beatable quest in [notExcluded] so cycling never surfaces impossible kill-kind hunts.
     *
     * Returns null when every template that is [templatesBeatableOnFloor] for [currentLevel] is in
     * [excludeQuestIds] (e.g. floor 1 has only five beatable archetypes and the log already holds all five).
     */
    fun randomOffer(
        excludeQuestIds: Set<String> = emptySet(),
        completedQuestIds: Set<String> = emptySet(),
        currentLevel: Int = 1,
    ): QuestTemplate? {
        val notExcluded = templates.filter { it.id !in excludeQuestIds }
        require(notExcluded.isNotEmpty()) { "randomOffer: all quest templates are active (log full overlap)" }
        val preferIncomplete = notExcluded.filter { it.id !in completedQuestIds }
        val primaryPool = if (preferIncomplete.isNotEmpty()) preferIncomplete else notExcluded

        templatesBeatableOnFloor(primaryPool, currentLevel).randomOrNull()?.let { return it }
        if (primaryPool !== notExcluded) {
            templatesBeatableOnFloor(notExcluded, currentLevel).randomOrNull()?.let { return it }
        }
        val beatableAll = templatesBeatableOnFloor(templates, currentLevel).filter { it.id !in excludeQuestIds }
        return beatableAll.randomOrNull()
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

    fun restHealAmount(@Suppress("UNUSED_PARAMETER") currentHealth: Int, maxHealth: Int = 30): Int {
        val pct = 0.35f
        val base = (maxHealth * pct).roundToInt().coerceAtLeast(2)
        return base
    }
}

