package com.cardgame.scene

import com.cardgame.*
import com.cardgame.game.GameState
import org.cosplay.*
import scala.Option

/**
 * In-game debug toggles (open from [GameScene] with Z). Return with B / ESC.
 */
object DebugMenuScene {
    private val BG_COLOR = CPColor(18, 18, 32, "debugmenu-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_4 = kbKey("KEY_4")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    fun create(): CPScene {
        val displaySprite = object : CPCanvasSprite("debugmenu-display", emptyScalaSeq(), emptyStringSet()) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val lines = mutableListOf<Pair<String, CPColor>>()
                lines += "DEBUG MENU" to CPColor.C_GOLD1()
                lines += "" to CPColor.C_GREY50()
                lines += onOffLine(1, "Invincible (no damage)", GameState.debugInvincible) to CPColor.C_STEEL_BLUE1()
                lines += onOffLine(2, "No score from kills", GameState.debugNoScore) to CPColor.C_STEEL_BLUE1()
                lines += spawnQueueStyleLine() to CPColor.C_STEEL_BLUE1()
                lines += onOffLine(
                    4,
                    "Loop enemy deck (endless floor; recycle discard)",
                    GameState.debugLoopEnemyDeck,
                ) to CPColor.C_STEEL_BLUE1()
                lines += "" to CPColor.C_GREY50()
                lines += "[1][2][4] toggle  [3] spawn queue style   B / ESC resume" to CPColor.C_GREY70()

                val cx = canv.width() / 2
                val cy = canv.height() / 2 - lines.size
                for ((idx, pair) in lines.withIndex()) {
                    val (txt, col) = pair
                    if (txt.isEmpty()) continue
                    canv.drawString(cx - txt.length / 2, cy + idx * 2, 1, txt, col, Option.empty())
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("debugmenu-input", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                when (evt.get().key()) {
                    KEY_1 -> GameState.debugInvincible = !GameState.debugInvincible
                    KEY_2 -> GameState.debugNoScore = !GameState.debugNoScore
                    KEY_3 -> GameState.cycleSpawnQueueHudStyle()
                    KEY_4 -> GameState.debugLoopEnemyDeck = !GameState.debugLoopEnemyDeck
                    KEY_B, KEY_ESC -> ctx.switchScene(SceneId.GAME, false)
                    else -> {}
                }
            }
        }

        return CPScene(
            SceneId.DEBUG_MENU.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite),
        )
    }

    private fun onOffLine(slot: Int, label: String, on: Boolean): String =
        "[$slot] $label  ${if (on) "[ON]" else "[off]"}"

    private fun spawnQueueStyleLine(): String {
        val name = when (GameState.spawnQueueHudStyle) {
            GameState.SpawnQueueHudStyle.LABELED -> "card: NEXT + [Enemy]/[Player]"
            GameState.SpawnQueueHudStyle.COMPACT -> "card: QUEUE + >E"
            GameState.SpawnQueueHudStyle.NUMBERED -> "card: ORDER + 1E…"
            GameState.SpawnQueueHudStyle.TIMELINE -> "card: E · P · …"
            GameState.SpawnQueueHudStyle.BOXED -> "card: rules + list"
        }
        return "[3] Spawn queue HUD: $name"
    }
}
