package com.cardgame.platform

import com.cardgame.SentryBootstrap
import com.cardgame.game.DisplayModeSetting
import com.cardgame.game.GridConfig
import java.awt.Dimension
import java.awt.Font
import java.awt.Window
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * CosPlay’s emuterm does not expose a built-in fullscreen toggle. After the game [JFrame]
 * appears, prefer native macOS fullscreen so Cmd-Tab still works while the Dock/menu bar are hidden.
 */
object EmutermFullscreen {

    private const val POLL_MS = 120L
    private const val MAX_TRIES = 250
    private var appliedMode: DisplayModeSetting? = null

    fun startWatcher(gameTitleContains: String, mode: DisplayModeSetting) {
        Thread({
            repeat(MAX_TRIES) {
                Thread.sleep(POLL_MS)
                var attached = false
                try {
                    SwingUtilities.invokeAndWait {
                        val frame = findEmutermFrame(gameTitleContains) ?: return@invokeAndWait
                        applyMode(frame, mode)
                        attached = true
                    }
                } catch (e: Exception) {
                    SentryBootstrap.captureCaughtError(
                        message = "Emuterm fullscreen watcher failed",
                        throwable = e,
                        attributes = mapOf("title_hint" to gameTitleContains),
                    )
                    return@Thread
                }
                if (attached) return@Thread
            }
        }, "emuterm-fullscreen").apply {
            isDaemon = true
            start()
        }
    }

    fun applyPreferredMode(gameTitleContains: String, mode: DisplayModeSetting) {
        SwingUtilities.invokeLater {
            val frame = findEmutermFrame(gameTitleContains) ?: return@invokeLater
            applyMode(frame, mode)
        }
    }

    private fun findEmutermFrame(titleHint: String): JFrame? {
        val frames = Window.getWindows()
            .filterIsInstance<JFrame>()
            .filter { it.isShowing }
        if (frames.isEmpty()) return null
        frames.find { it.title.contains(titleHint, ignoreCase = true) }?.let { return it }
        return frames.singleOrNull()
    }

    private fun applyMode(w: JFrame, mode: DisplayModeSetting) {
        if (appliedMode == mode) return
        when (mode) {
            DisplayModeSetting.FULLSCREEN -> applyFullscreenOrMaximized(w)
            DisplayModeSetting.WINDOWED -> applyWindowed(w)
        }
        appliedMode = mode
    }

    private fun applyFullscreenOrMaximized(w: JFrame) {
        try {
            w.isResizable = true
            w.minimumSize = minimumWindowSize(w)
            w.graphicsConfiguration.device.fullScreenWindow?.let { active ->
                if (active == w) w.graphicsConfiguration.device.fullScreenWindow = null
            }
            if (requestMacNativeFullscreen(w)) {
                w.toFront()
                w.requestFocus()
                return
            }
            if (w.isDisplayable) {
                w.dispose()
            }
            w.isUndecorated = true
            w.extendedState = JFrame.MAXIMIZED_BOTH
            w.isVisible = true
            w.toFront()
            w.requestFocus()
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Emuterm borderless fullscreen apply failed",
                throwable = e,
            )
            try {
                w.isUndecorated = false
                w.extendedState = JFrame.MAXIMIZED_BOTH
                w.isVisible = true
                w.toFront()
                w.requestFocus()
            } catch (fallback: Exception) {
                SentryBootstrap.captureCaughtError(
                    message = "Emuterm maximize fallback failed",
                    throwable = fallback,
                )
                // ignore
            }
        }
    }

    private fun applyWindowed(w: JFrame) {
        try {
            if (appliedMode == DisplayModeSetting.FULLSCREEN && isMacOs()) {
                requestMacNativeFullscreen(w)
                Thread {
                    Thread.sleep(350L)
                    SwingUtilities.invokeLater { applyWindowedFrameSize(w) }
                }.apply {
                    isDaemon = true
                    start()
                }
                return
            }
            applyWindowedFrameSize(w)
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Emuterm windowed apply failed",
                throwable = e,
            )
        }
    }

    private fun applyWindowedFrameSize(w: JFrame) {
        try {
            val gd = w.graphicsConfiguration.device
            if (gd.fullScreenWindow == w) {
                gd.fullScreenWindow = null
            }
            if (w.isUndecorated) {
                if (w.isDisplayable) {
                    w.dispose()
                }
                w.isUndecorated = false
                w.isVisible = true
            }
            w.minimumSize = minimumWindowSize(w)
            w.extendedState = JFrame.NORMAL
            w.isResizable = true
            w.setSize(w.minimumSize)
            w.toFront()
            w.requestFocus()
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Emuterm windowed frame size apply failed",
                throwable = e,
            )
        }
    }

    private fun minimumWindowSize(w: JFrame): Dimension {
        val fm = emutermFontMetrics()
        val charW = fm.stringWidth("0123456789") / 10
        val charH = (fm.height - fm.leading + emutermCharHeightOffset()).coerceAtLeast(1)
        val ins = w.insets
        return Dimension(
            GridConfig.MIN_WINDOWED_COLS * charW.coerceAtLeast(1) + ins.left + ins.right,
            GridConfig.MIN_WINDOWED_ROWS * charH + ins.top + ins.bottom,
        )
    }

    private fun emutermFontMetrics(): java.awt.FontMetrics {
        val fontName =
            System.getenv("COSPLAY_EMUTERM_FONT_NAME")
                ?: System.getProperty("COSPLAY_EMUTERM_FONT_NAME")
                ?: "Monospaced"
        val fontSize =
            (System.getenv("COSPLAY_EMUTERM_FONT_SIZE")
                ?: System.getProperty("COSPLAY_EMUTERM_FONT_SIZE")
                ?: "14").toIntOrNull() ?: 14
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        val font = Font(fontName, Font.PLAIN, fontSize)
        return g.getFontMetrics(font)
    }

    private fun emutermCharHeightOffset(): Int =
        (System.getenv("COSPLAY_EMUTERM_CH_HEIGHT_OFFSET")
            ?: System.getProperty("COSPLAY_EMUTERM_CH_HEIGHT_OFFSET")
            ?: "0").toIntOrNull() ?: 0

    private fun requestMacNativeFullscreen(w: JFrame): Boolean {
        if (!isMacOs()) return false
        return try {
            if (w.isUndecorated) {
                if (w.isDisplayable) {
                    w.dispose()
                }
                w.isUndecorated = false
                w.isVisible = true
            }
            w.rootPane.putClientProperty("apple.awt.fullscreenable", true)
            val fullScreenUtilities = Class.forName("com.apple.eawt.FullScreenUtilities")
            fullScreenUtilities
                .getMethod("setWindowCanFullScreen", Window::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, w, true)
            val applicationClass = Class.forName("com.apple.eawt.Application")
            val app = applicationClass.getMethod("getApplication").invoke(null)
            applicationClass.getMethod("requestToggleFullScreen", Window::class.java).invoke(app, w)
            true
        } catch (e: Throwable) {
            SentryBootstrap.captureCaughtError(
                message = "macOS native fullscreen request failed",
                throwable = e,
            )
            false
        }
    }

    private fun isMacOs(): Boolean =
        System.getProperty("os.name").contains("mac", ignoreCase = true)
}
