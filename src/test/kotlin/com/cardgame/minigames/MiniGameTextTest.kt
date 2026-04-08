package com.cardgame.minigames

import org.cosplay.CPColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniGameTextTest {

    @Test
    fun wrapText_shortUnchanged() {
        assertEquals(listOf("hi"), MiniGameText.wrapText("hi", 10))
    }

    @Test
    fun wrapText_splitsLongLine() {
        val lines = MiniGameText.wrapText("aaa bbb ccc ddd eee fff", 8)
        assertTrue(lines.size >= 2)
        assertTrue(lines.all { it.length <= 8 })
    }

    @Test
    fun wrapText_zeroWidth_empty() {
        assertTrue(MiniGameText.wrapText("hello", 0).isEmpty())
    }

    @Test
    fun columnLines_wrapsAndPreservesColors() {
        val c1 = CPColor.C_RED1()
        val c2 = CPColor.C_GREEN1()
        val lines = MiniGameText.columnLines(
            listOf(
                "WORD WORD WORD WORD" to c1,
                "" to c2,
                "X" to c2,
            ),
            panelWidth = 6
        )
        assertTrue(lines.size >= 4)
        assertEquals(c1, lines[0].second)
        assertEquals("", lines.first { it.first.isEmpty() }.first)
    }
}
