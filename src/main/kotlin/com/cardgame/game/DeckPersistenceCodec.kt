package com.cardgame.game

import com.cardgame.SentryBootstrap
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Deck persistence wire format and migrations.
 *
 * Current format is JSON [CURRENT_SCHEMA_VERSION]. Older legacy line-based saves (`v1` / `v2`)
 * and previous JSON schema versions are upgraded in-memory and rewritten on next save.
 */
object DeckPersistenceCodec {
    const val FORMAT = "code437.decks"
    const val CURRENT_SCHEMA_VERSION = 4

    data class PersistedDeckState(
        val selectedCharacter: String? = null,
        val characterDecks: Map<String, List<String>> = emptyMap(),
        val playerDeckDraw: List<String> = emptyList(),
        val playerDeckDiscard: List<String> = emptyList(),
        val enemyDeckDraw: List<String> = emptyList(),
        val enemyDeckDiscard: List<String> = emptyList(),
        val spawnQueue: List<String> = emptyList(),
        val equipped: Map<String, String> = emptyMap(),
        /** Per-floor enemy elimination objective; null on legacy saves (reconstructed in [GameState.loadDeckPersistenceAtStartup]). */
        val enemyObjectiveQuota: Int? = null,
        val enemyObjectiveDefeated: Int? = null,
        val sourceSchemaVersion: Int = CURRENT_SCHEMA_VERSION,
        val sourceFormat: String = FORMAT,
    )

    private data class VersionProbe(
        val schemaVersion: Int? = null,
    )

    private data class DeckDocV1(
        val schemaVersion: Int = 1,
        val selectedCharacter: String? = null,
        val characterDecks: Map<String, List<String>> = emptyMap(),
        val playerDeckDraw: List<String> = emptyList(),
        val playerDeckDiscard: List<String> = emptyList(),
        val enemyDeckDraw: List<String> = emptyList(),
        val enemyDeckDiscard: List<String> = emptyList(),
    )

    private data class DeckDocV2(
        val schemaVersion: Int = 2,
        val selectedCharacter: String? = null,
        val characterDecks: Map<String, List<String>> = emptyMap(),
        val playerDeckDraw: List<String> = emptyList(),
        val playerDeckDiscard: List<String> = emptyList(),
        val enemyDeckDraw: List<String> = emptyList(),
        val enemyDeckDiscard: List<String> = emptyList(),
        val spawnQueue: List<String> = emptyList(),
    )

    private data class DeckDocV3(
        val format: String = FORMAT,
        val schemaVersion: Int = 3,
        val selectedCharacter: String? = null,
        val characterDecks: Map<String, List<String>> = emptyMap(),
        val playerDeckDraw: List<String> = emptyList(),
        val playerDeckDiscard: List<String> = emptyList(),
        val enemyDeckDraw: List<String> = emptyList(),
        val enemyDeckDiscard: List<String> = emptyList(),
        val spawnQueue: List<String> = emptyList(),
        val equipped: Map<String, String> = emptyMap(),
    )

    private data class DeckDocV4(
        val format: String = FORMAT,
        val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
        val selectedCharacter: String? = null,
        val characterDecks: Map<String, List<String>> = emptyMap(),
        val playerDeckDraw: List<String> = emptyList(),
        val playerDeckDiscard: List<String> = emptyList(),
        val enemyDeckDraw: List<String> = emptyList(),
        val enemyDeckDiscard: List<String> = emptyList(),
        val spawnQueue: List<String> = emptyList(),
        val equipped: Map<String, String> = emptyMap(),
        val enemyObjectiveQuota: Int? = null,
        val enemyObjectiveDefeated: Int? = null,
    )

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val probeAdapter = moshi.adapter(VersionProbe::class.java)
    private val v1Adapter = moshi.adapter(DeckDocV1::class.java)
    private val v2Adapter = moshi.adapter(DeckDocV2::class.java)
    private val v3Adapter = moshi.adapter(DeckDocV3::class.java).indent("  ")
    private val v4Adapter = moshi.adapter(DeckDocV4::class.java).indent("  ")

    fun decode(text: String): PersistedDeckState? {
        val raw = text.trim()
        if (raw.isBlank()) return null
        return if (raw.startsWith("{")) decodeJson(raw) else decodeLegacy(raw)
    }

    fun encodeCurrent(state: PersistedDeckState): String {
        val doc = DeckDocV4(
            format = FORMAT,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            selectedCharacter = state.selectedCharacter,
            characterDecks = state.characterDecks,
            playerDeckDraw = state.playerDeckDraw,
            playerDeckDiscard = state.playerDeckDiscard,
            enemyDeckDraw = state.enemyDeckDraw,
            enemyDeckDiscard = state.enemyDeckDiscard,
            spawnQueue = state.spawnQueue,
            equipped = state.equipped,
            enemyObjectiveQuota = state.enemyObjectiveQuota,
            enemyObjectiveDefeated = state.enemyObjectiveDefeated,
        )
        return v4Adapter.toJson(doc)
    }

    fun needsRewrite(state: PersistedDeckState): Boolean =
        state.sourceFormat != FORMAT || state.sourceSchemaVersion != CURRENT_SCHEMA_VERSION

    private fun decodeJson(raw: String): PersistedDeckState? {
        val probe = runCatching { probeAdapter.fromJson(raw) }
            .onFailure {
                SentryBootstrap.captureCaughtError(
                    message = "Deck persistence probe decode failed",
                    throwable = it,
                )
            }
            .getOrNull() ?: return null
        return when (probe.schemaVersion) {
            1 -> runCatching { v1Adapter.fromJson(raw) }
                .onFailure {
                    SentryBootstrap.captureCaughtError(
                        message = "Deck persistence v1 decode failed",
                        throwable = it,
                    )
                }
                .getOrNull()?.let { v1 ->
                    PersistedDeckState(
                        selectedCharacter = v1.selectedCharacter,
                        characterDecks = v1.characterDecks,
                        playerDeckDraw = v1.playerDeckDraw,
                        playerDeckDiscard = v1.playerDeckDiscard,
                        enemyDeckDraw = v1.enemyDeckDraw,
                        enemyDeckDiscard = v1.enemyDeckDiscard,
                        sourceSchemaVersion = 1,
                        sourceFormat = FORMAT,
                    )
                }
            2 -> runCatching { v2Adapter.fromJson(raw) }
                .onFailure {
                    SentryBootstrap.captureCaughtError(
                        message = "Deck persistence v2 decode failed",
                        throwable = it,
                    )
                }
                .getOrNull()?.let { v2 ->
                    PersistedDeckState(
                        selectedCharacter = v2.selectedCharacter,
                        characterDecks = v2.characterDecks,
                        playerDeckDraw = v2.playerDeckDraw,
                        playerDeckDiscard = v2.playerDeckDiscard,
                        enemyDeckDraw = v2.enemyDeckDraw,
                        enemyDeckDiscard = v2.enemyDeckDiscard,
                        spawnQueue = v2.spawnQueue,
                        sourceSchemaVersion = 2,
                        sourceFormat = FORMAT,
                    )
                }
            3 -> runCatching { v3Adapter.fromJson(raw) }
                .onFailure {
                    SentryBootstrap.captureCaughtError(
                        message = "Deck persistence v3 decode failed",
                        throwable = it,
                    )
                }
                .getOrNull()?.let { v3 ->
                    PersistedDeckState(
                        selectedCharacter = v3.selectedCharacter,
                        characterDecks = v3.characterDecks,
                        playerDeckDraw = v3.playerDeckDraw,
                        playerDeckDiscard = v3.playerDeckDiscard,
                        enemyDeckDraw = v3.enemyDeckDraw,
                        enemyDeckDiscard = v3.enemyDeckDiscard,
                        spawnQueue = v3.spawnQueue,
                        equipped = v3.equipped,
                        sourceSchemaVersion = 3,
                        sourceFormat = v3.format,
                    )
                }
            4 -> runCatching { v4Adapter.fromJson(raw) }
                .onFailure {
                    SentryBootstrap.captureCaughtError(
                        message = "Deck persistence v4 decode failed",
                        throwable = it,
                    )
                }
                .getOrNull()?.let { v4 ->
                    PersistedDeckState(
                        selectedCharacter = v4.selectedCharacter,
                        characterDecks = v4.characterDecks,
                        playerDeckDraw = v4.playerDeckDraw,
                        playerDeckDiscard = v4.playerDeckDiscard,
                        enemyDeckDraw = v4.enemyDeckDraw,
                        enemyDeckDiscard = v4.enemyDeckDiscard,
                        spawnQueue = v4.spawnQueue,
                        equipped = v4.equipped,
                        enemyObjectiveQuota = v4.enemyObjectiveQuota,
                        enemyObjectiveDefeated = v4.enemyObjectiveDefeated,
                        sourceSchemaVersion = 4,
                        sourceFormat = v4.format,
                    )
                }
            else -> null
        }
    }

    private fun decodeLegacy(raw: String): PersistedDeckState? {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val ver = lines.firstOrNull()
        val sourceVer = when (ver) {
            "v1" -> 1
            "v2" -> 2
            else -> return null
        }
        val map = lines.drop(1).mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.take(idx) to line.drop(idx + 1)
        }.toMap()

        fun csv(rawCsv: String?): List<String> =
            rawCsv
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

        fun pipe(rawPipe: String?): List<String> =
            rawPipe
                ?.split('|')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

        val decks = map.entries
            .filter { it.key.startsWith("cd_") }
            .associate { (k, v) -> k.removePrefix("cd_") to csv(v) }
        val equipped = map.entries
            .filter { it.key.startsWith("eq_") }
            .associate { (k, v) -> k.removePrefix("eq_") to v.trim() }

        return PersistedDeckState(
            selectedCharacter = map["sel"]?.trim(),
            characterDecks = decks,
            playerDeckDraw = csv(map["pd"]),
            playerDeckDiscard = csv(map["prd"]),
            enemyDeckDraw = pipe(map["ed"]),
            enemyDeckDiscard = pipe(map["edd"]),
            spawnQueue = csv(map["sq"]),
            equipped = equipped,
            enemyObjectiveQuota = null,
            enemyObjectiveDefeated = null,
            sourceSchemaVersion = sourceVer,
            sourceFormat = "legacy",
        )
    }
}
