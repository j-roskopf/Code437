package com.cardgame.ui

import com.cardgame.art.CardArt
import com.cardgame.game.GameState
import com.cardgame.game.GridConfig
import com.cardgame.game.GridItem
import com.cardgame.game.ItemType
import com.cardgame.game.equipmentArmorValue
import com.cardgame.game.equipmentShortTag
import org.cosplay.*
import scala.Option

/**
 * Full-size deck card preview ([GridConfig.CELL_WIDTH]×[GridConfig.CELL_HEIGHT]) for character select and shop.
 * Uses the same [GridItem] preview as spawns from the player deck (base value + [GameState.DeckCardInstance.plus]).
 */
object DeckCardRender {
    private const val ART_W = 30

    private fun px(ch: Char, fg: CPColor, bg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(bg), 0)

    fun previewGridItem(entry: GameState.DeckCardInstance): GridItem {
        val card = entry.card
        val type = GameState.deckCardToItemType(card)
        val kt = card.keyTier()
        val atkV = GameState.deckCardAttackBoostArtVariant(card)
        val v = entry.totalEffectValue() ?: 0
        return when {
            kt != null -> GridItem(ItemType.KEY, 0, 0, 0, artVariant = kt.ordinal, tier = kt)
            atkV != null -> GridItem(type, v, 0, 0, artVariant = atkV, playerDeckPlus = entry.plus)
            else -> GridItem(type, v, 0, 0, playerDeckPlus = entry.plus)
        }
    }

    private fun bottomLabel(item: GridItem): String =
        when (item.type) {
            ItemType.HEALTH_POTION, ItemType.ATTACK_BOOST, ItemType.SHIELD -> "+${item.value}"
            ItemType.REST ->
                if (item.value > 0) "HEAL+${item.value}" else "HEAL"
            ItemType.KEY -> item.tier.name.take(8)
            ItemType.HAND_ARMOR, ItemType.HELMET, ItemType.NECKLACE,
            ItemType.CHEST_ARMOR, ItemType.LEGGINGS, ItemType.BOOTS_ARMOR,
            -> {
                val tag = item.type.equipmentShortTag()
                "+${item.type.equipmentArmorValue() + item.playerDeckPlus} $tag"
            }
            ItemType.CHEST -> if (item.value > 0) "${item.value} GP" else "CHEST"
            else -> "+${item.value}"
        }

    private fun drawCardBorder(canv: CPCanvas, sx: Int, sy: Int, w: Int, h: Int, z: Int, color: CPColor, bg: CPColor) {
        canv.drawPixel(px('+', color, bg), sx, sy, z)
        canv.drawPixel(px('+', color, bg), sx + w - 1, sy, z)
        canv.drawPixel(px('+', color, bg), sx, sy + h - 1, z)
        canv.drawPixel(px('+', color, bg), sx + w - 1, sy + h - 1, z)
        for (x in sx + 1 until sx + w - 1) {
            canv.drawPixel(px('-', color, bg), x, sy, z)
            canv.drawPixel(px('-', color, bg), x, sy + h - 1, z)
        }
        for (yy in sy + 1 until sy + h - 1) {
            canv.drawPixel(px('|', color, bg), sx, yy, z)
            canv.drawPixel(px('|', color, bg), sx + w - 1, yy, z)
        }
    }

    private fun drawAscii(
        canv: CPCanvas,
        art: List<String>,
        startX: Int,
        startY: Int,
        z: Int,
        color: CPColor,
        bg: CPColor,
        maxW: Int,
        maxH: Int,
    ) {
        for ((row, line) in art.take(maxH).withIndex()) {
            val clipped = line.take(maxW)
            for ((col, ch) in clipped.withIndex()) {
                if (ch != ' ') canv.drawPixel(px(ch, color, bg), startX + col, startY + row, z)
            }
        }
    }

    /**
     * @param footerOverride when non-null, drawn on the bottom row instead of [bottomLabel] (e.g. shop price line).
     */
    fun drawDeckTile(
        canv: CPCanvas,
        sx: Int,
        sy: Int,
        z: Int,
        entry: GameState.DeckCardInstance,
        borderColor: CPColor,
        bgColor: CPColor,
        footerOverride: String? = null,
    ) {
        val cw = GridConfig.CELL_WIDTH
        val ch = GridConfig.CELL_HEIGHT
        drawCardBorder(canv, sx, sy, cw, ch, z, borderColor, bgColor)
        val preview = previewGridItem(entry)
        val title = entry.displayLabel().take(cw - 2)
        canv.drawString(
            sx + (cw - title.length) / 2,
            sy + 1,
            z + 1,
            title,
            borderColor,
            Option.apply(bgColor),
        )
        val art = CardArt.itemSprite(preview)
        val maxH = (ch - 4).coerceAtLeast(3)
        val clipped = art.take(maxH)
        val artW = clipped.maxOfOrNull { it.length.coerceAtMost(ART_W) } ?: 0
        val artX = sx + (cw - artW) / 2
        val artY = sy + 2 + ((maxH - clipped.size).coerceAtLeast(0) / 2)
        drawAscii(canv, clipped, artX, artY, z + 1, borderColor, bgColor, ART_W, maxH)
        val foot = footerOverride ?: bottomLabel(preview)
        val footClipped = foot.take(cw - 2)
        canv.drawString(
            sx + (cw - footClipped.length) / 2,
            sy + ch - 2,
            z + 1,
            footClipped,
            borderColor,
            Option.apply(bgColor),
        )
    }

    /**
     * One row of offer cards centered; returns Y just below the row.
     * @param slotOffset global index of [offers[0]] in [sold] / [afford] / [prices] (for multi-row shops).
     */
    fun drawShopOfferRow(
        canv: CPCanvas,
        canvasW: Int,
        topY: Int,
        z: Int,
        offers: List<GameState.DeckCardInstance>,
        sold: BooleanArray,
        afford: BooleanArray,
        prices: IntArray,
        bgColor: CPColor,
        slotOffset: Int = 0,
    ): Int {
        val cw = GridConfig.CELL_WIDTH
        val ch = GridConfig.CELL_HEIGHT
        val gap = 2
        val n = offers.size
        val rowW = n * cw + (n - 1) * gap
        val startX = ((canvasW - rowW) / 2).coerceAtLeast(1)
        for (i in offers.indices) {
            val gi = slotOffset + i
            val sx = startX + i * (cw + gap)
            val entry = offers[i]
            val preview = previewGridItem(entry)
            val border = when {
                sold[gi] -> CPColor.C_GREY50()
                !afford[gi] -> CPColor.C_ORANGE_RED1()
                else -> CardArt.itemTileColor(preview)
            }
            val stat = bottomLabel(preview)
            val foot = when {
                sold[gi] -> "— SOLD —"
                !afford[gi] -> "${stat.take(10)} · ${prices[gi]}g !"
                else -> "$stat · ${prices[gi]}g"
            }
            drawDeckTile(canv, sx, topY, z, entry, border, bgColor, footerOverride = foot)
        }
        return topY + ch + 1
    }
}
