package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.*
import org.cosplay.*
import scala.Option

object LevelCompleteScene {
    private val BG_COLOR = CPColor(15, 35, 25, "lc-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_N = kbKey("KEY_LO_N")
    private val KEY_M = kbKey("KEY_LO_M")
    private val KEY_Q = kbKey("KEY_LO_Q")

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        val displaySprite = object : CPCanvasSprite("levelcomplete-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val lv = GameState.currentLevel
                val lines = mutableListOf<Pair<String, CPColor>>()
                lines.add("LEVEL $lv CLEARED!" to CPColor.C_GOLD1())
                lines.add("Score: ${GameState.score} (goal ${LevelConfig.targetScore(lv)})" to CPColor.C_STEEL_BLUE1())
                lines.add("" to CPColor.C_GREY50())
                if (lv < LevelConfig.COUNT) {
                    lines.add("N  Next level" to CPColor.C_GREEN1())
                } else {
                    lines.add("You finished all ${LevelConfig.COUNT} levels!" to CPColor.C_GREEN1())
                    lines.add("N  Run recap & celebration" to CPColor.C_GOLD1())
                }
                lines.add("M  Main menu" to CPColor.C_GREY70())
                lines.add("Q  Quit" to CPColor.C_GREY50())

                val centerX = canv.width() / 2
                val centerY = canv.height() / 2 - lines.size
                for ((idx, pair) in lines.withIndex()) {
                    val (text, color) = pair
                    if (text.isEmpty()) continue
                    val startX = centerX - text.length / 2
                    canv.drawString(startX, centerY + idx * 2, 1, text, color, Option.empty())
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("levelcomplete-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                when (key) {
                    KEY_N -> {
                        RunStats.bankLevelClear(GameState.score)
                        if (GameState.currentLevel < LevelConfig.COUNT) {
                            GameState.shopDismissAction = ShopDismissAction.AdvanceLevelRecreateGame
                            kotlin.runCatching { ctx.deleteScene(SceneId.SHOP) }
                            ctx.addScene(ShopScene.create(), false, false, false)
                            ctx.switchScene(SceneId.SHOP, false)
                        } else {
                            GameState.runEndKind = RunEndKind.VICTORY
                            ctx.switchScene(SceneId.RUN_SUMMARY, false)
                        }
                    }
                    KEY_M -> ctx.switchScene(SceneId.MENU, false)
                    KEY_Q -> ctx.exitGame()
                    else -> {}
                }
            }
        }

        return CPScene(
            SceneId.LEVEL_COMPLETE.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
