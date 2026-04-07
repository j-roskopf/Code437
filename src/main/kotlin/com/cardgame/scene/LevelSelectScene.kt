package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.GameState
import com.cardgame.game.LevelConfig
import com.cardgame.game.Progress
import org.cosplay.*
import scala.Option

object LevelSelectScene {
    private val BG_COLOR = CPColor(20, 20, 35, "ls-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_0 = kbKey("KEY_0")
    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_4 = kbKey("KEY_4")
    private val KEY_5 = kbKey("KEY_5")
    private val KEY_6 = kbKey("KEY_6")
    private val KEY_7 = kbKey("KEY_7")
    private val KEY_8 = kbKey("KEY_8")
    private val KEY_9 = kbKey("KEY_9")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    private fun tryStartLevel(ctx: CPSceneObjectContext, level: Int) {
        if (!Progress.isUnlocked(level)) return
        GameState.resetForLevel(level)
        kotlin.runCatching { ctx.deleteScene("game") }
        ctx.addScene(GameScene.create(), false, false, false)
        ctx.switchScene("game", false)
    }

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        val displaySprite = object : CPCanvasSprite("levelselect-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val centerX = canv.width() / 2
                var row = canv.height() / 2 - 14
                val title = "LEVEL SELECT"
                canv.drawString(centerX - title.length / 2, row, 1, title, CPColor.C_GOLD1(), Option.empty())
                row += 3
                canv.drawString(
                    centerX - 18, row, 1,
                    "Press number 1-9 for levels 1-9, 0 for 10",
                    CPColor.C_GREY50(), Option.empty()
                )
                row += 2
                for (lv in 1..LevelConfig.COUNT) {
                    val unlocked = Progress.isUnlocked(lv)
                    val target = LevelConfig.targetScore(lv)
                    val lock = if (unlocked) "" else "  (locked)"
                    val line = "  $lv  Goal: $target pts$lock"
                    val color = if (unlocked) CPColor.C_STEEL_BLUE1() else CPColor.C_GREY50()
                    canv.drawString(centerX - line.length / 2, row, 1, line, color, Option.empty())
                    row += 2
                }
                row += 1
                val hint = "B / ESC  Back to menu"
                canv.drawString(centerX - hint.length / 2, row, 1, hint, CPColor.C_GREY50(), Option.empty())
            }
        }

        val inputSprite = object : CPCanvasSprite("levelselect-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                when (key) {
                    KEY_1 -> tryStartLevel(ctx, 1)
                    KEY_2 -> tryStartLevel(ctx, 2)
                    KEY_3 -> tryStartLevel(ctx, 3)
                    KEY_4 -> tryStartLevel(ctx, 4)
                    KEY_5 -> tryStartLevel(ctx, 5)
                    KEY_6 -> tryStartLevel(ctx, 6)
                    KEY_7 -> tryStartLevel(ctx, 7)
                    KEY_8 -> tryStartLevel(ctx, 8)
                    KEY_9 -> tryStartLevel(ctx, 9)
                    KEY_0 -> tryStartLevel(ctx, 10)
                    KEY_B, KEY_ESC -> ctx.switchScene("menu", false)
                    else -> {}
                }
            }
        }

        return CPScene(
            "levelselect",
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
