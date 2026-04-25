package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.SicBoDiceArt
import com.cardgame.game.GameState
import com.cardgame.game.MiniGameScores
import com.cardgame.minigames.MiniGameText
import com.cardgame.minigames.SicBoRules
import kotlin.random.Random
import org.cosplay.*
import scala.Option

/**
 * Sic bo — three dice. Sum bets (Big/Small/Odd/Even) match standard ranges and **lose on any triple**.
 * Single-die bets pay 1:1 / 2:1 / 3:1 profit for 1–3 matching dice.
 * From the menu: session starts at 500 gp. From a run: uses [GameState.money].
 */
object SicBoScene {

    private val BG_COLOR = CPColor(18, 22, 32, "sicbo-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_4 = kbKey("KEY_4")
    private val KEY_5 = kbKey("KEY_5")
    private val KEY_6 = kbKey("KEY_6")
    private val KEY_7 = kbKey("KEY_7")
    private val KEY_Q = kbKey("KEY_LO_Q")
    private val KEY_W = kbKey("KEY_LO_W")
    private val KEY_E = kbKey("KEY_LO_E")
    private val KEY_R = kbKey("KEY_LO_R")
    private val KEY_T = kbKey("KEY_LO_T")
    private val KEY_Y = kbKey("KEY_LO_Y")
    private val KEY_SPACE = kbKey("KEY_SPACE")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    private val BETS = intArrayOf(5, 50, 500)
    private const val START_GOLD = 500

    private const val ART_W = 30
    private const val ART_H = 16
    private const val CELL_W = ART_W + 2
    private const val CELL_H = ART_H + 2
    private val GRID_PIX_W = CELL_W * 3

    /** One vivid color per face 1..6 (pip art is shared; color tells them apart). */
    private fun diceColor(face: Int): CPColor = when (face) {
        1 -> CPColor(255, 130, 150, "sicbo-d1")   // coral
        2 -> CPColor(90, 220, 255, "sicbo-d2")   // aqua
        3 -> CPColor(200, 170, 255, "sicbo-d3")  // lavender
        4 -> CPColor(255, 215, 90, "sicbo-d4")  // gold
        5 -> CPColor(255, 130, 230, "sicbo-d5") // pink
        else -> CPColor(130, 255, 150, "sicbo-d6") // mint
    }

    private fun px(ch: Char, fg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(BG_COLOR), 0)

    private fun drawCardBorder(canv: CPCanvas, sx: Int, sy: Int, w: Int, h: Int, z: Int, color: CPColor) {
        canv.drawPixel(px('+', color), sx, sy, z)
        canv.drawPixel(px('+', color), sx + w - 1, sy, z)
        canv.drawPixel(px('+', color), sx, sy + h - 1, z)
        canv.drawPixel(px('+', color), sx + w - 1, sy + h - 1, z)
        for (x in sx + 1 until sx + w - 1) {
            canv.drawPixel(px('-', color), x, sy, z)
            canv.drawPixel(px('-', color), x, sy + h - 1, z)
        }
        for (yy in sy + 1 until sy + h - 1) {
            canv.drawPixel(px('|', color), sx, yy, z)
            canv.drawPixel(px('|', color), sx + w - 1, yy, z)
        }
    }

    private fun drawArtInCell(
        canv: CPCanvas,
        art: List<String>,
        cellX: Int,
        cellY: Int,
        z: Int,
        fg: CPColor,
    ) {
        val startX = cellX + 1
        val startY = cellY + 1
        for ((row, line) in art.withIndex()) {
            val dy = startY + row
            for ((col, ch) in line.withIndex()) {
                if (ch != ' ') {
                    canv.drawPixel(px(ch, fg), startX + col, dy, z)
                }
            }
        }
    }

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        val useRunGold = GameState.minigamesReturnScene == SceneId.GAME
        var gold = if (useRunGold) GameState.money else START_GOLD
        var sessionPeak = gold
        var betIndex = 0
        var wager: SicBoRules.Wager = SicBoRules.Wager.SumBig

        var rolling = false
        val face = IntArray(3) { 1 }
        val finalFace = IntArray(3) { 1 }
        val remainingTicks = IntArray(3) { 0 }
        val locked = BooleanArray(3) { true }

        var statusMsg = ""
        var statusWin = false

        fun notePeak() {
            if (gold > sessionPeak) sessionPeak = gold
        }

        fun beginRoll() {
            val bet = BETS[betIndex]
            if (rolling) return
            if (useRunGold) {
                if (!GameState.trySpendMoney(bet)) return
                gold = GameState.money
            } else {
                if (gold < bet) return
                gold -= bet
            }
            notePeak()
            rolling = true
            GameAudio.startDiceShakeLoop()
            statusMsg = ""
            statusWin = false
            for (i in 0 until 3) {
                locked[i] = false
                finalFace[i] = Random.nextInt(1, 7)
                face[i] = Random.nextInt(1, 7)
                remainingTicks[i] = 22 + i * 12 + Random.nextInt(10)
            }
        }

        fun finishRoll() {
            rolling = false
            GameAudio.stopDiceShakeLoop()
            val bet = BETS[betIndex]
            val d1 = finalFace[0]
            val d2 = finalFace[1]
            val d3 = finalFace[2]
            val sum = SicBoRules.diceSum(d1, d2, d3)
            val triple = SicBoRules.isTriple(d1, d2, d3)
            val pay = SicBoRules.payoutOnWin(bet, wager, d1, d2, d3)
            val lbl = SicBoRules.wagerShortLabel(wager)
            if (pay != null) {
                GameAudio.playCasinoWin()
                if (useRunGold) {
                    GameState.addMoney(pay)
                    gold = GameState.money
                } else {
                    gold += pay
                }
                notePeak()
                statusWin = true
                val profit = pay - bet
                statusMsg = "WIN +$profit gp  $lbl  sum=$sum  $d1-$d2-$d3"
            } else {
                GameAudio.playCasinoLose()
                statusWin = false
                val sumBet = wager is SicBoRules.Wager.SumBig ||
                    wager is SicBoRules.Wager.SumSmall ||
                    wager is SicBoRules.Wager.SumOdd ||
                    wager is SicBoRules.Wager.SumEven
                statusMsg = if (triple && sumBet) {
                    "Triple  $d1-$d2-$d3  sum=$sum  (sum bets lose)"
                } else {
                    "No win  $lbl  sum=$sum  $d1-$d2-$d3"
                }
            }
            if (!useRunGold) MiniGameScores.recordSicboPeakGold(sessionPeak)
        }

        val displaySprite = object : CPCanvasSprite("sicbo-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()
                val margin = 2
                val gridLeft = (w - GRID_PIX_W) / 2
                val gridTop = ((h - CELL_H) / 2).coerceAtLeast(0)

                val leftX = margin
                val leftUseW = (gridLeft - leftX - 1).coerceAtLeast(0)
                val rightX = gridLeft + GRID_PIX_W + 1
                val rightUseW = (w - margin - rightX).coerceAtLeast(0)
                val minSideW = 10

                val bet = BETS[betIndex]
                val cur = wager
                fun sel(ok: Boolean) = if (ok) ">" else ""
                val betsLine = buildString {
                    append(sel(cur == SicBoRules.Wager.SumBig))
                    append("1:Big ")
                    append(sel(cur == SicBoRules.Wager.SumSmall))
                    append("2:Sm ")
                    append(sel(cur == SicBoRules.Wager.SumOdd))
                    append("6:Odd ")
                    append(sel(cur == SicBoRules.Wager.SumEven))
                    append("7:Ev ")
                    append(sel(cur is SicBoRules.Wager.SingleDie && cur.face == 1))
                    append("Q:1 ")
                    append(sel(cur is SicBoRules.Wager.SingleDie && cur.face == 2))
                    append("W:2 ")
                    append(sel(cur is SicBoRules.Wager.SingleDie && cur.face == 3))
                    append("E:3 ")
                    append(sel(cur is SicBoRules.Wager.SingleDie && cur.face == 4))
                    append("R:4 ")
                    append(sel(cur is SicBoRules.Wager.SingleDie && cur.face == 5))
                    append("T:5 ")
                    append(sel(cur is SicBoRules.Wager.SingleDie && cur.face == 6))
                    append("Y:6")
                }
                val stakeLine = "3/4/5 stake $bet"

                val hint = when {
                    rolling -> "Rolling…"
                    gold <= 0 -> "Busted! B=exit"
                    else -> "SPACE roll  B menu"
                }

                val leftBlocks = listOf(
                    "CODE 437" to CPColor.C_GOLD1(),
                    "SIC BO" to CPColor.C_GOLD1(),
                    "" to CPColor.C_GREY50(),
                    (if (useRunGold) "Using your run gold." else "Start 500 gp.") to CPColor.C_GREY70(),
                    "GOLD $gold" to CPColor.C_CYAN1(),
                    "PEAK $sessionPeak" to CPColor.C_CYAN1(),
                    "HIGH ${MiniGameScores.sicboPeakGold}" to CPColor.C_CYAN1(),
                    "" to CPColor.C_GREY50(),
                    stakeLine to CPColor.C_CYAN1(),
                    betsLine to CPColor.C_GREEN1(),
                    "" to CPColor.C_GREY50(),
                    hint to CPColor.C_GREY70(),
                )

                val rules = buildList {
                    add("RULES" to CPColor.C_GOLD1())
                    add("" to CPColor.C_GREY50())
                    add("Wagers can be" to CPColor.C_GREY70())
                    add("on 1, 2, or 3" to CPColor.C_GREY70())
                    add("dice (full" to CPColor.C_GREY70())
                    add("tables include" to CPColor.C_GREY70())
                    add("combos not here)." to CPColor.C_GREY70())
                    add("" to CPColor.C_GREY50())
                    add("1-die: pay by" to CPColor.C_STEEL_BLUE1())
                    add("how many show" to CPColor.C_STEEL_BLUE1())
                    add("your # (Q–Y)." to CPColor.C_STEEL_BLUE1())
                    add("" to CPColor.C_GREY50())
                    add("Sum: Big 11–17" to CPColor.C_GREY70())
                    add("Small 4–10." to CPColor.C_GREY70())
                    add("Odd / Even sum." to CPColor.C_GREY70())
                    add("All sum bets" to CPColor.C_ORANGE_RED1())
                    add("lose on triple." to CPColor.C_ORANGE_RED1())
                    add("" to CPColor.C_GREY50())
                    add("Stake $bet gp" to CPColor.C_CYAN1())
                }

                fun drawColumn(
                    blocks: List<Pair<String, CPColor>>,
                    panelLeft: Int,
                    panelWidth: Int,
                    z: Int,
                ) {
                    if (panelWidth <= 0) return
                    val lines = MiniGameText.columnLines(blocks, panelWidth)
                    var startY = ((h - lines.size) / 2).coerceAtLeast(0)
                    for ((txt, col) in lines) {
                        if (txt.isEmpty()) {
                            startY += 1
                            continue
                        }
                        val row = txt.take(panelWidth)
                        val cx = panelLeft + (panelWidth - row.length).coerceAtLeast(0) / 2
                        canv.drawString(cx, startY, z, row, col, Option.empty())
                        startY += 1
                    }
                }

                if (leftUseW >= minSideW) {
                    drawColumn(leftBlocks, leftX, leftUseW, 2)
                } else {
                    canv.drawString((w - "CODE 437 SIC BO".length) / 2, 0, 2, "CODE 437 SIC BO", CPColor.C_GOLD1(), Option.empty())
                    canv.drawString(
                        margin, 1, 2,
                        "G:$gold P:$sessionPeak  $hint".take(w - margin * 2),
                        CPColor.C_CYAN1(), Option.empty()
                    )
                }
                if (rightUseW >= minSideW) {
                    drawColumn(rules, rightX, rightUseW, 2)
                }

                val borderColor = CPColor(55, 55, 80, "sicbo-cell-border")
                for (i in 0 until 3) {
                    val cx = gridLeft + i * CELL_W
                    val cy = gridTop
                    val ox = if (rolling && !locked[i]) Random.nextInt(-1, 2) else 0
                    drawCardBorder(canv, cx + ox, cy, CELL_W, CELL_H, 3, borderColor)
                    val art = SicBoDiceArt.linesFor(face[i])
                    drawArtInCell(canv, art, cx + ox, cy, 2, diceColor(face[i]))
                }

                if (statusMsg.isNotEmpty()) {
                    val statusColor = if (statusWin) CPColor.C_GREEN1() else CPColor.C_GREY70()
                    if (leftUseW >= minSideW) {
                        val hudLines = MiniGameText.columnLines(leftBlocks, leftUseW)
                        val hudStartY = ((h - hudLines.size) / 2).coerceAtLeast(0)
                        val belowHudY = hudStartY + hudLines.size + 1
                        val wrapped = MiniGameText.wrapText(statusMsg, leftUseW)
                        for (i in wrapped.indices) {
                            val row = wrapped[i]
                            val cx = leftX + (leftUseW - row.length).coerceAtLeast(0) / 2
                            val y = belowHudY + i
                            if (y < h) {
                                canv.drawString(cx, y, 6, row, statusColor, Option.empty())
                            }
                        }
                    } else {
                        val panelW = (gridLeft - margin - 1).coerceAtLeast(8)
                        val wrapped = MiniGameText.wrapText(statusMsg, panelW)
                        val baseY = 3
                        for (i in wrapped.indices) {
                            val row = wrapped[i]
                            val cx = margin + (panelW - row.length).coerceAtLeast(0) / 2
                            val y = baseY + i
                            if (y < h) {
                                canv.drawString(cx, y, 6, row, statusColor, Option.empty())
                            }
                        }
                    }
                }
            }
        }

        val logicSprite = object : CPCanvasSprite("sicbo-logic", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (!ctx.isVisible()) {
                    GameAudio.stopDiceShakeLoop()
                    return
                }
                if (!rolling) return
                var allLocked = true
                for (i in 0 until 3) {
                    if (locked[i]) continue
                    allLocked = false
                    remainingTicks[i]--
                    if (remainingTicks[i] <= 0) {
                        face[i] = finalFace[i]
                        locked[i] = true
                    } else {
                        val slow = remainingTicks[i] <= 10
                        if (!slow || remainingTicks[i] % 2 == 0) {
                            face[i] = Random.nextInt(1, 7)
                        }
                    }
                }
                if (allLocked && rolling) {
                    finishRoll()
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("sicbo-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (rolling) return
                if (!ctx.isVisible()) return
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                when (key) {
                    KEY_1 -> wager = SicBoRules.Wager.SumBig
                    KEY_2 -> wager = SicBoRules.Wager.SumSmall
                    KEY_6 -> wager = SicBoRules.Wager.SumOdd
                    KEY_7 -> wager = SicBoRules.Wager.SumEven
                    KEY_Q -> wager = SicBoRules.Wager.SingleDie(1)
                    KEY_W -> wager = SicBoRules.Wager.SingleDie(2)
                    KEY_E -> wager = SicBoRules.Wager.SingleDie(3)
                    KEY_R -> wager = SicBoRules.Wager.SingleDie(4)
                    KEY_T -> wager = SicBoRules.Wager.SingleDie(5)
                    KEY_Y -> wager = SicBoRules.Wager.SingleDie(6)
                    KEY_3 -> betIndex = 0
                    KEY_4 -> betIndex = 1
                    KEY_5 -> betIndex = 2
                    KEY_SPACE -> if (gold > 0) beginRoll()
                    KEY_B, KEY_ESC -> {
                        GameAudio.stopDiceShakeLoop()
                        if (!useRunGold) MiniGameScores.recordSicboPeakGold(sessionPeak)
                        ctx.switchScene(SceneId.MINIGAMES, false)
                        kotlin.runCatching { ctx.deleteScene(SceneId.SICBO) }
                            .onFailure {
                                SentryBootstrap.captureCaughtError(
                                    message = "Delete Sic Bo scene failed",
                                    throwable = it,
                                )
                            }
                    }
                    else -> {}
                }
            }
        }

        return CPScene(
            SceneId.SICBO.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, logicSprite, inputSprite)
        )
    }
}
