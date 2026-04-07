package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.GameState
import org.cosplay.*
import scala.Option

object MenuScene {
    private val BG_COLOR = CPColor(20, 20, 35, "menu-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_Q = kbKey("KEY_LO_Q")

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        val displaySprite = object : CPCanvasSprite("menu-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val title = "CARD CRAWLER"
                val options = listOf(
                    "[1]  New Game (Level 1)" to CPColor.C_STEEL_BLUE1(),
                    "[2]  Level Select" to CPColor.C_STEEL_BLUE1(),
                    "[3]  Character" to CPColor.C_STEEL_BLUE1(),
                    "[Q]  Quit" to CPColor.C_GREY50(),
                )
                val allLines = listOf(title) + options.map { it.first }
                val maxW = allLines.maxOf { it.length }
                val blockX = (canv.width() - maxW) / 2

                // Rows: title, blank, then each option separated by a blank (step 2 in y)
                val totalRows = 2 * options.size + 1
                var y = ((canv.height() - totalRows) / 2).coerceAtLeast(1)

                canv.drawString(blockX, y, 1, title.padEnd(maxW), CPColor.C_GOLD1(), Option.empty())
                y += 2
                for ((text, color) in options) {
                    canv.drawString(blockX, y, 1, text.padEnd(maxW), color, Option.empty())
                    y += 2
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("menu-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                when (key) {
                    KEY_1 -> {
                        GameState.resetForLevel(1)
                        kotlin.runCatching { ctx.deleteScene("game") }
                        ctx.addScene(GameScene.create(), false, false, false)
                        ctx.switchScene("game", false)
                    }
                    KEY_2 -> ctx.switchScene("levelselect", false)
                    KEY_3 -> {
                        GameState.characterSelectCursor = GameState.selectedPlayerCharacter.ordinal
                        ctx.switchScene("characterselect", false)
                    }
                    KEY_Q -> ctx.exitGame()
                    else -> {}
                }
            }
        }

        return CPScene(
            "menu",
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
