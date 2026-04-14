package com.cardgame

import com.cardgame.platform.EmutermFullscreen
import com.cardgame.scene.GameOverScene
import com.cardgame.scene.InventoryScene
import com.cardgame.scene.LevelCompleteScene
import com.cardgame.scene.LevelSelectScene
import com.cardgame.scene.CharacterSelectScene
import com.cardgame.scene.DebugMenuScene
import com.cardgame.game.GameState
import com.cardgame.game.GridConfig
import com.cardgame.scene.SceneId
import com.cardgame.scene.MenuScene
import com.cardgame.scene.MiniGamesHubScene
import com.cardgame.scene.QuestScene
import com.cardgame.scene.RestScene
import com.cardgame.scene.RunSummaryScene
import org.cosplay.*
import scala.Option

private fun wantsWindowed(args: Array<String>): Boolean =
    args.any { it == "windowed" || it == "--windowed" }

private fun disablesFullscreenHook(args: Array<String>): Boolean =
    args.any { it == "--no-fullscreen-hook" } ||
        System.getenv("COSPLAY_FULLSCREEN_HOOK") == "0" ||
        System.getProperty("COSPLAY_FULLSCREEN_HOOK") == "0"

private fun wantsFullscreenHook(args: Array<String>): Boolean =
    !disablesFullscreenHook(args)

private fun resolvedEmutermFontSize(): String =
    System.getenv("COSPLAY_EMUTERM_FONT_SIZE")
        ?: System.getProperty("COSPLAY_EMUTERM_FONT_SIZE")
        ?: "11"

/**
 * Emuterm initial character grid: must cover the main board ([GridConfig.MIN_EMUTERM_*]) so the grid
 * and side HUD margins are not clipped. Pixel size still comes from font metrics; default font targets 1920×1080
 * (see Gradle `COSPLAY_EMUTERM_FONT_SIZE`).
 *
 * Pass `windowed` or `--windowed` for CosPlay’s built-in default (100×50).
 */
private fun emuInitDim(args: Array<String>): Option<CPDim> =
    if (wantsWindowed(args)) Option.empty()
    else Option.apply(CPDim.apply(GridConfig.MIN_EMUTERM_COLS, GridConfig.MIN_EMUTERM_ROWS))

fun main(args: Array<String>) {
    SentryBootstrap.init(args)
    SentryBootstrap.captureTestExceptionIfRequested(args)

    // Keep packaged runs visually consistent with local dev unless explicitly overridden.
    if (System.getProperty("COSPLAY_EMUTERM_FONT_SIZE").isNullOrBlank()) {
        System.setProperty("COSPLAY_EMUTERM_FONT_SIZE", resolvedEmutermFontSize())
    }
    val emuTerm = System.console() == null || args.contains("emuterm")

    val gameInfo = CPGameInfo(
        "code-437",
        "Code 437",
        "1.0.0",
        emuInitDim(args),
        CPColor(20, 20, 35, "bg"),
        Option.empty()
    )

    CPEngine.init(gameInfo, emuTerm)

    GameState.loadDeckPersistenceAtStartup()
    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching { GameState.persistDecksIfEnabled() }
                .onFailure {
                    SentryBootstrap.captureCaughtError(
                        message = "Shutdown deck persistence failed",
                        throwable = it,
                    )
                }
        }
    )

    if (emuTerm && !wantsWindowed(args) && wantsFullscreenHook(args)) {
        EmutermFullscreen.startWatcher("Code 437")
    }

    try {
        val scenes = scalaSeqOf(
            MenuScene.create(),
            MiniGamesHubScene.create(),
            CharacterSelectScene.create(),
            LevelSelectScene.create(),
            LevelCompleteScene.create(),
            QuestScene.create(),
            RestScene.create(),
            RunSummaryScene.create(),
            GameOverScene.create(),
            InventoryScene.create(),
            DebugMenuScene.create()
        )
        CPEngine.startGame(SceneId.MENU.id, scenes)
    } finally {
        CPEngine.dispose()
        SentryBootstrap.close()
    }
    // CosPlay/JavaFX may leave non-daemon threads alive after exitGame(); without this, `./gradlew run` never returns.
    System.exit(0)
}
