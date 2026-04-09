package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.SlotMachineArt
import com.cardgame.game.GameState
import com.cardgame.game.MiniGameScores
import com.cardgame.minigames.MiniGameText
import com.cardgame.minigames.SlotMachineRules
import kotlin.math.abs
import kotlin.random.Random
import org.cosplay.*
import scala.Option

/**
 * From the menu: session starts at 500 gp; peak is saved as high score.
 * From a run (gambling tile): bets and wins use [GameState.money].
 * Art is fixed 30×16 inside 32×18 cells. Left/right columns show HUD and rules when the canvas is wide enough.
 */
object SlotMachineScene {

    private val BG_COLOR = CPColor(18, 18, 32, "slots-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_SPACE = kbKey("KEY_SPACE")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    private val BETS = intArrayOf(5, 50, 500)
    private const val START_GOLD = 500
    private const val STRIP_LEN = 56

    /** Art is 30 chars wide × 16 lines tall. Cell = art + border on each side. */
    private const val ART_W = 30
    private const val ART_H = 16
    private const val CELL_W = ART_W + 2   // 32
    private const val CELL_H = ART_H + 2   // 18

    private val GRID_PIX_W = CELL_W * 3

    /** Cell interior center in canvas coordinates (for payline overlays). */
    private fun slotCellCenter(gridLeft: Int, gridTop: Int, col: Int, row: Int): Pair<Int, Int> {
        val cx = gridLeft + col * CELL_W + CELL_W / 2
        val cy = gridTop + row * CELL_H + CELL_H / 2
        return cx to cy
    }

    private fun drawLineChars(
        canv: CPCanvas,
        x0: Int, y0: Int, x1: Int, y1: Int,
        z: Int,
        color: CPColor,
        ch: Char,
    ) {
        if (y0 == y1) {
            val xa = minOf(x0, x1)
            val xb = maxOf(x0, x1)
            for (x in xa..xb) canv.drawPixel(px(ch, color), x, y0, z)
            return
        }
        if (x0 == x1) {
            val ya = minOf(y0, y1)
            val yb = maxOf(y0, y1)
            for (y in ya..yb) canv.drawPixel(px(ch, color), x0, y, z)
            return
        }
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        while (true) {
            canv.drawPixel(px(ch, color), x, y, z)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
    }

    /** Orange overlay on winning paylines only; drawn above reel art ([z] above symbols). */
    private fun drawWinningPaylineOverlays(
        canv: CPCanvas,
        gridLeft: Int,
        gridTop: Int,
        kinds: Set<SlotMachineRules.PaylineKind>,
        maxCanvasW: Int,
        z: Int,
    ) {
        if (kinds.isEmpty()) return
        val color = CPColor.C_ORANGE_RED1()
        val lineW = GRID_PIX_W.coerceAtMost(maxCanvasW - gridLeft).coerceAtLeast(0)
        if (SlotMachineRules.PaylineKind.MIDDLE in kinds && lineW > 0) {
            val payY = gridTop + CELL_H + CELL_H / 2
            canv.drawString(
                gridLeft, payY, z,
                "═".repeat(lineW),
                color, Option.empty()
            )
        }
        val chDown = '╲'
        val chUp = '╱'
        if (SlotMachineRules.PaylineKind.DIAG_TL_BR in kinds) {
            val (x0, y0) = slotCellCenter(gridLeft, gridTop, 0, 0)
            val (x1, y1) = slotCellCenter(gridLeft, gridTop, 2, 2)
            drawLineChars(canv, x0, y0, x1, y1, z, color, chDown)
        }
        if (SlotMachineRules.PaylineKind.DIAG_BL_TR in kinds) {
            val (x0, y0) = slotCellCenter(gridLeft, gridTop, 0, 2)
            val (x1, y1) = slotCellCenter(gridLeft, gridTop, 2, 0)
            drawLineChars(canv, x0, y0, x1, y1, z, color, chUp)
        }
    }

    /** Full art lines per symbol — drawn verbatim, not trimmed, so spacing matches what the user designed. */
    private val fullArt: Map<SlotMachineArt.Symbol, List<String>> =
        SlotMachineArt.Symbol.entries.associateWith { it.lines }

    private fun symColor(s: SlotMachineArt.Symbol): CPColor = when (s) {
        SlotMachineArt.Symbol.DICE -> CPColor.C_GOLD1()
        SlotMachineArt.Symbol.ROCKET -> CPColor.C_ORANGE_RED1()
        SlotMachineArt.Symbol.FOSSIL -> CPColor.C_STEEL_BLUE1()
        SlotMachineArt.Symbol.PEAR -> CPColor.C_GREEN1()
        SlotMachineArt.Symbol.APPLE -> CPColor.C_RED1()
        SlotMachineArt.Symbol.CARROT -> CPColor(255, 140, 60, "carrot")
        SlotMachineArt.Symbol.TOILET -> CPColor.C_GREY70()
        SlotMachineArt.Symbol.QUESTION -> CPColor.C_PURPLE1()
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

    /** Draw full art lines inside the cell (art fills the interior exactly). */
    private fun drawArtInCell(
        canv: CPCanvas,
        art: List<String>,
        cellX: Int, cellY: Int,
        z: Int, fg: CPColor
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

        val useRunGold = GameState.minigamesReturnScene == "game"
        var gold = if (useRunGold) GameState.money else START_GOLD
        var sessionPeak = gold
        var betIndex = 0
        var spinning = false
        var statusMsg = ""
        /** Cleared on next spin; shown until then. */
        var statusIsWin = false
        /** Orange payline overlay; only non-empty after a winning spin until the next spin. */
        var winningPaylines: Set<SlotMachineRules.PaylineKind> = emptySet()

        val strips = Array(3) { List(STRIP_LEN) { SlotMachineArt.Symbol.entries.random() }.toMutableList() }
        val topIndex = IntArray(3) { Random.nextInt(STRIP_LEN) }
        val smooth = IntArray(3) { 0 }
        val speed = IntArray(3) { 5 }
        val remainingSteps = IntArray(3) { 0 }
        val locked = BooleanArray(3) { true }
        val landingTop = IntArray(3) { 0 }

        fun notePeak() {
            if (gold > sessionPeak) sessionPeak = gold
        }

        fun randomOutcome(): Triple<SlotMachineArt.Symbol, SlotMachineArt.Symbol, SlotMachineArt.Symbol> {
            return Triple(
                SlotMachineArt.Symbol.entries.random(),
                SlotMachineArt.Symbol.entries.random(),
                SlotMachineArt.Symbol.entries.random()
            )
        }

        fun beginSpin() {
            val bet = BETS[betIndex]
            if (spinning) return
            if (useRunGold) {
                if (!GameState.trySpendMoney(bet)) return
                gold = GameState.money
            } else {
                if (gold < bet) return
                gold -= bet
            }
            notePeak()
            spinning = true
            statusMsg = ""
            statusIsWin = false
            winningPaylines = emptySet()

            val (t0, t1, t2) = randomOutcome()
            val targets = arrayOf(t0, t1, t2)

            for (r in 0 until 3) {
                val payPos = Random.nextInt(12, STRIP_LEN - 4)
                strips[r][payPos % STRIP_LEN] = targets[r]
                landingTop[r] = (payPos - 1 + STRIP_LEN) % STRIP_LEN
                val cur = topIndex[r]
                val land = landingTop[r]
                var dist = (land - cur + STRIP_LEN) % STRIP_LEN
                if (dist < 8) dist += STRIP_LEN
                // Short spin: ~half strip + stagger (still lands on planted symbols).
                val extra = STRIP_LEN / 2 + Random.nextInt(10) + r * 6
                remainingSteps[r] = dist + extra + (2 - r) * 3
                locked[r] = false
                speed[r] = 18 + r * 3
            }
        }

        fun finishSpin() {
            spinning = false
            fun symAt(col: Int, row: Int): SlotMachineArt.Symbol =
                strips[col][(topIndex[col] + row + STRIP_LEN) % STRIP_LEN]

            val bet = BETS[betIndex]
            val (win, paylineHits) = SlotMachineRules.collectPaylineHits(bet, ::symAt)
            if (win > 0) {
                if (useRunGold) {
                    GameState.addMoney(win)
                    gold = GameState.money
                } else {
                    gold += win
                }
                winningPaylines = paylineHits.map { it.kind }.toSet()
                val detail = paylineHits.joinToString("  ") { h ->
                    "${h.lineTitle}: ${h.symbolsPipe()} (+${h.payout} gp)"
                }
                statusMsg = "WIN +$win gp  $detail"
                statusIsWin = true
            } else {
                winningPaylines = emptySet()
                statusMsg = "No win"
                statusIsWin = false
            }
            notePeak()
            if (!useRunGold) MiniGameScores.recordSlotsPeakGold(sessionPeak)
        }

        val displaySprite = object : CPCanvasSprite("slots-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()

                val margin = 2
                val gridLeft = (w - GRID_PIX_W) / 2
                val gridTop = ((h - CELL_H * 3) / 2).coerceAtLeast(0)

                val leftX = margin
                val leftUseW = (gridLeft - leftX - 1).coerceAtLeast(0)
                val rightX = gridLeft + GRID_PIX_W + 1
                val rightUseW = (w - margin - rightX).coerceAtLeast(0)
                val minSideW = 10

                val betsLine = buildString {
                    append("BET ")
                    BETS.forEachIndexed { i, b ->
                        if (i > 0) append(' ')
                        val mark = if (i == betIndex) ">" else ""
                        append("$mark${i + 1}:$b")
                        if (i == betIndex) append("<")
                    }
                }
                val hint = when {
                    spinning -> "Spinning…"
                    gold <= 0 -> "Busted! B=exit"
                    else -> "1/2/3 bet  SPACE spin  B menu"
                }

                val leftBlocks = listOf(
                    "CODE 437" to CPColor.C_GOLD1(),
                    "SLOTS" to CPColor.C_GOLD1(),
                    "" to CPColor.C_GREY50(),
                    "GOLD $gold" to CPColor.C_CYAN1(),
                    "PEAK $sessionPeak" to CPColor.C_CYAN1(),
                    "HIGH ${MiniGameScores.slotsPeakGold}" to CPColor.C_CYAN1(),
                    "" to CPColor.C_GREY50(),
                    betsLine to CPColor.C_GREEN1(),
                    "" to CPColor.C_GREY50(),
                    hint to CPColor.C_GREY70(),
                )

                val bet = BETS[betIndex]
                val payRows = buildList {
                    add("RULES" to CPColor.C_GOLD1())
                    add("" to CPColor.C_GREY50())
                    add(
                        (if (useRunGold) "Using your run gold." else "Start 500 gp.") to CPColor.C_GREY70()
                    )
                    add("Orange line on" to CPColor.C_ORANGE_RED1())
                    add("winning paylines." to CPColor.C_ORANGE_RED1())
                    add("" to CPColor.C_GREY50())
                    add("PAYLINES" to CPColor.C_STEEL_BLUE1())
                    add("Mid row" to CPColor.C_GREY70())
                    add("Top-left to bottom-right" to CPColor.C_GREY70())
                    add("Bottom-left to top-right" to CPColor.C_GREY70())
                    add("Stack wins." to CPColor.C_GREY70())
                    add("" to CPColor.C_GREY50())
                    add("PAYOUTS" to CPColor.C_STEEL_BLUE1())
                    add("3 match:" to CPColor.C_GREEN1())
                    add(" bet x mult" to CPColor.C_GREEN1())
                    add("2 match:" to CPColor.C_GREEN1())
                    add(" 2x bet/line" to CPColor.C_GREEN1())
                    add("" to CPColor.C_GREY50())
                    add("Bet $bet gp:" to CPColor.C_CYAN1())
                }
                val symOrder = listOf(
                    SlotMachineArt.Symbol.DICE,
                    SlotMachineArt.Symbol.ROCKET,
                    SlotMachineArt.Symbol.FOSSIL,
                    SlotMachineArt.Symbol.PEAR,
                    SlotMachineArt.Symbol.APPLE,
                    SlotMachineArt.Symbol.CARROT,
                    SlotMachineArt.Symbol.TOILET,
                    SlotMachineArt.Symbol.QUESTION,
                )
                val payLines = payRows + symOrder.map { s ->
                    val mult = SlotMachineRules.tripleMult(s)
                    val win = bet * mult
                    "${s.label.take(6)} x$mult=$win" to symColor(s)
                }

                /** Vertically centered block; each line centered horizontally in [panelLeft]..[panelLeft]+[panelWidth]. */
                fun drawColumn(
                    blocks: List<Pair<String, CPColor>>,
                    panelLeft: Int,
                    panelWidth: Int,
                    z: Int
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
                    canv.drawString((w - "CODE 437 SLOTS".length) / 2, 0, 2, "CODE 437 SLOTS", CPColor.C_GOLD1(), Option.empty())
                    canv.drawString(
                        margin, 1, 2,
                        "G:$gold P:$sessionPeak  $hint".take(w - margin * 2),
                        CPColor.C_CYAN1(), Option.empty()
                    )
                }
                if (rightUseW >= minSideW) {
                    drawColumn(payLines, rightX, rightUseW, 2)
                }

                val borderColor = CPColor(55, 55, 80, "slot-cell-border")
                for (col in 0 until 3) {
                    for (row in 0 until 3) {
                        val cx = gridLeft + col * CELL_W
                        val cy = gridTop + row * CELL_H
                        drawCardBorder(canv, cx, cy, CELL_W, CELL_H, 3, borderColor)

                        val sym = strips[col][(topIndex[col] + row + STRIP_LEN) % STRIP_LEN]
                        val art = fullArt[sym] ?: emptyList()
                        drawArtInCell(canv, art, cx, cy, 2, symColor(sym))
                    }
                }

                drawWinningPaylineOverlays(canv, gridLeft, gridTop, winningPaylines, w, z = 5)

                if (statusMsg.isNotEmpty()) {
                    val statusColor = if (statusIsWin) CPColor.C_GREEN1() else CPColor.C_GREY70()
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

        val logicSprite = object : CPCanvasSprite("slots-logic", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (!spinning) return

                var allLocked = true
                for (r in 0 until 3) {
                    if (locked[r]) continue
                    allLocked = false
                    smooth[r] += speed[r]
                    if (smooth[r] >= CELL_H) {
                        smooth[r] = 0
                        topIndex[r] = (topIndex[r] + 1) % STRIP_LEN
                        remainingSteps[r]--
                        if (remainingSteps[r] <= 0) {
                            topIndex[r] = landingTop[r]
                            smooth[r] = 0
                            locked[r] = true
                            speed[r] = 0
                        } else {
                            val k = remainingSteps[r]
                            speed[r] = when {
                                k > 12 -> 20
                                k > 6 -> 12
                                k > 2 -> 6
                                else -> 2
                            }
                        }
                    }
                }
                if (allLocked && spinning) {
                    finishSpin()
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("slots-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (spinning) return
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                when (key) {
                    KEY_1 -> betIndex = 0
                    KEY_2 -> betIndex = 1
                    KEY_3 -> betIndex = 2
                    KEY_SPACE -> if (gold > 0) beginSpin()
                    KEY_B, KEY_ESC -> {
                        if (!useRunGold) MiniGameScores.recordSlotsPeakGold(sessionPeak)
                        ctx.switchScene("minigames", false)
                        kotlin.runCatching { ctx.deleteScene("slots") }
                    }
                    else -> {}
                }
            }
        }

        return CPScene(
            "slots",
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, logicSprite, inputSprite)
        )
    }
}
