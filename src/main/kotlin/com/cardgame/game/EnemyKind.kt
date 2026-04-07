package com.cardgame.game

/**
 * Spawnable enemy archetype. Add a new enum entry and a matching [AsciiArt.ENEMY_ARTS] map entry.
 * Which kinds appear on each level is defined by [LevelConfig.enemyKindsForLevel].
 */
enum class EnemyKind(val displayName: String) {
    SLIME("Slime"),
    IMP("Imp"),
    BAT("Bat"),
    GOBLIN("Goblin"),
    GHOST("Ghost"),
    SPIDER("Spider"),
    RAT("Rat"),
    SKELETON("Skeleton"),
}
