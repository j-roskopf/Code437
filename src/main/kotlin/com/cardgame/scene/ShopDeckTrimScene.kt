package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.CardArt
import com.cardgame.game.GameState
import com.cardgame.game.GridConfig
import com.cardgame.game.PlayerCharacter
import com.cardgame.ui.DeckCardRender
import org.cosplay.*
import scala.Option

/**
 * Full-screen picker: same deck grid style as [CharacterSelectScene], but for the current hero only.
 * Arrow keys move the highlight; Space asks for confirmation, then Y or Space again removes and returns to the shop.
 */
object ShopDeckTrimScene {
    private val BG_COLOR = CPColor(22, 20, 38, "shop-trim-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_LEFT = kbKey("KEY_LEFT")
    private val KEY_RIGHT = kbKey("KEY_RIGHT")
    private val KEY_UP = kbKey("KEY_UP")
    private val KEY_DOWN = kbKey("KEY_DOWN")
    private val KEY_W = kbKey("KEY_LO_W")
    private val KEY_S = kbKey("KEY_LO_S")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")
    private val KEY_SPACE = kbKey("KEY_SPACE")
    private val KEY_Y = kbKey("KEY_LO_Y")
    private val KEY_N = kbKey("KEY_LO_N")

    private val CARD_W = GridConfig.CELL_WIDTH
    private val CARD_H = GridConfig.CELL_HEIGHT

    private data class DeckGridLayout(
        val cols: Int,
        val cardsPerPage: Int,
        val totalPages: Int,
        val gridStartX: Int,
        val deckAreaTop: Int,
        val rowHeight: Int,
    )

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

    fun create(
        onCancel: (CPSceneObjectContext) -> Unit,
        onRemoved: (CPSceneObjectContext, String) -> Unit,
    ): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()
        var selectedDisplayIdx = 0
        var awaitingConfirm = false

        fun hero(): PlayerCharacter = GameState.selectedPlayerCharacter

        fun cards(): List<GameState.DeckCardInstance> =
            GameState.characterDeckCardsSortedForDisplay(hero())

        fun layout(ctx: CPSceneObjectContext): DeckGridLayout {
            val canv = ctx.canvas
            val w = canv.width()
            val h = canv.height()
            val deckHeaderY = 6
            val list = cards()
            return deckGridLayout(w, h, deckHeaderY, list.size)
        }

        fun clampSelection(n: Int) {
            if (n <= 0) {
                selectedDisplayIdx = 0
                return
            }
            selectedDisplayIdx = selectedDisplayIdx.coerceIn(0, n - 1)
        }

        val displaySprite = object : CPCanvasSprite("shop-trim-display", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()
                val cx = w / 2
                val list = cards()
                val n = list.size
                clampSelection(n)

                val title = "REMOVE FROM BUILD"
                canv.drawString(cx - title.length / 2, 1, 1, title, CPColor.C_GOLD1(), Option.empty())
                val help = if (awaitingConfirm) {
                    "Y or SPACE confirm   N / B / Esc cancel   (arrows cancel prompt)"
                } else {
                    "\u2190/\u2192/\u2191/\u2193 move   W/S page   SPACE remove   B/ESC back"
                }
                canv.drawString(cx - help.length / 2, 2, 1, help, CPColor.C_GREY50(), Option.empty())

                val deckHeaderY = 6
                val lay = deckGridLayout(w, h, deckHeaderY, n)
                val maxPage = (lay.totalPages - 1).coerceAtLeast(0)
                val page = (selectedDisplayIdx / lay.cardsPerPage).coerceAtMost(maxPage)
                val pageLabel = if (lay.totalPages > 1) {
                    "  p ${page + 1}/${lay.totalPages}"
                } else {
                    ""
                }
                val hdr = "${hero().label} DECK ($n)$pageLabel"
                canv.drawString(cx - hdr.length / 2, deckHeaderY, 1, hdr, CPColor.C_CYAN1(), Option.empty())

                val gap = 2
                val startIdx = page * lay.cardsPerPage
                val endIdx = minOf(n, startIdx + lay.cardsPerPage)
                for (idx in startIdx until endIdx) {
                    val entry = list[idx]
                    val i = idx - startIdx
                    val col = i % lay.cols
                    val row = i / lay.cols
                    val sx = lay.gridStartX + col * (CARD_W + gap)
                    val sy = lay.deckAreaTop + row * lay.rowHeight
                    val sel = idx == selectedDisplayIdx
                    val border = if (sel) CPColor.C_GOLD1() else CardArt.itemTileColor(DeckCardRender.previewGridItem(entry))
                    DeckCardRender.drawDeckTile(canv, sx, sy, 2, entry, border, BG_COLOR, footerOverride = null)
                }

                if (awaitingConfirm && n > 0) {
                    val q = "Remove ${list[selectedDisplayIdx].displayLabel()} from your build?"
                    val line = q.take((w - 4).coerceAtLeast(12))
                    val yBar = (h - 2).coerceAtLeast(3)
                    canv.drawString(cx - line.length / 2, yBar, 3, line, CPColor.C_ORANGE1(), Option.empty())
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("shop-trim-input", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                val list = cards()
                val n = list.size
                if (n <= 0) {
                    onCancel(ctx)
                    return
                }
                clampSelection(n)
                val lay = layout(ctx)
                val cp = lay.cardsPerPage.coerceAtLeast(1)
                val maxPage = (lay.totalPages - 1).coerceAtLeast(0)

                if (awaitingConfirm) {
                    when (key) {
                        KEY_Y, KEY_SPACE -> {
                            val label = list[selectedDisplayIdx].displayLabel()
                            if (GameState.removeSelectedCharacterBuildCardAtDisplayIndex(selectedDisplayIdx)) {
                                onRemoved(ctx, label)
                            }
                            return
                        }
                        KEY_N, KEY_B, KEY_ESC -> {
                            awaitingConfirm = false
                            return
                        }
                        KEY_LEFT, KEY_RIGHT, KEY_UP, KEY_DOWN, KEY_W, KEY_S -> {
                            awaitingConfirm = false
                        }
                        else -> return
                    }
                }

                when (key) {
                    KEY_B, KEY_ESC -> {
                        onCancel(ctx)
                        return
                    }
                    KEY_SPACE -> {
                        awaitingConfirm = true
                        return
                    }
                    KEY_LEFT -> {
                        selectedDisplayIdx = (selectedDisplayIdx - 1).coerceAtLeast(0)
                        return
                    }
                    KEY_RIGHT -> {
                        selectedDisplayIdx = (selectedDisplayIdx + 1).coerceAtMost(n - 1)
                        return
                    }
                    KEY_UP -> {
                        selectedDisplayIdx = (selectedDisplayIdx - lay.cols).coerceAtLeast(0)
                        return
                    }
                    KEY_DOWN -> {
                        selectedDisplayIdx = (selectedDisplayIdx + lay.cols).coerceAtMost(n - 1)
                        return
                    }
                    KEY_W -> {
                        val page = (selectedDisplayIdx / cp).coerceAtMost(maxPage)
                        val newPage = (page - 1).coerceAtLeast(0)
                        selectedDisplayIdx = (newPage * cp).coerceAtMost(n - 1)
                        return
                    }
                    KEY_S -> {
                        val page = (selectedDisplayIdx / cp).coerceAtMost(maxPage)
                        val newPage = (page + 1).coerceAtMost(maxPage)
                        selectedDisplayIdx = (newPage * cp).coerceAtMost(n - 1)
                        return
                    }
                    else -> {}
                }
            }
        }

        return CPScene(
            SceneId.SHOP_DECK_TRIM.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite),
        )
    }
}
