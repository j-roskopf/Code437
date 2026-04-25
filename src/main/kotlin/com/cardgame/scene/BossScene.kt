package com.cardgame.scene

import com.cardgame.*
import com.cardgame.art.AsciiArt
import com.cardgame.art.CardArt
import com.cardgame.game.BossId
import com.cardgame.game.EnemyKind
import com.cardgame.game.GameState
import com.cardgame.game.GridConfig
import com.cardgame.game.LevelConfig
import com.cardgame.game.RunEndKind
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import org.cosplay.*
import scala.Option

private enum class DuelPhase {
    INTRO,
    PLAYER_CHOICE,
    /** Player resolves their chosen action first (lunge or SHD). */
    PLAYER_ACTION_ANIM,
    /** After the player animation completes, the foe resolves (lunge + parry window, or SHD). */
    ENEMY_ACTION_ANIM,
    VICTORY,
    DEFEAT,
}

/** One segment of the round; order is chosen so guard always plays before the opposing strike. */
private enum class DuelAnimStep {
    PLAYER,
    ENEMY,
}

private fun buildAnimPlan(player: DuelAction, boss: DuelIntent): List<DuelAnimStep> =
    when {
        player == DuelAction.ATTACK && boss == DuelIntent.DEFEND ->
            listOf(DuelAnimStep.ENEMY, DuelAnimStep.PLAYER)
        player == DuelAction.DEFEND && boss == DuelIntent.ATTACK ->
            listOf(DuelAnimStep.PLAYER, DuelAnimStep.ENEMY)
        else -> listOf(DuelAnimStep.PLAYER, DuelAnimStep.ENEMY)
    }

private enum class DuelIntent {
    ATTACK,
    DEFEND,
}

private enum class DuelAction {
    ATTACK,
    DEFEND,
}

object BossScene {
    private val BG_COLOR = CPColor(26, 18, 30, "boss-bg")
    private val bgPx = CPPixel(' ', CPColor.C_WHITE(), Option.apply(BG_COLOR), 0)

    /**
     * Prisoner's-dilemma-shaped payoffs in JRPG terms (matches this duel: foe telegraphs, you commit same round).
     * Shown clipped in the command panel.
     */
    private const val DUEL_GAMBIT_SUMMARY =
        "Gambit duel: both Strike trade pain; both Guard calms the clash but foe stacks Grd; " +
            "Strike vs Guard glances; Guard vs Strike — parry for the edge."

    private const val DUEL_DAMAGE_FLOAT_FRAMES = 26

    /** JRPG-style bottom command panel (Duelist only). */
    private val PANEL_BG = CPColor(22, 38, 92, "boss-panel-bg")
    private val PANEL_BORDER = CPColor(180, 200, 255, "boss-panel-border")
    private val ARENA_SKY = CPColor(40, 55, 95, "boss-arena-sky")
    private val ARENA_GROUND = CPColor(35, 40, 55, "boss-arena-ground")
    private val KEY_SPACE = kbKey("KEY_SPACE")
    private val KEY_Q = kbKey("KEY_LO_Q")
    private val KEY_ESC = kbKey("KEY_ESC")
    private val KEY_UP = kbKey("KEY_UP")
    private val KEY_DOWN = kbKey("KEY_DOWN")

    /** Regal silhouette for “The Duelist” — reuse existing enemy art. */
    private val DUELIST_ENEMY_KIND = EnemyKind.KING

    /** Shield art at roughly one grid cell interior (matches main board card art). */
    private fun duelShieldIcon(): List<String> =
        clipArt(
            AsciiArt.SHIELD_ARTS.first(),
            maxW = (GridConfig.CELL_WIDTH - 4).coerceAtLeast(20),
            maxH = GridConfig.CELL_HEIGHT - 4,
        )

    private data class BossProfile(
        val id: BossId,
        val hp: Int,
        val attack: Int,
    )

    private fun profileForLevel(level: Int): BossProfile {
        val id = LevelConfig.bossForCheckpoint(level) ?: BossId.DUELIST
        return when (id) {
            BossId.DUELIST -> BossProfile(id = id, hp = 30, attack = 4)
            BossId.WARDEN -> BossProfile(id = id, hp = 42, attack = 6)
            BossId.CONDUCTOR -> BossProfile(id = id, hp = 56, attack = 7)
        }
    }

    private fun rollIntent(prev: DuelIntent?): DuelIntent {
        val base = if (Random.nextInt(100) < 65) DuelIntent.ATTACK else DuelIntent.DEFEND
        if (prev == DuelIntent.DEFEND && base == DuelIntent.DEFEND) return DuelIntent.ATTACK
        return base
    }

    /**
     * Duelist-specific authored pattern:
     * opens guarded, usually converts guard into a strike, and only occasionally resets tempo early.
     */
    private fun rollDuelistIntent(round: Int, prev: DuelIntent?, bossGuardStacks: Int): DuelIntent =
        when {
            round <= 1 -> DuelIntent.DEFEND
            bossGuardStacks >= 2 -> DuelIntent.ATTACK
            prev == DuelIntent.DEFEND -> DuelIntent.ATTACK
            prev == DuelIntent.ATTACK && round % 4 == 0 -> DuelIntent.DEFEND
            prev == DuelIntent.ATTACK && Random.nextInt(100) < 30 -> DuelIntent.DEFEND
            else -> DuelIntent.ATTACK
        }

    private fun centerStringX(text: String, canvasWidth: Int): Int =
        (canvasWidth / 2 - text.length / 2).coerceAtLeast(0)

    private fun clipArt(lines: List<String>, maxW: Int, maxH: Int): List<String> {
        if (lines.isEmpty()) return emptyList()
        val clipped = lines.take(maxH).map { row ->
            row.trimEnd().take(maxW.coerceAtLeast(0))
        }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        return clipped.ifEmpty { listOf("?") }
    }

    private fun px(ch: Char, fg: CPColor, bg: CPColor = BG_COLOR): CPPixel =
        CPPixel(ch, fg, Option.apply(bg), 0)

    fun create(): CPScene {
        val profile = profileForLevel(GameState.currentLevel)
        val bossName = profile.id.displayName
        val useDuelistUi = profile.id == BossId.DUELIST
        val mechanicsNote = when (profile.id) {
            BossId.DUELIST -> "Duel — read the lunge, parry on contact."
            BossId.WARDEN -> "Mode: Duel (hazard phase coming next)"
            BossId.CONDUCTOR -> "Mode: Duel (rhythm phase coming next)"
        }

        var phase = DuelPhase.INTRO
        var round = 1
        var bossHp = profile.hp
        var bossIntent = rollIntent(prev = null)
        var playerAction: DuelAction? = null
        var status = "Foe shows intent; choose Strike or Guard this round."

        var riposteBonus = 0
        var bossGuardStacks = 0
        var parrySuccess = false
        /** Turn animation frame while actions resolve. */
        var actionFrame = 0
        var actionFramesTotal = 24
        /** Hit band as fraction of lunge [0,1] when SPACE counts as a parry. */
        var parryHitStart = 0.42f
        var parryHitEnd = 0.58f
        var parryResolved = false
        var impactFlashFrames = 0
        /** When true, impact tint reflects a successful parry (green); otherwise a normal clash (amber). */
        var impactFlashParryStyle = false
        var parrySignalFrames = 0
        var parrySignalSuccess = false
        var victoryRecorded = false

        /** Floating damage numbers above fighters (Duelist arena). */
        var dmgFloatBoss: String? = null
        var dmgFloatBossFrames = 0
        var dmgFloatBossRiposte: String? = null
        var dmgFloatBossRiposteFrames = 0
        var dmgFloatPlayer: String? = null
        var dmgFloatPlayerFrames = 0
        var didPlayerStrikeImpact = false

        /** Damage from your strike this round (before riposte); used for floater on player lunge impact. */
        fun strikeDamageToBossPreview(): Int {
            val action = playerAction ?: return 0
            if (action != DuelAction.ATTACK) return 0
            val atk = (GameState.effectivePlayerAttack() + riposteBonus).coerceAtLeast(1)
            return if (bossIntent == DuelIntent.DEFEND) {
                (atk * 0.4f).roundToInt().coerceAtLeast(1)
            } else {
                atk
            }
        }

        fun nextBossIntent(roundNumber: Int, prev: DuelIntent?, guardStacks: Int): DuelIntent =
            if (profile.id == BossId.DUELIST) {
                rollDuelistIntent(roundNumber, prev, guardStacks)
            } else {
                rollIntent(prev)
            }

        fun previewIncomingDamage(action: DuelAction): Int {
            var remaining = (profile.attack + bossGuardStacks).coerceAtLeast(0)
            if (GameState.temporaryShield > 0 && remaining > 0) {
                remaining -= minOf(remaining, GameState.temporaryShield)
            }
            remaining = (remaining - GameState.totalEquipmentArmor()).coerceAtLeast(0)
            val defenseReduction = if (action == DuelAction.DEFEND) 1 else 0
            return (remaining - defenseReduction).coerceAtLeast(0)
        }

        fun previewAttackIntoGuardBossDamage(): Int {
            val atk = (GameState.effectivePlayerAttack() + riposteBonus).coerceAtLeast(1)
            return (atk * 0.4f).roundToInt().coerceAtLeast(1)
        }

        fun previewAttackIntoGuardRecoil(): Int =
            (1 + bossGuardStacks).coerceAtMost(3)

        /** JRPG command cursor: 0 = Attack, 1 = Defend */
        var duelMenuSel = 0

        var animPlan: List<DuelAnimStep> = listOf(DuelAnimStep.PLAYER, DuelAnimStep.ENEMY)
        var animPlanIndex = 0

        fun startAnimSegment() {
            actionFrame = 0
            didPlayerStrikeImpact = false
            when (animPlan[animPlanIndex]) {
                DuelAnimStep.PLAYER -> {
                    phase = DuelPhase.PLAYER_ACTION_ANIM
                    parryResolved = true
                    actionFramesTotal = when (playerAction) {
                        DuelAction.ATTACK -> 24
                        DuelAction.DEFEND -> 18
                        null -> 20
                    }
                }
                DuelAnimStep.ENEMY -> {
                    phase = DuelPhase.ENEMY_ACTION_ANIM
                    actionFramesTotal = if (bossIntent == DuelIntent.ATTACK) 28 else 18
                    parryResolved = bossIntent != DuelIntent.ATTACK
                    if (bossIntent == DuelIntent.ATTACK) {
                        if (playerAction == DuelAction.DEFEND) {
                            parryHitStart = 0.36f
                            parryHitEnd = 0.62f
                        } else {
                            parryHitStart = 0.44f
                            parryHitEnd = 0.56f
                        }
                    }
                }
            }
        }

        fun startActionAnim() {
            parrySuccess = false
            dmgFloatBoss = null
            dmgFloatBossFrames = 0
            dmgFloatBossRiposte = null
            dmgFloatBossRiposteFrames = 0
            dmgFloatPlayer = null
            dmgFloatPlayerFrames = 0
            val pa = playerAction ?: return
            animPlan = buildAnimPlan(pa, bossIntent)
            animPlanIndex = 0
            startAnimSegment()
        }

        fun actionProgress(): Float =
            (actionFrame / actionFramesTotal.toFloat()).coerceIn(0f, 1f)

        /** SPACE only; other keys ignored (except Q/ESC handled earlier). */
               fun tryParryInput(key: CPKeyboardKey): Boolean {
            if (parryResolved) return false
            val t = actionProgress()
            if (key == KEY_SPACE) {
                parryResolved = true
                parrySuccess = t in parryHitStart..parryHitEnd
                parrySignalSuccess = parrySuccess
                parrySignalFrames = if (parrySuccess) 14 else 10
                if (parrySuccess) {
                    impactFlashParryStyle = true
                    impactFlashFrames = max(impactFlashFrames, 6)
                    val rip = (GameState.effectivePlayerAttack() / 2).coerceAtLeast(1)
                    dmgFloatBossRiposte = "-$rip"
                    dmgFloatBossRiposteFrames = DUEL_DAMAGE_FLOAT_FRAMES
                }
                return true
            }
            return false
        }

        fun resolveRound() {
            val action = playerAction ?: DuelAction.DEFEND
            val playerBaseAttack = (GameState.effectivePlayerAttack() + riposteBonus).coerceAtLeast(1)
            var damageToBoss = if (action == DuelAction.ATTACK) playerBaseAttack else 0
            var damageToPlayer = 0

            if (bossIntent == DuelIntent.DEFEND) {
                if (damageToBoss > 0) {
                    damageToBoss = (damageToBoss * 0.4f).roundToInt().coerceAtLeast(1)
                    /** Punish greed: striking into an active guard causes recoil chip. */
                    damageToPlayer = (1 + bossGuardStacks).coerceAtMost(3)
                }
                bossGuardStacks = (bossGuardStacks + 1).coerceAtMost(3)
            } else {
                val chargedAttack = profile.attack + bossGuardStacks
                bossGuardStacks = 0
                val reducedByArmor = GameState.damageAfterEnemyAttack(chargedAttack)
                damageToPlayer = if (parrySuccess) {
                    0
                } else {
                    val defenseReduction = if (action == DuelAction.DEFEND) 1 else 0
                    (reducedByArmor - defenseReduction).coerceAtLeast(0)
                }
                if (parrySuccess) {
                    damageToBoss += (GameState.effectivePlayerAttack() / 2).coerceAtLeast(1)
                    riposteBonus = 1
                } else {
                    riposteBonus = 0
                }
            }

            bossHp -= damageToBoss
            if (damageToPlayer > 0) GameState.playerHealth -= damageToPlayer

            if (useDuelistUi && bossIntent == DuelIntent.ATTACK && damageToPlayer > 0) {
                dmgFloatPlayer = "-$damageToPlayer"
                dmgFloatPlayerFrames = DUEL_DAMAGE_FLOAT_FRAMES
            }

            status = buildString {
                append("Round $round -> ")
                append("You ")
                append(if (action == DuelAction.ATTACK) "strike" else "brace")
                append(". ")
                if (bossIntent == DuelIntent.ATTACK) {
                    append(if (parrySuccess) "Parry success. " else "Parry missed. ")
                }
                append("Boss -$damageToBoss HP")
                append(", You -$damageToPlayer HP.")
            }

            if (bossHp <= 0) {
                phase = DuelPhase.VICTORY
                return
            }
            if (GameState.playerHealth <= 0) {
                GameState.playerHealth = 0
                GameState.gameOver = true
                GameState.runEndKind = RunEndKind.DEATH
                phase = DuelPhase.DEFEAT
                return
            }

            round++
            playerAction = null
            duelMenuSel = 0
            bossIntent = nextBossIntent(round, bossIntent, bossGuardStacks)
            phase = DuelPhase.PLAYER_CHOICE
        }

        fun advanceAnimPlan() {
            animPlanIndex++
            if (animPlanIndex >= animPlan.size) {
                resolveRound()
            } else {
                startAnimSegment()
            }
        }

        val inputSprite = object : CPCanvasSprite("boss-input", emptyScalaSeq(), emptyStringSet()) {
            override fun update(ctx: CPSceneObjectContext) {
                super.update(ctx)
                if (!ctx.isVisible()) return
                if (impactFlashFrames > 0) impactFlashFrames--
                if (parrySignalFrames > 0) parrySignalFrames--
                if (dmgFloatBossFrames > 0) dmgFloatBossFrames--
                if (dmgFloatBossRiposteFrames > 0) dmgFloatBossRiposteFrames--
                if (dmgFloatPlayerFrames > 0) dmgFloatPlayerFrames--

                if (phase == DuelPhase.PLAYER_ACTION_ANIM) {
                    val evtAnim = ctx.kbEvent
                    if (evtAnim.isDefined) {
                        val keyAnim = evtAnim.get().key()
                        when (keyAnim) {
                            KEY_Q, KEY_ESC -> {
                                ctx.exitGame()
                                return
                            }
                            else -> {}
                        }
                    }
                    actionFrame++
                    val t = actionProgress()
                    if (playerAction == DuelAction.ATTACK && !didPlayerStrikeImpact && t >= 0.5f) {
                        didPlayerStrikeImpact = true
                        impactFlashParryStyle = false
                        impactFlashFrames = 3
                        if (strikeDamageToBossPreview() > 0) {
                            GameAudio.playPlayerAttack()
                        }
                        if (useDuelistUi) {
                            val sd = strikeDamageToBossPreview()
                            if (sd > 0) {
                                dmgFloatBoss = "-$sd"
                                dmgFloatBossFrames = DUEL_DAMAGE_FLOAT_FRAMES
                            }
                        }
                    }
                    if (actionFrame >= actionFramesTotal) {
                        advanceAnimPlan()
                    }
                    return
                }

                if (phase == DuelPhase.ENEMY_ACTION_ANIM) {
                    val evtAnim = ctx.kbEvent
                    if (evtAnim.isDefined) {
                        val keyAnim = evtAnim.get().key()
                        when (keyAnim) {
                            KEY_Q, KEY_ESC -> {
                                ctx.exitGame()
                                return
                            }
                            else -> {
                                if (bossIntent == DuelIntent.ATTACK) tryParryInput(keyAnim)
                            }
                        }
                    }
                    actionFrame++
                    val t = actionProgress()
                    if (bossIntent == DuelIntent.ATTACK && t >= 0.5f && impactFlashFrames <= 0) {
                        impactFlashParryStyle = false
                        impactFlashFrames = 4
                    }
                    if (bossIntent == DuelIntent.ATTACK && !parryResolved && t > parryHitEnd) {
                        parryResolved = true
                        parrySuccess = false
                        parrySignalSuccess = false
                        parrySignalFrames = 8
                    }
                    if (actionFrame >= actionFramesTotal) {
                        if (bossIntent == DuelIntent.ATTACK && !parryResolved) {
                            parryResolved = true
                            parrySuccess = false
                        }
                        advanceAnimPlan()
                    }
                    return
                }

                val evt = ctx.kbEvent
                if (!evt.isDefined) return
                val key = evt.get().key()

                when (key) {
                    KEY_Q, KEY_ESC -> {
                        ctx.exitGame()
                        return
                    }
                    else -> {}
                }

                when (phase) {
                    DuelPhase.INTRO -> {
                        if (key == KEY_SPACE) {
                            phase = DuelPhase.PLAYER_CHOICE
                        }
                    }
                    DuelPhase.PLAYER_CHOICE -> {
                        if (useDuelistUi) {
                            when (key) {
                                KEY_UP -> duelMenuSel = 0
                                KEY_DOWN -> duelMenuSel = 1
                                KEY_SPACE -> {
                                    playerAction = if (duelMenuSel == 0) DuelAction.ATTACK else DuelAction.DEFEND
                                    startActionAnim()
                                }
                                else -> {}
                            }
                        } else {
                            when (key) {
                                KEY_UP -> {
                                    playerAction = DuelAction.ATTACK
                                    startActionAnim()
                                }
                                KEY_DOWN -> {
                                    playerAction = DuelAction.DEFEND
                                    startActionAnim()
                                }
                                else -> {}
                            }
                        }
                    }
                    DuelPhase.VICTORY -> {
                        if (!victoryRecorded) {
                            GameState.markBossCleared(profile.id)
                            victoryRecorded = true
                        }
                        if (key == KEY_SPACE) {
                            if (GameState.bossRushActive) {
                                val nextCheckpoint = LevelConfig.nextBossCheckpoint(GameState.currentLevel)
                                if (nextCheckpoint != null) {
                                    GameState.clearBossRushDuelMirrorAfterFirstBoss()
                                    GameState.currentLevel = nextCheckpoint
                                    kotlin.runCatching { ctx.deleteScene(SceneId.BOSS_BATTLE) }
                                        .onFailure {
                                            SentryBootstrap.captureCaughtError(
                                                message = "Delete boss scene during boss rush transition failed",
                                                throwable = it,
                                            )
                                        }
                                    ctx.addScene(BossScene.create(), false, false, false)
                                    ctx.switchScene(SceneId.BOSS_BATTLE, false)
                                } else {
                                    GameState.runEndKind = RunEndKind.VICTORY
                                    GameState.bossRushActive = false
                                    ctx.switchScene(SceneId.RUN_SUMMARY, false)
                                }
                            } else {
                                ctx.switchScene(SceneId.LEVEL_COMPLETE, false)
                            }
                        }
                    }
                    DuelPhase.DEFEAT -> {
                        if (key == KEY_SPACE) {
                            GameState.bossRushActive = false
                            ctx.switchScene(SceneId.RUN_SUMMARY, false)
                        }
                    }
                    else -> {}
                }
            }
        }

        val displaySprite = object : CPCanvasSprite("boss-display", emptyScalaSeq(), emptyStringSet()) {
            override fun render(ctx: CPSceneObjectContext) {
                val canv = ctx.canvas
                val w = canv.width()
                val h = canv.height()

                if (!useDuelistUi) {
                    renderFallbackListUi(
                        canv, w, h, profile, bossName, mechanicsNote, phase, round, bossHp,
                        bossIntent, status, riposteBonus, bossGuardStacks,
                    )
                    return
                }

                // Centered battle window sized close to the normal board footprint.
                val panelRows = 9
                val targetW = GridConfig.GRID_TOTAL_WIDTH
                val targetH = GridConfig.GRID_TOTAL_HEIGHT
                val boxW = targetW.coerceAtMost(w - 4).coerceAtLeast(64)
                val boxH = targetH.coerceAtMost(h - 4).coerceAtLeast(panelRows + 16)
                val boxLeft = (w - boxW) / 2
                val boxTop = (h - boxH) / 2
                val boxRight = boxLeft + boxW - 1
                val boxBottom = boxTop + boxH - 1
                val panelStart = boxTop + boxH - panelRows
                val arenaTop = boxTop + 1
                val arenaBottom = panelStart - 2
                val innerH = (arenaBottom - arenaTop + 1).coerceAtLeast(3)

                val borderZ = 1
                val c = PANEL_BORDER
                val bgBox = BG_COLOR
                canv.drawPixel(px('+', c, bgBox), boxLeft, boxTop, borderZ)
                canv.drawPixel(px('+', c, bgBox), boxRight, boxTop, borderZ)
                canv.drawPixel(px('+', c, bgBox), boxLeft, boxBottom, borderZ)
                canv.drawPixel(px('+', c, bgBox), boxRight, boxBottom, borderZ)
                for (x in boxLeft + 1 until boxRight) {
                    canv.drawPixel(px('-', c, bgBox), x, boxTop, borderZ)
                    canv.drawPixel(px('-', c, bgBox), x, boxBottom, borderZ)
                }
                for (y in boxTop + 1 until boxBottom) {
                    canv.drawPixel(px('|', c, bgBox), boxLeft, y, borderZ)
                    canv.drawPixel(px('|', c, bgBox), boxRight, y, borderZ)
                }

                val arenaMidY = arenaTop + innerH / 2
                for (y in arenaTop..arenaBottom) {
                    val rowBg = if (y <= arenaMidY) ARENA_SKY else ARENA_GROUND
                    for (x in boxLeft + 1 until boxRight) {
                        canv.drawPixel(px(' ', CPColor.C_WHITE(), rowBg), x, y, 0)
                    }
                }

                val needShield =
                    (phase == DuelPhase.PLAYER_ACTION_ANIM && playerAction == DuelAction.DEFEND) ||
                        (phase == DuelPhase.ENEMY_ACTION_ANIM && bossIntent == DuelIntent.DEFEND)
                val shieldArtBlock = if (needShield) duelShieldIcon() else emptyList()

                val maxArtW = ((boxW - 10) / 2).coerceIn(12, 26)
                val maxArtH = innerH.coerceIn(6, GridConfig.CELL_HEIGHT - 4)

                val playerArtFull = AsciiArt.playerLines(GameState.selectedPlayerCharacter)
                val enemyArtFull = CardArt.enemySprite(DUELIST_ENEMY_KIND)
                val playerArt = clipArt(playerArtFull, maxArtW, maxArtH)
                val enemyArt = clipArt(enemyArtFull, maxArtW, maxArtH)
                val ph = playerArt.size
                val eh = enemyArt.size
                val pw = playerArt.maxOfOrNull { it.length } ?: 1
                val ew = enemyArt.maxOfOrNull { it.length } ?: 1

                val rowBase = arenaTop + ((innerH - max(ph, eh)) / 2).coerceAtLeast(0)
                val enemyBaseX = boxLeft + 3
                val playerBaseX = (boxRight - pw - 3).coerceAtLeast(enemyBaseX + ew + 6)
                val enemyMaxLunge = (playerBaseX - ew - 2 - enemyBaseX).coerceAtLeast(0)
                val playerMaxLunge = (playerBaseX - (enemyBaseX + ew + 2)).coerceAtLeast(0)
                val inAnim = phase == DuelPhase.PLAYER_ACTION_ANIM || phase == DuelPhase.ENEMY_ACTION_ANIM
                val actionT = if (inAnim) actionProgress() else 0f
                val lungePulse = if (inAnim) {
                    if (actionT <= 0.5f) actionT * 2f else (1f - actionT) * 2f
                } else {
                    0f
                }
                val enemyLunge = if (bossIntent == DuelIntent.ATTACK && phase == DuelPhase.ENEMY_ACTION_ANIM) {
                    (enemyMaxLunge * lungePulse).roundToInt()
                } else {
                    0
                }
                val playerLunge = if (playerAction == DuelAction.ATTACK && phase == DuelPhase.PLAYER_ACTION_ANIM) {
                    (playerMaxLunge * lungePulse).roundToInt()
                } else {
                    0
                }
                val enemyDrawX = (enemyBaseX + enemyLunge).coerceIn(enemyBaseX, enemyBaseX + enemyMaxLunge)
                val playerDrawX = (playerBaseX - playerLunge).coerceIn(playerBaseX - playerMaxLunge, playerBaseX)
                val enemyFg = CPColor.C_INDIAN_RED1()
                val playerFg = GameState.selectedPlayerCharacter.spriteColor

                for ((row, line) in enemyArt.withIndex()) {
                    for ((col, ch) in line.withIndex()) {
                        if (ch != ' ') {
                            canv.drawPixel(px(ch, enemyFg, ARENA_GROUND), enemyDrawX + col, rowBase + row, 2)
                        }
                    }
                }
                for ((row, line) in playerArt.withIndex()) {
                    for ((col, ch) in line.withIndex()) {
                        if (ch != ' ') {
                            canv.drawPixel(px(ch, playerFg, ARENA_GROUND), playerDrawX + col, rowBase + row, 2)
                        }
                    }
                }

                /** Full-size shield drawn above the sprite band without changing fighter row layout. */
                fun drawShieldAbove(spriteLeftX: Int, spriteW: Int, fg: CPColor, art: List<String>) {
                    if (art.isEmpty()) return
                    val sw = art.maxOfOrNull { it.length } ?: return
                    val sh = art.size
                    val shieldTop = rowBase - sh - 1
                    val sx = (spriteLeftX + (spriteW - sw) / 2).coerceAtLeast(boxLeft + 1)
                    for ((r, line) in art.withIndex()) {
                        val py = shieldTop + r
                        if (py < arenaTop || py > arenaBottom) continue
                        val rowBg = if (py <= arenaMidY) ARENA_SKY else ARENA_GROUND
                        for ((ci, ch) in line.withIndex()) {
                            if (ch != ' ') {
                                canv.drawPixel(px(ch, fg, rowBg), sx + ci, py, 4)
                            }
                        }
                    }
                }
                if (phase == DuelPhase.PLAYER_ACTION_ANIM && playerAction == DuelAction.DEFEND) {
                    drawShieldAbove(playerDrawX, pw, CPColor.C_CYAN1(), shieldArtBlock)
                }
                if (phase == DuelPhase.ENEMY_ACTION_ANIM && bossIntent == DuelIntent.DEFEND) {
                    drawShieldAbove(enemyDrawX, ew, CPColor(140, 200, 235, "foe-shield"), shieldArtBlock)
                }

                if (impactFlashFrames > 0) {
                    val flash = if (impactFlashParryStyle) {
                        CPColor(72, 130, 110, "arena-flash-parry")
                    } else {
                        CPColor(120, 86, 76, "arena-flash-hit")
                    }
                    for (y in arenaTop..arenaBottom) {
                        for (x in boxLeft + 1 until boxRight) {
                            canv.drawPixel(px(' ', CPColor.C_WHITE(), flash), x, y, 1)
                        }
                    }
                }

                fun drawDamageFloater(
                    label: String?,
                    framesLeft: Int,
                    centerX: Int,
                    headRow: Int,
                    fg: CPColor,
                    rowExtra: Int,
                ) {
                    if (label == null || framesLeft <= 0) return
                    val rise = (DUEL_DAMAGE_FLOAT_FRAMES - framesLeft) / 2
                    val y = (headRow - 1 - rowExtra - rise).coerceAtLeast(arenaTop)
                    val x = (centerX - label.length / 2).coerceAtLeast(boxLeft + 1)
                    canv.drawString(x, y, 6, label, fg, Option.empty())
                }

                val enemyMidX = enemyDrawX + ew / 2
                val playerMidX = playerDrawX + pw / 2
                drawDamageFloater(
                    dmgFloatBoss, dmgFloatBossFrames,
                    enemyMidX, rowBase, CPColor.C_GOLD1(), 0,
                )
                drawDamageFloater(
                    dmgFloatBossRiposte, dmgFloatBossRiposteFrames,
                    enemyMidX, rowBase, CPColor.C_GREEN1(), 2,
                )
                drawDamageFloater(
                    dmgFloatPlayer, dmgFloatPlayerFrames,
                    playerMidX, rowBase, CPColor.C_ORANGE_RED1(), 0,
                )

                fun clipArenaStat(s: String, maxLen: Int): String =
                    if (s.length <= maxLen) s else s.take((maxLen - 1).coerceAtLeast(1)) + "."

                val statMaxLen = ((boxW - 8) / 2).coerceIn(16, 32)
                val playerStatLine =
                    clipArenaStat(
                        "${GameState.playerHealth}/${GameState.playerMaxHealthDisplayed()} " +
                            "ATK${GameState.effectivePlayerAttack()} Rip+$riposteBonus",
                        statMaxLen,
                    )
                val foeStatLine =
                    clipArenaStat(
                        "${bossHp.coerceAtLeast(0)}/${profile.hp} " +
                            "ATK${profile.attack + bossGuardStacks} Grd$bossGuardStacks",
                        statMaxLen,
                    )
                val playerFeetY = (rowBase + ph).coerceIn(arenaTop, arenaBottom)
                val enemyFeetY = (rowBase + eh).coerceIn(arenaTop, arenaBottom)
                fun clampStatX(centerX: Int, text: String): Int {
                    val lo = boxLeft + 1
                    val hi = (boxRight - text.length).coerceAtLeast(lo)
                    return (centerX - text.length / 2).coerceIn(lo, hi)
                }
                val psx = clampStatX(playerMidX, playerStatLine)
                val fsx = clampStatX(enemyMidX, foeStatLine)
                canv.drawString(psx, playerFeetY, 5, playerStatLine, CPColor.C_STEEL_BLUE1(), Option.empty())
                canv.drawString(fsx, enemyFeetY, 5, foeStatLine, CPColor.C_INDIAN_RED1(), Option.empty())

                if (parrySignalFrames > 0) {
                    val sig = if (parrySignalSuccess) "PARRY!" else "MISS!"
                    val sigCol = if (parrySignalSuccess) CPColor.C_GREEN1() else CPColor.C_ORANGE_RED1()
                    canv.drawString(boxLeft + centerStringX(sig, boxW), arenaTop + 1, 4, sig, sigCol, Option.empty())
                }

                val tele = when (bossIntent) {
                    DuelIntent.ATTACK -> {
                        val raw = profile.attack + bossGuardStacks
                        "FOE: strike  raw $raw"
                    }
                    DuelIntent.DEFEND -> {
                        val recoil = previewAttackIntoGuardRecoil()
                        "FOE: guard  chip + recoil $recoil"
                    }
                }
                canv.drawString(boxLeft + 2, arenaTop, 3, tele, CPColor.C_GREY70(), Option.empty())

                for (y in panelStart..boxBottom) {
                    for (x in boxLeft + 1 until boxRight) {
                        canv.drawPixel(px(' ', CPColor.C_WHITE(), PANEL_BG), x, y, 0)
                    }
                }
                for (x in boxLeft + 1 until boxRight) {
                    canv.drawPixel(px('-', PANEL_BORDER, PANEL_BG), x, panelStart - 1, 1)
                }

                var menuRow = panelStart
                val title = bossName
                fun clipMenu(s: String): String =
                    if (s.length > boxW - 4) s.take(boxW - 5) + "." else s
                val textLeft = boxLeft + 3
                canv.drawString(textLeft, menuRow, 2, title, CPColor.C_GOLD1(), Option.apply(PANEL_BG))
                menuRow += 1

                val cmdX = textLeft
                when (phase) {
                    DuelPhase.INTRO -> {
                        canv.drawString(cmdX, menuRow, 2, "SPACE  Begin", CPColor.C_GREEN1(), Option.apply(PANEL_BG))
                        menuRow += 1
                        canv.drawString(cmdX, menuRow, 2, clipMenu(mechanicsNote), CPColor.C_GREY70(), Option.apply(PANEL_BG))
                        menuRow += 1
                        if (useDuelistUi) {
                            canv.drawString(
                                cmdX, menuRow, 2,
                                clipMenu(DUEL_GAMBIT_SUMMARY),
                                CPColor.C_GREY50(),
                                Option.apply(PANEL_BG),
                            )
                        }
                    }
                    DuelPhase.PLAYER_CHOICE -> {
                        val attackPreview = when (bossIntent) {
                            DuelIntent.ATTACK -> {
                                val deal = strikeDamageToBossPreview()
                                val take = previewIncomingDamage(DuelAction.ATTACK)
                                "Attack  Deal $deal, take $take"
                            }
                            DuelIntent.DEFEND -> {
                                val deal = previewAttackIntoGuardBossDamage()
                                val recoil = previewAttackIntoGuardRecoil()
                                "Attack  Chip $deal, recoil $recoil"
                            }
                        }
                        val defendPreview = when (bossIntent) {
                            DuelIntent.ATTACK -> {
                                val take = previewIncomingDamage(DuelAction.DEFEND)
                                "Defend  Wide parry, miss = take $take"
                            }
                            DuelIntent.DEFEND -> {
                                val nextAtk = profile.attack + (bossGuardStacks + 1).coerceAtMost(3)
                                "Defend  Yield tempo, foe strike -> $nextAtk"
                            }
                        }
                        val atkLine =
                            (if (duelMenuSel == 0) "> " else "  ") + clipMenu(attackPreview)
                        val defLine =
                            (if (duelMenuSel == 1) "> " else "  ") + clipMenu(defendPreview)
                        canv.drawString(cmdX, menuRow, 2, atkLine, if (duelMenuSel == 0) CPColor.C_WHITE() else CPColor.C_GREY70(), Option.apply(PANEL_BG))
                        menuRow += 1
                        canv.drawString(cmdX, menuRow, 2, defLine, if (duelMenuSel == 1) CPColor.C_WHITE() else CPColor.C_GREY70(), Option.apply(PANEL_BG))
                        menuRow += 2
                        canv.drawString(
                            cmdX, menuRow, 2,
                            "Arrows to select Attack / Defend - SPACE confirm / parry",
                            CPColor.C_GREY50(),
                            Option.apply(PANEL_BG),
                        )
                        menuRow += 1
                        if (bossIntent == DuelIntent.ATTACK) {
                            canv.drawString(
                                cmdX, menuRow, 2,
                                clipMenu("Duelist pattern: guard usually becomes strike."),
                                CPColor.C_GREY70(),
                                Option.apply(PANEL_BG),
                            )
                        } else {
                            canv.drawString(
                                cmdX, menuRow, 2,
                                clipMenu("Guard now usually means a heavier strike next."),
                                CPColor.C_GREY70(),
                                Option.apply(PANEL_BG),
                            )
                        }
                        menuRow += 1
                        if (useDuelistUi) {
                            canv.drawString(
                                cmdX, menuRow, 2,
                                clipMenu(DUEL_GAMBIT_SUMMARY),
                                CPColor.C_GREY50(),
                                Option.apply(PANEL_BG),
                            )
                        }
                    }
                    DuelPhase.PLAYER_ACTION_ANIM -> {
                        canv.drawString(
                            cmdX, menuRow, 2,
                            clipMenu("Your action..."),
                            CPColor.C_ORANGE1(),
                            Option.apply(PANEL_BG),
                        )
                        menuRow += 1
                        canv.drawString(
                            cmdX, menuRow, 2,
                            clipMenu("Attack = lunge. Defend = shield."),
                            CPColor.C_GREY70(),
                            Option.apply(PANEL_BG),
                        )
                    }
                    DuelPhase.ENEMY_ACTION_ANIM -> {
                        if (bossIntent == DuelIntent.ATTACK) {
                            canv.drawString(
                                cmdX, menuRow, 2,
                                clipMenu("Foe strikes — SPACE on contact to parry."),
                                CPColor.C_ORANGE1(),
                                Option.apply(PANEL_BG),
                            )
                            menuRow += 1
                            canv.drawString(
                                cmdX, menuRow, 2,
                                clipMenu("Early/late SPACE = miss."),
                                CPColor.C_GREY50(),
                                Option.apply(PANEL_BG),
                            )
                        } else {
                            canv.drawString(
                                cmdX, menuRow, 2,
                                clipMenu("Foe defends (shield)."),
                                CPColor.C_GREY70(),
                                Option.apply(PANEL_BG),
                            )
                        }
                    }
                    DuelPhase.VICTORY -> {
                        canv.drawString(cmdX, menuRow, 2, "Victory — foe falls.", CPColor.C_GREEN1(), Option.apply(PANEL_BG))
                        menuRow += 1
                        if (GameState.bossRushActive) {
                            val nextCheckpoint = LevelConfig.nextBossCheckpoint(GameState.currentLevel)
                            if (nextCheckpoint != null) {
                                canv.drawString(
                                    cmdX, menuRow, 2,
                                    "SPACE  Next boss (floor $nextCheckpoint)",
                                    CPColor.C_GREY70(),
                                    Option.apply(PANEL_BG),
                                )
                            } else {
                                canv.drawString(
                                    cmdX, menuRow, 2,
                                    "SPACE  Finish Boss Rush",
                                    CPColor.C_GREY70(),
                                    Option.apply(PANEL_BG),
                                )
                            }
                        } else {
                            canv.drawString(
                                cmdX, menuRow, 2,
                                "SPACE  Continue",
                                CPColor.C_GREY70(),
                                Option.apply(PANEL_BG),
                            )
                        }
                    }
                    DuelPhase.DEFEAT -> {
                        canv.drawString(cmdX, menuRow, 2, "Defeat…", CPColor.C_ORANGE_RED1(), Option.apply(PANEL_BG))
                        menuRow += 1
                        canv.drawString(
                            cmdX, menuRow, 2,
                            "SPACE  Run summary",
                            CPColor.C_GREY70(),
                            Option.apply(PANEL_BG),
                        )
                    }
                }

                val statusClip = clipMenu(status)
                canv.drawString(textLeft, boxBottom - 2, 2, statusClip, CPColor.C_GREY70(), Option.apply(PANEL_BG))
                canv.drawString(textLeft, boxBottom - 1, 2, "Q / ESC  Quit", CPColor.C_GREY50(), Option.apply(PANEL_BG))
            }
        }

        return CPScene(
            SceneId.BOSS_BATTLE.id,
            Option.empty(),
            bgPx,
            scalaSeqOf(displaySprite, inputSprite)
        )
    }

    private fun renderFallbackListUi(
        canv: CPCanvas,
        w: Int,
        h: Int,
        profile: BossProfile,
        bossName: String,
        mechanicsNote: String,
        phase: DuelPhase,
        round: Int,
        bossHp: Int,
        bossIntent: DuelIntent,
        status: String,
        riposteBonus: Int,
        bossGuardStacks: Int,
    ) {
        val lines = mutableListOf<Pair<String, CPColor>>()
        lines += "BOSS BATTLE - FLOOR ${GameState.currentLevel}" to CPColor.C_GOLD1()
        lines += bossName to CPColor.C_ORANGE1()
        lines += mechanicsNote to CPColor.C_GREY70()
        if (profile.id == BossId.DUELIST) {
            lines += DUEL_GAMBIT_SUMMARY to CPColor.C_GREY50()
        }
        lines += "" to CPColor.C_GREY50()
        lines += "Round: $round" to CPColor.C_GREY70()
        lines += "Your HP: ${GameState.playerHealth}   ATK: ${GameState.effectivePlayerAttack()}   Riposte: +$riposteBonus" to CPColor.C_STEEL_BLUE1()
        lines += "Boss HP: ${bossHp.coerceAtLeast(0)} / ${profile.hp}   Boss ATK: ${profile.attack + bossGuardStacks}" to CPColor.C_INDIAN_RED1()
        lines += "Boss intent: ${if (bossIntent == DuelIntent.ATTACK) "ATTACK" else "DEFEND"}" to
            if (bossIntent == DuelIntent.ATTACK) CPColor.C_ORANGE_RED1() else CPColor.C_YELLOW()
        lines += "" to CPColor.C_GREY50()

        when (phase) {
            DuelPhase.INTRO -> {
                lines += "Press SPACE to engage." to CPColor.C_GREEN1()
            }
            DuelPhase.PLAYER_CHOICE -> {
                lines += "[↑] Attack    [↓] Defend" to CPColor.C_GREEN1()
                if (bossIntent == DuelIntent.ATTACK) {
                    lines += "Defend gives a larger parry window." to CPColor.C_GREY70()
                } else {
                    lines += "Boss is bracing; attacks are reduced this round." to CPColor.C_GREY70()
                }
            }
            DuelPhase.PLAYER_ACTION_ANIM -> {
                lines += "Round resolving — your action." to CPColor.C_ORANGE1()
            }
            DuelPhase.ENEMY_ACTION_ANIM -> {
                lines += "Foe acts: SPACE to parry on impact (if they attack)." to CPColor.C_ORANGE1()
            }
            DuelPhase.VICTORY -> {
                lines += "Boss defeated." to CPColor.C_GREEN1()
                if (GameState.bossRushActive) {
                    val nextCheckpoint = LevelConfig.nextBossCheckpoint(GameState.currentLevel)
                    if (nextCheckpoint != null) {
                        lines += "Press SPACE for next boss (floor $nextCheckpoint)." to CPColor.C_GREY70()
                    } else {
                        lines += "Press SPACE to finish Boss Rush." to CPColor.C_GREY70()
                    }
                } else {
                    lines += "Press SPACE to continue." to CPColor.C_GREY70()
                }
            }
            DuelPhase.DEFEAT -> {
                lines += "You were defeated in the boss battle." to CPColor.C_ORANGE_RED1()
                lines += "Press SPACE for run summary." to CPColor.C_GREY70()
            }
        }

        lines += "" to CPColor.C_GREY50()
        lines += status to CPColor.C_GREY70()
        lines += "" to CPColor.C_GREY50()
        lines += "[Q / ESC] Quit game" to CPColor.C_GREY50()

        var row = (h / 2 - lines.size).coerceAtLeast(1)
        for ((txt, col) in lines) {
            if (txt.isNotEmpty()) {
                canv.drawString(centerStringX(txt, w), row, 1, txt, col, Option.empty())
            }
            row += 1
        }
    }
}
