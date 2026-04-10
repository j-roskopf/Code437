package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.CampfireArt
import com.cardgame.game.GameState
import com.cardgame.quest.QuestSystem
import org.cosplay.*
import scala.Option

/**
 * Campfire rest interstitial: animated fire, optional heal on confirm, return to game.
 */
object RestScene {
    private val BG_COLOR = CPColor(12, 14, 28, "rest-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_R = kbKey("KEY_LO_R")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    private fun centerStringX(text: String, canvasWidth: Int): Int =
        (canvasWidth / 2 - text.length / 2).coerceAtLeast(0)

    fun create(): CPScene {
        var frame = 0
        var rested = false
        var lastHealApplied = 0

        val contentSprite = object : CPCanvasSprite("rest-content", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                frame++
            }

            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()
                val previewHeal = QuestSystem.restHealAmount(GameState.playerHealth)

                val artW = CampfireArt.width()
                val artH = CampfireArt.height()
                val gapAfterFire = 3

                val textLines = buildList {
                    add("REST POINT" to CPColor.C_GOLD1())
                    add("" to CPColor.C_GREY70())
                    add("Warm yourself by the fire." to CPColor.C_GREY70())
                    add("" to CPColor.C_GREY70())
                    if (!rested) {
                        add("Resting restores +$previewHeal HP." to CPColor.C_GREEN1())
                        add("" to CPColor.C_GREY50())
                        add("R  Rest by the fire" to CPColor.C_STEEL_BLUE1())
                        add("B / ESC  Leave" to CPColor.C_GREY50())
                    } else {
                        val msg = when {
                            lastHealApplied > 0 -> "Restored +$lastHealApplied HP. The embers glow."
                            else -> "You linger by the fire, content."
                        }
                        add(msg to CPColor.C_GREEN1())
                        add("" to CPColor.C_GREY50())
                        add("R / B / ESC  Back to the crawl" to CPColor.C_GREY50())
                    }
                }

                val totalBlockH = artH + gapAfterFire + textLines.size
                val blockTop = ((h - totalBlockH) / 2).coerceAtLeast(1)

                val fireX = (w - artW) / 2
                val fireY = blockTop
                CampfireArt.draw(canv, frame, fireX, fireY, 2)

                var row = blockTop + artH + gapAfterFire
                for ((txt, col) in textLines) {
                    if (txt.isEmpty()) {
                        row += 1
                        continue
                    }
                    canv.drawString(centerStringX(txt, w), row, 1, txt, col, Option.empty())
                    row += 1
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("rest-input", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                when (evt.get().key()) {
                    KEY_R -> {
                        if (!rested) {
                            lastHealApplied = QuestSystem.restHealAmount(GameState.playerHealth)
                            if (lastHealApplied > 0) GameState.playerHealth += lastHealApplied
                            rested = true
                        } else {
                            ctx.switchScene(SceneId.GAME, false)
                        }
                    }
                    KEY_B, KEY_ESC -> ctx.switchScene(SceneId.GAME, false)
                    else -> {}
                }
            }
        }

        return CPScene(
            SceneId.REST.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(contentSprite, inputSprite)
        )
    }
}
