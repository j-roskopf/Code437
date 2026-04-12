package com.cardgame.game

import java.io.File

/**
 * Persists highest level the player may choose from the level-select screen.
 * Level 1 is always available; beating level N unlocks level N+1.
 */
object Progress {
    private val file: File
        get() = File(System.getProperty("user.home"), ".cardcrawler_progress")

    var maxSelectableLevel: Int = 1
        private set

    init {
        load()
    }

    fun load() {
        try {
            if (file.exists()) {
                val v = file.readText().trim().toIntOrNull() ?: 1
                maxSelectableLevel = v.coerceIn(1, LevelConfig.COUNT)
            }
        } catch (_: Exception) {
            maxSelectableLevel = 1
        }
    }

    fun onLevelCleared(level: Int) {
        if (level >= maxSelectableLevel && level < LevelConfig.COUNT) {
            maxSelectableLevel = level + 1
        } else if (level >= maxSelectableLevel) {
            maxSelectableLevel = LevelConfig.COUNT
        }
        save()
    }

    private fun save() {
        try {
            file.writeText(maxSelectableLevel.toString())
        } catch (_: Exception) {
            // ignore
        }
    }

    fun isUnlocked(level: Int): Boolean = level in 1..maxSelectableLevel

    /** Deletes `~/.cardcrawler_progress` and resets in-memory unlocks to level 1 only. */
    fun deleteSavedProgress() {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
            // ignore
        }
        maxSelectableLevel = 1
    }
}
