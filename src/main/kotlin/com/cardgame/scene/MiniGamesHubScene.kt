package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.GameState
import com.cardgame.game.MiniGameScores
import org.cosplay.*
import scala.Option

object MiniGamesHubScene {
    private val BG_COLOR = CPColor(20, 20, 35, "minigames-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        val displaySprite = object : CPCanvasSprite("minigames-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val title = "MINI GAMES"
                val highSlots = "Slots high (peak gold): ${MiniGameScores.slotsPeakGold}"
                val highSic = "Sic Bo high (peak gold): ${MiniGameScores.sicboPeakGold}"
                val backHint =
                    if (GameState.minigamesReturnScene == SceneId.GAME) "B / ESC  Back to game"
                    else "B / ESC  Back to menu"
                val options = listOf(
                    "[1]  Slot Machine" to CPColor.C_STEEL_BLUE1(),
                    "[2]  Sic Bo" to CPColor.C_STEEL_BLUE1(),
                    "" to CPColor.C_GREY50(),
                    backHint to CPColor.C_GREY50(),
                )
                val allLines = listOf(title, highSlots, highSic) + options.map { it.first }
                val maxW = allLines.maxOf { it.length }
                val blockX = (canv.width() - maxW) / 2
                var y = ((canv.height() - (2 + (2 + options.size) * 2)) / 2).coerceAtLeast(1)

                canv.drawString(blockX, y, 1, title.padEnd(maxW), CPColor.C_GOLD1(), Option.empty())
                y += 2
                canv.drawString(blockX, y, 1, highSlots.take(canv.width() - 2), CPColor.C_CYAN1(), Option.empty())
                y += 2
                canv.drawString(blockX, y, 1, highSic.take(canv.width() - 2), CPColor.C_CYAN1(), Option.empty())
                y += 2
                for ((text, color) in options) {
                    if (text.isEmpty()) {
                        y += 1
                        continue
                    }
                    canv.drawString(blockX, y, 1, text.padEnd(maxW), color, Option.empty())
                    y += 2
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("minigames-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                when (key) {
                    KEY_1 -> {
                        kotlin.runCatching {
                            ctx.deleteScene(SceneId.SLOTS)
                            ctx.deleteScene(SceneId.SICBO)
                        }
                            .onFailure {
                                SentryBootstrap.captureCaughtError(
                                    message = "Mini games hub scene cleanup failed for slots",
                                    throwable = it,
                                )
                            }
                        SentryBootstrap.info(
                            message = "Mini game entered",
                            attributes = mapOf("mini_game" to "slots"),
                            origin = "game.minigame",
                        )
                        ctx.addScene(SlotMachineScene.create(), false, false, false)
                        ctx.switchScene(SceneId.SLOTS, false)
                    }
                    KEY_2 -> {
                        kotlin.runCatching {
                            ctx.deleteScene(SceneId.SICBO)
                            ctx.deleteScene(SceneId.SLOTS)
                        }
                            .onFailure {
                                SentryBootstrap.captureCaughtError(
                                    message = "Mini games hub scene cleanup failed for sicbo",
                                    throwable = it,
                                )
                            }
                        SentryBootstrap.info(
                            message = "Mini game entered",
                            attributes = mapOf("mini_game" to "sicbo"),
                            origin = "game.minigame",
                        )
                        ctx.addScene(SicBoScene.create(), false, false, false)
                        ctx.switchScene(SceneId.SICBO, false)
                    }
                    KEY_B, KEY_ESC -> ctx.switchScene(GameState.minigamesReturnScene, false)
                    else -> {}
                }
            }
        }

        return CPScene(
            SceneId.MINIGAMES.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
