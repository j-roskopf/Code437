package com.cardgame.effects

import com.cardgame.game.GridConfig
import org.cosplay.*
import scala.Option
import kotlin.random.Random

/**
 * A confetti burst that spawns particles from a grid cell center and
 * scatters them outward with gravity, fading as they age.
 */
class ConfettiEffect(
    private val maxParticles: Int = 30,
    private val maxAge: Int = 18,
    /** CosPlay z-order for drawn pixels (e.g. above HUD text at 15). */
    private val drawZ: Int = 10,
) {
    private data class Particle(
        var x: Float,
        var y: Float,
        var dx: Float,
        var dy: Float,
        val ch: Char,
        val color: CPColor,
        var age: Int = 0
    )

    private val particles = mutableListOf<Particle>()
    private val confettiChars = charArrayOf('*', '+', '.', 'o', '~', '^', '#', '@', '%')
    private val confettiColors = listOf(
        CPColor(255, 215, 0, "gold"),
        CPColor(255, 105, 180, "pink"),
        CPColor(0, 255, 127, "spring"),
        CPColor(64, 224, 208, "turq"),
        CPColor(255, 165, 0, "orange"),
        CPColor(138, 43, 226, "violet"),
        CPColor(0, 191, 255, "skyblue"),
        CPColor(255, 69, 0, "flame"),
        CPColor(50, 205, 50, "lime"),
        CPColor(255, 255, 100, "yellow"),
    )

    val isActive: Boolean get() = particles.isNotEmpty()

    fun spawn(gridX: Int, gridY: Int) {
        val gc = GridConfig
        val cx = (gc.cellScreenX(gridX) + gc.CELL_WIDTH / 2).toFloat()
        val cy = (gc.cellScreenY(gridY) + gc.CELL_HEIGHT / 2).toFloat()
        spawnAt(cx, cy)
    }

    /** Screen-space burst (character cell coordinates), e.g. for interstitial scenes. */
    fun spawnScreen(centerX: Int, centerY: Int) {
        spawnAt(centerX.toFloat(), centerY.toFloat())
    }

    private fun spawnAt(cx: Float, cy: Float) {
        repeat(maxParticles) {
            particles.add(Particle(
                x = cx,
                y = cy,
                dx = (Random.nextFloat() - 0.5f) * 3.0f,
                dy = (Random.nextFloat() - 0.7f) * 2.0f,  // bias upward
                ch = confettiChars[Random.nextInt(confettiChars.size)],
                color = confettiColors[Random.nextInt(confettiColors.size)]
            ))
        }
    }

    fun update() {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.age++
            p.x += p.dx
            p.y += p.dy
            p.dy += 0.08f  // gravity
            // Slow down horizontally
            p.dx *= 0.95f
            if (p.age >= maxAge) {
                iter.remove()
            }
        }
    }

    fun draw(canv: CPCanvas) {
        for (p in particles) {
            val ix = p.x.toInt()
            val iy = p.y.toInt()
            if (canv.isValid(ix, iy)) {
                val fade = 1.0f - (p.age.toFloat() / maxAge)
                val r = (p.color.red() * fade).toInt().coerceIn(0, 255)
                val g = (p.color.green() * fade).toInt().coerceIn(0, 255)
                val b = (p.color.blue() * fade).toInt().coerceIn(0, 255)
                val px = CPPixel(p.ch, CPColor(r, g, b, "cf"), Option.empty(), 0)
                canv.drawPixel(px, ix, iy, drawZ)
            }
        }
    }
}
