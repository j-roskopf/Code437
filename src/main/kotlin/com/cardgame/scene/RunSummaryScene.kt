package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.CardArt
import com.cardgame.effects.ConfettiEffect
import com.cardgame.effects.ExplosionEffect
import com.cardgame.game.*
import kotlin.math.sin
import kotlin.random.Random
import org.cosplay.*
import scala.Option

/**
 * Full-run recap after death or after clearing the final level: stats, gold, enemy art grid,
 * and layered confetti / explosion canvas effects.
 */
object RunSummaryScene {
    private val BG = CPColor(12, 8, 28, "rs-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG), 0)

    private val KEY_N = kbKey("KEY_LO_N")
    private val KEY_Q = kbKey("KEY_LO_Q")

    private fun px(ch: Char, fg: CPColor) = CPPixel(ch, fg, Option.empty(), 0)

    private fun lerpColor(a: CPColor, b: CPColor, t: Float): CPColor {
        val tt = t.coerceIn(0f, 1f)
        return CPColor(
            (a.red() + (b.red() - a.red()) * tt).toInt().coerceIn(0, 255),
            (a.green() + (b.green() - a.green()) * tt).toInt().coerceIn(0, 255),
            (a.blue() + (b.blue() - a.blue()) * tt).toInt().coerceIn(0, 255),
            "lerp"
        )
    }

    private fun drawFanfareBorder(canv: CPCanvas, frame: Int) {
        val w = canv.width()
        val h = canv.height()
        val t = ((sin(frame * 0.06) + 1.0) * 0.5).toFloat()
        val c = lerpColor(CPColor(90, 50, 140, "rs-br0"), CPColor.C_GOLD1(), t)
        val ch = if ((frame / 4) % 2 == 0) '░' else '▒'
        for (x in 0 until w) {
            canv.drawPixel(px(ch, c), x, 0, 5)
            canv.drawPixel(px(ch, c), x, h - 1, 5)
        }
        for (y in 0 until h) {
            canv.drawPixel(px(ch, c), 0, y, 5)
            canv.drawPixel(px(ch, c), w - 1, y, 5)
        }
    }

    private fun shimmerFg(base: CPColor, frame: Int, kind: EnemyKind): CPColor {
        val phase = sin(frame * 0.07 + kind.ordinal * 0.9).toFloat()
        val t = (phase + 1f) * 0.5f
        return lerpColor(base, CPColor.C_WHITE(), t * 0.35f)
    }

    private fun drawEnemyArt(
        canv: CPCanvas,
        art: List<String>,
        startX: Int,
        startY: Int,
        maxW: Int,
        maxH: Int,
        frame: Int,
        fg: CPColor
    ) {
        val lines = art.take(maxH)
        for ((row, line) in lines.withIndex()) {
            val clipped = line.take(maxW)
            for ((col, ch) in clipped.withIndex()) {
                if (ch != ' ') {
                    val pulse = (sin(frame * 0.12 + col * 0.2 + row * 0.15).toFloat() + 1f) * 0.5f
                    val c = lerpColor(fg, CPColor(255, 250, 200, "sh"), pulse * 0.25f)
                    canv.drawPixel(px(ch, c), startX + col, startY + row, 3)
                }
            }
        }
    }

    fun create(): CPScene {
        var frame = 0
        val confetti = ConfettiEffect(maxParticles = 48, maxAge = 22)
        val explosions = ExplosionEffect(maxAge = 26)

        val fxSprite = object : CPCanvasSprite("runsummary-fx", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()
                frame++
                when (frame) {
                    1 -> {
                        confetti.spawnScreen(w / 2, 8)
                        explosions.spawnScreenBurst(w / 2f, 10f, 100)
                    }
                    12 -> {
                        confetti.spawnScreen(w / 5, h / 4)
                        explosions.spawnScreenBurst(w / 5f, h / 4f, 45)
                    }
                    24 -> {
                        confetti.spawnScreen(4 * w / 5, h / 4)
                        explosions.spawnScreenBurst(4 * w / 5f, h / 4f, 45)
                    }
                }
                if (frame > 30 && frame % 40 == 0) {
                    confetti.spawnScreen(Random.nextInt(6, (w - 6).coerceAtLeast(7)), Random.nextInt(4, h / 2))
                }
                if (frame > 20 && frame % 110 == 0) {
                    explosions.spawnScreenBurst(
                        Random.nextInt(15, (w - 15).coerceAtLeast(16)).toFloat(),
                        Random.nextInt(10, h * 2 / 3).toFloat(),
                        60
                    )
                }
                confetti.update()
                explosions.update()
            }

            override fun render(ctx: CPSceneObjectContext) {
                confetti.draw(ctx.canvas)
                explosions.draw(ctx.canvas)
            }
        }

        val displaySprite = object : CPCanvasSprite("runsummary-display", emptyScalaSeq(), emptyStringSet()) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val f = frame
                drawFanfareBorder(canv, f)

                val cx = canv.width() / 2
                var y = 2
                val victory = GameState.runEndKind == RunEndKind.VICTORY
                val title = if (victory) "★ RUN COMPLETE ★" else "— RUN SYNOPSIS —"
                val titleColor = if (victory) CPColor.C_GREEN1() else CPColor.C_GOLD1()
                val tw = title.length / 2
                canv.drawString(cx - tw, y, 1, title, shimmerFg(titleColor, f, EnemyKind.SLIME), Option.empty())
                y += 2
                val sub = if (victory) "You cleared every level — legendary!" else "Your crawl, by the numbers"
                canv.drawString(cx - sub.length / 2, y, 1, sub, CPColor.C_GREY70(), Option.empty())
                y += 2

                val earned = RunStats.goldEarned
                val spent = RunStats.goldSpent
                val pocket = GameState.money
                val runScore = if (victory) RunStats.bankedScore else RunStats.runTotalScore(GameState.score)
                val stats = listOf(
                    "Gold earned: $earned gp    Spent: $spent gp    In pocket: $pocket gp" to CPColor.C_GOLD1(),
                    "Secret rooms visited: ${RunStats.secretRoomsVisited}" to CPColor.C_STEEL_BLUE1(),
                    "Levels cleared (score goals met): ${RunStats.levelsCleared}" to CPColor.C_STEEL_BLUE1(),
                    "Run score: $runScore" to CPColor.C_CYAN1(),
                    "Total foes defeated: ${RunStats.totalEnemyKills()}" to CPColor.C_ORANGE1(),
                    "Keys on hand — Bronze: ${GameState.keysBronze}  Silver: ${GameState.keysSilver}  Gold: ${GameState.keysGold}" to CPColor.C_GREY70(),
                )
                for ((line, col) in stats) {
                    canv.drawString(cx - line.length / 2, y, 1, line, col, Option.empty())
                    y += 1
                }
                y += 1

                val kinds = RunStats.kindsWithKills()
                if (kinds.isEmpty()) {
                    canv.drawString(cx - 14, y, 1, "(No combat kills this run)", CPColor.C_GREY50(), Option.empty())
                    y += 2
                } else {
                    canv.drawString(cx - 10, y, 1, "— Slain —", CPColor.C_RED1(), Option.empty())
                    y += 2
                    // Draw all slain enemy arts together. Prefer full 30x16 art, then scale down only if screen-space demands it.
                    val artMaxW = 30
                    val artMaxH = 16
                    val tileW = artMaxW + 4
                    val maxColsByWidth = ((canv.width() - 4) / tileW).coerceAtLeast(1).coerceAtMost(4)
                    val availH = (canv.height() - y - 5).coerceAtLeast(8)

                    var bestCols = 1
                    var bestArtH = -1
                    for (candidateCols in 1..maxColsByWidth) {
                        val rows = (kinds.size + candidateCols - 1) / candidateCols
                        val perTileH = (availH / rows).coerceAtLeast(4)
                        val candidateArtH = (perTileH - 2).coerceAtLeast(3).coerceAtMost(artMaxH)
                        if (candidateArtH > bestArtH) {
                            bestArtH = candidateArtH
                            bestCols = candidateCols
                        }
                    }

                    val cols = bestCols
                    val rows = (kinds.size + cols - 1) / cols
                    val perTileH = (availH / rows).coerceAtLeast(4)
                    val drawArtH = (perTileH - 2).coerceAtLeast(3).coerceAtMost(artMaxH)
                    val contentW = cols * tileW
                    val baseX = ((canv.width() - contentW) / 2).coerceAtLeast(2)

                    for ((idx, kind) in kinds.withIndex()) {
                        val col = idx % cols
                        val row = idx / cols
                        val sx = baseX + col * tileW
                        val sy = y + row * perTileH

                        if (sy + perTileH >= canv.height() - 4) break

                        val n = RunStats.totalKills(kind)
                        val e = RunStats.eliteCount(kind)
                        val label = if (e > 0) "${kind.displayName} x$n  *$e" else "${kind.displayName} x$n"
                        val lx = sx + (tileW - label.length).coerceAtLeast(0) / 2
                        canv.drawString(lx, sy, 1, label, shimmerFg(CPColor.C_GREEN1(), f, kind), Option.empty())

                        val art = CardArt.enemySprite(kind)
                        val aw = (art.maxOfOrNull { it.length } ?: 0).coerceAtMost(artMaxW)
                        val ax = sx + ((tileW - aw) / 2).coerceAtLeast(0)
                        drawEnemyArt(canv, art, ax, sy + 1, artMaxW, drawArtH, f, CPColor.C_ORANGE_RED1())
                    }
                    y += rows * perTileH
                }

                y = (y + 1).coerceAtMost(canv.height() - 6)
                val hint = if (victory) "N  Main menu    Q  Quit" else "N  Continue to game over    Q  Quit"
                canv.drawString(cx - hint.length / 2, y.coerceAtMost(canv.height() - 4), 1, hint, CPColor.C_GREY50(), Option.empty())
            }
        }

        val inputSprite = object : CPCanvasSprite("runsummary-input", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                when (evt.get().key()) {
                    KEY_N -> {
                        when (GameState.runEndKind) {
                            RunEndKind.DEATH -> ctx.switchScene("gameover", false)
                            RunEndKind.VICTORY -> ctx.switchScene("menu", false)
                        }
                    }
                    KEY_Q -> ctx.exitGame()
                    else -> {}
                }
            }
        }

        return CPScene(
            "runsummary",
            Option.empty(),
            bgPx,
            scalaSeqOf(fxSprite, displaySprite, inputSprite)
        )
    }
}
