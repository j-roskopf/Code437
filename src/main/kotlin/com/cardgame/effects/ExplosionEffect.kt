package com.cardgame.effects

import com.cardgame.game.GridConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.cosplay.CPCanvas
import org.cosplay.CPColor
import org.cosplay.CPPixel
import scala.Option

/**
 * Multi-burst fire explosion (center + orthogonal neighbors). Uses layered particles with
 * heat-style colors and radial motion — reads like a shader flash on the emuterm canvas.
 */
class ExplosionEffect(
    private val maxAge: Int = 22
) {
    private data class Particle(
        var x: Float,
        var y: Float,
        var dx: Float,
        var dy: Float,
        val ch: Char,
        val base: CPColor,
        var age: Int = 0,
        val life: Int,
        var drag: Float = 0.92f
    )

    private val particles = mutableListOf<Particle>()

    private val fireChars = charArrayOf('*', '#', '%', '@', '░', '▒', '▓', '·', '+', '~')
    private val heatColors = listOf(
        CPColor(255, 255, 220, "heat0"),
        CPColor(255, 200, 60, "heat1"),
        CPColor(255, 120, 20, "heat2"),
        CPColor(255, 60, 10, "heat3"),
        CPColor(200, 30, 80, "heat4"),
        CPColor(120, 40, 180, "heat5"),
    )

    val isActive: Boolean get() = particles.isNotEmpty()

    /** Main detonation at [gridX],[gridY] plus smaller bursts on the four adjacent cells. */
    fun spawnCrossExplosion(centerGridX: Int, centerGridY: Int) {
        spawnBurst(centerGridX, centerGridY, 85)
        val dirs = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
        for ((dx, dy) in dirs) {
            val nx = centerGridX + dx
            val ny = centerGridY + dy
            if (nx in 0 until GridConfig.COLS && ny in 0 until GridConfig.ROWS) {
                spawnBurst(nx, ny, 42)
            }
        }
    }

    /** Radial burst at canvas character coordinates (for title screens, etc.). */
    fun spawnScreenBurst(centerX: Float, centerY: Float, count: Int) {
        repeat(count) {
            val ang = Random.nextFloat() * (PI * 2).toFloat()
            val spd = Random.nextFloat() * 2.8f + 0.6f
            val dx = cos(ang) * spd
            val dy = sin(ang) * spd - Random.nextFloat() * 0.5f
            val c = heatColors[Random.nextInt(heatColors.size)]
            particles.add(
                Particle(
                    x = centerX + Random.nextFloat() * 3f - 1.5f,
                    y = centerY + Random.nextFloat() * 3f - 1.5f,
                    dx = dx,
                    dy = dy,
                    ch = fireChars[Random.nextInt(fireChars.size)],
                    base = c,
                    life = maxAge + Random.nextInt(10),
                    drag = 0.88f + Random.nextFloat() * 0.08f
                )
            )
        }
    }

    private fun spawnBurst(gridX: Int, gridY: Int, count: Int) {
        val gc = GridConfig
        val cx = (gc.cellScreenX(gridX) + gc.CELL_WIDTH / 2).toFloat()
        val cy = (gc.cellScreenY(gridY) + gc.CELL_HEIGHT / 2).toFloat()
        repeat(count) {
            val ang = Random.nextFloat() * (PI * 2).toFloat()
            val spd = Random.nextFloat() * 3.2f + 0.8f
            val dx = cos(ang) * spd
            val dy = sin(ang) * spd - Random.nextFloat() * 0.4f
            val c = heatColors[Random.nextInt(heatColors.size)]
            particles.add(
                Particle(
                    x = cx + Random.nextFloat() * 4f - 2f,
                    y = cy + Random.nextFloat() * 4f - 2f,
                    dx = dx,
                    dy = dy,
                    ch = fireChars[Random.nextInt(fireChars.size)],
                    base = c,
                    life = maxAge + Random.nextInt(8),
                    drag = 0.88f + Random.nextFloat() * 0.08f
                )
            )
        }
    }

    fun update() {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.age++
            p.x += p.dx
            p.y += p.dy
            p.dx *= p.drag
            p.dy *= p.drag
            p.dy += 0.06f
            if (p.age >= p.life) iter.remove()
        }
    }

    fun draw(canv: CPCanvas) {
        for (p in particles) {
            val ix = p.x.toInt()
            val iy = p.y.toInt()
            if (!canv.isValid(ix, iy)) continue
            val t = p.age.toFloat() / p.life.coerceAtLeast(1)
            val fade = (1f - t).coerceIn(0f, 1f)
            val pulse = (kotlin.math.sin((p.age + p.x * 0.1).toDouble()) * 0.15 + 0.85).toFloat()
            val r = (p.base.red() * fade * pulse).toInt().coerceIn(0, 255)
            val g = (p.base.green() * fade * pulse).toInt().coerceIn(0, 255)
            val b = (p.base.blue() * fade * pulse).toInt().coerceIn(0, 255)
            val px = CPPixel(p.ch, CPColor(r, g, b, "ex"), Option.empty(), 0)
            canv.drawPixel(px, ix, iy, 12)
        }
    }
}
