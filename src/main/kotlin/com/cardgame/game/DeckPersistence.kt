package com.cardgame.game

import com.cardgame.SentryBootstrap
import java.io.File

/**
 * Persists [GameState] deck data across process restarts (JSON body, `.json` default path).
 * Disabled when system property [PROP_DISABLE] is `"true"` (used by tests).
 * Optional override: [PROP_FILE] = absolute path to the save file (any extension).
 */
object DeckPersistence {
    const val PROP_DISABLE = "code437.noDeckPersistence"
    const val PROP_FILE = "code437.deckPersistenceFile"

    private const val DEFAULT_FILENAME = "decks.json"
    private const val LEGACY_FILENAME = "decks.txt"

    fun enabled(): Boolean = System.getProperty(PROP_DISABLE) != "true"

    private fun defaultSaveDirectory(): File {
        val dir = File(System.getProperty("user.home"), ".code437")
        dir.mkdirs()
        return dir
    }

    /** Same path as [defaultSaveDirectory] but does not create the directory (used when deleting saves only). */
    private fun savesDirWithoutCreating(): File =
        File(System.getProperty("user.home"), ".code437")

    /** Path used when writing saves (and when no override: the default `~/.code437/decks.json`). */
    fun persistenceFile(): File =
        System.getProperty(PROP_FILE)?.let { File(it) }
            ?: File(defaultSaveDirectory(), DEFAULT_FILENAME)

    fun save(text: String) {
        try {
            val f = persistenceFile()
            f.writeText(text, Charsets.UTF_8)
            // One-time migration: remove legacy default filename so reads always hit the JSON file.
            if (System.getProperty(PROP_FILE) == null) {
                val legacy = File(f.parentFile ?: return, LEGACY_FILENAME)
                if (legacy.isFile) {
                    legacy.delete()
                }
            }
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Deck persistence save failed",
                throwable = e,
                attributes = mapOf("path" to persistenceFile().absolutePath),
            )
            // Best-effort; avoid crashing the game on bad permissions, etc.
        }
    }

    fun load(): String? =
        try {
            System.getProperty(PROP_FILE)?.let { path ->
                File(path).takeIf { it.isFile }?.readText(Charsets.UTF_8)
            } ?: run {
                val dir = defaultSaveDirectory()
                val json = File(dir, DEFAULT_FILENAME)
                if (json.isFile) {
                    json.readText(Charsets.UTF_8)
                } else {
                    val legacy = File(dir, LEGACY_FILENAME)
                    legacy.takeIf { it.isFile }?.readText(Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Deck persistence load failed",
                throwable = e,
                attributes = mapOf("path" to persistenceFile().absolutePath),
            )
            null
        }

    /**
     * Removes the deck/build save file(s). Default: `~/.code437/decks.json` and legacy `decks.txt`;
     * if [PROP_FILE] is set, deletes that path only.
     */
    fun deleteSaveFiles() {
        try {
            val override = System.getProperty(PROP_FILE)
            if (override != null) {
                File(override).takeIf { it.isFile }?.delete()
                return
            }
            val dir = savesDirWithoutCreating()
            File(dir, DEFAULT_FILENAME).takeIf { it.isFile }?.delete()
            File(dir, LEGACY_FILENAME).takeIf { it.isFile }?.delete()
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Deck persistence delete failed",
                throwable = e,
                attributes = mapOf("override_path" to System.getProperty(PROP_FILE)),
            )
            // ignore
        }
    }
}
