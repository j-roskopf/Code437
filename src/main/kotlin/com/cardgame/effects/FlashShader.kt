package com.cardgame.effects

import com.cardgame.game.GameState
import com.cardgame.game.GridConfig
import org.cosplay.*
import scala.Option

/**
 * A flash effect that tints a grid cell, fading over several frames.
 * If followPlayer is true, the flash renders on the player's current cell.
 * If false, it renders on the cell where flash() was called.
 */
class FlashEffect(
    private val tintColor: CPColor,
    private val durationFrames: Int = 12,
    private val followPlayer: Boolean = false
) {
    private var framesLeft = 0
    private var flashGridX = 0
    private var flashGridY = 0

    fun flash(gridX: Int, gridY: Int) {
        flashGridX = gridX
        flashGridY = gridY
        framesLeft = durationFrames
    }

    val isActive: Boolean get() = framesLeft > 0

    fun drawFlash(canv: CPCanvas) {
        if (framesLeft <= 0) return

        val gc = GridConfig
        val cellX = if (followPlayer) GameState.playerGridX else flashGridX
        val cellY = if (followPlayer) GameState.playerGridY else flashGridY
        val sx = gc.cellScreenX(cellX)
        val sy = gc.cellScreenY(cellY)
        val intensity = framesLeft.toFloat() / durationFrames

        for (y in sy until sy + gc.CELL_HEIGHT) {
            for (x in sx until sx + gc.CELL_WIDTH) {
                if (canv.isValid(x, y)) {
                    val zpx = canv.getZPixel(x, y)
                    val oldFg = zpx.fg()
                    val newR = (oldFg.red() + ((tintColor.red() - oldFg.red()) * intensity * 0.8f)).toInt().coerceIn(0, 255)
                    val newG = (oldFg.green() + ((tintColor.green() - oldFg.green()) * intensity * 0.8f)).toInt().coerceIn(0, 255)
                    val newB = (oldFg.blue() + ((tintColor.blue() - oldFg.blue()) * intensity * 0.8f)).toInt().coerceIn(0, 255)
                    val newFg = CPColor(newR, newG, newB, "fx")

                    val oldBg = zpx.bg()
                    val newBg = if (oldBg.isDefined) {
                        val bg = oldBg.get()
                        val bR = (bg.red() + ((tintColor.red() - bg.red()) * intensity * 0.5f)).toInt().coerceIn(0, 255)
                        val bG = (bg.green() + ((tintColor.green() - bg.green()) * intensity * 0.5f)).toInt().coerceIn(0, 255)
                        val bB = (bg.blue() + ((tintColor.blue() - bg.blue()) * intensity * 0.5f)).toInt().coerceIn(0, 255)
                        Option.apply(CPColor(bR, bG, bB, "fx-bg"))
                    } else {
                        val a = intensity * 0.4f
                        Option.apply(CPColor(
                            (tintColor.red() * a).toInt().coerceIn(0, 255),
                            (tintColor.green() * a).toInt().coerceIn(0, 255),
                            (tintColor.blue() * a).toInt().coerceIn(0, 255),
                            "fx-bg"
                        ))
                    }

                    val newPx = CPPixel(zpx.char(), newFg, newBg, zpx.tag())
                    canv.drawPixel(newPx, x, y, zpx.z() + 1)
                }
            }
        }

        framesLeft--
    }
}

/**
 * Multiple independent cell flashes (e.g. several bombs ticking down at once).
 * [intensityAt] is safe to call from the grid pass before [drawFlash] runs that frame.
 */
class MultiCellFlashEffect(
    private val tintColor: CPColor,
    private val durationFrames: Int = 16
) {
    private data class Entry(val gridX: Int, val gridY: Int, var framesLeft: Int)

    private val entries = mutableListOf<Entry>()

    fun flash(gridX: Int, gridY: Int) {
        entries.removeAll { it.gridX == gridX && it.gridY == gridY }
        entries.add(Entry(gridX, gridY, durationFrames))
    }

    /** Peak ~1.0 at start of flash, fades toward 0. Safe during grid render before [drawFlash]. */
    fun intensityAt(gridX: Int, gridY: Int): Float {
        val e = entries.find { it.gridX == gridX && it.gridY == gridY } ?: return 0f
        return e.framesLeft.toFloat() / durationFrames
    }

    fun drawFlash(canv: CPCanvas) {
        if (entries.isEmpty()) return
        val gc = GridConfig
        val iter = entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            val intensity = e.framesLeft.toFloat() / durationFrames
            val sx = gc.cellScreenX(e.gridX)
            val sy = gc.cellScreenY(e.gridY)
            for (y in sy until sy + gc.CELL_HEIGHT) {
                for (x in sx until sx + gc.CELL_WIDTH) {
                    if (!canv.isValid(x, y)) continue
                    val zpx = canv.getZPixel(x, y)
                    val oldFg = zpx.fg()
                    val newR = (oldFg.red() + ((tintColor.red() - oldFg.red()) * intensity * 0.85f)).toInt().coerceIn(0, 255)
                    val newG = (oldFg.green() + ((tintColor.green() - oldFg.green()) * intensity * 0.85f)).toInt().coerceIn(0, 255)
                    val newB = (oldFg.blue() + ((tintColor.blue() - oldFg.blue()) * intensity * 0.85f)).toInt().coerceIn(0, 255)
                    val newFg = CPColor(newR, newG, newB, "fx")

                    val oldBg = zpx.bg()
                    val newBg = if (oldBg.isDefined) {
                        val bg = oldBg.get()
                        val bR = (bg.red() + ((tintColor.red() - bg.red()) * intensity * 0.55f)).toInt().coerceIn(0, 255)
                        val bG = (bg.green() + ((tintColor.green() - bg.green()) * intensity * 0.55f)).toInt().coerceIn(0, 255)
                        val bB = (bg.blue() + ((tintColor.blue() - bg.blue()) * intensity * 0.55f)).toInt().coerceIn(0, 255)
                        Option.apply(CPColor(bR, bG, bB, "fx-bg"))
                    } else {
                        val a = intensity * 0.45f
                        Option.apply(
                            CPColor(
                                (tintColor.red() * a).toInt().coerceIn(0, 255),
                                (tintColor.green() * a).toInt().coerceIn(0, 255),
                                (tintColor.blue() * a).toInt().coerceIn(0, 255),
                                "fx-bg"
                            )
                        )
                    }

                    val newPx = CPPixel(zpx.char(), newFg, newBg, zpx.tag())
                    canv.drawPixel(newPx, x, y, zpx.z() + 1)
                }
            }
            e.framesLeft--
            if (e.framesLeft <= 0) iter.remove()
        }
    }
}
