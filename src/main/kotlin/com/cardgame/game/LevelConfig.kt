package com.cardgame.game

/** Score needed in the current run to clear a level (inclusive). Each step is higher than the last. */
object LevelConfig {
    const val COUNT = 10

    private val TARGET_SCORES = intArrayOf(
        40, 90, 160, 250, 360, 500, 650, 850, 1100, 1400
    )

    /**
     * One short line for the in-game HUD: floor name and/or a merchant aside.
     * Kept ≤ ~34 chars so it fits the left column with [GridConfig.CELL_WIDTH].
     */
    private val HUD_LORE = arrayOf(
        "FLOOR I — Crooked vestibule",
        "FLOOR II — 'Copper buys breath.'",
        "FLOOR III — Sliding archive",
        "FLOOR IV — 'Silver remembers.'",
        "FLOOR V — Brass threshold",
        "FLOOR VI — 'Gold is a promise.'",
        "FLOOR VII — Tilted treasury",
        "FLOOR VIII — 'No refunds here.'",
        "FLOOR IX — Last shuffle",
        "FLOOR X — Sealed roof",
    )

    fun hudLore(level: Int): String =
        HUD_LORE[(level - 1).coerceIn(0, COUNT - 1)]

    fun targetScore(level: Int): Int =
        TARGET_SCORES[(level - 1).coerceIn(0, COUNT - 1)]

    /**
     * Enemy types that can spawn on this level. [LevelGenerator] picks uniformly from this list.
     * Adjust per level as you add content; unused [EnemyKind] values still need art in [AsciiArt.ENEMY_ARTS].
     */
    fun enemyKindsForLevel(level: Int): List<EnemyKind> {
        val lv = level.coerceIn(1, COUNT)
        return when (lv) {
            1 -> listOf(
                EnemyKind.SLIME,
                EnemyKind.BAT,
                EnemyKind.RAT,
                EnemyKind.SPIDER,
            )
            2 -> listOf(EnemyKind.SKELETON, EnemyKind.GHOST)
            3 -> listOf(EnemyKind.GOBLIN, EnemyKind.IMP)
            4 -> listOf(EnemyKind.KING, EnemyKind.QUEEN)
            5 -> listOf(EnemyKind.GARGOYLE)
            6 -> listOf(EnemyKind.OWLBEAR)
            7 -> listOf(EnemyKind.GOLEM)
            8 -> listOf(EnemyKind.DEMON)
            9 -> listOf(EnemyKind.ROBOT)
            10 -> listOf(EnemyKind.EVERYTHING)
            else -> listOf(EnemyKind.GOBLIN, EnemyKind.IMP)
        }
    }
}
