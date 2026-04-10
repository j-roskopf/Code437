package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.game.GameState
import org.cosplay.*
import scala.Option

private data class ShopOffer(
    val slot: Int,
    val label: String,
    val price: Int,
    val cardType: GameState.PlayerDeckCard,
)

/**
 * Spend [GameState.money] on random cards. Offers refresh each visit.
 * Open from the main menu or level-complete screen.
 */
object ShopScene {
    private val BG_COLOR = CPColor(25, 22, 40, "shop-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")

    private fun generateOffers(): List<ShopOffer> {
        val cards = GameState.purchaseDeckCardOptions()
        return List(3) { idx ->
            val card = cards[idx]
            val price = when (card) {
                GameState.PlayerDeckCard.POTION -> 16
                GameState.PlayerDeckCard.SHIELD -> 20
                GameState.PlayerDeckCard.SWORD -> 22
                GameState.PlayerDeckCard.BOW_ARROW -> 22
                GameState.PlayerDeckCard.CAMPFIRE -> 24
                GameState.PlayerDeckCard.ARMOR -> 28
                GameState.PlayerDeckCard.KEY -> 18
                GameState.PlayerDeckCard.CHEST -> 0
            }
            ShopOffer(idx + 1, card.label, price, card)
        }
    }

    private fun px(ch: Char, fg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(BG_COLOR), 0)

    private fun drawAsciiLines(canv: CPCanvas, lines: List<String>, startX: Int, startY: Int, z: Int, fg: CPColor) {
        for ((row, line) in lines.withIndex()) {
            val clipped = line.take(30)
            for ((col, ch) in clipped.withIndex()) {
                if (ch != ' ') canv.drawPixel(px(ch, fg), startX + col, startY + row, z)
            }
        }
    }

    fun create(): CPScene {
        val offers = generateOffers()
        val sold = BooleanArray(offers.size) { false }

        val displaySprite = object : CPCanvasSprite("shop-display", emptyScalaSeq(), emptyStringSet()) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()

                val art = AsciiArt.SHOP_STALL
                val artW = 30
                val artH = art.size
                val offerLines = offers.indices.map { i ->
                    val o = offers[i]
                    if (sold[i]) {
                        "  [${o.slot}]  ${o.label}  —  SOLD"
                    } else {
                        val afford = GameState.money >= o.price
                        val suffix = if (afford) "" else " (can't afford)"
                        "  [${o.slot}]  ${o.label}  —  ${o.price} gp$suffix"
                    }
                }
                val textBlocks = listOf(
                    "DECK MERCHANT" to CPColor.C_GOLD1(),
                    "GOLD ${GameState.money}   P-DECK ${GameState.playerDeckSnapshot().draw + GameState.playerDeckSnapshot().discard}" to CPColor.C_CYAN1(),
                ) + offerLines.map { line ->
                    val col = when {
                        "SOLD" in line -> CPColor.C_GREY50()
                        "can't afford" in line -> CPColor.C_ORANGE_RED1()
                        else -> CPColor.C_GREEN1()
                    }
                    line to col
                } + listOf(
                    "" to CPColor.C_GREY50(),
                    "[1][2][3] buy    B return" to CPColor.C_GREY70(),
                )

                val lineCount = artH + 1 + textBlocks.size * 2
                var y = ((h - lineCount) / 2).coerceAtLeast(1)
                val artX = (w - artW) / 2
                drawAsciiLines(canv, art, artX, y, 1, CPColor.C_GOLD1())
                y += artH + 1

                val maxLen = textBlocks.maxOf { (t, _) -> t.length }
                val tx = (w - maxLen) / 2
                for ((line, color) in textBlocks) {
                    if (line.isEmpty()) {
                        y += 1
                        continue
                    }
                    canv.drawString(tx, y, 2, line.take(w - 2), color, Option.apply(BG_COLOR))
                    y += 2
                }
            }
        }

        val inputSprite = object : CPCanvasSprite("shop-input", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                val idx = when (key) {
                    KEY_1 -> 0
                    KEY_2 -> 1
                    KEY_3 -> 2
                    KEY_B, KEY_ESC -> {
                        when (val dismiss = GameState.shopDismissAction) {
                            ShopDismissAction.AdvanceLevelRecreateGame -> {
                                GameState.advanceToNextLevel()
                                kotlin.runCatching { ctx.deleteScene(SceneId.GAME) }
                                ctx.addScene(GameScene.create(), false, false, false)
                                ctx.switchScene(SceneId.GAME, false)
                            }
                            is ShopDismissAction.SwitchTo -> ctx.switchScene(dismiss.scene, false)
                        }
                        GameState.shopDismissAction = ShopDismissAction.SwitchTo(SceneId.MENU)
                        return
                    }
                    else -> return
                }
                if (idx !in offers.indices || sold[idx]) return
                val o = offers[idx]
                if (!GameState.trySpendMoney(o.price)) return
                GameState.addCardToPlayerDeck(o.cardType)
                sold[idx] = true
            }
        }

        return CPScene(
            SceneId.SHOP.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
