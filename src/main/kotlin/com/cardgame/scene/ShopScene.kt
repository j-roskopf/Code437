package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.game.GameState
import com.cardgame.game.KeyTier
import kotlin.random.Random
import org.cosplay.*
import scala.Option

private enum class ShopPurchaseKind {
    HEAL_HP,
    ADD_ATK,
    ADD_KEY,
    SHIELD_HP,
}

private data class ShopOffer(
    val slot: Int,
    val label: String,
    val price: Int,
    val kind: ShopPurchaseKind,
    val value: Int,
)

/**
 * Spend [GameState.money] on random power-ups. Offers refresh each visit.
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
        val kinds = ShopPurchaseKind.entries
        return List(3) { idx ->
            when (kinds.random()) {
                ShopPurchaseKind.HEAL_HP -> {
                    val v = Random.nextInt(5, 11)
                    ShopOffer(idx + 1, "+$v HP", Random.nextInt(14, 28), ShopPurchaseKind.HEAL_HP, v)
                }
                ShopPurchaseKind.ADD_ATK -> {
                    val v = Random.nextInt(1, 3)
                    ShopOffer(idx + 1, "+$v ATK", Random.nextInt(24, 44), ShopPurchaseKind.ADD_ATK, v)
                }
                ShopPurchaseKind.ADD_KEY -> ShopOffer(
                    idx + 1,
                    "+1 Key",
                    Random.nextInt(16, 30),
                    ShopPurchaseKind.ADD_KEY,
                    1
                )
                ShopPurchaseKind.SHIELD_HP -> {
                    val v = Random.nextInt(4, 10)
                    ShopOffer(idx + 1, "+$v HP (tough)", Random.nextInt(12, 22), ShopPurchaseKind.SHIELD_HP, v)
                }
            }
        }
    }

    private fun applyPurchase(offer: ShopOffer) {
        when (offer.kind) {
            ShopPurchaseKind.HEAL_HP, ShopPurchaseKind.SHIELD_HP ->
                GameState.playerHealth = (GameState.playerHealth + offer.value).coerceAtMost(99)
            ShopPurchaseKind.ADD_ATK -> GameState.playerAttack += offer.value
            ShopPurchaseKind.ADD_KEY -> repeat(offer.value) { GameState.addKey(KeyTier.entries.random()) }
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
                    "MERCHANT" to CPColor.C_GOLD1(),
                    "GOLD ${GameState.money}   HP ${GameState.playerHealth}   ATK ${GameState.playerAttack}   KEYS ${GameState.keysBronze}B ${GameState.keysSilver}S ${GameState.keysGold}G" to CPColor.C_CYAN1(),
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
                        ctx.switchScene(GameState.shopReturnScene, false)
                        return
                    }
                    else -> return
                }
                if (idx !in offers.indices || sold[idx]) return
                val o = offers[idx]
                if (!GameState.trySpendMoney(o.price)) return
                applyPurchase(o)
                sold[idx] = true
            }
        }

        return CPScene(
            "shop",
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }
}
