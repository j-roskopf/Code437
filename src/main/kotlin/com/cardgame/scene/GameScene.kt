package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.art.CardArt
import com.cardgame.art.metalColor
import com.cardgame.effects.*
import com.cardgame.game.*
import com.cardgame.quest.QuestSystem
import java.util.IdentityHashMap
import kotlin.random.Random
import org.cosplay.*
import scala.Option

object GameScene {
    /** Main game HUD: left column (run meta) and right column (quests / keys / controls) each vertically centered. */
    internal data class HudLayout(
        val leftStartY: Int,
        val rightStartY: Int,
        val rightQuestStartY: Int,
        val rightInvRow: Int,
        val rightKeysRow: Int,
        val rightDebugRow: Int,
        val rightBarY: Int,
        val leftLineCount: Int,
        val rightLineCount: Int,
    )

    internal data class WallChipResult(
        val remainingHp: Int,
        val destroyed: Boolean,
    )

    internal enum class MoveCollision {
        NONE,
        CHEST_LOCKED,
        CHEST_UNLOCKED,
        BOMB,
        WALL,
    }

    internal data class MoveResolution(
        val collision: MoveCollision,
        val chest: GridItem? = null,
        val bomb: GridItem? = null,
        val wall: GridItem? = null,
    )

    internal data class ItemEffect(
        val healthDelta: Int = 0,
        val attackDelta: Int = 0,
        val tempShieldDelta: Int = 0,
        val keyTierToAdd: KeyTier? = null,
        val equipType: ItemType? = null,
    )

    internal data class CombatExchange(
        val playerDamageTaken: Int,
        val enemyDamageTaken: Int,
        val enemyDefeated: Boolean,
        val playerDefeated: Boolean,
    )

    internal enum class SlideKind {
        NONE,
        COLUMN_UP,
        ROW_LEFT,
    }

    internal data class PostMovePlan(
        val moved: Boolean,
        val slideKind: SlideKind,
        val shouldTickBombs: Boolean,
        val shouldCheckShop: Boolean,
        val shouldCheckLevelComplete: Boolean,
    )

    internal enum class PostMoveSceneRoute {
        NONE,
        MINIGAMES,
        SHOP,
        LEVEL_COMPLETE,
    }

    private val BG_COLOR = CPColor(20, 20, 35, "bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    private var items = listOf<GridItem>()
    private var enemies = listOf<EnemyCard>()

    private val KEY_W = kbKey("KEY_LO_W")
    private val KEY_A = kbKey("KEY_LO_A")
    private val KEY_S = kbKey("KEY_LO_S")
    private val KEY_D = kbKey("KEY_LO_D")
    private val KEY_Z = kbKey("KEY_LO_Z")
    private val KEY_Q = kbKey("KEY_LO_Q")
    private val KEY_I = kbKey("KEY_LO_I")
    private val KEY_ESC = kbKey("KEY_ESC")
    private val KEY_UP = kbKey("KEY_UP")
    private val KEY_DOWN = kbKey("KEY_DOWN")
    private val KEY_LEFT = kbKey("KEY_LEFT")
    private val KEY_RIGHT = kbKey("KEY_RIGHT")

    internal fun computeHudLayout(canvasHeight: Int, questLineCount: Int): HudLayout {
        val qc = questLineCount.coerceAtLeast(1)
        val leftLines = 5
        val rightLines = qc + 4
        val leftStart = hudCenteredBlockStartY(canvasHeight, leftLines)
        val rightStart = hudCenteredBlockStartY(canvasHeight, rightLines)
        return HudLayout(
            leftStartY = leftStart,
            rightStartY = rightStart,
            rightQuestStartY = rightStart,
            rightInvRow = rightStart + qc,
            rightKeysRow = rightStart + qc + 1,
            rightDebugRow = rightStart + qc + 2,
            rightBarY = rightStart + qc + 3,
            leftLineCount = leftLines,
            rightLineCount = rightLines,
        )
    }

    /** Vertical start row for a block of [lineCount] lines, centered on [canvasHeight] (odd slack biased up). */
    internal fun hudCenteredBlockStartY(canvasHeight: Int, lineCount: Int): Int {
        val slack = (canvasHeight - lineCount).coerceAtLeast(0)
        return ((slack + 1) / 2).coerceAtLeast(1)
    }

    internal fun hudStatsLine(level: Int, hp: Int, atkDisplay: String, shieldDisplay: String): String =
        "LV $level HP:$hp ATK:$atkDisplay SHD:$shieldDisplay"

    internal fun playerCardStatsLine(atkDisplay: String, hp: Int, shieldDisplay: String): String =
        "ATK:$atkDisplay  HP:$hp  SHD:$shieldDisplay"

    internal fun resolveWallChip(currentHp: Int): WallChipResult {
        val base = if (currentHp <= 0) 2 else currentHp
        val next = base - 1
        return WallChipResult(remainingHp = next, destroyed = next <= 0)
    }

    internal fun resolveItemEffect(
        type: ItemType,
        value: Int,
        tier: KeyTier
    ): ItemEffect = when (type) {
        ItemType.HEALTH_POTION -> ItemEffect(healthDelta = value)
        ItemType.ATTACK_BOOST -> ItemEffect(attackDelta = value)
        ItemType.SHIELD -> ItemEffect(tempShieldDelta = value)
        ItemType.KEY -> ItemEffect(keyTierToAdd = tier)
        ItemType.HAND_ARMOR,
        ItemType.HELMET,
        ItemType.NECKLACE,
        ItemType.CHEST_ARMOR,
        ItemType.LEGGINGS,
        ItemType.BOOTS_ARMOR -> ItemEffect(equipType = type)
        else -> ItemEffect()
    }

    internal fun resolveCombatExchange(
        playerHealth: Int,
        enemyHealth: Int,
        enemyAttack: Int,
        playerAttack: Int,
        damageAfterEnemyAttack: (Int) -> Int
    ): CombatExchange {
        val dmgToPlayer = damageAfterEnemyAttack(enemyAttack).coerceAtLeast(0)
        val dmgToEnemy = playerAttack.coerceAtLeast(0)
        val nextPlayer = playerHealth - dmgToPlayer
        val nextEnemy = enemyHealth - dmgToEnemy
        return CombatExchange(
            playerDamageTaken = dmgToPlayer,
            enemyDamageTaken = dmgToEnemy,
            enemyDefeated = nextEnemy <= 0,
            playerDefeated = nextPlayer <= 0,
        )
    }

    internal fun planPostMoveFlow(
        prevX: Int,
        prevY: Int,
        curX: Int,
        curY: Int,
        inputDx: Int,
        inputDy: Int
    ): PostMovePlan {
        val moved = curX != prevX || curY != prevY
        val slide = when {
            !moved -> SlideKind.NONE
            inputDx != 0 -> SlideKind.COLUMN_UP
            inputDy != 0 -> SlideKind.ROW_LEFT
            else -> SlideKind.NONE
        }
        return PostMovePlan(
            moved = moved,
            slideKind = slide,
            shouldTickBombs = moved,
            shouldCheckShop = moved,
            shouldCheckLevelComplete = true,
        )
    }

    internal fun resolvePostMoveSceneRoute(
        moved: Boolean,
        onGamblingTile: Boolean,
        onShopTile: Boolean,
        reachedLevelTarget: Boolean
    ): PostMoveSceneRoute = when {
        moved && onGamblingTile -> PostMoveSceneRoute.MINIGAMES
        moved && onShopTile -> PostMoveSceneRoute.SHOP
        reachedLevelTarget -> PostMoveSceneRoute.LEVEL_COMPLETE
        else -> PostMoveSceneRoute.NONE
    }

    internal fun resolveMoveCollision(
        newX: Int,
        newY: Int,
        items: List<GridItem>,
        hasKeyForTier: (KeyTier) -> Boolean
    ): MoveResolution {
        val chest = items.find {
            it.type == ItemType.CHEST && !it.collected && !it.chestOpened &&
                it.gridX == newX && it.gridY == newY
        }
        if (chest != null) {
            return if (hasKeyForTier(chest.tier)) {
                MoveResolution(MoveCollision.CHEST_UNLOCKED, chest = chest)
            } else {
                MoveResolution(MoveCollision.CHEST_LOCKED, chest = chest)
            }
        }

        val bomb = items.find {
            it.type == ItemType.BOMB && !it.collected &&
                it.gridX == newX && it.gridY == newY
        }
        if (bomb != null) return MoveResolution(MoveCollision.BOMB, bomb = bomb)

        val wall = items.find {
            it.type == ItemType.WALL && !it.collected &&
                it.gridX == newX && it.gridY == newY
        }
        if (wall != null) return MoveResolution(MoveCollision.WALL, wall = wall)

        return MoveResolution(MoveCollision.NONE)
    }

    // Collision flash effects
    private val itemFlash = FlashEffect(CPColor(80, 255, 80, "item-flash"), 10, followPlayer = true)
    private val combatFlash = FlashEffect(CPColor(255, 60, 40, "combat-flash"), 14, followPlayer = false)

    /** Warm pulse on each bomb cell when its countdown drops (multi-cell). */
    private val bombTickFlash = MultiCellFlashEffect(CPColor(255, 210, 72, "bomb-tick"), 18)

    // Confetti bursts
    private val confetti = ConfettiEffect(maxParticles = 35, maxAge = 16)
    private val hudConfetti = ConfettiEffect(maxParticles = 55, maxAge = 18)

    /** Fire-style burst for bomb detonations (center + cross). */
    private val explosionFx = ExplosionEffect()

    private val slideAnimations = IdentityHashMap<Any, SlideAnim>()
    private const val SLIDE_DURATION_FRAMES = 12

    private class SlideAnim(
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int,
        var elapsed: Int = 0,
        val durationFrames: Int = SLIDE_DURATION_FRAMES
    ) {
        private val lastFrame = (durationFrames - 1).coerceAtLeast(0)

        fun progress(): Float =
            (elapsed.coerceAtMost(lastFrame) / lastFrame.coerceAtLeast(1).toFloat()).coerceAtMost(1f)

        fun isFinished(): Boolean = elapsed > lastFrame
        fun tick() {
            elapsed++
        }
    }

    private fun registerSlide(entity: Any, fromX: Int, fromY: Int, toX: Int, toY: Int) {
        if (fromX == toX && fromY == toY) return
        slideAnimations[entity] = SlideAnim(fromX, fromY, toX, toY)
    }

    private fun slidingScreenPos(entity: Any, baseX: Int, baseY: Int): Pair<Int, Int> {
        val anim = slideAnimations[entity] ?: return baseX to baseY
        val t = easeSlide(anim.progress())
        val x = anim.fromX + ((anim.toX - anim.fromX) * t).toInt()
        val y = anim.fromY + ((anim.toY - anim.fromY) * t).toInt()
        return x to y
    }

    private fun easeSlide(t: Float): Float {
        val c = 1f - t.coerceIn(0f, 1f)
        return 1f - c * c * c
    }

    private fun tickSlideAnimations() {
        val it = slideAnimations.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val ent = e.key
            val remove = when (ent) {
                is GridItem -> ent.collected
                is EnemyCard -> ent.defeated
                else -> true
            }
            if (remove) {
                it.remove()
                continue
            }
            e.value.tick()
            if (e.value.isFinished()) it.remove()
        }
    }

    private fun setEntityRow(e: Any, row: Int) {
        when (e) {
            is GridItem -> e.gridY = row
            is EnemyCard -> e.gridY = row
        }
    }

    private fun setEntityCol(e: Any, col: Int) {
        when (e) {
            is GridItem -> e.gridX = col
            is EnemyCard -> e.gridX = col
        }
    }

    /**
     * Removes items/enemies still logically at this cell (e.g. opened chest sharing the player’s
     * tile). Slide spawn assumes the slot is empty; without this, a new card is appended while the
     * old entity remains → overlapping art.
     */
    private fun removeEntitiesAt(gridX: Int, gridY: Int) {
        items = items.filterNot {
            it.gridX == gridX && it.gridY == gridY && !it.collected && it.type != ItemType.WALL
        }
        enemies = enemies.filterNot { it.gridX == gridX && it.gridY == gridY && !it.defeated }
    }

    /** Clears other entities occupying [gridX],[gridY] before [mover] slides there (keeps [mover]). */
    private fun removeOthersAtCell(gridX: Int, gridY: Int, mover: Any) {
        when (mover) {
            is GridItem -> {
                items = items.filterNot {
                    !it.collected && it.gridX == gridX && it.gridY == gridY &&
                        it !== mover && it.type != ItemType.WALL
                }
                enemies = enemies.filterNot { !it.defeated && it.gridX == gridX && it.gridY == gridY }
            }
            is EnemyCard -> {
                items = items.filterNot {
                    !it.collected && it.gridX == gridX && it.gridY == gridY && it.type != ItemType.WALL
                }
                enemies = enemies.filterNot {
                    !it.defeated && it.gridX == gridX && it.gridY == gridY && it !== mover
                }
            }
        }
    }

    /**
     * After a **horizontal** move (left/right): repack the vacated **column** — cards below the
     * empty cell shift up; new tiles spawn as needed. (Grid: x=0 left, y=0 top, y grows downward.)
     */
    private fun slideColumnUpAfterPlayerLeft(col: Int, vacRow: Int) {
        val px = GameState.playerGridX
        val py = GameState.playerGridY

                val freeSlots = (vacRow until GridConfig.ROWS)
            .filter { row ->
                !(col == px && row == py) &&
                    items.none { !it.collected && it.type == ItemType.WALL && it.gridX == col && it.gridY == row }
            }
            .sorted()

        val movers = mutableListOf<Any>()
        for (item in items) {
            if (!item.collected && item.type != ItemType.WALL && item.gridX == col && item.gridY > vacRow) movers.add(item)
        }
        for (enemy in enemies) {
            if (!enemy.defeated && enemy.gridX == col && enemy.gridY > vacRow) movers.add(enemy)
        }
        movers.sortBy {
            when (it) {
                is GridItem -> it.gridY
                is EnemyCard -> it.gridY
                else -> 0
            }
        }

        val n = minOf(movers.size, freeSlots.size)
        for (i in 0 until n) {
            val row = freeSlots[i]
            val m = movers[i]
            val oldRow = when (m) {
                is GridItem -> m.gridY
                is EnemyCard -> m.gridY
                else -> row
            }
            val oldSx = GridConfig.cellScreenX(col)
            val oldSy = GridConfig.cellScreenY(oldRow)
            removeOthersAtCell(col, row, m)
            setEntityRow(m, row)
            val newSx = GridConfig.cellScreenX(col)
            val newSy = GridConfig.cellScreenY(row)
            registerSlide(m, oldSx, oldSy, newSx, newSy)
        }
        for (i in n until freeSlots.size) {
            val row = freeSlots[i]
            removeEntitiesAt(col, row)
            val toX = GridConfig.cellScreenX(col)
            val toY = GridConfig.cellScreenY(row)
            val fromX = toX
            val fromY = toY + GridConfig.CELL_HEIGHT
            if (Random.nextBoolean()) {
                val newItem = LevelGenerator.randomItemAt(col, row, items, enemies)
                items = items + newItem
                registerSlide(newItem, fromX, fromY, toX, toY)
            } else {
                val newEnemy = LevelGenerator.randomEnemyAt(col, row, enemies, items)
                enemies = enemies + newEnemy
                registerSlide(newEnemy, fromX, fromY, toX, toY)
            }
        }
    }

    /**
     * After a **vertical** move (up/down): repack the vacated **row** — cards to the **right** of
     * the empty cell shift **left** (e.g. leaving bottom-left (0,2) by moving up pulls (1,2)→(0,2)).
     */
    private fun slideRowLeftAfterPlayerLeft(row: Int, vacCol: Int) {
        val px = GameState.playerGridX
        val py = GameState.playerGridY

        val freeSlots = (vacCol until GridConfig.COLS)
            .filter { col ->
                !(col == px && row == py) &&
                    items.none { !it.collected && it.type == ItemType.WALL && it.gridX == col && it.gridY == row }
            }
            .sorted()

        val movers = mutableListOf<Any>()
        for (item in items) {
            if (!item.collected && item.type != ItemType.WALL && item.gridY == row && item.gridX > vacCol) movers.add(item)
        }
        for (enemy in enemies) {
            if (!enemy.defeated && enemy.gridY == row && enemy.gridX > vacCol) movers.add(enemy)
        }
        movers.sortBy {
            when (it) {
                is GridItem -> it.gridX
                is EnemyCard -> it.gridX
                else -> 0
            }
        }

        val n = minOf(movers.size, freeSlots.size)
        for (i in 0 until n) {
            val col = freeSlots[i]
            val m = movers[i]
            val oldCol = when (m) {
                is GridItem -> m.gridX
                is EnemyCard -> m.gridX
                else -> col
            }
            val oldSx = GridConfig.cellScreenX(oldCol)
            val oldSy = GridConfig.cellScreenY(row)
            removeOthersAtCell(col, row, m)
            setEntityCol(m, col)
            val newSx = GridConfig.cellScreenX(col)
            val newSy = GridConfig.cellScreenY(row)
            registerSlide(m, oldSx, oldSy, newSx, newSy)
        }
        for (i in n until freeSlots.size) {
            val col = freeSlots[i]
            removeEntitiesAt(col, row)
            val toX = GridConfig.cellScreenX(col)
            val toY = GridConfig.cellScreenY(row)
            val fromX = toX + GridConfig.CELL_WIDTH
            val fromY = toY
            if (Random.nextBoolean()) {
                val newItem = LevelGenerator.randomItemAt(col, row, items, enemies)
                items = items + newItem
                registerSlide(newItem, fromX, fromY, toX, toY)
            } else {
                val newEnemy = LevelGenerator.randomEnemyAt(col, row, enemies, items)
                enemies = enemies + newEnemy
                registerSlide(newEnemy, fromX, fromY, toX, toY)
            }
        }
    }

    fun create(): CPScene {
        slideAnimations.clear()
        val initial = LevelGenerator.fillAllCellsExcept(Pair(0, 0))
        items = initial.first
        enemies = initial.second

        val noShaders = emptyScalaSeq()
        val tags = emptyStringSet()

        val effectSprite = createEffectSprite(noShaders, tags)
        val gridSprite = createGridSprite(noShaders, tags)
        val playerSprite = createPlayerSprite(noShaders, tags)
        val hudSprite = createHudSprite(noShaders, tags)

        return CPScene(
            "game",
            Option.empty(),  // adaptive to terminal size
            bgPx,
            scalaSeqOf(gridSprite, playerSprite, hudSprite, effectSprite)
        )
    }

    private fun px(ch: Char, fg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(BG_COLOR), 0)

    private fun px(ch: Char, fg: CPColor, bg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(bg), 0)

    private fun leftHudSpanWidth(gridStartX: Int): Int =
        (gridStartX - GridConfig.HUD_GAP_BEFORE_GRID).coerceAtLeast(0)

    private fun rightHudStartX(gridStartX: Int): Int =
        gridStartX + GridConfig.GRID_TOTAL_WIDTH + GridConfig.HUD_GAP_BEFORE_GRID

    private fun rightHudSpanWidth(canvasWidth: Int, gridStartX: Int): Int =
        (canvasWidth - rightHudStartX(gridStartX)).coerceAtLeast(0)

    /** Horizontally center a line of length [textLen] in [spanLeft]..+[spanWidth]. */
    private fun hudXCenteredInSpan(spanLeft: Int, spanWidth: Int, textLen: Int): Int {
        if (spanWidth <= 0) return spanLeft
        return spanLeft + ((spanWidth - textLen).coerceAtLeast(0) + 1) / 2
    }

    private fun hudLineFit(s: String, max: Int): String {
        if (max <= 0) return ""
        if (s.length <= max) return s
        if (max <= 1) return s.take(max)
        return s.take(max - 1) + "…"
    }

    /** Pixel width of item/enemy ASCII (matches [AsciiArt] lines; keeps art inside the card). */
    private val artPixelWidth = 30

    /** Draw a line of text char-by-char onto the canvas. */
    private fun drawArt(
        canv: CPCanvas, art: List<String>, startX: Int, startY: Int, z: Int, fg: CPColor
    ) {
        for ((row, line) in art.withIndex()) {
            val clipped = line.take(artPixelWidth)
            for ((col, ch) in clipped.withIndex()) {
                if (ch != ' ') {
                    canv.drawPixel(px(ch, fg), startX + col, startY + row, z)
                }
            }
        }
    }

    /** Trim ASCII art if the cell is shorter than the asset (title + stats rows reserved). */
    private fun clippedArt(art: List<String>): List<String> {
        val max = (GridConfig.CELL_HEIGHT - 4).coerceAtLeast(3)
        return art.take(max)
    }

    /** Centers ASCII vertically between the title row and the bottom stats row. */
    private fun artCenterY(sy: Int, lineCount: Int): Int {
        val h = GridConfig.CELL_HEIGHT
        val topRow = sy + 2
        val bottomRow = sy + h - 3
        val maxRows = bottomRow - topRow + 1
        val lc = lineCount.coerceIn(1, maxRows)
        val slack = maxRows - lc
        // Put odd slack above the art so sprites don’t sit high with empty space under them.
        return topRow + (slack + 1) / 2
    }

    /** Draw a card border (box-drawing chars). */
    private fun drawCardBorder(
        canv: CPCanvas, sx: Int, sy: Int, w: Int, h: Int, z: Int, color: CPColor
    ) {
        // Corners
        canv.drawPixel(px('+', color), sx, sy, z)
        canv.drawPixel(px('+', color), sx + w - 1, sy, z)
        canv.drawPixel(px('+', color), sx, sy + h - 1, z)
        canv.drawPixel(px('+', color), sx + w - 1, sy + h - 1, z)
        // Top/bottom
        for (x in sx + 1 until sx + w - 1) {
            canv.drawPixel(px('-', color), x, sy, z)
            canv.drawPixel(px('-', color), x, sy + h - 1, z)
        }
        // Left/right
        for (y in sy + 1 until sy + h - 1) {
            canv.drawPixel(px('|', color), sx, y, z)
            canv.drawPixel(px('|', color), sx + w - 1, y, z)
        }
    }

    /** Center a string inside a given width, returning the x offset. */
    private fun centerX(text: String, sx: Int, w: Int): Int =
        sx + (w - text.length) / 2

    private fun lerpColor(a: CPColor, b: CPColor, t: Float): CPColor {
        val tt = t.coerceIn(0f, 1f)
        return CPColor(
            (a.red() + (b.red() - a.red()) * tt).toInt().coerceIn(0, 255),
            (a.green() + (b.green() - a.green()) * tt).toInt().coerceIn(0, 255),
            (a.blue() + (b.blue() - a.blue()) * tt).toInt().coerceIn(0, 255),
            "lerp"
        )
    }

    private fun tickBombs(ctx: CPSceneObjectContext) {
        val tickedCells = mutableListOf<Pair<Int, Int>>()
        for (item in items) {
            if (item.type != ItemType.BOMB || item.collected || item.bombTicks <= 0) continue
            tickedCells.add(item.gridX to item.gridY)
            item.bombTicks--
        }
        for ((gx, gy) in tickedCells) {
            bombTickFlash.flash(gx, gy)
        }
        val toDetonate = items.filter { it.type == ItemType.BOMB && !it.collected && it.bombTicks == 0 }
        for (b in toDetonate) {
            if (b.collected) continue
            explodeBomb(b, ctx)
            if (GameState.gameOver) return
        }
    }

    private fun explodeBomb(bomb: GridItem, ctx: CPSceneObjectContext) {
        explosionFx.spawnCrossExplosion(bomb.gridX, bomb.gridY)
        bomb.collected = true
        val bx = bomb.gridX
        val by = bomb.gridY
        val dirs = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
        for ((dx, dy) in dirs) {
            val nx = bx + dx
            val ny = by + dy
            if (nx !in 0 until GridConfig.COLS || ny !in 0 until GridConfig.ROWS) continue
            if (GameState.playerGridX == nx && GameState.playerGridY == ny) {
                GameState.gameOver = true
                GameState.playerHealth = 0
                GameState.runEndKind = RunEndKind.DEATH
                ctx.switchScene("runsummary", false)
                return
            }
            for (enemy in enemies) {
                if (!enemy.defeated && enemy.gridX == nx && enemy.gridY == ny) {
                    GameState.registerEnemyDefeat(enemy.kind, enemy.isElite)
                    RunStats.recordEnemyKill(enemy.kind, enemy.isElite)
                    enemy.defeated = true
                }
            }
            for (it in items) {
                if (!it.collected && it.gridX == nx && it.gridY == ny) {
                    it.collected = true
                }
            }
        }
    }

    private fun createGridSprite(
        shaders: scala.collection.immutable.Seq<CPShader>,
        tags: scala.collection.immutable.Set<String>
    ): CPSceneObject {
        return object : CPCanvasSprite("grid", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                tickSlideAnimations()
            }

            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val gc = GridConfig

                // Center grid on screen each frame
                gc.updateOffsets(canv.width(), canv.height())

                val emptyBorder = CPColor(50, 50, 70, "empty-border")

                // Draw empty cell borders
                for (row in 0 until gc.ROWS) {
                    for (col in 0 until gc.COLS) {
                        val sx = gc.cellScreenX(col)
                        val sy = gc.cellScreenY(row)
                        drawCardBorder(canv, sx, sy, gc.CELL_WIDTH, gc.CELL_HEIGHT, 0, emptyBorder)
                    }
                }

                // Draw items
                for (item in items) {
                    if (item.collected) continue
                    val (sx, sy) = slidingScreenPos(
                        item,
                        gc.cellScreenX(item.gridX),
                        gc.cellScreenY(item.gridY)
                    )
                    val color = CardArt.itemTileColor(item)
                    val dimColor = color.transformRGB(0.5f)

                    drawCardBorder(canv, sx, sy, gc.CELL_WIDTH, gc.CELL_HEIGHT, 1, color)

                    // Item name at top
                    val title = when (item.type) {
                        ItemType.KEY -> item.tier.name
                        ItemType.CHEST -> "${item.tier.name} CHEST"
                        ItemType.SPIKES -> "DEADLY"
                        ItemType.BOMB -> "BOMB"
                        ItemType.WALL -> "WALL"
                        ItemType.QUEST -> "QUEST"
                        ItemType.REST -> "REST POINT"
                        ItemType.HAND_ARMOR -> "GUARD"
                        ItemType.HELMET -> "HELM"
                        ItemType.NECKLACE -> "NECK"
                        ItemType.CHEST_ARMOR -> "ARMOR"
                        ItemType.LEGGINGS -> "LEGS"
                        ItemType.BOOTS_ARMOR -> "BOOT"
                        else -> item.type.label
                    }.take(gc.CELL_WIDTH - 2)
                    val tx = centerX(title, sx, gc.CELL_WIDTH)
                    for ((i, ch) in title.withIndex()) {
                        canv.drawPixel(px(ch, color), tx + i, sy + 1, 1)
                    }
                    if (GameState.debugRevealSecrets && item.secretRoom) {
                        drawCardBorder(canv, sx, sy, gc.CELL_WIDTH, gc.CELL_HEIGHT, 2, CPColor.C_GOLD1())
                        canv.drawPixel(px('?', CPColor.C_GOLD1()), sx + gc.CELL_WIDTH - 3, sy + 1, 1)
                    }

                    val art = if (item.type == ItemType.CHEST && item.chestOpened) {
                        clippedArt(AsciiArt.MONEY_PILE)
                    } else {
                        clippedArt(CardArt.itemSprite(item))
                    }
                    val artX = sx + (gc.CELL_WIDTH - artPixelWidth) / 2
                    val artY = artCenterY(sy, art.size)
                    drawArt(canv, art, artX, artY, 1, color)

                    val label = when {
                        item.type == ItemType.CHEST && !item.chestOpened ->
                            hudLineFit("NEED ${item.tier.name} KEY  ${item.value} GP", gc.CELL_WIDTH - 2)
                        item.type == ItemType.CHEST && !item.chestGoldClaimed ->
                            hudLineFit("${item.value} GP  STEP", gc.CELL_WIDTH - 2)
                        item.type == ItemType.CHEST ->
                            ""
                        item.type == ItemType.KEY -> item.tier.name
                        item.type == ItemType.SPIKES -> "!!"
                        item.type == ItemType.BOMB -> "▶ ${item.bombTicks}"
                        item.type == ItemType.WALL -> "BRK ${item.wallHp.coerceAtLeast(1)}"
                        item.type == ItemType.QUEST -> "STEP"
                        item.type == ItemType.REST -> "HEAL"
                        item.type == ItemType.SHOP || item.type == ItemType.GAMBLING -> "STEP"
                        item.type.equipmentSlot() != null -> "EQUIP"
                        else -> "+${item.value}"
                    }
                    val labelRow = sy + gc.CELL_HEIGHT - 2
                    if (item.type == ItemType.BOMB) {
                        val prefix = "▶ "
                        val numStr = "${item.bombTicks}"
                        val full = prefix + numStr
                        val lx = centerX(full, sx, gc.CELL_WIDTH)
                        val pulse = bombTickFlash.intensityAt(item.gridX, item.gridY)
                        val digitHot = CPColor(255, 245, 120, "bomb-digit")
                        val arrowHot = CPColor(255, 200, 80, "bomb-arrow")
                        for ((i, ch) in prefix.withIndex()) {
                            val c = lerpColor(dimColor, arrowHot, pulse * 0.9f)
                            canv.drawPixel(px(ch, c), lx + i, labelRow, 1)
                        }
                        for ((i, ch) in numStr.withIndex()) {
                            val c = lerpColor(dimColor, digitHot, pulse * 0.95f)
                            canv.drawPixel(px(ch, c), lx + prefix.length + i, labelRow, 1)
                        }
                        if (pulse > 0.12f) {
                            val glow = lerpColor(dimColor, CPColor(255, 255, 255, "glow"), pulse * 0.55f)
                            for ((i, ch) in numStr.withIndex()) {
                                if (ch.isDigit()) {
                                    canv.drawPixel(px(ch, glow), lx + prefix.length + i, labelRow - 1, 1)
                                }
                            }
                        }
                    } else {
                        val lx = centerX(label, sx, gc.CELL_WIDTH)
                        for ((i, ch) in label.withIndex()) {
                            canv.drawPixel(px(ch, dimColor), lx + i, labelRow, 1)
                        }
                    }
                }

                // Draw enemies
                for (enemy in enemies) {
                    if (enemy.defeated) continue
                    val (sx, sy) = slidingScreenPos(
                        enemy,
                        gc.cellScreenX(enemy.gridX),
                        gc.cellScreenY(enemy.gridY)
                    )
                    val nameColor = if (enemy.isElite) CPColor.C_GOLD1() else CPColor.C_INDIAN_RED1()
                    val borderColor = if (enemy.isElite) CPColor.C_ORANGE1() else CPColor.C_INDIAN_RED1()
                    val artColor = CPColor.C_RED1()
                    val statsColor = if (enemy.isElite) CPColor.C_GOLD1() else CPColor.C_ORANGE_RED1()

                    drawCardBorder(canv, sx, sy, gc.CELL_WIDTH, gc.CELL_HEIGHT, 1, borderColor)

                    // Name at top
                    val rawTitle =
                        if (enemy.isElite) "ELITE ${enemy.kind.displayName}" else enemy.kind.displayName
                    val name = rawTitle.take(gc.CELL_WIDTH - 2)
                    val nx = centerX(name, sx, gc.CELL_WIDTH)
                    for ((i, ch) in name.withIndex()) {
                        canv.drawPixel(px(ch, nameColor), nx + i, sy + 1, 1)
                    }
                    if (GameState.debugRevealSecrets && enemy.secretRoom) {
                        drawCardBorder(canv, sx, sy, gc.CELL_WIDTH, gc.CELL_HEIGHT, 2, CPColor.C_GOLD1())
                        canv.drawPixel(px('?', CPColor.C_GOLD1()), sx + gc.CELL_WIDTH - 3, sy + 1, 1)
                    }

                    val art = clippedArt(CardArt.enemySprite(enemy.kind))
                    val artX = sx + (gc.CELL_WIDTH - (art.firstOrNull()?.length ?: 0)) / 2
                    val artY = artCenterY(sy, art.size)
                    drawArt(canv, art, artX, artY, 1, artColor)

                    val stats =
                        if (enemy.isElite) "★ ATK:${enemy.attack}  HP:${enemy.health}"
                        else "ATK:${enemy.attack}  HP:${enemy.health}"
                    val stx = centerX(stats, sx, gc.CELL_WIDTH)
                    for ((i, ch) in stats.withIndex()) {
                        canv.drawPixel(px(ch, statsColor), stx + i, sy + gc.CELL_HEIGHT - 2, 1)
                    }
                }
            }
        }
    }

    private fun createPlayerSprite(
        shaders: scala.collection.immutable.Seq<CPShader>,
        tags: scala.collection.immutable.Set<String>
    ): CPSceneObject {
        return object : CPCanvasSprite("player", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (GameState.gameOver) return

                val evt = ctx.kbEvent
                if (!evt.isDefined) return

                val key = evt.get().key()
                var dx = 0
                var dy = 0

                when (key) {
                    KEY_Z -> {
                        GameState.debugRevealSecrets = !GameState.debugRevealSecrets
                        return
                    }
                    KEY_I -> {
                        GameState.inventoryReturnScene = "game"
                        ctx.switchScene("inventory", false)
                        return
                    }
                    KEY_W, KEY_UP -> dy = -1
                    KEY_S, KEY_DOWN -> dy = 1
                    KEY_A, KEY_LEFT -> dx = -1
                    KEY_D, KEY_RIGHT -> dx = 1
                    KEY_Q, KEY_ESC -> { ctx.exitGame(); return }
                    else -> {}
                }

                if (dx == 0 && dy == 0) return
                GameState.onPlayerMovementActionStart()

                val prevX = GameState.playerGridX
                val prevY = GameState.playerGridY
                val newX = (prevX + dx).coerceIn(0, GridConfig.COLS - 1)
                val newY = (prevY + dy).coerceIn(0, GridConfig.ROWS - 1)

                val moveResolution = resolveMoveCollision(
                    newX = newX,
                    newY = newY,
                    items = items,
                    hasKeyForTier = { tier -> GameState.hasKey(tier) }
                )
                when (moveResolution.collision) {
                    MoveCollision.CHEST_LOCKED -> {
                        return
                    }
                    MoveCollision.CHEST_UNLOCKED -> {
                        val chest = moveResolution.chest ?: return
                        GameState.tryConsumeKey(chest.tier)
                        chest.chestOpened = true
                        itemFlash.flash(newX, newY)
                        confetti.spawn(newX, newY)
                        return
                    }
                    MoveCollision.BOMB -> {
                        return
                    }
                    MoveCollision.WALL -> {
                        val wallBlocking = moveResolution.wall ?: return
                        val chip = resolveWallChip(wallBlocking.wallHp)
                        wallBlocking.wallHp = chip.remainingHp
                        GameState.applyWallChipPenalty()
                        itemFlash.flash(newX, newY)
                        if (chip.destroyed) {
                            wallBlocking.collected = true
                            confetti.spawn(newX, newY)
                        }
                        tickBombs(ctx)
                        if (GameState.gameOver) return
                        return
                    }
                    MoveCollision.NONE -> {}
                }

                GameState.playerGridX = newX
                GameState.playerGridY = newY

                val spikeHere = items.find {
                    it.type == ItemType.SPIKES && !it.collected &&
                        it.gridX == newX && it.gridY == newY
                }
                if (spikeHere != null) {
                    GameState.gameOver = true
                    GameState.playerHealth = 0
                    GameState.runEndKind = RunEndKind.DEATH
                    ctx.switchScene("runsummary", false)
                    return
                }

                val secretHere = items.find {
                    !it.collected && it.secretRoom && it.gridX == newX && it.gridY == newY
                }
                if (secretHere != null && GameState.hasAnyKey()) {
                    GameState.consumeAnyKey()
                    secretHere.collected = true
                    itemFlash.flash(newX, newY)
                    confetti.spawn(newX, newY)
                    RunStats.recordSecretRoom()
                    kotlin.runCatching { ctx.deleteScene("secretroom") }
                    ctx.addScene(SecretRoomScene.create(), false, false, false)
                    ctx.switchScene("secretroom", false)
                    return
                }

                // Item pickup (chest gold is collected when stepping onto an opened chest)
                for (item in items) {
                    if (!item.collected && item.gridX == newX && item.gridY == newY) {
                        if (item.type == ItemType.CHEST && item.chestOpened && !item.chestGoldClaimed) {
                            GameState.addMoney(item.value)
                            item.chestGoldClaimed = true
                            item.collected = true
                            itemFlash.flash(newX, newY)
                            confetti.spawn(newX, newY)
                            continue
                        }
                        if (item.type == ItemType.CHEST || item.type == ItemType.SHOP || item.type == ItemType.GAMBLING) continue
                        if (item.type == ItemType.SPIKES || item.type == ItemType.BOMB || item.type == ItemType.WALL) continue
                        if (item.type == ItemType.QUEST) {
                            item.collected = true
                            GameState.pendingQuestOffer = QuestSystem.randomOffer(
                                excludeQuestIds = GameState.activeIncompleteQuestTemplateIds(),
                                completedQuestIds = GameState.completedQuestIds(),
                                currentLevel = GameState.currentLevel,
                            )
                            itemFlash.flash(newX, newY)
                            confetti.spawn(newX, newY)
                            ctx.switchScene("quest", false)
                            return
                        }
                        if (item.type == ItemType.REST) {
                            item.collected = true
                            itemFlash.flash(newX, newY)
                            confetti.spawn(newX, newY)
                            kotlin.runCatching { ctx.deleteScene("rest") }
                            ctx.addScene(RestScene.create(), false, false, false)
                            ctx.switchScene("rest", false)
                            return
                        }
                        item.collected = true
                        val effect = resolveItemEffect(item.type, item.value, item.tier)
                        GameState.playerHealth += effect.healthDelta
                        GameState.playerAttack += effect.attackDelta
                        if (effect.tempShieldDelta > 0) GameState.addTemporaryShield(effect.tempShieldDelta)
                        effect.keyTierToAdd?.let { GameState.addKey(it) }
                        effect.equipType?.let { eq ->
                            val slot = eq.equipmentSlot() ?: return@let
                            GameState.setEquippedItem(slot, eq)
                        }
                        itemFlash.flash(newX, newY)
                        confetti.spawn(newX, newY)
                    }
                }

                // Enemy combat
                for (enemy in enemies) {
                    if (!enemy.defeated && enemy.gridX == newX && enemy.gridY == newY) {
                        if (enemy.secretRoom) {
                            if (GameState.hasAnyKey()) GameState.consumeAnyKey()
                            enemy.defeated = true
                            itemFlash.flash(newX, newY)
                            confetti.spawn(newX, newY)
                            RunStats.recordSecretRoom()
                            kotlin.runCatching { ctx.deleteScene("secretroom") }
                            ctx.addScene(SecretRoomScene.create(), false, false, false)
                            ctx.switchScene("secretroom", false)
                            return
                        }
                        val exchange = resolveCombatExchange(
                            playerHealth = GameState.playerHealth,
                            enemyHealth = enemy.health,
                            enemyAttack = enemy.attack,
                            playerAttack = GameState.effectivePlayerAttack(),
                            damageAfterEnemyAttack = { atk -> GameState.damageAfterEnemyAttack(atk) }
                        )
                        GameState.playerHealth -= exchange.playerDamageTaken
                        enemy.health -= exchange.enemyDamageTaken
                        combatFlash.flash(newX, newY)

                        if (exchange.enemyDefeated) {
                            enemy.defeated = true
                            GameState.registerEnemyDefeat(enemy.kind, enemy.isElite)
                            RunStats.recordEnemyKill(enemy.kind, enemy.isElite)
                            GameState.score += if (enemy.isElite) 40 else 25
                            if (enemy.isElite) {
                                if (Random.nextBoolean()) {
                                    GameState.addMoney(Random.nextInt(15, 32))
                                } else {
                                    GameState.addKey(KeyTier.entries.random())
                                }
                            }
                            confetti.spawn(newX, newY)
                        }

                        if (exchange.playerDefeated) {
                            GameState.playerHealth = 0
                            GameState.gameOver = true
                            GameState.runEndKind = RunEndKind.DEATH
                            ctx.switchScene("runsummary", false)
                            return
                        }

                        if (!enemy.defeated) {
                            GameState.playerGridX = (newX - dx).coerceIn(0, GridConfig.COLS - 1)
                            GameState.playerGridY = (newY - dy).coerceIn(0, GridConfig.ROWS - 1)
                        }
                    }
                }

                val px = GameState.playerGridX
                val py = GameState.playerGridY
                val postMove = planPostMoveFlow(prevX, prevY, px, py, dx, dy)
                if (postMove.moved) {
                    when (postMove.slideKind) {
                        SlideKind.COLUMN_UP -> slideColumnUpAfterPlayerLeft(prevX, prevY)
                        SlideKind.ROW_LEFT -> slideRowLeftAfterPlayerLeft(prevY, prevX)
                        SlideKind.NONE -> {}
                    }
                }

                if (postMove.shouldTickBombs) {
                    tickBombs(ctx)
                    if (GameState.gameOver) return
                }

                val onGamblingTile = postMove.shouldCheckShop && items.any {
                    it.type == ItemType.GAMBLING && !it.collected &&
                        it.gridX == GameState.playerGridX && it.gridY == GameState.playerGridY
                }
                val onShopTile = postMove.shouldCheckShop && items.any {
                    it.type == ItemType.SHOP && !it.collected &&
                        it.gridX == GameState.playerGridX && it.gridY == GameState.playerGridY
                }
                val reachedLevelTarget =
                    postMove.shouldCheckLevelComplete &&
                        GameState.score >= LevelConfig.targetScore(GameState.currentLevel)
                when (resolvePostMoveSceneRoute(postMove.moved, onGamblingTile, onShopTile, reachedLevelTarget)) {
                    PostMoveSceneRoute.MINIGAMES -> {
                        GameState.minigamesReturnScene = "game"
                        ctx.switchScene("minigames", false)
                        return
                    }
                    PostMoveSceneRoute.SHOP -> {
                        GameState.shopReturnScene = "game"
                        kotlin.runCatching { ctx.deleteScene("shop") }
                        ctx.addScene(ShopScene.create(), false, false, false)
                        ctx.switchScene("shop", false)
                        return
                    }
                    PostMoveSceneRoute.LEVEL_COMPLETE -> {
                        Progress.onLevelCleared(GameState.currentLevel)
                        ctx.switchScene("levelcomplete", false)
                        return
                    }
                    PostMoveSceneRoute.NONE -> {}
                }
            }

            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val gc = GridConfig
                val sx = gc.cellScreenX(GameState.playerGridX)
                val sy = gc.cellScreenY(GameState.playerGridY)
                val hlColor = CPColor(25, 30, 55, "hl")
                val gold = CPColor.C_GOLD1()
                val blue = CPColor.C_STEEL_BLUE1()

                // Fill player cell background
                for (y in sy + 1 until sy + gc.CELL_HEIGHT - 1) {
                    for (x in sx + 1 until sx + gc.CELL_WIDTH - 1) {
                        canv.drawPixel(px(' ', CPColor.C_WHITE(), hlColor), x, y, 2)
                    }
                }

                // Card border
                drawCardBorder(canv, sx, sy, gc.CELL_WIDTH, gc.CELL_HEIGHT, 3, gold)

                val title = "~ ${GameState.selectedPlayerCharacter.label.uppercase()} ~"
                val tx = centerX(title, sx, gc.CELL_WIDTH)
                for ((i, ch) in title.withIndex()) {
                    canv.drawPixel(px(ch, gold, hlColor), tx + i, sy + 1, 4)
                }

                val pc = GameState.selectedPlayerCharacter
                val art = clippedArt(AsciiArt.playerLines(pc))
                val artX = sx + (gc.CELL_WIDTH - (art.firstOrNull()?.length ?: 0)) / 2
                val artY = artCenterY(sy, art.size)
                for ((row, line) in art.withIndex()) {
                    for ((col, ch) in line.withIndex()) {
                        if (ch != ' ') {
                            canv.drawPixel(px(ch, pc.spriteColor, hlColor), artX + col, artY + row, 4)
                        }
                    }
                }

                val stats =
                    playerCardStatsLine(
                        atkDisplay = GameState.playerAttackDisplay(),
                        hp = GameState.playerHealth,
                        shieldDisplay = GameState.playerShieldDisplay(),
                    )
                val stx = centerX(stats, sx, gc.CELL_WIDTH)
                for ((i, ch) in stats.withIndex()) {
                    canv.drawPixel(px(ch, blue, hlColor), stx + i, sy + gc.CELL_HEIGHT - 2, 4)
                }
            }
        }
    }

    private fun createHudSprite(
        shaders: scala.collection.immutable.Seq<CPShader>,
        tags: scala.collection.immutable.Set<String>
    ): CPSceneObject {
        return object : CPCanvasSprite("hud", shaders, tags) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()
                GridConfig.updateOffsets(w, h)
                val gx = GridConfig.offsetX
                val leftSpan = leftHudSpanWidth(gx)
                val rightX0 = rightHudStartX(gx)
                val rightSpan = rightHudSpanWidth(w, gx)
                val leftMax = leftSpan.coerceAtLeast(8)
                val rightMax = rightSpan.coerceAtLeast(8)

                val questLinesRaw = GameState.questHudLines()
                val layout = computeHudLayout(h, questLinesRaw.size)
                val questLines = questLinesRaw.map { hudLineFit(it, rightMax) }
                val hudZ = 15 // above grid/player (≤4) and confetti (10)

                val goal = LevelConfig.targetScore(GameState.currentLevel)
                val ly = layout.leftStartY

                val line1 = hudLineFit("CARD CRAWLER", leftMax)
                val loreLine = hudLineFit(LevelConfig.hudLore(GameState.currentLevel), leftMax)
                val line2 = hudLineFit(
                    hudStatsLine(
                        level = GameState.currentLevel,
                        hp = GameState.playerHealth,
                        atkDisplay = GameState.playerAttackDisplay(),
                        shieldDisplay = GameState.playerShieldDisplay(),
                    ),
                    leftMax,
                )
                val line3 = hudLineFit("SCORE ${GameState.score} / $goal", leftMax)
                val line4 = hudLineFit("GOLD ${GameState.money}", leftMax)
                val dbgSecret = run {
                    val secretItem = items.firstOrNull { !it.collected && it.secretRoom }
                    val secretEnemy = enemies.firstOrNull { !it.defeated && it.secretRoom }
                    when {
                        secretItem != null -> "DBG:I ${secretItem.gridX},${secretItem.gridY}"
                        secretEnemy != null -> "DBG:E ${secretEnemy.gridX},${secretEnemy.gridY}"
                        else -> "DBG:none"
                    }
                }
                val invHint = hudLineFit("I — open inventory", rightMax)
                val lineControls = hudLineFit(
                    if (GameState.debugRevealSecrets) "WASD Z-dbg Q/Esc  $dbgSecret"
                    else "WASD move  Z debug  Q/Esc quit",
                    rightMax,
                )

                val bg = Option.apply(BG_COLOR)
                val blue = CPColor.C_STEEL_BLUE1()
                val goldHud = CPColor.C_GOLD1()
                canv.drawString(hudXCenteredInSpan(0, leftSpan, line1.length), ly, hudZ, line1, CPColor.C_GOLD1(), bg)
                canv.drawString(hudXCenteredInSpan(0, leftSpan, loreLine.length), ly + 1, hudZ, loreLine, CPColor.C_GREY70(), bg)
                canv.drawString(hudXCenteredInSpan(0, leftSpan, line2.length), ly + 2, hudZ, line2, blue, bg)
                canv.drawString(hudXCenteredInSpan(0, leftSpan, line3.length), ly + 3, hudZ, line3, blue, bg)
                canv.drawString(hudXCenteredInSpan(0, leftSpan, line4.length), ly + 4, hudZ, line4, goldHud, bg)
                val questColor = when {
                    GameState.questFlashText() != null -> CPColor.C_GOLD1()
                    else -> CPColor(165, 120, 255, "quest-hud")
                }
                val qy = layout.rightQuestStartY
                for ((i, qLine) in questLines.withIndex()) {
                    canv.drawString(
                        hudXCenteredInSpan(rightX0, rightSpan, qLine.length),
                        qy + i,
                        hudZ,
                        qLine,
                        questColor,
                        bg,
                    )
                }
                canv.drawString(
                    hudXCenteredInSpan(rightX0, rightSpan, invHint.length),
                    layout.rightInvRow,
                    hudZ,
                    invHint,
                    CPColor.C_STEEL_BLUE1(),
                    bg,
                )

                val keySeg1 = "KEYS "
                val keySeg2 = "${GameState.keysBronze}B "
                val keySeg3 = "${GameState.keysSilver}S "
                val keySeg4 = "${GameState.keysGold}G"
                val keyLine = keySeg1 + keySeg2 + keySeg3 + keySeg4
                val keyStart = hudXCenteredInSpan(rightX0, rightSpan, keyLine.length)
                var kx = keyStart
                val keysRow = layout.rightKeysRow
                canv.drawString(kx, keysRow, hudZ, keySeg1, CPColor.C_GREY70(), bg)
                kx += keySeg1.length
                canv.drawString(kx, keysRow, hudZ, keySeg2, KeyTier.BRONZE.metalColor(), bg)
                kx += keySeg2.length
                canv.drawString(kx, keysRow, hudZ, keySeg3, KeyTier.SILVER.metalColor(), bg)
                kx += keySeg3.length
                canv.drawString(kx, keysRow, hudZ, keySeg4, KeyTier.GOLD.metalColor(), bg)
                val barY = layout.rightBarY
                canv.drawString(
                    hudXCenteredInSpan(rightX0, rightSpan, lineControls.length),
                    layout.rightDebugRow,
                    hudZ,
                    lineControls,
                    CPColor.C_GREY50(),
                    bg,
                )
                val hpPrefix = "HP "
                val maxBar = (rightSpan - hpPrefix.length).coerceIn(8, 40)
                val barBlockW = hpPrefix.length + maxBar
                val hpStartX = hudXCenteredInSpan(rightX0, rightSpan, barBlockW)
                canv.drawString(hpStartX, barY, hudZ, hpPrefix, CPColor.C_WHITE(), bg)
                val filled = (GameState.playerHealth.coerceAtMost(30).toFloat() / 30 * maxBar).toInt()
                val barColor = when {
                    GameState.playerHealth > 15 -> CPColor.C_GREEN1()
                    GameState.playerHealth > 7 -> CPColor.C_YELLOW1()
                    else -> CPColor.C_RED1()
                }
                for (i in 0 until maxBar) {
                    val barChar = if (i < filled) '#' else '-'
                    canv.drawPixel(px(barChar, barColor), hpStartX + hpPrefix.length + i, barY, hudZ)
                }
            }
        }
    }

    private fun createEffectSprite(
        shaders: scala.collection.immutable.Seq<CPShader>,
        tags: scala.collection.immutable.Set<String>
    ): CPSceneObject {
        return object : CPCanvasSprite("effects", shaders, tags) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                confetti.update()
                hudConfetti.update()
                explosionFx.update()
                GameState.tickQuestHudFlash()
                if (GameState.consumeHudQuestCelebrate()) {
                    val canv = ctx.canvas
                    val w = canv.width()
                    GridConfig.updateOffsets(w, canv.height())
                    val gx = GridConfig.offsetX
                    val rightX0 = gx + GridConfig.GRID_TOTAL_WIDTH + GridConfig.HUD_GAP_BEFORE_GRID
                    val rightSpan = (w - rightX0).coerceAtLeast(0)
                    val centerX = rightX0 + (rightSpan + 1) / 2
                    val questLines = GameState.questHudLines()
                    val layout = computeHudLayout(canv.height(), questLines.size)
                    val centerY = layout.rightQuestStartY + ((questLines.size - 1).coerceAtLeast(0) / 2)
                    hudConfetti.spawnScreen(centerX, centerY)
                }
            }

            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                itemFlash.drawFlash(canv)
                combatFlash.drawFlash(canv)
                bombTickFlash.drawFlash(canv)
                confetti.draw(canv)
                hudConfetti.draw(canv)
                explosionFx.draw(canv)
            }
        }
    }
}
