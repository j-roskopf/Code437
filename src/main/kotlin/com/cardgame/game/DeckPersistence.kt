package com.cardgame.game

import java.io.File

/**
 * Persists [GameState] deck data across process restarts.
 * Disabled when system property [PROP_DISABLE] is `"true"` (used by tests).
 * Optional override: [PROP_FILE] = absolute path to the save file.
 */
object DeckPersistence {
    const val PROP_DISABLE = "code437.noDeckPersistence"
    const val PROP_FILE = "code437.deckPersistenceFile"

    fun enabled(): Boolean = System.getProperty(PROP_DISABLE) != "true"

    fun persistenceFile(): File =
        System.getProperty(PROP_FILE)?.let { File(it) }
            ?: run {
                val dir = File(System.getProperty("user.home"), ".code437")
                dir.mkdirs()
                File(dir, "decks.txt")
            }

    fun save(text: String) {
        try {
            persistenceFile().writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {
            // Best-effort; avoid crashing the game on bad permissions, etc.
        }
    }

    fun load(): String? =
        try {
            persistenceFile().takeIf { it.isFile && it.exists() }?.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
}
