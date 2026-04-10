package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.game.GameState
import kotlin.math.sin
import org.cosplay.*
import scala.Option

object MenuScene {
    private val BG_COLOR = CPColor(20, 20, 35, "menu-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private fun lerpColor(a: CPColor, b: CPColor, t: Float): CPColor {
        val tt = t.coerceIn(0f, 1f)
        return CPColor(
            (a.red() + (b.red() - a.red()) * tt).toInt().coerceIn(0, 255),
            (a.green() + (b.green() - a.green()) * tt).toInt().coerceIn(0, 255),
            (a.blue() + (b.blue() - a.blue()) * tt).toInt().coerceIn(0, 255),
            "menu-lerp"
        )
    }

    private fun menuPx(ch: Char, fg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_4 = kbKey("KEY_4")
    private val KEY_Q = kbKey("KEY_LO_Q")

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        var menuFrame = 0

        val displaySprite = object : CPCanvasSprite("menu-display", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                menuFrame++
            }

            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val f = menuFrame
                val titleArt = AsciiArt.MENU_TITLE_CODE437
                val artH = titleArt.size

                val options = listOf(
                    "[1]  New Game (Level 1)" to CPColor.C_STEEL_BLUE1(),
                    "[2]  Level Select" to CPColor.C_STEEL_BLUE1(),
                    "[3]  Character" to CPColor.C_STEEL_BLUE1(),
                    "[4]  Mini Games" to CPColor.C_STEEL_BLUE1(),
                    "[Q]  Quit" to CPColor.C_GREY50(),
                )
                val totalRows = artH + 4 + 2 * options.size
                val startY = ((canv.height() - totalRows) / 2).coerceAtLeast(1)

                val glowOffsets = listOf(
                    -2 to 0, 2 to 0, 0 to -2, 0 to 2,
                    -1 to 0, 1 to 0, 0 to -1, 0 to 1,
                    -1 to -1, 1 to -1, -1 to 1, 1 to 1,
                    -2 to -1, 2 to -1, -2 to 1, 2 to 1,
                )
                val glowCol = CPColor(35, 55, 120, "menu-glow")
                val glowCol2 = CPColor(55, 85, 160, "menu-glow2")

                for ((row, line) in titleArt.withIndex()) {
                    val lineX = (canv.width() - line.length) / 2
                    for ((col, ch) in line.withIndex()) {
                        if (ch.isWhitespace()) continue
                        for ((i, oxy) in glowOffsets.withIndex()) {
                            val (ox, oy) = oxy
                            val gc = if (i < 4) glowCol2 else glowCol
                            canv.drawPixel(menuPx(ch, gc), lineX + col + ox, startY + row + oy, 1)
                        }
                    }
                }
                for ((row, line) in titleArt.withIndex()) {
                    val lineX = (canv.width() - line.length) / 2
                    for ((col, ch) in line.withIndex()) {
                        if (ch.isWhitespace()) continue
                        val pulse = (sin(f * 0.07 + col * 0.11 + row * 0.09).toFloat() + 1f) * 0.5f
                        val gold = CPColor.C_GOLD1()
                        val hot = CPColor(255, 255, 245, "menu-hot")
                        val cyan = CPColor(180, 235, 255, "menu-glint")
                        val c = lerpColor(lerpColor(gold, hot, pulse * 0.5f), cyan, pulse * 0.22f)
                        canv.drawPixel(menuPx(ch, c), lineX + col, startY + row, 3)
                    }
                }

                var y = startY + artH + 4
                for ((text, color) in options) {
                    val optionX = (canv.width() - text.length) / 2
                    canv.drawString(optionX, y, 2, text, color, Option.empty())
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
                        kotlin.runCatching { ctx.deleteScene(SceneId.GAME) }
                        ctx.addScene(GameScene.create(), false, false, false)
                        ctx.switchScene(SceneId.GAME, false)
                    }
                    KEY_2 -> ctx.switchScene(SceneId.LEVEL_SELECT, false)
                    KEY_3 -> {
                        GameState.characterSelectCursor = GameState.selectedPlayerCharacter.ordinal
                        ctx.switchScene(SceneId.CHARACTER_SELECT, false)
                    }
                    KEY_4 -> {
                        GameState.minigamesReturnScene = SceneId.MENU
                        ctx.switchScene(SceneId.MINIGAMES, false)
                    }
                    KEY_Q -> ctx.exitGame()
                    else -> {}
                }
            }
        }

        return CPScene(
            SceneId.MENU.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
