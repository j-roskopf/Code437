package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.art.CardArt
import com.cardgame.game.GameState
import com.cardgame.game.GridConfig
import com.cardgame.game.PlayerCharacter
import org.cosplay.*
import scala.Option

object CharacterSelectScene {
    private val BG_COLOR = CPColor(20, 20, 35, "charsel-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_LEFT = kbKey("KEY_LEFT")
    private val KEY_RIGHT = kbKey("KEY_RIGHT")
    private val KEY_A = kbKey("KEY_LO_A")
    private val KEY_D = kbKey("KEY_LO_D")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")
    private val KEY_SPACE = kbKey("KEY_SPACE")
    private val KEY_UP = kbKey("KEY_UP")
    private val KEY_DOWN = kbKey("KEY_DOWN")
    private val KEY_W = kbKey("KEY_LO_W")
    private val KEY_S = kbKey("KEY_LO_S")

    private val options = PlayerCharacter.entries.toTypedArray()
    private val CARD_W = GridConfig.CELL_WIDTH
    private val CARD_H = GridConfig.CELL_HEIGHT
    private val ART_W = 30

    private fun cursorIdx(): Int =
        GameState.characterSelectCursor.coerceIn(0, options.size - 1)

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
        for (y in sy + 1 until sy + h - 1) {
            canv.drawPixel(px('|', color), sx, y, z)
            canv.drawPixel(px('|', color), sx + w - 1, y, z)
        }
    }

    private fun drawAscii(canv: CPCanvas, art: List<String>, sx: Int, sy: Int, z: Int, color: CPColor, maxW: Int, maxH: Int) {
        for ((r, line) in art.take(maxH).withIndex()) {
            val clipped = line.take(maxW)
            for ((c, ch) in clipped.withIndex()) {
                if (ch != ' ') canv.drawPixel(px(ch, color), sx + c, sy + r, z)
            }
        }
    }

    private fun drawArtInCard(canv: CPCanvas, art: List<String>, sx: Int, sy: Int, color: CPColor, z: Int) {
        val maxH = (CARD_H - 4).coerceAtLeast(3)
        val clipped = art.take(maxH)
        val artW = clipped.maxOfOrNull { it.length.coerceAtMost(ART_W) } ?: 0
        val artX = sx + (CARD_W - artW) / 2
        val artY = sy + 2 + ((maxH - clipped.size).coerceAtLeast(0) / 2)
        drawAscii(canv, clipped, artX, artY, z, color, ART_W, maxH)
    }

    private data class DeckGridLayout(
        val cols: Int,
        val cardsPerPage: Int,
        val totalPages: Int,
        val gridStartX: Int,
        val deckAreaTop: Int,
        val rowHeight: Int,
    )

    /**
     * [deckHeaderY] is the row where the "NAME DECK (n)" line is drawn; card rows start two lines below.
     */
    private fun deckGridLayout(canvasW: Int, canvasH: Int, deckHeaderY: Int, cardCount: Int): DeckGridLayout {
        val gap = 2
        val rowHeight = CARD_H + 1
        val deckAreaTop = deckHeaderY + 2
        val reserveBottom = 1
        val availableHeight = (canvasH - deckAreaTop - reserveBottom).coerceAtLeast(rowHeight)
        val rowsPerPage = (availableHeight / rowHeight).coerceAtLeast(1)
        val cols = ((canvasW - 2 + gap) / (CARD_W + gap)).coerceAtLeast(1)
        val cardsPerPage = (rowsPerPage * cols).coerceAtLeast(1)
        val totalPages = if (cardCount == 0) 1 else (cardCount + cardsPerPage - 1) / cardsPerPage
        val gridW = cols * CARD_W + (cols - 1) * gap
        val gridStartX = ((canvasW - gridW) / 2).coerceAtLeast(1)
        return DeckGridLayout(cols, cardsPerPage, totalPages, gridStartX, deckAreaTop, rowHeight)
    }

    private class DeckPageState {
        var page: Int = 0
        private var lastHero: PlayerCharacter? = null

        fun onHeroChange(hero: PlayerCharacter) {
            if (lastHero != hero) {
                page = 0
                lastHero = hero
            }
        }

        fun clamp(totalPages: Int) {
            val maxP = (totalPages - 1).coerceAtLeast(0)
            page = page.coerceIn(0, maxP)
        }
    }

    /** Display order only; persistent deck draw order in [GameState] is unchanged. */
    private fun characterDeckSortedForDisplay(hero: PlayerCharacter): List<GameState.PlayerDeckCard> =
        GameState.characterDeckCards(hero).sortedBy { it.ordinal }

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()
        val deckPageState = DeckPageState()

        val displaySprite = object : CPCanvasSprite("charsel-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()
                val cx = w / 2
                val topY = 4

                val title = "CHOOSE YOUR HERO"
                canv.drawString(cx - title.length / 2, 1, 1, title, CPColor.C_GOLD1(), Option.empty())
                val help = "[1][2][3]  \u2190/\u2192  A/D  W/S \u2191/\u2193 page  SPACE save  B/ESC back"
                canv.drawString(cx - help.length / 2, 2, 1, help, CPColor.C_GREY50(), Option.empty())

                val gapTop = 2
                val totalTopW = options.size * CARD_W + (options.size - 1) * gapTop
                val topX = ((w - totalTopW) / 2).coerceAtLeast(1)
                val selIdx = cursorIdx()
                for ((i, pc) in options.withIndex()) {
                    val sx = topX + i * (CARD_W + gapTop)
                    val sy = topY
                    val sel = i == selIdx
                    drawCardBorder(
                        canv,
                        sx,
                        sy,
                        CARD_W,
                        CARD_H,
                        1,
                        if (sel) CPColor.C_GOLD1() else CPColor.C_GREY50()
                    )
                    val label = if (sel) "> ${pc.label} <" else pc.label
                    canv.drawString(
                        sx + (CARD_W - label.length) / 2,
                        sy + 1,
                        2,
                        label,
                        if (sel) CPColor.C_WHITE() else CPColor.C_GREY70(),
                        Option.empty()
                    )
                    drawArtInCard(canv, AsciiArt.playerLines(pc), sx, sy, pc.spriteColor, 2)
                }

                val choice = options[selIdx]
                deckPageState.onHeroChange(choice)
                val cards = characterDeckSortedForDisplay(choice)
                val bottomY = topY + CARD_H + 2
                val layout = deckGridLayout(w, h, bottomY, cards.size)
                deckPageState.clamp(layout.totalPages)

                val pageLabel = if (layout.totalPages > 1) {
                    "  p ${deckPageState.page + 1}/${layout.totalPages}"
                } else {
                    ""
                }
                val hdr = "${choice.label} DECK (${cards.size})$pageLabel"
                canv.drawString(cx - hdr.length / 2, bottomY, 1, hdr, CPColor.C_CYAN1(), Option.empty())

                val gap = 2
                val startIdx = deckPageState.page * layout.cardsPerPage
                val endIdx = minOf(cards.size, startIdx + layout.cardsPerPage)
                for (idx in startIdx until endIdx) {
                    val card = cards[idx]
                    val i = idx - startIdx
                    val col = i % layout.cols
                    val row = i / layout.cols
                    val sx = layout.gridStartX + col * (CARD_W + gap)
                    val sy = layout.deckAreaTop + row * layout.rowHeight
                    val type = GameState.deckCardToItemType(card)
                    val border = CardArt.itemTileColorForType(type)
                    drawCardBorder(canv, sx, sy, CARD_W, CARD_H, 1, border)
                    val nm = card.label.take(CARD_W - 2)
                    canv.drawString(sx + (CARD_W - nm.length) / 2, sy + 1, 2, nm, border, Option.empty())
                    val art = CardArt.itemSpriteForType(type)
                    drawArtInCard(canv, art, sx, sy, border, 2)
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("charsel-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()

                fun move(d: Int) {
                    val n = options.size
                    GameState.characterSelectCursor = (cursorIdx() + d + n) % n
                }

                fun layoutForCurrentHero(): DeckGridLayout {
                    val canv = ctx.canvas
                    val w = canv.width()
                    val h = canv.height()
                    val topY = 4
                    val bottomY = topY + CARD_H + 2
                    val choice = options[cursorIdx()]
                    val cards = characterDeckSortedForDisplay(choice)
                    return deckGridLayout(w, h, bottomY, cards.size)
                }

                when (key) {
                    KEY_1 -> GameState.characterSelectCursor = 0
                    KEY_2 -> GameState.characterSelectCursor = 1
                    KEY_3 -> GameState.characterSelectCursor = 2
                    KEY_LEFT, KEY_A -> move(-1)
                    KEY_RIGHT, KEY_D -> move(1)
                    KEY_UP, KEY_W -> {
                        deckPageState.page = (deckPageState.page - 1).coerceAtLeast(0)
                    }
                    KEY_DOWN, KEY_S -> {
                        val lay = layoutForCurrentHero()
                        deckPageState.page = (deckPageState.page + 1).coerceAtMost((lay.totalPages - 1).coerceAtLeast(0))
                    }
                    KEY_SPACE -> {
                        GameState.selectedPlayerCharacter = options[cursorIdx()]
                        GameState.persistDecksIfEnabled()
                        ctx.switchScene(SceneId.MENU, false)
                    }
                    KEY_B, KEY_ESC -> ctx.switchScene(SceneId.MENU, false)
                    else -> {}
                }
            }
        }

        return CPScene(
            SceneId.CHARACTER_SELECT.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
