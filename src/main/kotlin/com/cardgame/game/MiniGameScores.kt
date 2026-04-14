package com.cardgame.game

import com.cardgame.SentryBootstrap
import java.io.File

/**
 * Persists high scores for mini games launched from the main menu.
 * Format: simple `key=value` lines in `~/.cardcrawler_minigames`.
 */
object MiniGameScores {
    private const val KEY_SLOTS_PEAK = "slots_peak_gold"
    private const val KEY_SICBO_PEAK = "sicbo_peak_gold"

    private val file: File
        get() = File(System.getProperty("user.home"), ".cardcrawler_minigames")

    /** Best peak gold reached in any slot session from the mini games menu. */
    var slotsPeakGold: Int = 0
        private set

    /** Best peak gold reached in any Sic Bo session from the mini games menu. */
    var sicboPeakGold: Int = 0
        private set

    init {
        load()
    }

    fun load() {
        try {
            if (!file.exists()) return
            for (line in file.readLines()) {
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("#")) continue
                val idx = t.indexOf('=')
                if (idx <= 0) continue
                val k = t.substring(0, idx).trim()
                val v = t.substring(idx + 1).trim().toIntOrNull() ?: continue
                when (k) {
                    KEY_SLOTS_PEAK -> slotsPeakGold = v.coerceAtLeast(0)
                    KEY_SICBO_PEAK -> sicboPeakGold = v.coerceAtLeast(0)
                }
            }
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Mini game scores load failed",
                throwable = e,
                attributes = mapOf("path" to file.absolutePath),
            )
            slotsPeakGold = 0
            sicboPeakGold = 0
        }
    }

    fun recordSlotsPeakGold(peak: Int) {
        val p = peak.coerceAtLeast(0)
        if (p <= slotsPeakGold) return
        slotsPeakGold = p
        save()
    }

    fun recordSicboPeakGold(peak: Int) {
        val p = peak.coerceAtLeast(0)
        if (p <= sicboPeakGold) return
        sicboPeakGold = p
        save()
    }

    private fun save() {
        try {
            file.writeText(
                buildString {
                    appendLine("# Code 437 mini game high scores")
                    appendLine("$KEY_SLOTS_PEAK=$slotsPeakGold")
                    appendLine("$KEY_SICBO_PEAK=$sicboPeakGold")
                }
            )
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Mini game scores save failed",
                throwable = e,
                attributes = mapOf(
                    "path" to file.absolutePath,
                    "slots_peak_gold" to slotsPeakGold,
                    "sicbo_peak_gold" to sicboPeakGold,
                ),
            )
            // ignore
        }
    }
}
