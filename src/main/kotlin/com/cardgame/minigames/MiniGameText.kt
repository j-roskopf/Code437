package com.cardgame.minigames

import org.cosplay.CPColor

/**
 * Shared HUD text layout for mini games (slots, Sic Bo). Testable without a canvas.
 */
object MiniGameText {

    fun wrapText(text: String, maxW: Int): List<String> {
        if (maxW <= 0) return emptyList()
        if (text.length <= maxW) return listOf(text)
        val out = mutableListOf<String>()
        var rest = text.trim()
        while (rest.isNotEmpty()) {
            if (rest.length <= maxW) {
                out += rest
                break
            }
            var cut = rest.lastIndexOf(' ', maxW).let { if (it > maxW / 2) it else maxW }
            cut = cut.coerceAtMost(maxW).coerceAtLeast(1)
            out += rest.take(cut).trimEnd()
            rest = rest.drop(cut).trimStart()
        }
        return out
    }

    fun columnLines(blocks: List<Pair<String, CPColor>>, panelWidth: Int): List<Pair<String, CPColor>> {
        if (panelWidth <= 0) return emptyList()
        val lines = mutableListOf<Pair<String, CPColor>>()
        for ((txt, col) in blocks) {
            if (txt.isEmpty()) {
                lines += "" to col
                continue
            }
            for (part in wrapText(txt, panelWidth)) {
                lines += part to col
            }
        }
        return lines
    }
}
