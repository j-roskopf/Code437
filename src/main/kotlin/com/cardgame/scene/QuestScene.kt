package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.GameState
import org.cosplay.*
import scala.Option

object QuestScene {
    private val BG_COLOR = CPColor(22, 15, 35, "quest-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_4 = kbKey("KEY_4")
    private val KEY_5 = kbKey("KEY_5")
    private val KEY_LO_Y = kbKey("KEY_LO_Y")
    private val KEY_UP_Y = kbKey("KEY_UP_Y")
    private val KEY_LO_N = kbKey("KEY_LO_N")
    private val KEY_UP_N = kbKey("KEY_UP_N")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    private fun activeQuestDropIndexForKey(key: CPKeyboardKey): Int = when (key) {
        KEY_1 -> 0
        KEY_2 -> 1
        KEY_3 -> 2
        KEY_4 -> 3
        KEY_5 -> 4
        else -> -1
    }

    /** Lists active quests with 1–5 hotkeys to abandon (no reward); not used in swap-for-offer mode. */
    private fun appendActiveQuestAbandonLines(lines: MutableList<Pair<String, CPColor>>) {
        if (GameState.activeQuests.isEmpty()) return
        lines += "" to CPColor.C_GREY50()
        lines += "Active quests — abandon (no reward, stay here):" to CPColor.C_GREY70()
        GameState.activeQuests.forEachIndexed { idx, aq ->
            val hotkey = idx + 1
            lines += "[$hotkey] ${aq.template.title}" to CPColor.C_ORANGE1()
        }
        lines += "Press 1-${GameState.activeQuests.size} to drop one from your log." to CPColor.C_GREEN1()
    }

    fun create(): CPScene {
        val displaySprite = object : CPCanvasSprite("quest-display", emptyScalaSeq(), emptyStringSet()) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val q = GameState.pendingQuestOffer
                val lines = mutableListOf<Pair<String, CPColor>>()
                lines += "QUEST BOARD" to CPColor.C_GOLD1()
                lines += "" to CPColor.C_GREY50()
                when {
                    !GameState.canAcceptNewQuest() && q != null -> {
                        lines += "Maximum accepted quests (${GameState.MAX_CONCURRENT_QUESTS}/${GameState.MAX_CONCURRENT_QUESTS})." to CPColor.C_ORANGE1()
                        lines += "Drop one quest to accept this new offer:" to CPColor.C_GREY70()
                        lines += "" to CPColor.C_GREY50()
                        lines += q.title to CPColor.C_STEEL_BLUE1()
                        lines += q.description to CPColor.C_GREY70()
                        lines += "Reward: ${q.rewardGold} gp" to CPColor.C_GOLD1()
                        lines += "" to CPColor.C_GREY50()
                        lines += "Accepted quests:" to CPColor.C_GREY70()
                        GameState.activeQuests.forEachIndexed { idx, aq ->
                            val hotkey = idx + 1
                            lines += "[$hotkey] Drop ${aq.template.title}" to CPColor.C_ORANGE1()
                        }
                        lines += "" to CPColor.C_GREY50()
                        lines += "Press 1-${GameState.activeQuests.size} to abandon one and make room." to CPColor.C_GREEN1()
                        lines += "Y accept this offer once a slot is free." to CPColor.C_GREEN1()
                        lines += "N / B / ESC cancel offer" to CPColor.C_GREY50()
                    }
                    !GameState.canAcceptNewQuest() -> {
                        lines += "Maximum accepted quests (${GameState.MAX_CONCURRENT_QUESTS}/${GameState.MAX_CONCURRENT_QUESTS})." to CPColor.C_ORANGE1()
                        lines += "No contract could be drawn for this floor yet." to CPColor.C_GREY70()
                        lines += "Abandon one quest below — a new offer may appear." to CPColor.C_GREY70()
                        appendActiveQuestAbandonLines(lines)
                        lines += "" to CPColor.C_GREY50()
                        lines += "Press N / B / ESC to return." to CPColor.C_GREY50()
                    }
                    q == null -> {
                        lines += "No new contract fits this floor with your" to CPColor.C_GREY70()
                        lines += "current quest log (or none left to draw)." to CPColor.C_GREY70()
                        appendActiveQuestAbandonLines(lines)
                        lines += "" to CPColor.C_GREY50()
                        lines += "Press N / B / ESC to return." to CPColor.C_GREY50()
                    }
                    else -> {
                        lines += q.title to CPColor.C_STEEL_BLUE1()
                        lines += q.description to CPColor.C_GREY70()
                        lines += "Reward: ${q.rewardGold} gp" to CPColor.C_GOLD1()
                        appendActiveQuestAbandonLines(lines)
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
                val key = evt.get().key()
                val dropIdx = activeQuestDropIndexForKey(key)
                when (key) {
                    KEY_LO_Y, KEY_UP_Y -> {
                        val offer = GameState.pendingQuestOffer
                        when {
                            offer != null && GameState.canAcceptNewQuest() -> {
                                GameState.acceptQuest(offer)
                                ctx.switchScene(SceneId.GAME, false)
                            }
                            offer == null -> {
                                GameState.denyPendingQuest()
                                ctx.switchScene(SceneId.GAME, false)
                            }
                            else -> {
                                // Log still full — abandon a quest with 1–5 first; keep offer on the board.
                            }
                        }
                    }
                    KEY_LO_N, KEY_UP_N, KEY_B, KEY_ESC -> {
                        GameState.denyPendingQuest()
                        ctx.switchScene(SceneId.GAME, false)
                    }
                    else -> {
                        if (dropIdx < 0 || dropIdx >= GameState.activeQuests.size) return
                        if (GameState.dropActiveQuestAt(dropIdx)) {
                            GameState.refreshPendingQuestOfferAfterBoardAbandon()
                        }
                    }
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

