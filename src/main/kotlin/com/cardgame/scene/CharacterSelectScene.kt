package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.game.GameState
import com.cardgame.game.PlayerCharacter
import org.cosplay.*
import scala.Option

object CharacterSelectScene {
    private val BG_COLOR = CPColor(20, 20, 35, "charsel-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_LEFT = kbKey("KEY_LEFT")
    private val KEY_RIGHT = kbKey("KEY_RIGHT")
    private val KEY_A = kbKey("KEY_LO_A")
    private val KEY_D = kbKey("KEY_LO_D")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")
    private val KEY_SPACE = kbKey("KEY_SPACE")

    private val options = PlayerCharacter.entries.toTypedArray()

    private fun cursorIdx(): Int =
        GameState.characterSelectCursor.coerceIn(0, options.size - 1)

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        val displaySprite = object : CPCanvasSprite("charsel-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val cx = canv.width() / 2
                var row = (canv.height() / 2 - 22).coerceAtLeast(1)

                val title = "CHOOSE YOUR HERO"
                canv.drawString(cx - title.length / 2, row, 1, title, CPColor.C_GOLD1(), Option.empty())
                row += 2

                val sub = "Pick a class, then SPACE to save   B / ESC  back to menu"
                canv.drawString(cx - sub.length / 2, row, 1, sub, CPColor.C_GREY50(), Option.empty())
                row += 2

                val keysLine = "[1] Knight   [2] Thief   [3] Mage   ← / →   A / D"
                canv.drawString(cx - keysLine.length / 2, row, 1, keysLine, CPColor.C_GREY70(), Option.empty())
                row += 3

                val choice = options[cursorIdx()]
                val art = AsciiArt.playerLines(choice)
                val artW = art.maxOfOrNull { it.length } ?: 0
                val artX = cx - artW / 2
                val artY = row
                for ((r, line) in art.withIndex()) {
                    for ((c, ch) in line.withIndex()) {
                        if (ch != ' ') {
                            canv.drawString(artX + c, artY + r, 1, ch.toString(), choice.spriteColor, Option.empty())
                        }
                    }
                }
                row += art.size + 2

                for ((i, pc) in options.withIndex()) {
                    val sel = i == cursorIdx()
                    val prefix = if (sel) "> " else "  "
                    val line = "$prefix${pc.label}"
                    val col = if (sel) CPColor.C_WHITE() else CPColor.C_GREY50()
                    canv.drawString(cx - 12, row, 1, line.padEnd(14), col, Option.empty())
                    row += 1
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("charsel-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()

                fun move(d: Int) {
                    val n = options.size
                    GameState.characterSelectCursor = (cursorIdx() + d + n) % n
                }

                when (key) {
                    KEY_1 -> GameState.characterSelectCursor = 0
                    KEY_2 -> GameState.characterSelectCursor = 1
                    KEY_3 -> GameState.characterSelectCursor = 2
                    KEY_LEFT, KEY_A -> move(-1)
                    KEY_RIGHT, KEY_D -> move(1)
                    KEY_SPACE -> {
                        GameState.selectedPlayerCharacter = options[cursorIdx()]
                        ctx.switchScene("menu", false)
                    }
                    KEY_B, KEY_ESC -> ctx.switchScene("menu", false)
                    else -> {}
                }
            }
        }

        return CPScene(
            "characterselect",
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
