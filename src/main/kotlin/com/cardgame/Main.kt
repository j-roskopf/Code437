package com.cardgame

import com.cardgame.platform.EmutermFullscreen
import com.cardgame.scene.GameOverScene
import com.cardgame.scene.InventoryScene
import com.cardgame.scene.LevelCompleteScene
import com.cardgame.scene.LevelSelectScene
import com.cardgame.scene.CharacterSelectScene
import com.cardgame.scene.DebugMenuScene
import com.cardgame.scene.BossScene
import com.cardgame.game.DisplayModeSetting
import com.cardgame.game.GameState
import com.cardgame.game.GridConfig
import com.cardgame.game.UserSettings
import com.cardgame.scene.SceneId
import com.cardgame.scene.MenuScene
import com.cardgame.scene.MiniGamesHubScene
import com.cardgame.scene.QuestScene
import com.cardgame.scene.RestScene
import com.cardgame.scene.RunSummaryScene
import com.cardgame.scene.SettingsScene
import org.cosplay.*
import scala.Option

private fun cliWantsWindowed(args: Array<String>): Boolean =
    args.any { it == "windowed" || it == "--windowed" }

private fun cliWantsFullscreen(args: Array<String>): Boolean =
    args.any { it == "fullscreen" || it == "--fullscreen" }

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
 * Start at the minimum playable character grid. Windowed mode also uses this as its shrink limit.
 */
private fun emuInitDim(mode: DisplayModeSetting): Option<CPDim> {
    val cols =
        if (mode == DisplayModeSetting.WINDOWED) GridConfig.MIN_WINDOWED_COLS
        else GridConfig.MIN_EMUTERM_COLS
    val rows =
        if (mode == DisplayModeSetting.WINDOWED) GridConfig.MIN_WINDOWED_ROWS
        else GridConfig.MIN_EMUTERM_ROWS
    return Option.apply(CPDim.apply(cols, rows))
}

private fun resolvedDisplayMode(args: Array<String>): DisplayModeSetting =
    when {
        cliWantsWindowed(args) -> DisplayModeSetting.WINDOWED
        cliWantsFullscreen(args) -> DisplayModeSetting.FULLSCREEN
        else -> UserSettings.displayMode
    }

fun main(args: Array<String>) {
    SentryBootstrap.init(args)
    SentryBootstrap.captureTestExceptionIfRequested(args)

    // Keep packaged runs visually consistent with local dev unless explicitly overridden.
    if (System.getProperty("COSPLAY_EMUTERM_FONT_SIZE").isNullOrBlank()) {
        System.setProperty("COSPLAY_EMUTERM_FONT_SIZE", resolvedEmutermFontSize())
    }
    val emuTerm = System.console() == null || args.contains("emuterm")
    val displayMode = resolvedDisplayMode(args)

    val gameInfo = CPGameInfo(
        "code-437",
        "Code 437",
        "1.0.0",
        emuInitDim(displayMode),
        CPColor(20, 20, 35, "bg"),
        Option.empty()
    )

    CPEngine.init(gameInfo, emuTerm)
    GameAudio.preload()

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

    if (emuTerm && wantsFullscreenHook(args)) {
        EmutermFullscreen.startWatcher("Code 437", displayMode)
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
            BossScene.create(),
            RunSummaryScene.create(),
            GameOverScene.create(),
            InventoryScene.create(),
            SettingsScene.create(),
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
