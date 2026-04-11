package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.GameState
import org.cosplay.*
import scala.Option

object QuestScene {
    private val BG_COLOR = CPColor(22, 15, 35, "quest-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_Y = kbKey("KEY_LO_Y")
    private val KEY_N = kbKey("KEY_LO_N")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    fun create(): CPScene {
        val displaySprite = object : CPCanvasSprite("quest-display", emptyScalaSeq(), emptyStringSet()) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val q = GameState.pendingQuestOffer
                val lines = mutableListOf<Pair<String, CPColor>>()
                lines += "QUEST BOARD" to CPColor.C_GOLD1()
                lines += "" to CPColor.C_GREY50()
                when {
                    GameState.pendingQuestAtCapacity -> {
                        lines += "Maximum accepted quests (${GameState.MAX_CONCURRENT_QUESTS}/${GameState.MAX_CONCURRENT_QUESTS})." to CPColor.C_ORANGE1()
                        lines += "Finish or complete a quest before accepting another." to CPColor.C_GREY70()
                        lines += "" to CPColor.C_GREY50()
                        lines += "Press N / B / ESC to return." to CPColor.C_GREY50()
                    }
                    q == null -> {
                        lines += "No quest offer (internal)." to CPColor.C_GREY70()
                        lines += "Press N / B / ESC to return." to CPColor.C_GREY50()
                    }
                    else -> {
                        lines += q.title to CPColor.C_STEEL_BLUE1()
                        lines += q.description to CPColor.C_GREY70()
                        lines += "Reward: ${q.rewardGold} gp" to CPColor.C_GOLD1()
                        lines += "" to CPColor.C_GREY50()
                        lines += "Y accept quest" to CPColor.C_GREEN1()
                        lines += "N deny quest" to CPColor.C_ORANGE1()
                        lines += "B / ESC deny and return" to CPColor.C_GREY50()
                    }
                }

                val cx = canv.width() / 2
                val cy = canv.height() / 2 - lines.size
                for ((idx, pair) in lines.withIndex()) {
                    val (txt, col) = pair
                    if (txt.isEmpty()) continue
                    canv.drawString(cx - txt.length / 2, cy + idx * 2, 1, txt, col, Option.empty())
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("quest-input", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                when (evt.get().key()) {
                    KEY_Y -> {
                        if (GameState.pendingQuestAtCapacity) {
                            GameState.denyPendingQuest()
                        } else {
                            GameState.pendingQuestOffer?.let { GameState.acceptQuest(it) } ?: GameState.denyPendingQuest()
                        }
                        ctx.switchScene(SceneId.GAME, false)
                    }
                    KEY_N, KEY_B, KEY_ESC -> {
                        GameState.denyPendingQuest()
                        ctx.switchScene(SceneId.GAME, false)
                    }
                    else -> {}
                }
            }
        }

        return CPScene(
            SceneId.QUEST.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}

