package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.GameState
import org.cosplay.*
import scala.Option

object GameOverScene {
    private val BG_COLOR = CPColor(10, 5, 15, "go-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_Q = kbKey("KEY_LO_Q")
    private val KEY_R = kbKey("KEY_LO_R")
    private val KEY_M = kbKey("KEY_LO_M")

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        val displaySprite = object : CPCanvasSprite("gameover-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val lines = listOf(
                    "GAME OVER" to CPColor.C_RED1(),
                    "Level ${GameState.currentLevel}  Final Score: ${GameState.score}" to CPColor.C_STEEL_BLUE1(),
                    "HP: ${GameState.playerHealth}  ATK: ${GameState.playerAttack}" to CPColor.C_GREY70(),
                    "R  Retry level   M  Menu   Q  Quit" to CPColor.C_GREY50(),
                )

                val centerX = canv.width() / 2
                val centerY = canv.height() / 2 - lines.size
                for ((idx, pair) in lines.withIndex()) {
                    val (text, color) = pair
                    val startX = centerX - text.length / 2
                    canv.drawString(startX, centerY + idx * 2, 1, text, color, Option.empty())
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("gameover-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return

                val key = evt.get().key()
                when (key) {
                    KEY_Q -> ctx.exitGame()
                    KEY_R -> {
                        GameState.resetForLevel(GameState.currentLevel)
                        kotlin.runCatching { ctx.deleteScene("game") }
                        ctx.addScene(GameScene.create(), false, false, false)
                        ctx.switchScene("game", false)
                    }
                    KEY_M -> ctx.switchScene("menu", false)
                    else -> {}
                }
            }
        }

        return CPScene(
            "gameover",
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
