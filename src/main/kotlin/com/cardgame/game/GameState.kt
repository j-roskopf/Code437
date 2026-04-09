package com.cardgame.game

import com.cardgame.art.CardArt
import com.cardgame.quest.ActiveQuest
import com.cardgame.quest.QuestCountersSnapshot
import com.cardgame.quest.QuestSystem
import com.cardgame.quest.QuestTargetType
import com.cardgame.quest.QuestTemplate
import kotlin.random.Random

enum class RunEndKind {
    DEATH,
    VICTORY,
}

object GameState {
    /** Chosen on the character screen; not cleared by [resetForLevel]. */
    var selectedPlayerCharacter: PlayerCharacter = PlayerCharacter.MAGE

    /** Highlight index on the character screen; set when opening that scene from the menu. */
    var characterSelectCursor: Int = 0

    /** Equipped item per [EquipmentSlot]; full-size art is shown on the inventory screen. */
    val equippedItems: Array<ItemType?> = arrayOfNulls(EquipmentSlot.entries.size)

    /**
     * Absorption pool from [ItemType.SHIELD] pickups; depleted by enemy hits before equipment armor applies.
     */
    var temporaryShield: Int = 0

    /** Sum of [ItemType.equipmentArmorValue] for each filled [equippedItems] slot. */
    fun totalEquipmentArmor(): Int =
        equippedItems.sumOf { type -> type?.equipmentArmorValue() ?: 0 }

    /** Sets [equippedItems] for [slot]. Armor comes from [ItemType.equipmentArmorValue], not [GridItem.value]. */
    fun setEquippedItem(slot: EquipmentSlot, type: ItemType?) {
        equippedItems[slot.ordinal] = type
    }

    fun addTemporaryShield(amount: Int) {
        if (amount <= 0) return
        temporaryShield += amount
    }

    /**
     * Temporary fatigue from wall chipping. Value `> 0` means current action uses `ATK - 1`
     * (min 1). Chipping sets this to 2 so the penalty applies on the next movement action.
     */
    var wallChipAtkPenaltySteps: Int = 0

    fun applyWallChipPenalty() {
        wallChipAtkPenaltySteps += 2
    }

    /** Call once when a movement action starts (before resolving combat). */
    fun onPlayerMovementActionStart() {
        if (wallChipAtkPenaltySteps > 0) wallChipAtkPenaltySteps--
    }

    /** Combat damage dealt this action (base ATK minus temporary wall-chip fatigue). */
    fun effectivePlayerAttack(): Int =
        (playerAttack - if (wallChipAtkPenaltySteps > 0) 1 else 0).coerceAtLeast(1)

    /** HUD / player-card ATK text with temporary penalty marker. */
    fun playerAttackDisplay(): String =
        if (wallChipAtkPenaltySteps > 0) "${effectivePlayerAttack()} (-1)" else "${playerAttack}"

    /** Permanent armor from all equipped pieces (for logic that must not include pickup shield). */
    fun playerShield(): Int = totalEquipmentArmor()

    /** HUD / player card: total equipment armor, with `(+temp)` when pickup shield is active. */
    fun playerShieldDisplay(): String {
        val e = totalEquipmentArmor()
        val t = temporaryShield
        return if (t > 0) "$e (+$t)" else "$e"
    }

    /**
     * Enemy hit: depletes [temporaryShield] first, then subtracts [totalEquipmentArmor]; returns HP damage.
     */
    fun damageAfterEnemyAttack(rawAttack: Int): Int {
        var remaining = rawAttack.coerceAtLeast(0)
        if (temporaryShield > 0 && remaining > 0) {
            val absorb = minOf(remaining, temporaryShield)
            temporaryShield -= absorb
            remaining -= absorb
        }
        return (remaining - totalEquipmentArmor()).coerceAtLeast(0)
    }

    var playerHealth = 20
    var playerAttack = 3
    var score = 0
    /** Gold from chests; persists across levels until a full run reset. */
    var money = 0
    var gameOver = false
    var playerGridX = 0
    var playerGridY = 0

    /** Stackable keys by tier; persists across levels until a full run reset. */
    var keysBronze: Int = 0
    var keysSilver: Int = 0
    var keysGold: Int = 0

    /** 1-based; set before [GameScene.create]. */
    var currentLevel: Int = 1

    /** Where [ShopScene] goes on Back: `"menu"` or `"levelcomplete"`. */
    var shopReturnScene: String = "menu"

    /** Where [MiniGamesHubScene] goes on Back: `"menu"` or `"game"` (from gambling tile). */
    var minigamesReturnScene: String = "menu"

    /** Where [InventoryScene] returns: `"menu"` or `"game"`. */
    var inventoryReturnScene: String = "menu"

    /** Debug-only: reveal which visible item/enemy tile hides a secret room trigger. */
    var debugRevealSecrets: Boolean = true

    /** Set before switching to the run summary scene (death vs full run win). */
    var runEndKind: RunEndKind = RunEndKind.DEATH

    /** Accepted quests in progress (progress counts only actions after acceptance). */
    val activeQuests: MutableList<ActiveQuest> = mutableListOf()

    /** Max concurrent accepted quests; also limits quest-tile spawns when full. */
    private const val MAX_CONCURRENT_QUESTS = 12

    fun canSpawnQuestTile(): Boolean =
        !hasCompletedAllQuestTemplates() && activeQuests.size < MAX_CONCURRENT_QUESTS

    /** Template ids for quests already accepted (incomplete) — exclude from new offers. */
    fun activeIncompleteQuestTemplateIds(): Set<String> =
        activeQuests.map { it.template.id }.toSet()
    /** Quest shown in [QuestScene] after stepping on a quest tile. */
    var pendingQuestOffer: QuestTemplate? = null
    /** One-shot trigger so HUD can play a quest-complete confetti burst. */
    var hudQuestCelebrate: Boolean = false
    var hudQuestFlashText: String = ""
    private var hudQuestFlashFrames: Int = 0

    private val killCounter = EnemyKind.entries.associateWith { 0 }.toMutableMap()
    private var eliteKills: Int = 0
    private var totalKills: Int = 0
    private var goldCollectedForQuest: Int = 0
    private val completedQuestIds = mutableSetOf<String>()

    fun hasKey(tier: KeyTier): Boolean = when (tier) {
        KeyTier.BRONZE -> keysBronze > 0
        KeyTier.SILVER -> keysSilver > 0
        KeyTier.GOLD -> keysGold > 0
    }

    /** Spends one key of [tier] if available. */
    fun tryConsumeKey(tier: KeyTier): Boolean {
        return when (tier) {
            KeyTier.BRONZE -> if (keysBronze > 0) {
                keysBronze--
                true
            } else false
            KeyTier.SILVER -> if (keysSilver > 0) {
                keysSilver--
                true
            } else false
            KeyTier.GOLD -> if (keysGold > 0) {
                keysGold--
                true
            } else false
        }
    }

    fun addKey(tier: KeyTier) {
        when (tier) {
            KeyTier.BRONZE -> keysBronze++
            KeyTier.SILVER -> keysSilver++
            KeyTier.GOLD -> keysGold++
        }
    }

    fun hasAnyKey(): Boolean =
        keysBronze > 0 || keysSilver > 0 || keysGold > 0

    /** Consumes one key, preferring bronze, then silver, then gold. */
    fun consumeAnyKey(): Boolean {
        if (keysBronze > 0) {
            keysBronze--
            return true
        }
        if (keysSilver > 0) {
            keysSilver--
            return true
        }
        if (keysGold > 0) {
            keysGold--
            return true
        }
        return false
    }

    fun reset() {
        resetForLevel(1)
    }

    fun resetForLevel(level: Int) {
        currentLevel = level.coerceIn(1, LevelConfig.COUNT)
        playerHealth = 20
        playerAttack = 3
        score = 0
        money = 0
        keysBronze = 0
        keysSilver = 0
        keysGold = 0
        shopReturnScene = "menu"
        minigamesReturnScene = "menu"
        inventoryReturnScene = "menu"
        gameOver = false
        playerGridX = 0
        playerGridY = 0
        runEndKind = RunEndKind.DEATH
        RunStats.reset()
        activeQuests.clear()
        pendingQuestOffer = null
        hudQuestCelebrate = false
        EnemyKind.entries.forEach { killCounter[it] = 0 }
        eliteKills = 0
        totalKills = 0
        goldCollectedForQuest = 0
        completedQuestIds.clear()
        hudQuestFlashText = ""
        hudQuestFlashFrames = 0
        for (i in equippedItems.indices) equippedItems[i] = null
        temporaryShield = 0
        wallChipAtkPenaltySteps = 0
    }

    fun addMoney(amount: Int) {
        if (amount <= 0) return
        money += amount
        RunStats.recordGoldEarned(amount)
        goldCollectedForQuest += amount
        updateQuestProgress()
    }

    fun trySpendMoney(amount: Int): Boolean {
        if (amount <= 0 || money < amount) return false
        money -= amount
        RunStats.recordGoldSpent(amount)
        return true
    }

    fun advanceToNextLevel() {
        if (currentLevel < LevelConfig.COUNT) {
            currentLevel++
            playerHealth = 20
            score = 0
            gameOver = false
            playerGridX = 0
            playerGridY = 0
        }
    }

    fun acceptQuest(template: QuestTemplate) {
        pendingQuestOffer = null
        if (template.id in completedQuestIds) return
        if (activeQuests.any { it.template.id == template.id }) return
        if (activeQuests.size >= MAX_CONCURRENT_QUESTS) return
        activeQuests.add(
            ActiveQuest(
                template = template,
                baselineAtAccept = snapshotQuestCounters(),
            )
        )
        updateQuestProgress()
    }

    fun denyPendingQuest() {
        pendingQuestOffer = null
    }

    fun completedQuestIds(): Set<String> = completedQuestIds.toSet()

    fun hasCompletedAllQuestTemplates(): Boolean =
        completedQuestIds.size >= QuestSystem.templates.size

    fun registerEnemyDefeat(kind: EnemyKind, elite: Boolean) {
        killCounter[kind] = killCounter.getValue(kind) + 1
        totalKills++
        if (elite) eliteKills++
        updateQuestProgress()
    }

    fun questHudLines(): List<String> {
        val flash = questFlashText()
        if (flash != null) return listOf(flash)
        if (activeQuests.isEmpty()) return listOf("Quest: none")
        return activeQuests.map { q ->
            val p = progressForQuest(q).coerceAtMost(q.template.targetCount)
            val objective = when (q.template.targetType) {
                QuestTargetType.KILL_KIND -> q.template.targetKind?.displayName ?: EnemyKind.RAT.displayName
                QuestTargetType.KILL_ANY -> "Enemies"
                QuestTargetType.KILL_ELITE -> "Elites"
                QuestTargetType.COLLECT_GOLD -> "Gold"
            }
            "${q.template.title} $p/${q.template.targetCount} ($objective)"
        }
    }

    fun questFlashText(): String? =
        if (hudQuestFlashFrames > 0 && hudQuestFlashText.isNotBlank()) hudQuestFlashText else null

    fun tickQuestHudFlash() {
        if (hudQuestFlashFrames > 0) hudQuestFlashFrames--
        if (hudQuestFlashFrames <= 0) {
            hudQuestFlashFrames = 0
            hudQuestFlashText = ""
        }
    }

    private fun snapshotQuestCounters(): QuestCountersSnapshot =
        QuestCountersSnapshot(
            killByKind = EnemyKind.entries.associateWith { kind -> killCounter[kind] ?: 0 },
            totalKills = totalKills,
            eliteKills = eliteKills,
            goldCollected = goldCollectedForQuest,
        )

    /** Progress since this quest was accepted (ignores stats from before acceptance). */
    private fun progressForQuest(q: ActiveQuest): Int {
        val t = q.template
        val s = q.baselineAtAccept
        return when (t.targetType) {
            QuestTargetType.KILL_KIND -> {
                val kind = t.targetKind ?: EnemyKind.RAT
                ((killCounter[kind] ?: 0) - (s.killByKind[kind] ?: 0)).coerceAtLeast(0)
            }
            QuestTargetType.KILL_ANY -> (totalKills - s.totalKills).coerceAtLeast(0)
            QuestTargetType.KILL_ELITE -> (eliteKills - s.eliteKills).coerceAtLeast(0)
            QuestTargetType.COLLECT_GOLD -> (goldCollectedForQuest - s.goldCollected).coerceAtLeast(0)
        }
    }

    private fun updateQuestProgress() {
        if (activeQuests.isEmpty()) return
        val completedRewards = mutableListOf<Int>()
        val iter = activeQuests.iterator()
        while (iter.hasNext()) {
            val q = iter.next()
            val p = progressForQuest(q)
            q.progress = p
            if (p >= q.template.targetCount) {
                completedRewards.add(q.template.rewardGold)
                completedQuestIds.add(q.template.id)
                iter.remove()
            }
        }
        if (completedRewards.isEmpty()) return
        val total = completedRewards.sum()
        hudQuestFlashText = if (completedRewards.size == 1) "Quest Complete +$total GP"
        else "Quests complete +$total GP"
        hudQuestFlashFrames = 120
        hudQuestCelebrate = true
        addMoney(total)
    }

    /** Consumed by [GameScene] to play HUD confetti once. */
    fun consumeHudQuestCelebrate(): Boolean {
        if (!hudQuestCelebrate) return false
        hudQuestCelebrate = false
        return true
    }
}

enum class ItemType(val label: String) {
    HEALTH_POTION("HP Potion"),
    ATTACK_BOOST("ATK Boost"),
    SHIELD("Shield"),
    KEY("Key"),
    CHEST("Chest"),
    SHOP("Shop"),
    /** Opens the mini games hub (slots / Sic Bo). */
    GAMBLING("Gambling"),
    /** Instant death when stepped on. */
    SPIKES("Spikes"),
    /** Counts down each player move; at 0 explodes orthogonally. */
    BOMB("Bomb"),
    /** Static barrier; blocks movement/sliding, but bomb blasts can destroy it. */
    WALL("Wall"),
    /** One-shot tile: offers a quest to accept/deny. */
    QUEST("Quest"),
    /** One-shot tile: restores a percentage of HP. */
    REST("Rest"),
    /** Pick up to equip in [EquipmentSlot.HANDS]; armor from [ItemType.equipmentArmorValue]. */
    HAND_ARMOR("Stone Guard"),
    /** Equip to head slot. */
    HELMET("Helmet"),
    /** Equip to neck slot. */
    NECKLACE("Necklace"),
    /** Equip to chest slot (not a gold chest tile — see [CHEST]). */
    CHEST_ARMOR("Chestplate"),
    /** Equip to pants slot. */
    LEGGINGS("Pants"),
    /** Equip to boots slot. */
    BOOTS_ARMOR("Boots"),
}

/** Keys and chests use the same tier; art index matches [KeyTier.ordinal]. */
enum class KeyTier {
    BRONZE,
    SILVER,
    GOLD,
}

data class GridItem(
    val type: ItemType,
    val value: Int,
    var gridX: Int,
    var gridY: Int,
    var collected: Boolean = false,
    /** Index into [CardArt] variant list for [type] (see [CardArt.itemVariantCount]). */
    val artVariant: Int = 0,
    /** [ItemType.KEY] / [ItemType.CHEST]: must match key art and lock. */
    val tier: KeyTier = KeyTier.BRONZE,
    /** For [ItemType.CHEST]: becomes true after spending a key; tile stays on the grid. */
    var chestOpened: Boolean = false,
    /** For [ItemType.CHEST]: gold moves to [GameState.money] when the player steps on the tile. */
    var chestGoldClaimed: Boolean = false,
    /** For [ItemType.BOMB]: turns remaining until detonation (starts at 5). */
    var bombTicks: Int = 0,
    /** For [ItemType.WALL]: remaining chips before removal (defaults to 2 on spawn). */
    var wallHp: Int = 0,
    /** Hidden flag: stepping here with any key opens a one-shot secret room instead of normal interaction. */
    var secretRoom: Boolean = false,
)

data class EnemyCard(
    val kind: EnemyKind,
    var health: Int,
    val attack: Int,
    var gridX: Int,
    var gridY: Int,
    var defeated: Boolean = false,
    /** At most one non-defeated elite on the grid at a time; higher stats; gold or key on kill. */
    val isElite: Boolean = false,
    /** Hidden flag: stepping here with any key opens a one-shot secret room instead of combat. */
    var secretRoom: Boolean = false,
)

object GridConfig {
    const val COLS = 5
    const val ROWS = 3
    /** Card size in CosPlay canvas character cells (place to draw ASCII / future art). */
    const val CELL_WIDTH = 34
    /** Shorter than width so tiles are not overly tall vs sprites; interior art uses [CELL_HEIGHT]−4 rows (≥18 fits 16-line hero art). */
    const val CELL_HEIGHT = 22

    /** Total character-cell width/height of the grid (without offsets). */
    val GRID_TOTAL_WIDTH get() = COLS * CELL_WIDTH
    val GRID_TOTAL_HEIGHT get() = ROWS * CELL_HEIGHT

    /** Legacy hint for HUD scale (split left/right in [GameScene]); not all lines on one side. */
    const val HUD_LINE_COUNT = 10
    /** Screen columns between HUD text and the grid (avoids overlap). */
    const val HUD_GAP_BEFORE_GRID = 1

    /**
     * Minimum CosPlay emuterm canvas for the main game: centered grid plus left and right HUD margins
     * (each at least [CELL_WIDTH] wide) and [HUD_GAP_BEFORE_GRID] beside the board.
     */
    const val MIN_EMUTERM_COLS =
        COLS * CELL_WIDTH + 2 * HUD_GAP_BEFORE_GRID + 2 * CELL_WIDTH
    const val MIN_EMUTERM_ROWS = ROWS * CELL_HEIGHT

    // Dynamic offsets — grid is horizontally centered; HUD uses columns left and right of the board.
    var offsetX = 4
        private set
    var offsetY = 2
        private set

    /**
     * Center the grid on the visible canvas. CosPlay uses 0-based x/y; last column is [canvasWidth - 1].
     */
    fun updateOffsets(canvasWidth: Int, canvasHeight: Int) {
        val slackX = (canvasWidth - GRID_TOTAL_WIDTH).coerceAtLeast(0)
        offsetX = (slackX + 1) / 2
        val slackY = (canvasHeight - GRID_TOTAL_HEIGHT).coerceAtLeast(0)
        // Integer centering leaves odd slack on the bottom; bias one row upward so margins match.
        offsetY = (slackY + 1) / 2
    }

    fun cellScreenX(gridX: Int) = offsetX + gridX * CELL_WIDTH
    fun cellScreenY(gridY: Int) = offsetY + gridY * CELL_HEIGHT
}

object LevelGenerator {
    /** Share of item spawns that roll a hazard ([ItemType.SPIKES] / [ItemType.BOMB]); rest use normal loot. */
    private const val HAZARD_SPAWN_CHANCE = 0.02f
    /** Chance to place one hidden secret-room trigger on a normal item/enemy tile. */
    private const val SECRET_ROOM_CHANCE = 0.03f
    /**
     * Share of non-hazard loot rolls that draw from the equipment sub-pool (when it is non-empty).
     * Lower = equipment spawns less often vs consumables/keys/etc.
     */
    private const val EQUIPMENT_LOOT_WEIGHT = 0.18f
    /** Target chance for wall on non-hazard item rolls (when walls are allowed). */
    private const val WALL_KEEP_CHANCE = 0.15f
    private const val WALL_DURABILITY = 2

    /**
     * After blocking already-equipped gear, prefer non-equipment most of the time so armor is rarer.
     * Also enforces at most one equipment tile visible on the board at any time.
     */
    private fun lootPoolForNonHazardRoll(nonHazardPool: List<ItemType>, existingItems: List<GridItem>): List<ItemType> {
        val hasEquipmentOnGrid = existingItems.any { !it.collected && it.type.equipmentSlot() != null }
        val filtered = nonHazardPool.filter { type ->
            !type.isBlockedByCurrentEquipment() &&
                (!hasEquipmentOnGrid || type.equipmentSlot() == null)
        }
        if (filtered.isEmpty()) return listOf(ItemType.HEALTH_POTION)

        val equipmentLoot = filtered.filter { it.equipmentSlot() != null }
        val nonEquipmentLoot = filtered.filter { it.equipmentSlot() == null }

        val pickEquipment = Random.nextFloat() < EQUIPMENT_LOOT_WEIGHT
        return when {
            pickEquipment && equipmentLoot.isNotEmpty() -> equipmentLoot
            nonEquipmentLoot.isNotEmpty() -> nonEquipmentLoot
            equipmentLoot.isNotEmpty() -> equipmentLoot
            else -> filtered
        }
    }

    /**
     * @param existingItems current grid items — if any non-collected [ItemType.SHOP] / [ItemType.GAMBLING] exists,
     * another of that kind will not be rolled. [ItemType.GAMBLING] is never rolled while [money] is 0.
     */
    fun randomItemAt(
        gridX: Int,
        gridY: Int,
        existingItems: List<GridItem> = emptyList(),
        existingEnemies: List<EnemyCard> = emptyList()
    ): GridItem {
        val shopOnGrid = existingItems.any { !it.collected && it.type == ItemType.SHOP }
        val gamblingOnGrid = existingItems.any { !it.collected && it.type == ItemType.GAMBLING }
        val pool = ItemType.entries.filter { type ->
            !(shopOnGrid && type == ItemType.SHOP) &&
                !(gamblingOnGrid && type == ItemType.GAMBLING) &&
                !(type == ItemType.GAMBLING && GameState.money <= 0)
        }
        val nonHazardPool = pool.filter { it != ItemType.SPIKES && it != ItemType.BOMB }
        val hazardInPool = listOf(ItemType.SPIKES, ItemType.BOMB).filter { it in pool }
        val type = when {
            nonHazardPool.isEmpty() -> pool[Random.nextInt(pool.size)]
            hazardInPool.isNotEmpty() && Random.nextFloat() < HAZARD_SPAWN_CHANCE ->
                hazardInPool[Random.nextInt(hazardInPool.size)]
            else -> {
                val tunedPool = lootPoolForNonHazardRoll(nonHazardPool, existingItems)
                val canRollWall = ItemType.WALL in tunedPool
                if (canRollWall && Random.nextFloat() < WALL_KEEP_CHANCE) {
                    ItemType.WALL
                } else {
                    val noWallPool = tunedPool.filter { it != ItemType.WALL }
                    val basePool = if (noWallPool.isNotEmpty()) noWallPool else tunedPool
                    QuestSystem.tunedItemTypeFromPool(
                        basePool,
                        canSpawnQuestTile = GameState.canSpawnQuestTile(),
                        allQuestsCompleted = GameState.hasCompletedAllQuestTemplates()
                    )
                }
            }
        }
        val tier = KeyTier.entries.random()
        val value = when (type) {
            ItemType.HEALTH_POTION -> Random.nextInt(3, 8)
            ItemType.ATTACK_BOOST -> Random.nextInt(1, 4)
            ItemType.SHIELD -> Random.nextInt(2, 6)
            ItemType.KEY -> 0
            ItemType.CHEST -> when (tier) {
                KeyTier.BRONZE -> Random.nextInt(15, 33)
                KeyTier.SILVER -> Random.nextInt(26, 52)
                KeyTier.GOLD -> Random.nextInt(38, 76)
            }
            ItemType.SHOP -> 0
            ItemType.GAMBLING -> 0
            ItemType.SPIKES -> 0
            ItemType.BOMB -> 0
            ItemType.WALL -> 0
            ItemType.QUEST -> 0
            ItemType.REST -> 0
            ItemType.HAND_ARMOR -> 1
            ItemType.HELMET,
            ItemType.NECKLACE,
            ItemType.CHEST_ARMOR,
            ItemType.LEGGINGS,
            ItemType.BOOTS_ARMOR -> 0
        }
        val variants = CardArt.itemVariantCount(type)
        val artVariant = when (type) {
            ItemType.KEY, ItemType.CHEST -> tier.ordinal
            else -> if (variants <= 1) 0 else Random.nextInt(variants)
        }
        val itemTier = when (type) {
            ItemType.KEY, ItemType.CHEST -> tier
            else -> KeyTier.BRONZE
        }
        val bombTicks = when (type) {
            ItemType.BOMB -> 5
            else -> 0
        }
        val wallHp = when (type) {
            ItemType.WALL -> WALL_DURABILITY
            else -> 0
        }
        val hasSecretRoomAlready =
            existingItems.any { !it.collected && it.secretRoom } ||
                existingEnemies.any { !it.defeated && it.secretRoom }
        val secretEligible =
            type != ItemType.CHEST &&
                type != ItemType.SHOP &&
                type != ItemType.GAMBLING &&
                type != ItemType.SPIKES &&
                type != ItemType.BOMB &&
                type != ItemType.WALL &&
                type != ItemType.QUEST &&
                type != ItemType.REST
        val secretRoom = !hasSecretRoomAlready && secretEligible && Random.nextFloat() < SECRET_ROOM_CHANCE
        return GridItem(
            type, value, gridX, gridY,
            artVariant = artVariant,
            tier = itemTier,
            bombTicks = bombTicks,
            wallHp = wallHp,
            secretRoom = secretRoom,
        )
    }

    /**
     * @param existingEnemies if any non-defeated [EnemyCard.isElite] exists, a new elite will not be rolled
     * (at most one elite tile on the board).
     */
    fun randomEnemyAt(
        gridX: Int,
        gridY: Int,
        existingEnemies: List<EnemyCard> = emptyList(),
        existingItems: List<GridItem> = emptyList()
    ): EnemyCard {
        val pool = LevelConfig.enemyKindsForLevel(GameState.currentLevel)
        val kind = if (pool.isNotEmpty()) pool[Random.nextInt(pool.size)]
        else EnemyKind.entries[Random.nextInt(EnemyKind.entries.size)]
        val eliteExists = existingEnemies.any { !it.defeated && it.isElite }
        val rollElite = !eliteExists && Random.nextFloat() < 0.18f
        val (health, attack) = if (rollElite) {
            Pair(Random.nextInt(12, 22), Random.nextInt(5, 10))
        } else {
            Pair(Random.nextInt(3, 10), Random.nextInt(2, 6))
        }
        val hasSecretRoomAlready =
            existingItems.any { !it.collected && it.secretRoom } ||
                existingEnemies.any { !it.defeated && it.secretRoom }
        val secretRoom = !hasSecretRoomAlready && Random.nextFloat() < SECRET_ROOM_CHANCE
        return EnemyCard(
            kind,
            health,
            attack,
            gridX,
            gridY,
            defeated = false,
            isElite = rollElite,
            secretRoom = secretRoom,
        )
    }

    /**
     * Fills every grid cell except [playerCell] with a random mix of items and enemies.
     * Refills are independent of what was on a tile before — any cell can become item or enemy.
     */
    fun fillAllCellsExcept(playerCell: Pair<Int, Int>): Pair<List<GridItem>, List<EnemyCard>> {
        val items = mutableListOf<GridItem>()
        val enemies = mutableListOf<EnemyCard>()
        val cells = (0 until GridConfig.COLS).flatMap { x ->
            (0 until GridConfig.ROWS).map { y -> Pair(x, y) }
        }.filter { it != playerCell }.shuffled()

        for (pos in cells) {
            if (Random.nextBoolean()) {
                items.add(randomItemAt(pos.first, pos.second, items, enemies))
            } else {
                enemies.add(randomEnemyAt(pos.first, pos.second, enemies, items))
            }
        }
        ensureOriginNotFullyBlockedByChests(items)
        return Pair(items, enemies)
    }

    /**
     * From (0,0) the player can only move to (1,0) and (0,1). With no keys, a closed [ItemType.CHEST]
     * on a cell blocks movement onto that cell — two adjacent closed chests soft-lock the run.
     */
    private fun isOriginTrappedByClosedChests(items: List<GridItem>): Boolean {
        fun blocksWithoutResource(x: Int, y: Int): Boolean {
            val it = items.find { !it.collected && it.gridX == x && it.gridY == y } ?: return false
            return when (it.type) {
                ItemType.CHEST -> !it.chestOpened && !GameState.hasKey(it.tier)
                ItemType.BOMB, ItemType.WALL -> true
                else -> false
            }
        }
        return blocksWithoutResource(1, 0) && blocksWithoutResource(0, 1)
    }

    private fun ensureOriginNotFullyBlockedByChests(items: MutableList<GridItem>) {
        var guard = 0
        while (isOriginTrappedByClosedChests(items) && guard++ < 64) {
            val fix = listOf(1 to 0, 0 to 1).firstOrNull { (x, y) ->
                val it = items.find { !it.collected && it.gridX == x && it.gridY == y }
                it != null && it.type == ItemType.CHEST && !it.chestOpened
            } ?: break
            val (fx, fy) = fix
            val idx = items.indexOfFirst { !it.collected && it.gridX == fx && it.gridY == fy }
            if (idx < 0) break
            items.removeAt(idx)
            val snapshot = items.toList()
            var replacement: GridItem
            var tries = 0
            do {
                replacement = randomItemAt(fx, fy, snapshot)
                tries++
            } while (replacement.type == ItemType.CHEST && tries < 48)
            if (replacement.type == ItemType.CHEST) {
                replacement = GridItem(ItemType.HEALTH_POTION, Random.nextInt(3, 8), fx, fy)
            }
            items.add(replacement)
        }
    }
}
