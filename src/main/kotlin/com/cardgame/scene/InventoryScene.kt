package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.CardArt
import com.cardgame.game.EquipmentSlot
import com.cardgame.game.GameState
import com.cardgame.game.GridConfig
import com.cardgame.game.ItemType
import org.cosplay.*
import scala.Option

/**
 * Full-screen equipment view: board-sized cards in two rows of three, centered on screen.
 * Row 1: head, neck, chest — row 2: hands, pants, boots.
 */
object InventoryScene {
    private val BG_COLOR = CPColor(20, 20, 35, "inv-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")
    private val KEY_I = kbKey("KEY_LO_I")

    private val artPixelWidth = 30

    private fun px(ch: Char, fg: CPColor, bg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(bg), 0)

    private fun drawCardBorder(canv: CPCanvas, sx: Int, sy: Int, w: Int, h: Int, z: Int, color: CPColor) {
        canv.drawPixel(px('+', color, BG_COLOR), sx, sy, z)
        canv.drawPixel(px('+', color, BG_COLOR), sx + w - 1, sy, z)
        canv.drawPixel(px('+', color, BG_COLOR), sx, sy + h - 1, z)
        canv.drawPixel(px('+', color, BG_COLOR), sx + w - 1, sy + h - 1, z)
        for (x in sx + 1 until sx + w - 1) {
            canv.drawPixel(px('-', color, BG_COLOR), x, sy, z)
            canv.drawPixel(px('-', color, BG_COLOR), x, sy + h - 1, z)
        }
        for (yy in sy + 1 until sy + h - 1) {
            canv.drawPixel(px('|', color, BG_COLOR), sx, yy, z)
            canv.drawPixel(px('|', color, BG_COLOR), sx + w - 1, yy, z)
        }
    }

    private fun fillInterior(canv: CPCanvas, sx: Int, sy: Int, w: Int, h: Int, z: Int) {
        for (yy in sy + 1 until sy + h - 1) {
            for (xx in sx + 1 until sx + w - 1) {
                canv.drawPixel(px(' ', CPColor.C_WHITE(), BG_COLOR), xx, yy, z)
            }
        }
    }

    private fun clippedArt(art: List<String>): List<String> {
        val max = (GridConfig.CELL_HEIGHT - 4).coerceAtLeast(3)
        return art.take(max)
    }

    private fun artCenterY(sy: Int, lineCount: Int): Int {
        val h = GridConfig.CELL_HEIGHT
        val topRow = sy + 2
        val bottomRow = sy + h - 3
        val maxRows = bottomRow - topRow + 1
        val lc = lineCount.coerceIn(1, maxRows)
        val slack = maxRows - lc
        return topRow + (slack + 1) / 2
    }

    private fun drawArt(canv: CPCanvas, art: List<String>, startX: Int, startY: Int, z: Int, fg: CPColor) {
        for ((row, line) in art.withIndex()) {
            val clipped = line.take(artPixelWidth)
            for ((col, ch) in clipped.withIndex()) {
                if (ch != ' ') {
                    canv.drawPixel(px(ch, fg, BG_COLOR), startX + col, startY + row, z)
                }
            }
        }
    }

    private fun centerX(text: String, sx: Int, w: Int): Int =
        sx + (w - text.length) / 2

    /**
     * One grid-sized card: label on top row, full item art (or empty), optional bottom line.
     */
    private fun drawSlot(
        canv: CPCanvas,
        sx: Int,
        sy: Int,
        z: Int,
        label: String,
        itemType: ItemType?,
        borderColor: CPColor
    ) {
        val cw = GridConfig.CELL_WIDTH
        val cellH = GridConfig.CELL_HEIGHT
        fillInterior(canv, sx, sy, cw, cellH, z)
        drawCardBorder(canv, sx, sy, cw, cellH, z, borderColor)
        val title = label.take(cw - 2)
        val tx = centerX(title, sx, cw)
        for ((i, c) in title.withIndex()) {
            canv.drawPixel(px(c, borderColor, BG_COLOR), tx + i, sy + 1, z)
        }
        if (itemType != null) {
            val art = clippedArt(CardArt.itemSpriteForType(itemType))
            val fg = CardArt.itemTileColorForType(itemType)
            val artX = sx + (cw - (art.firstOrNull()?.length ?: 0)) / 2
            val artY = artCenterY(sy, art.size)
            drawArt(canv, art, artX, artY, z, fg)
            val sub = itemType.label.take(cw - 2)
            val bx = centerX(sub, sx, cw)
            val labelRow = sy + cellH - 2
            for ((i, c) in sub.withIndex()) {
                canv.drawPixel(px(c, CPColor.C_GREY70(), BG_COLOR), bx + i, labelRow, z)
            }
        } else {
            val empty = "—"
            val ex = centerX(empty, sx, cw)
            val midY = sy + cellH / 2
            for ((i, c) in empty.withIndex()) {
                canv.drawPixel(px(c, CPColor.C_GREY50(), BG_COLOR), ex + i, midY, z)
            }
        }
    }

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()
        val gear = GameState.equippedItems
        val border = CPColor(110, 110, 125, "inv-slot")

        val displaySprite = object : CPCanvasSprite("inventory-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()
                val cw = GridConfig.CELL_WIDTH
                val ch = GridConfig.CELL_HEIGHT
                val gap = 1
                val cx = w / 2

                val title = "INVENTORY"
                val hint = "B / ESC / I  Close"
                val gridW = 3 * cw + 2 * gap
                val gridH = 2 * ch + gap
                val blockH = 2 + gridH + 1 + 1
                var y = ((h - blockH) / 2).coerceAtLeast(1)
                val z = 1

                canv.drawString(cx - title.length / 2, y, z, title, CPColor.C_GOLD1(), Option.apply(BG_COLOR))
                y += 2

                val leftX = (w - gridW) / 2
                val col1 = leftX + cw + gap
                val col2 = leftX + 2 * (cw + gap)

                var rowY = y
                drawSlot(canv, leftX, rowY, z, "HEAD", gear[EquipmentSlot.HEAD.ordinal], border)
                drawSlot(canv, col1, rowY, z, "NECK", gear[EquipmentSlot.NECK.ordinal], border)
                drawSlot(canv, col2, rowY, z, "CHEST", gear[EquipmentSlot.CHEST.ordinal], border)
                rowY += ch + gap

                drawSlot(canv, leftX, rowY, z, "HANDS", gear[EquipmentSlot.HANDS.ordinal], border)
                drawSlot(canv, col1, rowY, z, "PANTS", gear[EquipmentSlot.PANTS.ordinal], border)
                drawSlot(canv, col2, rowY, z, "BOOTS", gear[EquipmentSlot.BOOTS.ordinal], border)

                val hy = y + gridH + 1
                canv.drawString(cx - hint.length / 2, hy, z, hint, CPColor.C_GREY50(), Option.apply(BG_COLOR))
            }
        }

        val inputSprite = object : CPCanvasSprite("inventory-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                when (key) {
                    KEY_B, KEY_ESC, KEY_I -> ctx.switchScene(GameState.inventoryReturnScene, false)
                    else -> {}
                }
            }
        }

        return CPScene(
            "inventory",
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
