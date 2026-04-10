package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.art.CardArt
import com.cardgame.art.metalColor
import com.cardgame.effects.*
import com.cardgame.game.*
import com.cardgame.quest.QuestSystem
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import org.cosplay.*
import scala.Option

object GameScene {
    /** Row indices within the unified HUD text block (above the deck). */
    internal data class HudLayout(
        val totalLineCount: Int,
        val questBlockStartLine: Int,
        val inventoryLineIndex: Int,
        val keysLineIndex: Int,
        val controlsLineIndex: Int,
        val hpBarLineIndex: Int,
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

    internal fun computeHudLayout(
        @Suppress("UNUSED_PARAMETER") canvasHeight: Int,
        questLineCount: Int,
    ): HudLayout {
        val qc = questLineCount.coerceAtLeast(1)
        val meta = 5
        val footer = 4
        return HudLayout(
            totalLineCount = meta + qc + footer,
            questBlockStartLine = meta,
            inventoryLineIndex = meta + qc,
            keysLineIndex = meta + qc + 1,
            controlsLineIndex = meta + qc + 2,
            hpBarLineIndex = meta + qc + 3,
        )
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

    /**
     * Repacks the column/row after the player leaves a cell and ticks bombs — same as the main move tail.
     * Must run whenever the player actually moved but we leave the scene before the normal [planPostMoveFlow] block
     * (quest offer, rest, secret room); otherwise vacated slots never fill with new cards.
     * @return true if bombs ended the run ([GameState.gameOver]); caller should return without switching scenes.
     */
    private fun applyPostMoveSlidingAndBombs(
        ctx: CPSceneObjectContext,
        postMove: PostMovePlan,
        prevX: Int,
        prevY: Int,
    ): Boolean {
        if (postMove.moved) {
            when (postMove.slideKind) {
                SlideKind.COLUMN_UP -> slideColumnUpAfterPlayerLeft(prevX, prevY)
                SlideKind.ROW_LEFT -> slideRowLeftAfterPlayerLeft(prevY, prevX)
                SlideKind.NONE -> {}
            }
        }
        if (postMove.shouldTickBombs) {
            tickBombs(ctx)
            if (GameState.gameOver) return true
        }
        return false
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
    private const val SLIDE_DURATION_FRAMES = 6
    private const val DECK_SLIDE_DURATION_FRAMES = 27
    /** Normalized time: flip at deck (face-down → face-up), then exit left, then approach from board edge. */
    private const val DECK_FLIP_END_T = 0.40f
    private const val DECK_EXIT_END_T = 0.64f

    private enum class DeckSpawnEdge { BOTTOM, RIGHT }
    private enum class DeckSource { ENEMY, PLAYER }

    private class SlideAnim(
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int,
        var elapsed: Int = 0,
        val durationFrames: Int = SLIDE_DURATION_FRAMES,
        val deckSpawnEdge: DeckSpawnEdge? = null,
        val deckSource: DeckSource = DeckSource.PLAYER,
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

    private fun registerDeckSpawnSlide(entity: Any, toX: Int, toY: Int, edge: DeckSpawnEdge, source: DeckSource) {
        slideAnimations[entity] = SlideAnim(
            fromX = 0,
            fromY = 0,
            toX = toX,
            toY = toY,
            durationFrames = DECK_SLIDE_DURATION_FRAMES,
            deckSpawnEdge = edge,
            deckSource = source,
        )
    }

    /** Gentler than [easeSlide] so deck spawn phases feel less snappy. */
    private fun easeDeckMotion(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun deckSpawnScreenPos(anim: SlideAnim, rawT: Float): Pair<Int, Int> {
        val edge = anim.deckSpawnEdge ?: return anim.toX to anim.toY
        val deckX = GridConfig.deckScreenX
        val deckY = when (anim.deckSource) {
            DeckSource.ENEMY -> GridConfig.enemyDeckScreenY
            DeckSource.PLAYER -> GridConfig.playerDeckScreenY
        }
        val w = GridConfig.CELL_WIDTH
        val h = GridConfig.CELL_HEIGHT
        val t = rawT.coerceIn(0f, 1f)
        val flipEnd = DECK_FLIP_END_T
        val exitEnd = DECK_EXIT_END_T
        when {
            t < flipEnd -> return deckX to deckY
            t < exitEnd -> {
                val u = easeDeckMotion(((t - flipEnd) / (exitEnd - flipEnd)).coerceIn(0f, 1f))
                val exitX = -w
                val x = deckX + ((exitX - deckX) * u).toInt()
                return x to deckY
            }
        }
        val u = easeDeckMotion(((t - exitEnd) / (1f - exitEnd)).coerceIn(0f, 1f))
        return when (edge) {
            DeckSpawnEdge.BOTTOM -> {
                val sx = anim.toX
                val sy = anim.toY + h
                val x = sx + ((anim.toX - sx) * u).toInt()
                val y = sy + ((anim.toY - sy) * u).toInt()
                x to y
            }
            DeckSpawnEdge.RIGHT -> {
                val sx = anim.toX + w
                val sy = anim.toY
                val x = sx + ((anim.toX - sx) * u).toInt()
                val y = sy + ((anim.toY - sy) * u).toInt()
                x to y
            }
        }
    }

    /** 0..1 while the deck spawn is in the flip phase (card stays on deck); null afterward. */
    private fun deckSpawnFlipProgress(entity: Any): Float? {
        val anim = slideAnimations[entity] ?: return null
        if (anim.deckSpawnEdge == null) return null
        val t = anim.progress()
        if (t >= DECK_FLIP_END_T) return null
        return easeDeckMotion((t / DECK_FLIP_END_T).coerceIn(0f, 1f))
    }

    private fun slidingScreenPos(entity: Any, baseX: Int, baseY: Int): Pair<Int, Int> {
        val anim = slideAnimations[entity] ?: return baseX to baseY
        val tLinear = anim.progress()
        val edge = anim.deckSpawnEdge
        if (edge != null) {
            return deckSpawnScreenPos(anim, tLinear)
        }
        val t = easeSlide(tLinear)
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

    private fun markItemResolved(item: GridItem) {
        if (item.collected) return
        item.collected = true
        GameState.onSpawnedItemResolved(item)
    }

    private fun markEnemyResolved(enemy: EnemyCard) {
        if (enemy.defeated) return
        enemy.defeated = true
        GameState.onSpawnedEnemyResolved(enemy)
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
            val spawn = GameState.drawSpawnForCell(col, row, items, enemies)
            if (spawn.first != null) {
                val newItem = spawn.first
                items = items + newItem!!
                val src = if (newItem.type == ItemType.SPIKES || newItem.type == ItemType.BOMB || newItem.type == ItemType.WALL) {
                    DeckSource.ENEMY
                } else {
                    DeckSource.PLAYER
                }
                registerDeckSpawnSlide(newItem, toX, toY, DeckSpawnEdge.BOTTOM, src)
            } else {
                val newEnemy = spawn.second
                enemies = enemies + newEnemy!!
                registerDeckSpawnSlide(newEnemy, toX, toY, DeckSpawnEdge.BOTTOM, DeckSource.ENEMY)
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
            val spawn = GameState.drawSpawnForCell(col, row, items, enemies)
            if (spawn.first != null) {
                val newItem = spawn.first
                items = items + newItem!!
                val src = if (newItem.type == ItemType.SPIKES || newItem.type == ItemType.BOMB || newItem.type == ItemType.WALL) {
                    DeckSource.ENEMY
                } else {
                    DeckSource.PLAYER
                }
                registerDeckSpawnSlide(newItem, toX, toY, DeckSpawnEdge.RIGHT, src)
            } else {
                val newEnemy = spawn.second
                enemies = enemies + newEnemy!!
                registerDeckSpawnSlide(newEnemy, toX, toY, DeckSpawnEdge.RIGHT, DeckSource.ENEMY)
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
            SceneId.GAME.id,
            Option.empty(),  // adaptive to terminal size
            bgPx,
            scalaSeqOf(gridSprite, playerSprite, hudSprite, effectSprite)
        )
    }

    private fun px(ch: Char, fg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(BG_COLOR), 0)

    private fun px(ch: Char, fg: CPColor, bg: CPColor): CPPixel =
        CPPixel(ch, fg, Option.apply(bg), 0)

    private fun syncClusterLayout(canv: CPCanvas) {
        val qc = GameState.questHudLines().size
        GridConfig.updateClusterLayout(canv.width(), canv.height(), qc)
    }

    /** Horizontally center a line of length [textLen] in the cluster ([clusterWidth] chars wide). */
    private fun hudXCenteredInCluster(clusterLeft: Int, clusterWidth: Int, textLen: Int): Int {
        if (clusterWidth <= 0) return clusterLeft
        return clusterLeft + ((clusterWidth - textLen).coerceAtLeast(0) + 1) / 2
    }

    private fun hudLineFit(s: String, max: Int): String {
        if (max <= 0) return ""
        if (s.length <= max) return s
        if (max <= 1) return s.take(max)
        return s.take(max - 1) + "…"
    }

    /** Pixel width of item/enemy ASCII (matches [AsciiArt] lines; keeps art inside the card). */
    private val artPixelWidth = 30

    /** Horizontal strip around card center; null = no clipping. */
    private fun allowInteriorClipDraw(sx: Int, w: Int, x: Int, clipHalfW: Int?): Boolean {
        if (clipHalfW == null) return true
        val cx = sx + w / 2
        return abs(x - cx) <= clipHalfW
    }

    private fun lerpInt(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t.coerceIn(0f, 1f)).roundToInt()

    /** Draw a line of text char-by-char onto the canvas. */
    private fun drawArt(
        canv: CPCanvas,
        art: List<String>,
        startX: Int,
        startY: Int,
        z: Int,
        fg: CPColor,
        clipCardSx: Int? = null,
        clipCardW: Int? = null,
        interiorClipHalfWidth: Int? = null,
    ) {
        val useClip = clipCardSx != null && clipCardW != null && interiorClipHalfWidth != null
        for ((row, line) in art.withIndex()) {
            val clipped = line.take(artPixelWidth)
            for ((col, ch) in clipped.withIndex()) {
                if (ch != ' ') {
                    val pxX = startX + col
                    val pxY = startY + row
                    if (useClip && !allowInteriorClipDraw(clipCardSx!!, clipCardW!!, pxX, interiorClipHalfWidth)) {
                        continue
                    }
                    canv.drawPixel(px(ch, fg), pxX, pxY, z)
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

    private fun drawDeckCardBackAt(canv: CPCanvas, sx: Int, sy: Int, w: Int, h: Int, z: Int) {
        val borderColor = CPColor(110, 105, 145, "deck-border")
        val pat = CPColor(72, 68, 108, "deck-pattern")
        drawCardBorder(canv, sx, sy, w, h, z, borderColor)
        val inner = AsciiArt.tiledDeckBackInterior(w - 2, h - 2)
        val innerW = w - 2
        for ((row, line) in inner.withIndex()) {
            val rowText = if (line.length >= innerW) line else line.padEnd(innerW)
            for (col in 0 until innerW) {
                val ch = rowText[col]
                if (ch != ' ') canv.drawPixel(px(ch, pat), sx + 1 + col, sy + 1 + row, z)
            }
        }
    }

    /** Face-down pattern narrowed toward the horizontal center (rotation toward edge-on). */
    private fun drawDeckCardBackSqueezedAt(
        canv: CPCanvas, sx: Int, sy: Int, w: Int, h: Int, z: Int, clipHalfW: Int
    ) {
        val borderColor = CPColor(110, 105, 145, "deck-border")
        val pat = CPColor(72, 68, 108, "deck-pattern")
        drawCardBorder(canv, sx, sy, w, h, z, borderColor)
        val inner = AsciiArt.tiledDeckBackInterior(w - 2, h - 2)
        val innerW = w - 2
        for ((row, line) in inner.withIndex()) {
            val rowText = if (line.length >= innerW) line else line.padEnd(innerW)
            for (col in 0 until innerW) {
                val ch = rowText[col]
                if (ch == ' ') continue
                val x = sx + 1 + col
                if (!allowInteriorClipDraw(sx, w, x, clipHalfW)) continue
                canv.drawPixel(px(ch, pat), x, sy + 1 + row, z)
            }
        }
    }

    /**
     * Edge-on card: [coreHalfWidth] is how many columns left/right of center get ink (0 = spine only).
     * Darker glyphs at the spine, lighter at the “fold” to read as thickness.
     */
    private fun drawCardFlipEdgeAt(
        canv: CPCanvas, sx: Int, sy: Int, w: Int, h: Int, z: Int, coreHalfWidth: Int
    ) {
        val edgeBorder = CPColor(120, 115, 155, "flip-edge-border")
        val edgeCore = CPColor(88, 84, 118, "flip-edge-core")
        val edgeMid = CPColor(118, 112, 152, "flip-edge-mid")
        val edgeHi = CPColor(148, 142, 182, "flip-edge-hi")
        drawCardBorder(canv, sx, sy, w, h, z, edgeBorder)
        val cx = sx + w / 2
        val top = sy + 2
        val bot = sy + h - 3
        for (yy in top..bot) {
            for (dx in -coreHalfWidth..coreHalfWidth) {
                val x = cx + dx
                if (x <= sx || x >= sx + w - 1) continue
                val ad = abs(dx)
                val ch = when {
                    ad == 0 -> '█'
                    ad == 1 -> '▓'
                    ad == 2 -> '▒'
                    else -> '░'
                }
                val fg = when {
                    ad == 0 -> edgeHi
                    ad == 1 -> edgeMid
                    else -> edgeCore
                }
                canv.drawPixel(px(ch, fg), x, yy, z)
            }
        }
    }

    private fun drawDeckFaceDown(canv: CPCanvas, gc: GridConfig) {
        drawDeckCardBackAt(canv, gc.deckScreenX, gc.enemyDeckScreenY, gc.CELL_WIDTH, gc.CELL_HEIGHT, 0)
        drawDeckCardBackAt(canv, gc.deckScreenX, gc.playerDeckScreenY, gc.CELL_WIDTH, gc.CELL_HEIGHT, 0)
    }

    private fun maxInteriorClipHalf(w: Int): Int = (w - 2) / 2

    /** Multi-stage flip: squeeze back → thick edge → spine → widen face. Always returns true (covers whole flip phase). */
    private fun drawDeckSpawnFlipForItem(
        canv: CPCanvas, item: GridItem, sx: Int, sy: Int, flipP: Float, gc: GridConfig,
    ): Boolean {
        val w = gc.CELL_WIDTH
        val h = gc.CELL_HEIGHT
        val maxHalf = maxInteriorClipHalf(w)
        when {
            flipP < 0.10f -> drawDeckCardBackAt(canv, sx, sy, w, h, 1)
            flipP < 0.30f -> {
                val u = easeDeckMotion((flipP - 0.10f) / 0.20f)
                val halfW = lerpInt(maxHalf, 5, u).coerceAtLeast(2)
                drawDeckCardBackSqueezedAt(canv, sx, sy, w, h, 1, halfW)
            }
            flipP < 0.34f -> drawCardFlipEdgeAt(canv, sx, sy, w, h, 1, 3)
            flipP < 0.38f -> drawCardFlipEdgeAt(canv, sx, sy, w, h, 1, 2)
            flipP < 0.42f -> drawCardFlipEdgeAt(canv, sx, sy, w, h, 1, 1)
            flipP < 0.45f -> drawCardFlipEdgeAt(canv, sx, sy, w, h, 1, 0)
            flipP < 0.82f -> {
                val u = easeDeckMotion((flipP - 0.45f) / 0.37f)
                val halfW = lerpInt(1, maxHalf, u).coerceIn(1, maxHalf)
                drawGridItemCard(canv, item, sx, sy, gc, interiorClipHalfWidth = halfW)
            }
            else -> drawGridItemCard(canv, item, sx, sy, gc, interiorClipHalfWidth = null)
        }
        return true
    }

    private fun drawDeckSpawnFlipForEnemy(
        canv: CPCanvas, enemy: EnemyCard, sx: Int, sy: Int, flipP: Float, gc: GridConfig,
    ): Boolean {
        val w = gc.CELL_WIDTH
        val h = gc.CELL_HEIGHT
        val maxHalf = maxInteriorClipHalf(w)
        when {
            flipP < 0.10f -> drawDeckCardBackAt(canv, sx, sy, w, h, 1)
            flipP < 0.30f -> {
                val u = easeDeckMotion((flipP - 0.10f) / 0.20f)
                val halfW = lerpInt(maxHalf, 5, u).coerceAtLeast(2)
                drawDeckCardBackSqueezedAt(canv, sx, sy, w, h, 1, halfW)
            }
            flipP < 0.34f -> drawCardFlipEdgeAt(canv, sx, sy, w, h, 1, 3)
            flipP < 0.38f -> drawCardFlipEdgeAt(canv, sx, sy, w, h, 1, 2)
            flipP < 0.42f -> drawCardFlipEdgeAt(canv, sx, sy, w, h, 1, 1)
            flipP < 0.45f -> drawCardFlipEdgeAt(canv, sx, sy, w, h, 1, 0)
            flipP < 0.82f -> {
                val u = easeDeckMotion((flipP - 0.45f) / 0.37f)
                val halfW = lerpInt(1, maxHalf, u).coerceIn(1, maxHalf)
                drawGridEnemyCard(canv, enemy, sx, sy, gc, interiorClipHalfWidth = halfW)
            }
            else -> drawGridEnemyCard(canv, enemy, sx, sy, gc, interiorClipHalfWidth = null)
        }
        return true
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
        markItemResolved(bomb)
        val bx = bomb.gridX
        val by = bomb.gridY
        val dirs = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
        for ((dx, dy) in dirs) {
            val nx = bx + dx
            val ny = by + dy
            if (nx !in 0 until GridConfig.COLS || ny !in 0 until GridConfig.ROWS) continue
            if (GameState.playerGridX == nx && GameState.playerGridY == ny) {
                if (!GameState.debugMode) {
                    GameState.gameOver = true
                    GameState.playerHealth = 0
                    GameState.runEndKind = RunEndKind.DEATH
                    ctx.switchScene(SceneId.RUN_SUMMARY, false)
                    return
                }
            }
            for (enemy in enemies) {
                if (!enemy.defeated && enemy.gridX == nx && enemy.gridY == ny) {
                    GameState.registerEnemyDefeat(enemy.kind, enemy.isElite)
                    RunStats.recordEnemyKill(enemy.kind, enemy.isElite)
                    markEnemyResolved(enemy)
                    if (enemy.isElite) dropEliteKeyAt(nx, ny)
                }
            }
            for (it in items) {
                if (!it.collected && it.gridX == nx && it.gridY == ny) {
                    markItemResolved(it)
                }
            }
        }
    }

    /** Elite defeats always grant a tiered key by level on the defeated elite cell. */
    private fun dropEliteKeyAt(gridX: Int, gridY: Int) {
        val tier = LevelGenerator.keyTierForLevel(GameState.currentLevel)
        items = items + LevelGenerator.spawnKeyAtTier(
            tier = tier,
            gridX = gridX,
            gridY = gridY,
            existingItems = items,
            existingEnemies = enemies,
        )
    }

    private fun drawGridItemCard(
        canv: CPCanvas,
        item: GridItem,
        sx: Int,
        sy: Int,
        gc: GridConfig,
        interiorClipHalfWidth: Int? = null,
    ) {
        val cw = gc.CELL_WIDTH
        val ch = gc.CELL_HEIGHT
        val color = CardArt.itemTileColor(item)
        val dimColor = color.transformRGB(0.5f)

        drawCardBorder(canv, sx, sy, cw, ch, 1, color)

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
        }.take(cw - 2)
        val tx = centerX(title, sx, cw)
        for ((i, ch) in title.withIndex()) {
            val pxX = tx + i
            if (allowInteriorClipDraw(sx, cw, pxX, interiorClipHalfWidth)) {
                canv.drawPixel(px(ch, color), pxX, sy + 1, 1)
            }
        }
        if (GameState.debugMode && item.secretRoom) {
            drawCardBorder(canv, sx, sy, cw, ch, 2, CPColor.C_GOLD1())
            val qx = sx + cw - 3
            if (allowInteriorClipDraw(sx, cw, qx, interiorClipHalfWidth)) {
                canv.drawPixel(px('?', CPColor.C_GOLD1()), qx, sy + 1, 1)
            }
        }

        val art = if (item.type == ItemType.CHEST && item.chestOpened) {
            clippedArt(AsciiArt.MONEY_PILE)
        } else {
            clippedArt(CardArt.itemSprite(item))
        }
        val artX = sx + (cw - artPixelWidth) / 2
        val artY = artCenterY(sy, art.size)
        drawArt(canv, art, artX, artY, 1, color, sx, cw, interiorClipHalfWidth)

        val label = when {
            item.type == ItemType.CHEST && !item.chestOpened ->
                hudLineFit("NEED ${item.tier.name} KEY  ${item.value} GP", cw - 2)
            item.type == ItemType.CHEST && !item.chestGoldClaimed ->
                hudLineFit("${item.value} GP  STEP", cw - 2)
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
        val labelRow = sy + ch - 2
        if (item.type == ItemType.BOMB) {
            val prefix = "▶ "
            val numStr = "${item.bombTicks}"
            val lx = centerX(prefix + numStr, sx, cw)
            val pulse = bombTickFlash.intensityAt(item.gridX, item.gridY)
            val digitHot = CPColor(255, 245, 120, "bomb-digit")
            val arrowHot = CPColor(255, 200, 80, "bomb-arrow")
            for ((i, ch) in prefix.withIndex()) {
                val pxX = lx + i
                if (allowInteriorClipDraw(sx, cw, pxX, interiorClipHalfWidth)) {
                    val c = lerpColor(dimColor, arrowHot, pulse * 0.9f)
                    canv.drawPixel(px(ch, c), pxX, labelRow, 1)
                }
            }
            for ((i, ch) in numStr.withIndex()) {
                val pxX = lx + prefix.length + i
                if (allowInteriorClipDraw(sx, cw, pxX, interiorClipHalfWidth)) {
                    val c = lerpColor(dimColor, digitHot, pulse * 0.95f)
                    canv.drawPixel(px(ch, c), pxX, labelRow, 1)
                }
            }
            if (pulse > 0.12f) {
                val glow = lerpColor(dimColor, CPColor(255, 255, 255, "glow"), pulse * 0.55f)
                for ((i, ch) in numStr.withIndex()) {
                    if (ch.isDigit()) {
                        val pxX = lx + prefix.length + i
                        if (allowInteriorClipDraw(sx, cw, pxX, interiorClipHalfWidth)) {
                            canv.drawPixel(px(ch, glow), pxX, labelRow - 1, 1)
                        }
                    }
                }
            }
        } else {
            val lx = centerX(label, sx, cw)
            for ((i, ch) in label.withIndex()) {
                val pxX = lx + i
                if (allowInteriorClipDraw(sx, cw, pxX, interiorClipHalfWidth)) {
                    canv.drawPixel(px(ch, dimColor), pxX, labelRow, 1)
                }
            }
        }
    }

    private fun drawGridEnemyCard(
        canv: CPCanvas,
        enemy: EnemyCard,
        sx: Int,
        sy: Int,
        gc: GridConfig,
        interiorClipHalfWidth: Int? = null,
    ) {
        val cw = gc.CELL_WIDTH
        val ch = gc.CELL_HEIGHT
        val nameColor = if (enemy.isElite) CPColor.C_GOLD1() else CPColor.C_INDIAN_RED1()
        val borderColor = if (enemy.isElite) CPColor.C_ORANGE1() else CPColor.C_INDIAN_RED1()
        val artColor = CPColor.C_RED1()
        val statsColor = if (enemy.isElite) CPColor.C_GOLD1() else CPColor.C_ORANGE_RED1()

        drawCardBorder(canv, sx, sy, cw, ch, 1, borderColor)

        val rawTitle =
            if (enemy.isElite) "ELITE ${enemy.kind.displayName}" else enemy.kind.displayName
        val name = rawTitle.take(cw - 2)
        val nx = centerX(name, sx, cw)
        for ((i, ch) in name.withIndex()) {
            val pxX = nx + i
            if (allowInteriorClipDraw(sx, cw, pxX, interiorClipHalfWidth)) {
                canv.drawPixel(px(ch, nameColor), pxX, sy + 1, 1)
            }
        }
        if (GameState.debugMode && enemy.secretRoom) {
            drawCardBorder(canv, sx, sy, cw, ch, 2, CPColor.C_GOLD1())
            val qx = sx + cw - 3
            if (allowInteriorClipDraw(sx, cw, qx, interiorClipHalfWidth)) {
                canv.drawPixel(px('?', CPColor.C_GOLD1()), qx, sy + 1, 1)
            }
        }

        val art = clippedArt(CardArt.enemySprite(enemy.kind))
        val artX = sx + (cw - (art.firstOrNull()?.length ?: 0)) / 2
        val artY = artCenterY(sy, art.size)
        drawArt(canv, art, artX, artY, 1, artColor, sx, cw, interiorClipHalfWidth)

        val stats =
            if (enemy.isElite) "★ ATK:${enemy.attack}  HP:${enemy.health}"
            else "ATK:${enemy.attack}  HP:${enemy.health}"
        val stx = centerX(stats, sx, cw)
        val statsRow = sy + ch - 2
        for ((i, cch) in stats.withIndex()) {
            val pxX = stx + i
            if (allowInteriorClipDraw(sx, cw, pxX, interiorClipHalfWidth)) {
                canv.drawPixel(px(cch, statsColor), pxX, statsRow, 1)
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

                syncClusterLayout(canv)

                val emptyBorder = CPColor(50, 50, 70, "empty-border")

                drawDeckFaceDown(canv, gc)

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
                    val flipP = deckSpawnFlipProgress(item)
                    if (flipP != null) {
                        drawDeckSpawnFlipForItem(canv, item, sx, sy, flipP, gc)
                        continue
                    }
                    drawGridItemCard(canv, item, sx, sy, gc)
                }

                // Draw enemies
                for (enemy in enemies) {
                    if (enemy.defeated) continue
                    val (sx, sy) = slidingScreenPos(
                        enemy,
                        gc.cellScreenX(enemy.gridX),
                        gc.cellScreenY(enemy.gridY)
                    )
                    val flipP = deckSpawnFlipProgress(enemy)
                    if (flipP != null) {
                        drawDeckSpawnFlipForEnemy(canv, enemy, sx, sy, flipP, gc)
                        continue
                    }
                    drawGridEnemyCard(canv, enemy, sx, sy, gc)
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
                        GameState.debugMode = !GameState.debugMode
                        return
                    }
                    KEY_I -> {
                        GameState.inventoryReturnScene = SceneId.GAME
                        ctx.switchScene(SceneId.INVENTORY, false)
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
                            markItemResolved(wallBlocking)
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
                if (spikeHere != null && !GameState.debugMode) {
                    GameState.gameOver = true
                    GameState.playerHealth = 0
                    GameState.runEndKind = RunEndKind.DEATH
                    ctx.switchScene(SceneId.RUN_SUMMARY, false)
                    return
                }

                val secretHere = items.find {
                    !it.collected && it.secretRoom && it.gridX == newX && it.gridY == newY
                }
                if (secretHere != null && GameState.hasAnyKey()) {
                    GameState.consumeAnyKey()
                    markItemResolved(secretHere)
                    itemFlash.flash(newX, newY)
                    confetti.spawn(newX, newY)
                    RunStats.recordSecretRoom()
                    val pmSecret = planPostMoveFlow(prevX, prevY, GameState.playerGridX, GameState.playerGridY, dx, dy)
                    if (applyPostMoveSlidingAndBombs(ctx, pmSecret, prevX, prevY)) return
                    kotlin.runCatching { ctx.deleteScene(SceneId.SECRET_ROOM) }
                    ctx.addScene(SecretRoomScene.create(), false, false, false)
                    ctx.switchScene(SceneId.SECRET_ROOM, false)
                    return
                }

                // Item pickup (chest gold is collected when stepping onto an opened chest)
                for (item in items) {
                    if (!item.collected && item.gridX == newX && item.gridY == newY) {
                        if (item.type == ItemType.CHEST && item.chestOpened && !item.chestGoldClaimed) {
                            GameState.addMoney(item.value)
                            item.chestGoldClaimed = true
                            markItemResolved(item)
                            itemFlash.flash(newX, newY)
                            confetti.spawn(newX, newY)
                            continue
                        }
                        if (item.type == ItemType.CHEST || item.type == ItemType.SHOP || item.type == ItemType.GAMBLING) continue
                        if (item.type == ItemType.SPIKES || item.type == ItemType.BOMB || item.type == ItemType.WALL) continue
                        if (item.type == ItemType.QUEST) {
                            markItemResolved(item)
                            GameState.pendingQuestOffer = QuestSystem.randomOffer(
                                excludeQuestIds = GameState.activeIncompleteQuestTemplateIds(),
                                completedQuestIds = GameState.completedQuestIds(),
                                currentLevel = GameState.currentLevel,
                            )
                            itemFlash.flash(newX, newY)
                            confetti.spawn(newX, newY)
                            val pmQuest = planPostMoveFlow(prevX, prevY, GameState.playerGridX, GameState.playerGridY, dx, dy)
                            if (applyPostMoveSlidingAndBombs(ctx, pmQuest, prevX, prevY)) return
                            ctx.switchScene(SceneId.QUEST, false)
                            return
                        }
                        if (item.type == ItemType.REST) {
                            markItemResolved(item)
                            itemFlash.flash(newX, newY)
                            confetti.spawn(newX, newY)
                            val pmRest = planPostMoveFlow(prevX, prevY, GameState.playerGridX, GameState.playerGridY, dx, dy)
                            if (applyPostMoveSlidingAndBombs(ctx, pmRest, prevX, prevY)) return
                            kotlin.runCatching { ctx.deleteScene(SceneId.REST) }
                            ctx.addScene(RestScene.create(), false, false, false)
                            ctx.switchScene(SceneId.REST, false)
                            return
                        }
                        markItemResolved(item)
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
                            markEnemyResolved(enemy)
                            itemFlash.flash(newX, newY)
                            confetti.spawn(newX, newY)
                            RunStats.recordSecretRoom()
                            val pmSecE = planPostMoveFlow(prevX, prevY, GameState.playerGridX, GameState.playerGridY, dx, dy)
                            if (applyPostMoveSlidingAndBombs(ctx, pmSecE, prevX, prevY)) return
                            kotlin.runCatching { ctx.deleteScene(SceneId.SECRET_ROOM) }
                            ctx.addScene(SecretRoomScene.create(), false, false, false)
                            ctx.switchScene(SceneId.SECRET_ROOM, false)
                            return
                        }
                        val exchange = resolveCombatExchange(
                            playerHealth = GameState.playerHealth,
                            enemyHealth = enemy.health,
                            enemyAttack = enemy.attack,
                            playerAttack = GameState.effectivePlayerAttack(),
                            damageAfterEnemyAttack = { atk -> GameState.damageAfterEnemyAttack(atk) }
                        )
                        if (!GameState.debugMode) {
                            GameState.playerHealth -= exchange.playerDamageTaken
                        }
                        enemy.health -= exchange.enemyDamageTaken
                        combatFlash.flash(newX, newY)

                        if (exchange.enemyDefeated) {
                            markEnemyResolved(enemy)
                            GameState.registerEnemyDefeat(enemy.kind, enemy.isElite)
                            RunStats.recordEnemyKill(enemy.kind, enemy.isElite)
                            GameState.score += if (enemy.isElite) 40 else 25
                            if (enemy.isElite) {
                                // Keep the player in their original tile so the elite cell holds the dropped key card.
                                GameState.playerGridX = (newX - dx).coerceIn(0, GridConfig.COLS - 1)
                                GameState.playerGridY = (newY - dy).coerceIn(0, GridConfig.ROWS - 1)
                                dropEliteKeyAt(newX, newY)
                                if (GameState.score >= LevelConfig.targetScore(GameState.currentLevel)) {
                                    GameState.deferLevelCompleteForEliteKey = true
                                }
                            }
                            confetti.spawn(newX, newY)
                        }

                        if (exchange.playerDefeated && !GameState.debugMode) {
                            GameState.playerHealth = 0
                            GameState.gameOver = true
                            GameState.runEndKind = RunEndKind.DEATH
                            ctx.switchScene(SceneId.RUN_SUMMARY, false)
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
                if (applyPostMoveSlidingAndBombs(ctx, postMove, prevX, prevY)) return

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
                        GameState.score >= LevelConfig.targetScore(GameState.currentLevel) &&
                        !GameState.deferLevelCompleteForEliteKey
                when (resolvePostMoveSceneRoute(postMove.moved, onGamblingTile, onShopTile, reachedLevelTarget)) {
                    PostMoveSceneRoute.MINIGAMES -> {
                        GameState.minigamesReturnScene = SceneId.GAME
                        ctx.switchScene(SceneId.MINIGAMES, false)
                        return
                    }
                    PostMoveSceneRoute.SHOP -> {
                        GameState.shopDismissAction = ShopDismissAction.SwitchTo(SceneId.GAME)
                        kotlin.runCatching { ctx.deleteScene(SceneId.SHOP) }
                        ctx.addScene(ShopScene.create(), false, false, false)
                        ctx.switchScene(SceneId.SHOP, false)
                        return
                    }
                    PostMoveSceneRoute.LEVEL_COMPLETE -> {
                        Progress.onLevelCleared(GameState.currentLevel)
                        ctx.switchScene(SceneId.LEVEL_COMPLETE, false)
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
                syncClusterLayout(canv)
                val cx = GridConfig.clusterOriginX
                val cw = GridConfig.HUD_COLUMN_WIDTH
                val textMax = cw.coerceAtLeast(8)
                val hudZ = 15 // above grid/player (≤4) and confetti (10)

                val ly = GridConfig.hudTopY
                val oneThird = (GridConfig.GRID_TOTAL_HEIGHT / 3).coerceAtLeast(4)
                val topStart = ly
                val midStart = ly + oneThird
                val botStart = ly + oneThird * 2

                val enemyDeck = GameState.enemyDeckSnapshot()
                val playerDeck = GameState.playerDeckSnapshot()
                val goal = LevelConfig.targetScore(GameState.currentLevel)
                val questLine = GameState.questHudLines().firstOrNull() ?: "Quest: none"
                val middleLines = listOf(
                    hudLineFit("CARD CRAWLER", textMax),
                    hudLineFit(LevelConfig.hudLore(GameState.currentLevel), textMax),
                    hudLineFit("LV ${GameState.currentLevel} HP:${GameState.playerHealth}", textMax),
                    hudLineFit("ATK ${GameState.playerAttackDisplay()}", textMax),
                    hudLineFit("SHD ${GameState.playerShieldDisplay()}", textMax),
                    hudLineFit("SCORE ${GameState.score}/$goal", textMax),
                    hudLineFit("GOLD ${GameState.money}", textMax),
                    hudLineFit("KEYS ${GameState.keysBronze}B ${GameState.keysSilver}S ${GameState.keysGold}G", textMax),
                    hudLineFit(questLine, textMax),
                )

                val bg = Option.apply(BG_COLOR)
                val topLines = listOf(
                    hudLineFit("ENEMY DECK", textMax),
                    hudLineFit("D:${enemyDeck.draw} R:${enemyDeck.discard}", textMax),
                    hudLineFit("TOP ${enemyDeck.top}", textMax),
                )
                for ((i, ln) in topLines.withIndex()) {
                    val col = if (i == 0) CPColor.C_INDIAN_RED1() else CPColor.C_GREY70()
                    canv.drawString(hudXCenteredInCluster(cx, cw, ln.length), topStart + i, hudZ, ln, col, bg)
                }
                val middleVisible = middleLines.take(oneThird)
                val middleStartY = midStart + ((oneThird - middleVisible.size).coerceAtLeast(0) / 2)
                for ((i, ln) in middleVisible.withIndex()) {
                    val col = when (i) {
                        0 -> CPColor.C_GOLD1()
                        1 -> CPColor.C_GREY70()
                        5, 6 -> CPColor.C_STEEL_BLUE1()
                        else -> CPColor.C_GREY70()
                    }
                    canv.drawString(hudXCenteredInCluster(cx, cw, ln.length), middleStartY + i, hudZ, ln, col, bg)
                }

                val botLines = listOf(
                    hudLineFit("PLAYER DECK", textMax),
                    hudLineFit("D:${playerDeck.draw} R:${playerDeck.discard}", textMax),
                    hudLineFit("TOP ${playerDeck.top}", textMax),
                )
                for ((i, ln) in botLines.withIndex()) {
                    val col = if (i == 0) CPColor.C_GREEN1() else CPColor.C_GREY70()
                    canv.drawString(hudXCenteredInCluster(cx, cw, ln.length), botStart + i, hudZ, ln, col, bg)
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
                    syncClusterLayout(canv)
                    val cx = GridConfig.clusterOriginX
                    val cw = GridConfig.HUD_COLUMN_WIDTH
                    val centerX = cx + (cw + 1) / 2
                    val cy = GridConfig.hudTopY
                    val third = (GridConfig.hudTextLineCount(GameState.questHudLines().size) / 3).coerceAtLeast(4)
                    val centerY = cy + third + 5
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
