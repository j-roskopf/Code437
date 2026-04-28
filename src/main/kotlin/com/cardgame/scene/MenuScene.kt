package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.game.GameState
import com.cardgame.game.LevelConfig
import kotlin.math.sin
import org.cosplay.*
import scala.Option

object MenuScene {
    private val BG_COLOR = CPColor(20, 20, 35, "menu-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private fun lerpColor(a: CPColor, b: CPColor, t: Float): CPColor {
        val tt = t.coerceIn(0f, 1f)
        return CPColor(
            (a.red() + (b.red() - a.red()) * tt).toInt().coerceIn(0, 255),
            (a.green() + (b.green() - a.green()) * tt).toInt().coerceIn(0, 255),
            (a.blue() + (b.blue() - a.blue()) * tt).toInt().coerceIn(0, 255),
            "menu-lerp"
        )
    }

    private fun menuPx(ch: Char, fg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(BG_COLOR), 0)

    private val KEY_1 = kbKey("KEY_1")
    private val KEY_2 = kbKey("KEY_2")
    private val KEY_3 = kbKey("KEY_3")
    private val KEY_4 = kbKey("KEY_4")
    private val KEY_5 = kbKey("KEY_5")
    private val KEY_6 = kbKey("KEY_6")
    private val KEY_LO_X = kbKey("KEY_LO_X")
    private val KEY_UP_X = kbKey("KEY_UP_X")
    private val KEY_LO_Y = kbKey("KEY_LO_Y")
    private val KEY_UP_Y = kbKey("KEY_UP_Y")
    private val KEY_LO_N = kbKey("KEY_LO_N")
    private val KEY_UP_N = kbKey("KEY_UP_N")
    private val KEY_LO_B = kbKey("KEY_LO_B")
    private val KEY_ESC = kbKey("KEY_ESC")
    private val KEY_Q = kbKey("KEY_LO_Q")
    private const val MENU_MUSIC_RESOURCE = "music/folk-round-kevin-macleod-main-version-18634-03-03.mp3"
    private var menuMusic: CPSound? = null
    private var menuMusicPlaying = false

    private fun startMenuMusic() {
        if (menuMusicPlaying) return
        GameScene.stopAndResetGameMusic()
        kotlin.runCatching {
            val snd = menuMusic ?: CPSound.apply(MENU_MUSIC_RESOURCE).also { menuMusic = it }
            snd.loop(1200L, snd.`loop$default$2`())
            menuMusicPlaying = true
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Menu music start failed",
                throwable = it,
            )
        }
    }

    private fun stopMenuMusic() {
        val snd = menuMusic ?: return
        kotlin.runCatching {
            snd.stop(700L)
            menuMusicPlaying = false
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Menu music stop failed",
                throwable = it,
            )
        }
    }

    private fun isYesKey(key: CPKeyboardKey): Boolean =
        key == KEY_LO_Y ||
            key == KEY_UP_Y ||
            key.id().equals("y", ignoreCase = true) ||
            key.ch() == 'y' ||
            key.ch() == 'Y'

    private fun isNoKey(key: CPKeyboardKey): Boolean =
        key == KEY_LO_N ||
            key == KEY_UP_N ||
            key.id().equals("n", ignoreCase = true) ||
            key.ch() == 'n' ||
            key.ch() == 'N'

    private fun wantsDeletePrompt(key: CPKeyboardKey): Boolean =
        key == KEY_LO_X ||
            key == KEY_UP_X ||
            key.id().equals("x", ignoreCase = true) ||
            key.ch() == 'x' ||
            key.ch() == 'X'

    fun create(): CPScene {
        startMenuMusic()
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        var menuFrame = 0
        var deleteConfirmActive = false
        var deleteSaveFeedbackTicks = 0

        val menuSprite = object : CPCanvasSprite("menu-root", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (ctx.isVisible()) startMenuMusic()
                if (deleteSaveFeedbackTicks > 0) {
                    deleteSaveFeedbackTicks--
                }
                val evt = ctx.getKbEvent()
                if (!evt.isDefined) {
                    menuFrame++
                    return
                }
                val key = evt.get().key()
                var handled = false
                fun leaveMenu(next: () -> Unit) {
                    stopMenuMusic()
                    next()
                }

                if (deleteConfirmActive) {
                    when {
                        isYesKey(key) -> {
                            val ok = kotlin.runCatching { GameState.deleteAllSaveDataAndResetToDefaults() }
                                .onFailure {
                                    SentryBootstrap.captureCaughtError(
                                        message = "Delete save data failed",
                                        throwable = it,
                                    )
                                }
                                .isSuccess
                            deleteConfirmActive = false
                            if (ok) {
                                deleteSaveFeedbackTicks = 120
                            }
                            kotlin.runCatching { ctx.deleteScene(SceneId.GAME) }
                                .onFailure {
                                    SentryBootstrap.captureCaughtError(
                                        message = "Delete game scene from menu failed",
                                        throwable = it,
                                    )
                                }
                            handled = true
                        }
                        isNoKey(key) || key == KEY_ESC || key == KEY_LO_B -> {
                            deleteConfirmActive = false
                            handled = true
                        }
                        else -> {
                            // Swallow other keys so they do not trigger menu actions under the dialog.
                            handled = true
                        }
                    }
                    if (handled) {
                        ctx.consumeKbEvent()
                    }
                    menuFrame++
                    return
                }

                when {
                    key == KEY_1 -> {
                        GameState.resetForLevel(1)
                        SentryBootstrap.recordNewGameStarted(
                            startingLevel = 1,
                            source = "menu",
                            character = GameState.selectedPlayerCharacter.name,
                        )
                        kotlin.runCatching { ctx.deleteScene(SceneId.GAME) }
                            .onFailure {
                                SentryBootstrap.captureCaughtError(
                                    message = "Delete game scene for new run failed",
                                    throwable = it,
                                )
                            }
                        kotlin.runCatching { ctx.deleteScene(SceneId.INTRO_STORY) }
                            .onFailure {
                                SentryBootstrap.captureCaughtError(
                                    message = "Delete intro story scene for new run failed",
                                    throwable = it,
                                )
                            }
                        ctx.addScene(GameScene.create(), false, false, false)
                        ctx.addScene(IntroStoryScene.create(), false, false, false)
                        leaveMenu { ctx.switchScene(SceneId.INTRO_STORY, false) }
                        handled = true
                    }
                    key == KEY_2 -> {
                        leaveMenu { ctx.switchScene(SceneId.LEVEL_SELECT, false) }
                        handled = true
                    }
                    key == KEY_3 -> {
                        GameState.characterSelectCursor = GameState.selectedPlayerCharacter.ordinal
                        leaveMenu { ctx.switchScene(SceneId.CHARACTER_SELECT, false) }
                        handled = true
                    }
                    key == KEY_4 -> {
                        GameState.minigamesReturnScene = SceneId.MENU
                        leaveMenu { ctx.switchScene(SceneId.MINIGAMES, false) }
                        handled = true
                    }
                    key == KEY_5 -> {
                        GameState.startBossRush()
                        SentryBootstrap.recordNewGameStarted(
                            startingLevel = LevelConfig.firstBossCheckpoint(),
                            source = "menu_boss_rush",
                            character = GameState.selectedPlayerCharacter.name,
                        )
                        kotlin.runCatching { ctx.deleteScene(SceneId.BOSS_BATTLE) }
                            .onFailure {
                                SentryBootstrap.captureCaughtError(
                                    message = "Delete boss scene for boss rush failed",
                                    throwable = it,
                                )
                            }
                        ctx.addScene(BossScene.create(), false, false, false)
                        leaveMenu { ctx.switchScene(SceneId.BOSS_BATTLE, false) }
                        handled = true
                    }
                    key == KEY_6 -> {
                        leaveMenu { ctx.switchScene(SceneId.SETTINGS, false) }
                        handled = true
                    }
                    wantsDeletePrompt(key) -> {
                        deleteConfirmActive = true
                        handled = true
                    }
                    key == KEY_Q -> {
                        leaveMenu { ctx.exitGame() }
                        handled = true
                    }
                }
                if (handled) {
                    ctx.consumeKbEvent()
                }
                menuFrame++
            }

            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val f = menuFrame
                val titleArt = AsciiArt.MENU_TITLE_CODE437
                val artH = titleArt.size

                val options = listOf(
                    "[1]  New Game (Level 1)" to CPColor.C_STEEL_BLUE1(),
                    "[2]  Level Select" to CPColor.C_STEEL_BLUE1(),
                    "[3]  Character" to CPColor.C_STEEL_BLUE1(),
                    "[4]  Mini Games" to CPColor.C_STEEL_BLUE1(),
                    "[5]  Boss Rush" to CPColor.C_ORANGE1(),
                    "[6]  Settings" to CPColor.C_CYAN1(),
                    "[X]  Delete save (unlocks + decks)" to CPColor.C_ORANGE_RED1(),
                    "[Q]  Quit" to CPColor.C_GREY50(),
                )
                val totalRows = artH + 4 + 2 * options.size
                val startY = ((canv.height() - totalRows) / 2).coerceAtLeast(1)

                val glowOffsets = listOf(
                    -2 to 0, 2 to 0, 0 to -2, 0 to 2,
                    -1 to 0, 1 to 0, 0 to -1, 0 to 1,
                    -1 to -1, 1 to -1, -1 to 1, 1 to 1,
                    -2 to -1, 2 to -1, -2 to 1, 2 to 1,
                )
                val glowCol = CPColor(35, 55, 120, "menu-glow")
                val glowCol2 = CPColor(55, 85, 160, "menu-glow2")

                for ((row, line) in titleArt.withIndex()) {
                    val lineX = (canv.width() - line.length) / 2
                    for ((col, ch) in line.withIndex()) {
                        if (ch.isWhitespace()) continue
                        for ((i, oxy) in glowOffsets.withIndex()) {
                            val (ox, oy) = oxy
                            val gc = if (i < 4) glowCol2 else glowCol
                            canv.drawPixel(menuPx(ch, gc), lineX + col + ox, startY + row + oy, 1)
                        }
                    }
                }
                for ((row, line) in titleArt.withIndex()) {
                    val lineX = (canv.width() - line.length) / 2
                    for ((col, ch) in line.withIndex()) {
                        if (ch.isWhitespace()) continue
                        val pulse = (sin(f * 0.07 + col * 0.11 + row * 0.09).toFloat() + 1f) * 0.5f
                        val gold = CPColor.C_GOLD1()
                        val hot = CPColor(255, 255, 245, "menu-hot")
                        val cyan = CPColor(180, 235, 255, "menu-glint")
                        val c = lerpColor(lerpColor(gold, hot, pulse * 0.5f), cyan, pulse * 0.22f)
                        canv.drawPixel(menuPx(ch, c), lineX + col, startY + row, 3)
                    }
                }

                var y = startY + artH + 4
                for ((text, color) in options) {
                    val optionX = (canv.width() - text.length) / 2
                    canv.drawString(optionX, y, 2, text, color, Option.empty())
                    y += 2
                }
                val rowAfterMenu = y
                if (deleteSaveFeedbackTicks > 0) {
                    val msg = "Save data cleared (progress + decks)."
                    canv.drawString(
                        (canv.width() - msg.length) / 2,
                        rowAfterMenu + 1,
                        2,
                        msg,
                        CPColor.C_GREEN1(),
                        Option.empty(),
                    )
                }

                if (deleteConfirmActive) {
                    val lines = listOf(
                        "  ERASE ALL SAVE DATA?  " to CPColor.C_ORANGE_RED1(),
                        " Unlock progress + deck builds " to CPColor.C_GREY70(),
                        "" to CPColor.C_GREY50(),
                        "  [Y]  Yes, delete everything  " to CPColor.C_WHITE(),
                        "  [N]  Cancel   B / ESC  also cancel  " to CPColor.C_GREY50(),
                    )
                    val boxW = lines.maxOf { it.first.length }.coerceAtLeast(36)
                    val boxH = lines.count { it.first.isNotEmpty() } * 2 + 2
                    val boxX = (canv.width() - boxW) / 2
                    // Place directly under the menu so the ASCII title stays visible (not screen-centered).
                    val preferredY = rowAfterMenu + 1
                    val bottomPinnedY = (canv.height() - boxH - 2).coerceAtLeast(2)
                    val boxY =
                        if (preferredY + boxH + 1 <= canv.height()) {
                            preferredY
                        } else {
                            // Very short terminal: pin to bottom instead of sliding up over the title.
                            bottomPinnedY
                        }
                    val border = CPColor(90, 40, 40, "confirm-border")
                    for (bx in boxX - 1 until boxX + boxW + 1) {
                        canv.drawPixel(CPPixel(' ', CPColor.C_WHITE(), Option.apply(border), 0), bx, boxY - 1, 1)
                        canv.drawPixel(CPPixel(' ', CPColor.C_WHITE(), Option.apply(border), 0), bx, boxY + boxH, 1)
                    }
                    for (by in boxY - 1..boxY + boxH) {
                        canv.drawPixel(CPPixel(' ', CPColor.C_WHITE(), Option.apply(border), 0), boxX - 1, by, 1)
                        canv.drawPixel(CPPixel(' ', CPColor.C_WHITE(), Option.apply(border), 0), boxX + boxW, by, 1)
                    }
                    var ly = boxY + 1
                    for ((text, color) in lines) {
                        if (text.isEmpty()) {
                            ly += 1
                            continue
                        }
                        val lx = boxX + (boxW - text.length) / 2
                        canv.drawString(lx, ly, 2, text, color, Option.empty())
                        ly += 2
                    }
                }
            }
        }

        return CPScene(
            SceneId.MENU.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(menuSprite)
        )
    }
}
