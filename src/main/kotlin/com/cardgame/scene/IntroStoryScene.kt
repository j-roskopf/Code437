package com.cardgame.scene

import com.cardgame.*
import org.cosplay.*
import scala.Option

object IntroStoryScene {
    private val BG_COLOR = CPColor(20, 20, 35, "intro-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private val KEY_ESC = kbKey("KEY_ESC")
    private val KEY_ENTER = kbKey("KEY_ENTER")
    private val KEY_SPACE = kbKey("KEY_SPACE")

    // Roughly "normal reading" speed in emuterm frame steps.
    private const val CHARS_PER_TICK = 2
    private const val TICKS_PER_CHAR = 1
    private const val SIDE_MARGIN = 6

    private val PARAGRAPHS = listOf(
        "You awake on the floor of a tavern without any of the armor you were wearing when you entered. You can't remember how you ended up on the floor, but you have a deck of cards in your hands and you assume that has something to do with it.",
        "The bartender yells from behind the counter: \"You took quite a fall there friend\"",
        "\"How did I get here?\" you ask, starting to remember",
        "\"You tried to leave before paying your debts.\" the bartender said curtly. \"But I tell ya what. If you can beat the patrons here in a game of 437, we'll call it even\"",
        "\"Okay?\"",
    )

    private fun wrapParagraph(text: String, maxWidth: Int): List<String> {
        if (maxWidth <= 1) return listOf(text.take(maxWidth.coerceAtLeast(0)))
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var line = StringBuilder()
        for (word in words) {
            if (line.isEmpty()) {
                line.append(word)
                continue
            }
            val proposedLen = line.length + 1 + word.length
            if (proposedLen <= maxWidth) {
                line.append(' ').append(word)
            } else {
                out += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }

    private fun centeredX(canvasWidth: Int, text: String): Int =
        ((canvasWidth - text.length) / 2).coerceAtLeast(0)

    private fun isAdvanceKey(key: CPKeyboardKey): Boolean =
        key == KEY_ENTER ||
            key == KEY_SPACE ||
            key.id().equals("enter", ignoreCase = true) ||
            key.id().equals("space", ignoreCase = true) ||
            key.ch() == '\n' ||
            key.ch() == '\r' ||
            key.ch() == ' '

    fun create(): CPScene {
        val shaders = emptyScalaSeq()
        val tags = emptyStringSet()

        var tick = 0
        var revealedChars = 0

        val sprite = object : CPCanvasSprite("intro-story", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)

                val canv = ctx.canvas
                val maxWidth = (canv.width() - SIDE_MARGIN * 2).coerceAtLeast(24)
                val lines = buildList {
                    for ((idx, paragraph) in PARAGRAPHS.withIndex()) {
                        addAll(wrapParagraph(paragraph, maxWidth))
                        if (idx != PARAGRAPHS.lastIndex) add("")
                    }
                }
                val fullText = lines.joinToString("\n")
                val isComplete = revealedChars >= fullText.length

                val evt = ctx.getKbEvent()
                if (evt.isDefined) {
                    val key = evt.get().key()
                    when {
                        key == KEY_ESC -> {
                            ctx.switchScene(SceneId.MENU, false)
                            ctx.consumeKbEvent()
                            return
                        }
                        !isComplete && isAdvanceKey(key) -> {
                            revealedChars = fullText.length
                            ctx.consumeKbEvent()
                        }
                        isComplete && isAdvanceKey(key) -> {
                            ctx.switchScene(SceneId.GAME, false)
                            ctx.consumeKbEvent()
                            return
                        }
                    }
                }

                if (!isComplete) {
                    tick++
                    if (tick % TICKS_PER_CHAR == 0) {
                        revealedChars = (revealedChars + CHARS_PER_TICK).coerceAtMost(fullText.length)
                    }
                }
            }

            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val maxWidth = (canv.width() - SIDE_MARGIN * 2).coerceAtLeast(24)
                val lines = buildList {
                    for ((idx, paragraph) in PARAGRAPHS.withIndex()) {
                        addAll(wrapParagraph(paragraph, maxWidth))
                        if (idx != PARAGRAPHS.lastIndex) add("")
                    }
                }
                val fullText = lines.joinToString("\n")
                val visibleText = fullText.take(revealedChars.coerceAtMost(fullText.length))
                val visibleLines = visibleText.split('\n')

                val yStart = ((canv.height() - lines.size) / 2).coerceAtLeast(1)
                val bg = Option.apply(BG_COLOR)
                val bodyCol = CPColor.C_GREY70()

                for ((i, ln) in visibleLines.withIndex()) {
                    if (ln.isEmpty()) continue
                    canv.drawString(centeredX(canv.width(), ln), yStart + i, 2, ln, bodyCol, bg)
                }

                if (revealedChars >= fullText.length) {
                    val prompt = "Press ENTER or SPACE to begin"
                    val promptY = (yStart + lines.size + 2).coerceAtMost(canv.height() - 2)
                    canv.drawString(
                        centeredX(canv.width(), prompt),
                        promptY,
                        2,
                        prompt,
                        CPColor.C_GOLD1(),
                        bg,
                    )
                } else {
                    val hint = "ENTER / SPACE to reveal all"
                    val hintY = (yStart + lines.size + 2).coerceAtMost(canv.height() - 2)
                    canv.drawString(
                        centeredX(canv.width(), hint),
                        hintY,
                        2,
                        hint,
                        CPColor.C_GREY50(),
                        bg,
                    )
                }
            }
        }

        return CPScene(
            SceneId.INTRO_STORY.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(sprite),
        )
    }
}
