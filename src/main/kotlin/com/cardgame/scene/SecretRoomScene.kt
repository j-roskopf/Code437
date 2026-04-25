package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.GameState
import kotlin.random.Random
import org.cosplay.*
import scala.Option

private enum class SecretMiniGameKind {
    LOCKBOX_PICK,
    HIGH_LOW,
}

object SecretRoomScene {
    private val BG_COLOR = CPColor(18, 15, 30, "secret-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

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

    fun create(): CPScene {
        val game = SecretMiniGameKind.entries.random()
        val lockboxWinSlot = Random.nextInt(1, 4)
        val highLowTarget = Random.nextInt(1, 10)
        var highLowGuessesLeft = 3
        var status = "Pick a game input to begin."
        var won = false
        var resolved = false
        var reward = 0

        val displaySprite = object : CPCanvasSprite("secret-display", emptyScalaSeq(), emptyStringSet()) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val lines = mutableListOf<Pair<String, CPColor>>()
                lines += "SECRET ROOM" to CPColor.C_GOLD1()
                lines += "A hidden lock yields for one key..." to CPColor.C_GREY70()
                lines += "" to CPColor.C_GREY50()

                when (game) {
                    SecretMiniGameKind.LOCKBOX_PICK -> {
                        lines += "Mini-game: Lockbox Pick" to CPColor.C_STEEL_BLUE1()
                        if (!resolved) {
                            lines += "Choose [1] [2] or [3]" to CPColor.C_GREEN1()
                        }
                    }
                    SecretMiniGameKind.HIGH_LOW -> {
                        lines += "Mini-game: High/Low Vault" to CPColor.C_STEEL_BLUE1()
                        if (!resolved) {
                            lines += "Guess 1..9 (${highLowGuessesLeft} tries left)" to CPColor.C_GREEN1()
                        }
                    }
                }

                lines += "" to CPColor.C_GREY50()
                val statusColor = when {
                    resolved && won -> CPColor.C_GREEN1()
                    resolved -> CPColor.C_ORANGE_RED1()
                    else -> CPColor.C_GREY70()
                }
                lines += status to statusColor
                if (resolved) {
                    lines += "Reward: $reward gp" to CPColor.C_GOLD1()
                    lines += "B / ESC return" to CPColor.C_GREY50()
                }

                val centerX = canv.width() / 2
                val centerY = canv.height() / 2 - lines.size
                for ((idx, pair) in lines.withIndex()) {
                    val (text, color) = pair
                    if (text.isEmpty()) continue
                    canv.drawString(centerX - text.length / 2, centerY + idx * 2, 1, text, color, Option.empty())
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("secret-input", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (!ctx.isVisible()) return
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()

                if (resolved) {
                    if (key == KEY_B || key == KEY_ESC) {
                        ctx.switchScene(SceneId.GAME, false)
                    }
                    return
                }

                when (game) {
                    SecretMiniGameKind.LOCKBOX_PICK -> {
                        val pick = when (key) {
                            KEY_1 -> 1
                            KEY_2 -> 2
                            KEY_3 -> 3
                            else -> return
                        }
                        won = pick == lockboxWinSlot
                        reward = if (won) Random.nextInt(24, 71) else Random.nextInt(6, 18)
                        status =
                            if (won) "Perfect pick. The box clicks open."
                            else "Wrong lockbox. You salvage a few coins."
                        GameState.addMoney(reward)
                        GameAudio.playMoneyPickup()
                        resolved = true
                    }
                    SecretMiniGameKind.HIGH_LOW -> {
                        val guess = when (key) {
                            KEY_1 -> 1
                            KEY_2 -> 2
                            KEY_3 -> 3
                            KEY_4 -> 4
                            KEY_5 -> 5
                            KEY_6 -> 6
                            KEY_7 -> 7
                            KEY_8 -> 8
                            KEY_9 -> 9
                            else -> return
                        }
                        highLowGuessesLeft--
                        if (guess == highLowTarget) {
                            won = true
                            reward = when (highLowGuessesLeft) {
                                2 -> 60
                                1 -> 42
                                else -> 30
                            }
                            status = "Exact! The vault pays out."
                            GameState.addMoney(reward)
                            GameAudio.playMoneyPickup()
                            resolved = true
                        } else if (highLowGuessesLeft <= 0) {
                            won = false
                            reward = Random.nextInt(8, 16)
                            status = "The lock seals. You grab leftover coins."
                            GameState.addMoney(reward)
                            GameAudio.playMoneyPickup()
                            resolved = true
                        } else {
                            status = if (guess < highLowTarget) "Too low." else "Too high."
                        }
                    }
                }
            }
        }

        return CPScene(
            SceneId.SECRET_ROOM.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
