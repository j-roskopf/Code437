package com.cardgame.game

/**
 * Accumulates per-run metrics. Reset in [GameState.resetForLevel] when a new run starts.
 */
object RunStats {
    private val normalKills = EnemyKind.entries.associateWith { 0 }.toMutableMap()
    private val eliteKills = EnemyKind.entries.associateWith { 0 }.toMutableMap()

    var goldEarned: Int = 0
        private set
    var goldSpent: Int = 0
        private set
    var secretRoomsVisited: Int = 0
        private set
    /** Number of level clears (each time the player reached the level-complete screen). */
    var levelsCleared: Int = 0
        private set
    /** Sum of [GameState.score] banked when leaving each level-complete screen (N). */
    var bankedScore: Int = 0
        private set

    fun reset() {
        EnemyKind.entries.forEach {
            normalKills[it] = 0
            eliteKills[it] = 0
        }
        goldEarned = 0
        goldSpent = 0
        secretRoomsVisited = 0
        levelsCleared = 0
        bankedScore = 0
    }

    fun recordEnemyKill(kind: EnemyKind, elite: Boolean) {
        if (elite) eliteKills[kind] = eliteKills.getValue(kind) + 1
        else normalKills[kind] = normalKills.getValue(kind) + 1
    }

    fun recordGoldEarned(amount: Int) {
        if (amount > 0) goldEarned += amount
    }

    fun recordGoldSpent(amount: Int) {
        if (amount > 0) goldSpent += amount
    }

    fun recordSecretRoom() {
        secretRoomsVisited++
    }

    /** Call when the player confirms a level on the level-complete screen (before advancing). */
    fun bankLevelClear(score: Int) {
        levelsCleared++
        bankedScore += score.coerceAtLeast(0)
    }

    fun normalCount(kind: EnemyKind): Int = normalKills.getValue(kind)

    fun eliteCount(kind: EnemyKind): Int = eliteKills.getValue(kind)

    fun totalKills(kind: EnemyKind): Int = normalCount(kind) + eliteCount(kind)

    fun kindsWithKills(): List<EnemyKind> =
        EnemyKind.entries.filter { totalKills(it) > 0 }
            .sortedByDescending { totalKills(it) }

    /** Score across the run: banked from finished levels plus the current (possibly incomplete) level. */
    fun runTotalScore(currentLevelScore: Int): Int = bankedScore + currentLevelScore.coerceAtLeast(0)

    fun totalEnemyKills(): Int = EnemyKind.entries.sumOf { totalKills(it) }
}
