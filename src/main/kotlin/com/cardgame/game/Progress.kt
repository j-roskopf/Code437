package com.cardgame.game

import com.cardgame.SentryBootstrap
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
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Progress load failed",
                throwable = e,
                attributes = mapOf("path" to file.absolutePath),
            )
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
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Progress save failed",
                throwable = e,
                attributes = mapOf("path" to file.absolutePath),
            )
            // ignore
        }
    }

    fun isUnlocked(level: Int): Boolean = level in 1..maxSelectableLevel

    /** Deletes `~/.cardcrawler_progress` and resets in-memory unlocks to level 1 only. */
    fun deleteSavedProgress() {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Progress delete failed",
                throwable = e,
                attributes = mapOf("path" to file.absolutePath),
            )
            // ignore
        }
        maxSelectableLevel = 1
    }
}
