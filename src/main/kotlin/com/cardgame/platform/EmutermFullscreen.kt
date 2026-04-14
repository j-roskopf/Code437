package com.cardgame.platform

import com.cardgame.SentryBootstrap
import java.awt.Window
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * CosPlay’s emuterm does not expose a built-in fullscreen toggle. After the game [JFrame]
 * appears, request exclusive fullscreen (no title bar). If that fails, fall back to maximize.
 */
object EmutermFullscreen {

    private const val POLL_MS = 120L
    private const val MAX_TRIES = 250

    fun startWatcher(gameTitleContains: String) {
        Thread({
            repeat(MAX_TRIES) {
                Thread.sleep(POLL_MS)
                var attached = false
                try {
                    SwingUtilities.invokeAndWait {
                        val frame = findEmutermFrame(gameTitleContains) ?: return@invokeAndWait
                        applyFullscreenOrMaximized(frame)
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

    private fun findEmutermFrame(titleHint: String): JFrame? {
        val frames = Window.getWindows()
            .filterIsInstance<JFrame>()
            .filter { it.isShowing }
        if (frames.isEmpty()) return null
        frames.find { it.title.contains(titleHint, ignoreCase = true) }?.let { return it }
        return frames.singleOrNull()
    }

    private fun applyFullscreenOrMaximized(w: JFrame) {
        try {
            val gd = w.graphicsConfiguration.device
            gd.fullScreenWindow = w
            w.toFront()
            w.requestFocus()
        } catch (e: Exception) {
            SentryBootstrap.captureCaughtError(
                message = "Emuterm fullscreen apply failed",
                throwable = e,
            )
            try {
                w.extendedState = JFrame.MAXIMIZED_BOTH
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
}
