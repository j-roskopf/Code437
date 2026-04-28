package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.DisplayModeSetting
import com.cardgame.game.GridConfig
import com.cardgame.game.UserSettings
import com.cardgame.platform.EmutermFullscreen
import org.cosplay.*
import scala.Option

object SettingsScene {
    private val BG_COLOR = CPColor(20, 20, 35, "settings-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    private const val GAME_TITLE = "Code 437"

    fun create(): CPScene {
        var feedbackTicks = 0
        var feedback = ""

        val inputSprite = object : CPCanvasSprite("settings-input", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (!ctx.isVisible()) return
                if (feedbackTicks > 0) feedbackTicks--
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()

                fun setMode(mode: DisplayModeSetting) {
                    UserSettings.displayMode = mode
                    EmutermFullscreen.applyPreferredMode(GAME_TITLE, mode)
                    feedback = "Display mode set to ${modeLabel(mode)}."
                    feedbackTicks = 120
                    ctx.consumeKbEvent()
                }

                when (key) {
                    KEY_1 -> setMode(DisplayModeSetting.FULLSCREEN)
                    KEY_2 -> setMode(DisplayModeSetting.WINDOWED)
                    KEY_B, KEY_ESC -> {
                        ctx.consumeKbEvent()
                        ctx.switchScene(SceneId.MENU, false)
                    }
                    else -> {}
                }
            }
        }

        val displaySprite = object : CPCanvasSprite("settings-display", emptyScalaSeq(), emptyStringSet()) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val centerX = canv.width() / 2
                var row = (canv.height() / 2 - 9).coerceAtLeast(1)
                val mode = UserSettings.displayMode
                val title = "SETTINGS"
                canv.drawString(centerX - title.length / 2, row, 1, title, CPColor.C_GOLD1(), Option.empty())
                row += 3

                val current = "Display: ${modeLabel(mode)}"
                canv.drawString(centerX - current.length / 2, row, 1, current, CPColor.C_CYAN1(), Option.empty())
                row += 3

                drawOption(canv, centerX, row, "[1]  Full Screen", mode == DisplayModeSetting.FULLSCREEN)
                row += 2
                drawOption(canv, centerX, row, "[2]  Windowed", mode == DisplayModeSetting.WINDOWED)
                row += 3

                val minLine =
                    "Windowed minimum: ${GridConfig.MIN_WINDOWED_COLS}x${GridConfig.MIN_WINDOWED_ROWS} cells"
                canv.drawString(centerX - minLine.length / 2, row, 1, minLine, CPColor.C_GREY70(), Option.empty())
                row += 2
                val note = "Prevents shrinking below the board + side UI footprint."
                canv.drawString(centerX - note.length / 2, row, 1, note, CPColor.C_GREY50(), Option.empty())
                row += 3

                if (feedbackTicks > 0) {
                    canv.drawString(centerX - feedback.length / 2, row, 1, feedback, CPColor.C_GREEN1(), Option.empty())
                    row += 2
                }

                val back = "B / ESC  Back to menu"
                canv.drawString(centerX - back.length / 2, row, 1, back, CPColor.C_GREY50(), Option.empty())
            }
        }

        return CPScene(
            SceneId.SETTINGS.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }

    private fun modeLabel(mode: DisplayModeSetting): String =
        when (mode) {
            DisplayModeSetting.FULLSCREEN -> "Full Screen"
            DisplayModeSetting.WINDOWED -> "Windowed"
        }

    private fun drawOption(canv: CPCanvas, centerX: Int, row: Int, text: String, selected: Boolean) {
        val prefix = if (selected) "> " else "  "
        val line = prefix + text
        val color = if (selected) CPColor.C_WHITE() else CPColor.C_STEEL_BLUE1()
        canv.drawString(centerX - line.length / 2, row, 1, line, color, Option.empty())
    }
}
