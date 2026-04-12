package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.game.GameState
import com.cardgame.game.GridConfig
import com.cardgame.ui.DeckCardRender
import org.cosplay.*
import scala.Option

private data class ShopOffer(
    val slot: Int,
    val label: String,
    val price: Int,
    val instance: GameState.DeckCardInstance,
)

/**
 * Spend [GameState.money] on random cards. Offers refresh each visit.
 * Open from the main menu or level-complete screen.
 */
object ShopScene {
    private val BG_COLOR = CPColor(25, 22, 40, "shop-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    /** Frames to show trade confirmation / short error (CosPlay ~60 FPS). */
    private const val TRADE_OK_FEEDBACK_TICKS = 96
    private const val TRADE_FAIL_FEEDBACK_TICKS = 48
    private const val REMOVE_FEEDBACK_TICKS = 96

    private val KEY_0 = kbKey("KEY_0")
    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_4 = kbKey("KEY_4")
    private val KEY_5 = kbKey("KEY_5")
    private val KEY_6 = kbKey("KEY_6")
    private val KEY_7 = kbKey("KEY_7")
    private val KEY_8 = kbKey("KEY_8")
    private val KEY_9 = kbKey("KEY_9")
    private val KEY_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")
    private val KEY_M = kbKey("KEY_LO_M")

    private fun shopPriceFor(card: GameState.PlayerDeckCard): Int = when (card) {
        GameState.PlayerDeckCard.POTION -> 16
        GameState.PlayerDeckCard.SHIELD -> 20
        GameState.PlayerDeckCard.SWORD -> 22
        GameState.PlayerDeckCard.BOW_ARROW -> 22
        GameState.PlayerDeckCard.CAMPFIRE -> 24
        GameState.PlayerDeckCard.ARMOR,
        GameState.PlayerDeckCard.HELM_GEAR,
        GameState.PlayerDeckCard.NECK_GEAR,
        GameState.PlayerDeckCard.PLATE_CHEST,
        GameState.PlayerDeckCard.LEGS_GEAR,
        GameState.PlayerDeckCard.BOOTS_GEAR,
        -> 28
        GameState.PlayerDeckCard.KEY_BRONZE -> 18
        GameState.PlayerDeckCard.KEY_SILVER -> 26
        GameState.PlayerDeckCard.KEY_GOLD -> 36
        GameState.PlayerDeckCard.CHEST -> 0
    }

    private fun generateOffers(): List<ShopOffer> {
        val cards = GameState.purchaseDeckCardOptions()
        return List(GameState.SHOP_OFFER_COUNT) { idx ->
            val inst = cards[idx]
            ShopOffer(idx + 1, inst.displayLabel(), shopPriceFor(inst.card), inst)
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

    private fun drawStr(canv: CPCanvas, w: Int, y: Int, z: Int, text: String, fg: CPColor, centered: Boolean = true) {
        val t = text.take((w - 2).coerceAtLeast(1))
        val x = if (centered) ((w - t.length) / 2).coerceAtLeast(1) else 1
        canv.drawString(x, y, z, t, fg, Option.apply(BG_COLOR))
    }

    /** Fixed-width key rows: `Br / Sl / Gd` columns aligned for scanability. */
    private fun keyStatusBlock(
        pouchB: Int,
        pouchS: Int,
        pouchG: Int,
        deckB: Int,
        deckS: Int,
        deckG: Int,
    ): List<String> {
        val hdr = "            Br   Sl   Gd"
        val r1 = "  Pouch     %3d  %3d  %3d".format(pouchB, pouchS, pouchG)
        val r2 = "  In deck   %3d  %3d  %3d".format(deckB, deckS, deckG)
        return listOf(hdr, r1, r2)
    }

    fun create(): CPScene {
        val offers = generateOffers()
        val sold = BooleanArray(offers.size) { false }
        var tradeFeedback: String? = null
        var tradeFeedbackTicks = 0
        var removeFeedback: String? = null
        var removeFeedbackTicks = 0
        var removeUsedThisVisit = false

        fun startRemoveFeedback(msg: String) {
            removeFeedback = msg
            removeFeedbackTicks = REMOVE_FEEDBACK_TICKS
        }

        val shopSprite = object : CPCanvasSprite("shop", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (tradeFeedbackTicks > 0) {
                    tradeFeedbackTicks--
                    if (tradeFeedbackTicks == 0) tradeFeedback = null
                }
                if (removeFeedbackTicks > 0) {
                    removeFeedbackTicks--
                    if (removeFeedbackTicks == 0) removeFeedback = null
                }
                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()
                val build = GameState.characterDeckCards(GameState.selectedPlayerCharacter)

                if (key == KEY_M) {
                    if (removeUsedThisVisit) {
                        startRemoveFeedback("  You already removed a card this visit.")
                        return
                    }
                    if (build.size <= 1) {
                        startRemoveFeedback("  Need at least 2 cards in your build to remove one.")
                        return
                    }
                    kotlin.runCatching { ctx.deleteScene(SceneId.SHOP_DECK_TRIM) }
                    ctx.addScene(
                        ShopDeckTrimScene.create(
                            onCancel = { c ->
                                kotlin.runCatching { c.deleteScene(SceneId.SHOP_DECK_TRIM) }
                                c.switchScene(SceneId.SHOP, false)
                            },
                            onRemoved = { c, label ->
                                removeUsedThisVisit = true
                                startRemoveFeedback("  Removed $label from your build.")
                                kotlin.runCatching { c.deleteScene(SceneId.SHOP_DECK_TRIM) }
                                c.switchScene(SceneId.SHOP, false)
                            },
                        ),
                        false,
                        false,
                        false,
                    )
                    ctx.switchScene(SceneId.SHOP_DECK_TRIM, false)
                    return
                }

                val idx = when (key) {
                    KEY_1 -> 0
                    KEY_2 -> 1
                    KEY_3 -> 2
                    KEY_4 -> 3
                    KEY_5 -> 4
                    KEY_6 -> 5
                    KEY_7 -> 6
                    KEY_8 -> 7
                    KEY_9 -> {
                        val cost = GameState.KEY_TRADE_UP_COST
                        if (GameState.tryTradeBronzeKeysForSilver()) {
                            tradeFeedback = "  Traded $cost bronze keys for 1 silver."
                            tradeFeedbackTicks = TRADE_OK_FEEDBACK_TICKS
                        } else {
                            tradeFeedback = "  Need at least $cost bronze keys to trade."
                            tradeFeedbackTicks = TRADE_FAIL_FEEDBACK_TICKS
                        }
                        return
                    }
                    KEY_0 -> {
                        val cost = GameState.KEY_TRADE_UP_COST
                        if (GameState.tryTradeSilverKeysForGold()) {
                            tradeFeedback = "  Traded $cost silver keys for 1 gold."
                            tradeFeedbackTicks = TRADE_OK_FEEDBACK_TICKS
                        } else {
                            tradeFeedback = "  Need at least $cost silver keys to trade."
                            tradeFeedbackTicks = TRADE_FAIL_FEEDBACK_TICKS
                        }
                        return
                    }
                    KEY_B, KEY_ESC -> {
                        kotlin.runCatching { ctx.deleteScene(SceneId.SHOP_DECK_TRIM) }
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
                GameState.addCardToPlayerDeck(o.instance)
                sold[idx] = true
            }

            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()

                val art = AsciiArt.SHOP_STALL
                val artW = 30
                val artH = art.size
                val cost = GameState.KEY_TRADE_UP_COST
                val tradeBronzeOk = GameState.canTradeBronzeKeysForSilver()
                val tradeSilverOk = GameState.canTradeSilverKeysForGold()
                val tradeBronzeLine =
                    "[9]  ${cost} bronze → 1 silver   " +
                        if (tradeBronzeOk) "ready" else "need $cost bronze"
                val tradeSilverLine =
                    "[0]  ${cost} silver → 1 gold   " +
                        if (tradeSilverOk) "ready" else "need $cost silver"
                val (deckKb, deckKs, deckKg) = GameState.playerDeckKeyCardsInPiles()
                val deckSnap = GameState.playerDeckSnapshot()
                val inPiles = deckSnap.draw + deckSnap.discard
                val buildSize = GameState.selectedCharacterDeckBuildSize()
                val build = GameState.characterDeckCards(GameState.selectedPlayerCharacter)
                val feedbackPair: Pair<String, CPColor>? =
                    if (tradeFeedback != null && tradeFeedbackTicks > 0) {
                        val ok = tradeFeedback!!.contains("Traded")
                        tradeFeedback!!.trim() to if (ok) CPColor.C_GREEN1() else CPColor.C_ORANGE1()
                    } else null
                val removeFeedbackPair: Pair<String, CPColor>? =
                    if (removeFeedback != null && removeFeedbackTicks > 0) {
                        val ok = removeFeedback!!.contains("Removed")
                        removeFeedback!!.trim() to if (ok) CPColor.C_GREEN1() else CPColor.C_ORANGE1()
                    } else null

                val keyLines = keyStatusBlock(
                    GameState.keysBronze,
                    GameState.keysSilver,
                    GameState.keysGold,
                    deckKb,
                    deckKs,
                    deckKg,
                )
                val removeHintLines = 1 // card removal status line (picker is a separate scene)
                // Title + rule + resources + blank + 3 key lines + optional feedback + blank + stall + gap + offers label + cards + gap + remove block + trades + blank + help
                val preStallLines = 1 + 1 + 1 + 1 + keyLines.size +
                    (if (feedbackPair != null) 2 else 1) +
                    (if (removeFeedbackPair != null) 2 else 1) + 1
                val oneRowH = GridConfig.CELL_HEIGHT + 2
                val cardRowsH = oneRowH
                val postCardLines = removeHintLines + 1 + 2 + 1 + 1 // gap after cards, two trades, blank, help
                val lineCount = preStallLines + artH + 1 + 1 + cardRowsH + postCardLines
                var y = ((h - lineCount) / 2).coerceAtLeast(1)
                val zHud = 2

                drawStr(canv, w, y++, zHud, "DECK MERCHANT", CPColor.C_GOLD1())
                val ruleW = (40).coerceAtMost(w - 4).coerceAtLeast(12)
                drawStr(canv, w, y++, zHud, "─".repeat(ruleW), CPColor.C_GREY50())
                drawStr(
                    canv,
                    w,
                    y++,
                    zHud,
                    "Gold ${GameState.money}    ·    ${GameState.selectedPlayerCharacter.label} build $buildSize  (draw+discard $inPiles)",
                    CPColor.C_CYAN1(),
                )
                y += 1
                for (kl in keyLines) {
                    drawStr(canv, w, y++, zHud, kl, CPColor.C_STEEL_BLUE1())
                }
                if (feedbackPair != null) {
                    y += 1
                    drawStr(canv, w, y++, zHud, feedbackPair.first.take(w - 2), feedbackPair.second)
                } else {
                    y += 1
                }
                if (removeFeedbackPair != null) {
                    y += 1
                    drawStr(canv, w, y++, zHud, removeFeedbackPair.first.take(w - 2), removeFeedbackPair.second)
                } else {
                    y += 1
                }
                y += 1

                val artX = (w - artW) / 2
                drawAsciiLines(canv, art, artX, y, 1, CPColor.C_GOLD1())
                y += artH + 1

                val nOffer = GameState.SHOP_OFFER_COUNT
                drawStr(canv, w, y++, zHud, "Offers  [1]-[$nOffer]", CPColor.C_GREY70())
                val cardTypes = offers.map { it.instance }
                val prices = IntArray(offers.size) { offers[it].price }
                val afford = BooleanArray(offers.size) { i -> GameState.money >= offers[i].price }
                y = DeckCardRender.drawShopOfferRow(
                    canv,
                    w,
                    y,
                    zHud,
                    cardTypes,
                    sold,
                    afford,
                    prices,
                    BG_COLOR,
                    slotOffset = 0,
                )

                y += 1
                if (removeUsedThisVisit) {
                    drawStr(canv, w, y++, zHud, "Card Removal already used this visit.", CPColor.C_GREY50())
                } else if (build.size <= 1) {
                    drawStr(canv, w, y++, zHud, "[M] Card Removal (need 2+ cards)", CPColor.C_GREY50())
                } else {
                    drawStr(canv, w, y++, zHud, "[M] Card Removal — one card per visit (full screen)", CPColor.C_GREY70())
                }

                drawStr(
                    canv,
                    w,
                    y++,
                    zHud,
                    tradeBronzeLine,
                    if (tradeBronzeOk) CPColor.C_GREEN1() else CPColor.C_GREY50(),
                )
                drawStr(
                    canv,
                    w,
                    y++,
                    zHud,
                    tradeSilverLine,
                    if (tradeSilverOk) CPColor.C_GREEN1() else CPColor.C_GREY50(),
                )
                y += 1
                drawStr(
                    canv,
                    w,
                    y,
                    zHud,
                    "[9][0] key trades    M Card Removal    B / Esc leave shop",
                    CPColor.C_GREY70(),
                )
            }
        }

        return CPScene(
            SceneId.SHOP.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(shopSprite),
        )
    }
}
