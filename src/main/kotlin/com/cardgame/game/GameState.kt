package com.cardgame.game

import com.cardgame.art.CardArt
import com.cardgame.quest.ActiveQuest
import com.cardgame.scene.SceneId
import com.cardgame.scene.ShopDismissAction
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
    data class EnemyDeckCard(
        val enemyKind: EnemyKind? = null,
        val isElite: Boolean = false,
        val hazardType: ItemType? = null,
        /** Set when [hazardType] is [ItemType.CHEST]; ignored for other hazards. */
        val hazardTier: KeyTier? = null,
    )

    data class DeckSnapshot(
        val draw: Int,
        val discard: Int,
        val top: String,
    )

    /** Which deck supplies the next grid spawn; shown in the HUD spawn queue. */
    enum class SpawnSource {
        ENEMY,
        PLAYER,
    }

    enum class PlayerDeckCard(val label: String) {
        POTION("Potion"),
        SHIELD("Shield"),
        SWORD("Sword"),
        BOW_ARROW("Bow+Arrow"),
        CAMPFIRE("Campfire"),
        ARMOR("Armor"),
        KEY_BRONZE("Bronze Key"),
        KEY_SILVER("Silver Key"),
        KEY_GOLD("Gold Key"),
        CHEST("Chest"),
        ;

        /** Grid key tier for key cards; null for non-key cards. */
        fun keyTier(): KeyTier? = when (this) {
            KEY_BRONZE -> KeyTier.BRONZE
            KEY_SILVER -> KeyTier.SILVER
            KEY_GOLD -> KeyTier.GOLD
            else -> null
        }
    }

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

    /**
     * At [ShopScene], spend this many keys of a lower tier to receive **one** key of the next tier
     * (bronze→silver, silver→gold). Gold cannot be traded up.
     */
    const val KEY_TRADE_UP_COST = 3

    /** 1-based; set before [GameScene.create]. */
    var currentLevel: Int = 1

    /** How [ShopScene] should exit when the player presses Back / ESC. */
    var shopDismissAction: ShopDismissAction = ShopDismissAction.SwitchTo(SceneId.MENU)

    /** Where [MiniGamesHubScene] goes on Back (e.g. from the gambling tile → [SceneId.GAME]). */
    var minigamesReturnScene: SceneId = SceneId.MENU

    /** Where [InventoryScene] returns after Back / ESC / I. */
    var inventoryReturnScene: SceneId = SceneId.MENU

    /**
     * From the Z-key debug menu: no HP loss from combat, spikes, or bomb blasts; highlights secret-room tiles.
     */
    var debugInvincible: Boolean = false

    /** From the Z-key debug menu: enemy kills do not increase [score]. */
    var debugNoScore: Boolean = false

    /** How the left-column spawn queue is drawn; cycle from the Z debug menu with [3]. Panel = one grid cell. */
    enum class SpawnQueueHudStyle {
        /** NEXT + [Enemy] / [Player] lines, centered in the card panel. */
        LABELED,
        /** QUEUE + rule + `>E` / ` P` lines. */
        COMPACT,
        /** ORDER + `1E` `2P` … */
        NUMBERED,
        /** Title + single line `E · P · …`. */
        TIMELINE,
        /** Top/bottom rules with centered list inside. */
        BOXED,
    }

    var spawnQueueHudStyle: SpawnQueueHudStyle = SpawnQueueHudStyle.LABELED

    fun cycleSpawnQueueHudStyle() {
        val e = SpawnQueueHudStyle.entries
        spawnQueueHudStyle = e[(spawnQueueHudStyle.ordinal + 1) % e.size]
    }

    fun addScorePoints(delta: Int) {
        if (delta <= 0 || debugNoScore) return
        score += delta
    }

    /**
     * When an elite kill pushes [score] to the current level target, we defer the level-complete
     * transition until the player picks up a grid [ItemType.KEY] (the elite drop), so they are not
     * pulled away before collecting it.
     */
    var deferLevelCompleteForEliteKey: Boolean = false

    /**
     * After defeating an **elite** with [EnemyCard.secretRoom], the secret room opens only after the
     * player moves onto that cell again (typically after collecting the dropped key). Cleared when
     * entering the secret room or on level advance/reset.
     */
    var secretRoomPendingCell: Pair<Int, Int>? = null

    /** Set before switching to the run summary scene (death vs full run win). */
    var runEndKind: RunEndKind = RunEndKind.DEATH

    /**
     * Cards the shop may roll for sale. [PlayerDeckCard.CHEST] is excluded — treasure chests spawn
     * from the enemy deck only, not as purchased player-deck cards.
     */
    private val SHOP_OFFER_POOL = listOf(
        PlayerDeckCard.POTION,
        PlayerDeckCard.SHIELD,
        PlayerDeckCard.SWORD,
        PlayerDeckCard.BOW_ARROW,
        PlayerDeckCard.CAMPFIRE,
        PlayerDeckCard.ARMOR,
        PlayerDeckCard.KEY_BRONZE,
        PlayerDeckCard.KEY_SILVER,
        PlayerDeckCard.KEY_GOLD,
    )
    private const val ENEMY_DECK_BUILD_SIZE = 30

    /** Visible upcoming spawn sources; consumed by [drawSpawnForCell]. */
    const val SPAWN_QUEUE_DISPLAY_SIZE = 5

    /**
     * Full board at level start: every non-player cell is one deck draw — this many from the enemy
     * deck and this many from the player deck; assignment order is shuffled. Must equal
     * [GridConfig.COLS] * [GridConfig.ROWS] - 1.
     */
    const val INITIAL_BOARD_ENEMY_DRAWS = 10
    const val INITIAL_BOARD_PLAYER_DRAWS = 4

    private val spawnQueue: MutableList<SpawnSource> = mutableListOf()

    private val enemyDeckDraw: MutableList<EnemyDeckCard> = mutableListOf()
    private val enemyDeckDiscard: MutableList<EnemyDeckCard> = mutableListOf()
    private val playerDeckDraw: MutableList<PlayerDeckCard> = mutableListOf()
    private val playerDeckDiscard: MutableList<PlayerDeckCard> = mutableListOf()
    private val characterDecks: MutableMap<PlayerCharacter, MutableList<PlayerDeckCard>> =
        mutableMapOf(
            PlayerCharacter.KNIGHT to mutableListOf(
                PlayerDeckCard.POTION,
                PlayerDeckCard.SHIELD,
                PlayerDeckCard.SHIELD,
                PlayerDeckCard.SWORD,
                PlayerDeckCard.SWORD,
            ),
            PlayerCharacter.THIEF to mutableListOf(
                PlayerDeckCard.BOW_ARROW,
                PlayerDeckCard.BOW_ARROW,
                PlayerDeckCard.POTION,
                PlayerDeckCard.CAMPFIRE,
                PlayerDeckCard.SWORD,
            ),
            PlayerCharacter.MAGE to mutableListOf(
                PlayerDeckCard.POTION,
                PlayerDeckCard.POTION,
                PlayerDeckCard.POTION,
                PlayerDeckCard.CAMPFIRE,
                PlayerDeckCard.ARMOR,
            ),
        )

    /** Accepted quests in progress (progress counts only actions after acceptance). */
    val activeQuests: MutableList<ActiveQuest> = mutableListOf()

    /** Max concurrent accepted quests; also limits quest-tile spawns and new offers when full. */
    const val MAX_CONCURRENT_QUESTS = 5

    fun canSpawnQuestTile(): Boolean =
        !hasCompletedAllQuestTemplates() && activeQuests.size < MAX_CONCURRENT_QUESTS

    fun canAcceptNewQuest(): Boolean = activeQuests.size < MAX_CONCURRENT_QUESTS

    /** Template ids for quests already accepted (incomplete) — exclude from new offers. */
    fun activeIncompleteQuestTemplateIds(): Set<String> =
        activeQuests.map { it.template.id }.toSet()
    /** Quest shown in [QuestScene] after stepping on a quest tile. */
    var pendingQuestOffer: QuestTemplate? = null
    /**
     * Stepped on a quest tile while [activeQuests] was already at [MAX_CONCURRENT_QUESTS];
     * [QuestScene] shows a full-log message (no accept).
     */
    var pendingQuestAtCapacity: Boolean = false
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

    fun canTradeBronzeKeysForSilver(): Boolean = keysBronze >= KEY_TRADE_UP_COST

    fun canTradeSilverKeysForGold(): Boolean = keysSilver >= KEY_TRADE_UP_COST

    /** Spend [KEY_TRADE_UP_COST] bronze keys for 1 silver. */
    fun tryTradeBronzeKeysForSilver(): Boolean {
        if (!canTradeBronzeKeysForSilver()) return false
        keysBronze -= KEY_TRADE_UP_COST
        keysSilver++
        return true
    }

    /** Spend [KEY_TRADE_UP_COST] silver keys for 1 gold. */
    fun tryTradeSilverKeysForGold(): Boolean {
        if (!canTradeSilverKeysForGold()) return false
        keysSilver -= KEY_TRADE_UP_COST
        keysGold++
        return true
    }

    /**
     * Counts of key [PlayerDeckCard]s in the current draw + discard piles (not yet resolved on the grid).
     * Order: bronze, silver, gold.
     */
    fun playerDeckKeyCardsInPiles(): Triple<Int, Int, Int> {
        val all = playerDeckDraw + playerDeckDiscard
        val b = all.count { it == PlayerDeckCard.KEY_BRONZE }
        val s = all.count { it == PlayerDeckCard.KEY_SILVER }
        val g = all.count { it == PlayerDeckCard.KEY_GOLD }
        return Triple(b, s, g)
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
        shopDismissAction = ShopDismissAction.SwitchTo(SceneId.MENU)
        minigamesReturnScene = SceneId.MENU
        inventoryReturnScene = SceneId.MENU
        gameOver = false
        playerGridX = 0
        playerGridY = 0
        runEndKind = RunEndKind.DEATH
        RunStats.reset()
        activeQuests.clear()
        pendingQuestOffer = null
        pendingQuestAtCapacity = false
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
        deferLevelCompleteForEliteKey = false
        secretRoomPendingCell = null
        spawnQueue.clear()
        refillSpawnQueue()
        resetPlayerDeck()
        rebuildEnemyDeckForCurrentLevel()
        debugInvincible = false
        debugNoScore = false
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
            deferLevelCompleteForEliteKey = false
            secretRoomPendingCell = null
            spawnQueue.clear()
            refillSpawnQueue()
            rebuildEnemyDeckForCurrentLevel()
        }
    }

    fun purchaseDeckCardOptions(): List<PlayerDeckCard> =
        List(3) { SHOP_OFFER_POOL.random() }

    fun addCardToPlayerDeck(card: PlayerDeckCard) {
        if (card != PlayerDeckCard.CHEST && card !in SHOP_OFFER_POOL) return
        val deck = characterDecks.getOrPut(selectedPlayerCharacter) { mutableListOf() }
        deck.add(card)
        playerDeckDiscard.add(card)
        persistDecksIfEnabled()
    }

    fun playerDeckSnapshot(): DeckSnapshot {
        val top = playerDeckDraw.firstOrNull()?.label ?: "reshuffle"
        return DeckSnapshot(playerDeckDraw.size, playerDeckDiscard.size, top)
    }

    fun characterDeckPreview(character: PlayerCharacter): String {
        val d = characterDecks[character].orEmpty()
        val counts = PlayerDeckCard.entries.associateWith { c -> d.count { it == c } }
            .filter { it.value > 0 }
            .entries
            .joinToString(" ") { "${it.value}${it.key.label.take(1)}" }
        return if (counts.isBlank()) "empty" else counts
    }

    fun characterDeckCards(character: PlayerCharacter): List<PlayerDeckCard> =
        characterDecks[character]?.toList() ?: emptyList()

    fun enemyDeckSnapshot(): DeckSnapshot {
        val top = enemyDeckDraw.firstOrNull()?.let {
            when {
                it.hazardType == ItemType.CHEST && it.hazardTier != null ->
                    "${it.hazardType.label} ${it.hazardTier.name.take(1)}"
                it.hazardType != null -> it.hazardType.label
                else -> (it.enemyKind?.displayName ?: "Unknown") + if (it.isElite) "★" else ""
            }
        } ?: "reshuffle"
        return DeckSnapshot(enemyDeckDraw.size, enemyDeckDiscard.size, top)
    }

    /** Upcoming spawn sources (enemy vs player deck); always length [SPAWN_QUEUE_DISPLAY_SIZE]. */
    fun peekSpawnQueue(): List<SpawnSource> {
        refillSpawnQueue()
        return spawnQueue.toList()
    }

    private fun refillSpawnQueue() {
        while (spawnQueue.size < SPAWN_QUEUE_DISPLAY_SIZE) {
            spawnQueue.add(if (Random.nextBoolean()) SpawnSource.ENEMY else SpawnSource.PLAYER)
        }
    }

    private fun popSpawnSource(): SpawnSource {
        refillSpawnQueue()
        val s = spawnQueue.removeAt(0)
        refillSpawnQueue()
        return s
    }

    private fun resetPlayerDeck() {
        playerDeckDraw.clear()
        playerDeckDiscard.clear()
        val deck = characterDecks[selectedPlayerCharacter].orEmpty()
        if (deck.isEmpty()) {
            playerDeckDraw.addAll(
                listOf(
                    PlayerDeckCard.POTION,
                    PlayerDeckCard.POTION,
                    PlayerDeckCard.POTION,
                    PlayerDeckCard.CAMPFIRE,
                    PlayerDeckCard.ARMOR,
                )
            )
        } else {
            // Preserve persistent deck order; draws consume left-to-right without random picking.
            playerDeckDraw.addAll(deck)
        }
    }

    fun rebuildEnemyDeckForCurrentLevel() {
        enemyDeckDraw.clear()
        enemyDeckDiscard.clear()
        enemyDeckDraw.addAll(LevelGenerator.buildEnemyDeckForLevel(currentLevel, ENEMY_DECK_BUILD_SIZE).shuffled())
        persistDecksIfEnabled()
    }

    private fun drawEnemyDeckCard(): EnemyDeckCard {
        if (enemyDeckDraw.isEmpty()) {
            if (enemyDeckDiscard.isNotEmpty()) {
                enemyDeckDraw.addAll(enemyDeckDiscard.shuffled())
                enemyDeckDiscard.clear()
            } else {
                rebuildEnemyDeckForCurrentLevel()
            }
        }
        if (enemyDeckDraw.isEmpty()) {
            return EnemyDeckCard(enemyKind = EnemyKind.RAT)
        }
        return enemyDeckDraw.removeAt(0)
    }

    private fun drawPlayerDeckCard(): PlayerDeckCard {
        if (playerDeckDraw.isEmpty()) {
            if (playerDeckDiscard.isNotEmpty()) {
                // Cycle discard back into draw in order (queue semantics).
                playerDeckDraw.addAll(playerDeckDiscard)
                playerDeckDiscard.clear()
            } else {
                resetPlayerDeck()
            }
        }
        return playerDeckDraw.removeAt(0)
    }

    private fun hasVisibleEquipmentOnBoard(existingItems: List<GridItem>): Boolean =
        existingItems.any { !it.collected && it.type.equipmentSlot() != null }

    private fun drawPlayerDeckCardForSpawn(existingItems: List<GridItem>): PlayerDeckCard {
        val maxAttempts = (playerDeckDraw.size + playerDeckDiscard.size).coerceAtLeast(1)
        repeat(maxAttempts) {
            val c = drawPlayerDeckCard()
            // At most one equipment tile visible at once.
            if (c == PlayerDeckCard.ARMOR && hasVisibleEquipmentOnBoard(existingItems)) {
                playerDeckDraw.add(c)
                return@repeat
            }
            return c
        }
        // If we couldn't find a legal card (e.g., deck is all ARMOR while one equipment tile is up),
        // fall back to a non-equipment utility card to avoid deadlock.
        return PlayerDeckCard.POTION
    }

    private fun toDeckCard(itemType: ItemType): PlayerDeckCard? = when (itemType) {
        ItemType.HEALTH_POTION -> PlayerDeckCard.POTION
        ItemType.SHIELD -> PlayerDeckCard.SHIELD
        ItemType.ATTACK_BOOST -> PlayerDeckCard.SWORD
        ItemType.REST -> PlayerDeckCard.CAMPFIRE
        ItemType.HAND_ARMOR, ItemType.HELMET, ItemType.NECKLACE,
        ItemType.CHEST_ARMOR, ItemType.LEGGINGS, ItemType.BOOTS_ARMOR -> PlayerDeckCard.ARMOR
        else -> null
    }

    fun deckCardToItemType(card: PlayerDeckCard): ItemType = when (card) {
        PlayerDeckCard.POTION -> ItemType.HEALTH_POTION
        PlayerDeckCard.SHIELD -> ItemType.SHIELD
        PlayerDeckCard.SWORD -> ItemType.ATTACK_BOOST
        PlayerDeckCard.BOW_ARROW -> ItemType.ATTACK_BOOST
        PlayerDeckCard.CAMPFIRE -> ItemType.REST
        PlayerDeckCard.ARMOR -> ItemType.HAND_ARMOR
        PlayerDeckCard.KEY_BRONZE, PlayerDeckCard.KEY_SILVER, PlayerDeckCard.KEY_GOLD -> ItemType.KEY
        PlayerDeckCard.CHEST -> ItemType.CHEST
    }

    private fun playerDeckCardFromResolvedPickup(item: GridItem): PlayerDeckCard? =
        when (item.type) {
            ItemType.KEY -> when (item.tier) {
                KeyTier.BRONZE -> PlayerDeckCard.KEY_BRONZE
                KeyTier.SILVER -> PlayerDeckCard.KEY_SILVER
                KeyTier.GOLD -> PlayerDeckCard.KEY_GOLD
            }
            else -> toDeckCard(item.type)
        }

    fun onSpawnedItemResolved(item: GridItem) {
        if (item.type == ItemType.KEY) {
            deferLevelCompleteForEliteKey = false
        }
        when {
            item.type == ItemType.CHEST -> {
                if (item.spawnedFromEnemyDeck) {
                    enemyDeckDiscard.add(EnemyDeckCard(hazardType = ItemType.CHEST, hazardTier = item.tier))
                } else {
                    playerDeckDiscard.add(PlayerDeckCard.CHEST)
                }
            }
            item.type == ItemType.SPIKES || item.type == ItemType.BOMB || item.type == ItemType.WALL -> {
                enemyDeckDiscard.add(EnemyDeckCard(hazardType = item.type))
            }
            item.type == ItemType.QUEST && item.spawnedFromEnemyDeck -> {
                enemyDeckDiscard.add(EnemyDeckCard(hazardType = ItemType.QUEST))
            }
            else -> {
                playerDeckCardFromResolvedPickup(item)?.let { dc ->
                    if (dc == PlayerDeckCard.ARMOR) {
                        // Armor is consumed permanently; don't cycle it back into the draw/discard loop.
                        val deck = characterDecks.getOrPut(selectedPlayerCharacter) { mutableListOf() }
                        val idx = deck.indexOfFirst { it == PlayerDeckCard.ARMOR }
                        if (idx >= 0) deck.removeAt(idx) else Unit
                    } else {
                        playerDeckDiscard.add(dc)
                    }
                }
            }
        }
        persistDecksIfEnabled()
    }

    fun onSpawnedEnemyResolved(enemy: EnemyCard) {
        enemyDeckDiscard.add(EnemyDeckCard(enemyKind = enemy.kind, isElite = enemy.isElite))
        persistDecksIfEnabled()
    }

    /** Spawns a grid item from a drawn [PlayerDeckCard]; key cards use the tier that matches the deck entry. */
    private fun spawnGridItemFromPlayerDeckCard(
        card: PlayerDeckCard,
        gridX: Int,
        gridY: Int,
        existingItems: List<GridItem>,
        existingEnemies: List<EnemyCard>,
    ): GridItem {
        val kt = card.keyTier()
        return if (kt != null) {
            LevelGenerator.spawnKeyAtTier(kt, gridX, gridY, existingItems, existingEnemies)
        } else {
            LevelGenerator.spawnItemFromType(
                deckCardToItemType(card),
                gridX,
                gridY,
                existingItems,
                existingEnemies,
            )
        }
    }

    fun drawSpawnForCell(
        gridX: Int,
        gridY: Int,
        existingItems: List<GridItem>,
        existingEnemies: List<EnemyCard>,
        /** If set (e.g. initial board fill), does not consume the HUD spawn queue. */
        spawnSourceOverride: SpawnSource? = null,
    ): Pair<GridItem?, EnemyCard?> {
        val spawnEnemySide = if (spawnSourceOverride != null) {
            spawnSourceOverride == SpawnSource.ENEMY
        } else {
            popSpawnSource() == SpawnSource.ENEMY
        }
        val result = if (spawnEnemySide) {
            val d = drawEnemyDeckCard()
            if (d.hazardType != null) {
                val spawned = when (d.hazardType) {
                    ItemType.CHEST -> {
                        val tier = d.hazardTier ?: LevelGenerator.keyTierForLevel(currentLevel)
                        LevelGenerator.spawnChestAtTier(
                            tier,
                            gridX,
                            gridY,
                            spawnedFromEnemyDeck = true,
                        )
                    }
                    else -> LevelGenerator.spawnItemFromType(
                        d.hazardType,
                        gridX,
                        gridY,
                        existingItems,
                        existingEnemies,
                        spawnedFromEnemyDeck = true,
                    )
                }
                Pair(spawned, null)
            } else {
                Pair(
                    null,
                    LevelGenerator.spawnEnemyFromSpec(
                        kind = d.enemyKind ?: EnemyKind.RAT,
                        elite = d.isElite,
                        gridX = gridX,
                        gridY = gridY,
                        existingEnemies = existingEnemies,
                        existingItems = existingItems,
                    )
                )
            }
        } else {
            val c = drawPlayerDeckCardForSpawn(existingItems)
            Pair(spawnGridItemFromPlayerDeckCard(c, gridX, gridY, existingItems, existingEnemies), null)
        }
        persistDecksIfEnabled()
        return result
    }

    /** Legacy save files used [KEY]; map to [PlayerDeckCard.KEY_BRONZE]. */
    private fun parsePersistedPlayerDeckCard(raw: String): PlayerDeckCard? {
        val t = raw.trim()
        if (t == "KEY") return PlayerDeckCard.KEY_BRONZE
        return runCatching { PlayerDeckCard.valueOf(t) }.getOrNull()
    }

    /** Call once at process start (after [GameState] defaults exist) to restore saved decks. */
    fun loadDeckPersistenceAtStartup() {
        if (!DeckPersistence.enabled()) return
        try {
            val text = DeckPersistence.load() ?: return
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.firstOrNull() != "v1") return
            val map = lines.drop(1).mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.take(idx) to line.drop(idx + 1)
            }.toMap()
            map["sel"]?.let { sel ->
                runCatching { selectedPlayerCharacter = PlayerCharacter.valueOf(sel) }
            }
            for (ch in PlayerCharacter.entries) {
                val csv = map["cd_${ch.name}"] ?: continue
                val list = characterDecks.getOrPut(ch) { mutableListOf() }
                list.clear()
                if (csv.isNotBlank()) {
                    csv.split(",").forEach { part ->
                        parsePersistedPlayerDeckCard(part)?.let { list.add(it) }
                    }
                }
            }
            fun parsePlayerCards(key: String, target: MutableList<PlayerDeckCard>) {
                val csv = map[key] ?: return
                target.clear()
                if (csv.isBlank()) return
                csv.split(",").forEach { part ->
                    parsePersistedPlayerDeckCard(part)?.let { target.add(it) }
                }
            }
            parsePlayerCards("pd", playerDeckDraw)
            parsePlayerCards("prd", playerDeckDiscard)
            fun parseEnemyLine(key: String, target: MutableList<EnemyDeckCard>) {
                val raw = map[key] ?: return
                target.clear()
                if (raw.isBlank()) return
                for (seg in raw.split('|')) {
                    if (seg.isBlank()) continue
                    decodeEnemyDeckCardPersist(seg)?.let { target.add(it) }
                }
            }
            parseEnemyLine("ed", enemyDeckDraw)
            parseEnemyLine("edd", enemyDeckDiscard)
            map["sq"]?.let { csv ->
                spawnQueue.clear()
                if (csv.isNotBlank()) {
                    for (part in csv.split(",")) {
                        when (part.trim()) {
                            "E" -> spawnQueue.add(SpawnSource.ENEMY)
                            "P" -> spawnQueue.add(SpawnSource.PLAYER)
                        }
                    }
                }
            }
            refillSpawnQueue()
        } catch (_: Exception) {
            return
        }
    }

    fun persistDecksIfEnabled() {
        if (!DeckPersistence.enabled()) return
        val sb = StringBuilder()
        sb.appendLine("v1")
        sb.appendLine("sel=${selectedPlayerCharacter.name}")
        for (ch in PlayerCharacter.entries) {
            val cards = characterDecks[ch].orEmpty().joinToString(",") { it.name }
            sb.appendLine("cd_${ch.name}=$cards")
        }
        sb.appendLine("pd=${playerDeckDraw.joinToString(",") { it.name }}")
        sb.appendLine("prd=${playerDeckDiscard.joinToString(",") { it.name }}")
        sb.appendLine("ed=${enemyDeckDraw.joinToString("|") { encodeEnemyDeckCardPersist(it) }}")
        sb.appendLine("edd=${enemyDeckDiscard.joinToString("|") { encodeEnemyDeckCardPersist(it) }}")
        refillSpawnQueue()
        sb.appendLine(
            "sq=${spawnQueue.joinToString(",") { if (it == SpawnSource.ENEMY) "E" else "P" }}",
        )
        DeckPersistence.save(sb.toString())
    }

    private fun encodeEnemyDeckCardPersist(c: EnemyDeckCard): String =
        if (c.hazardType != null) {
            val tier = c.hazardTier?.name ?: ""
            "h,${c.hazardType.name},$tier"
        } else {
            val kind = c.enemyKind?.name ?: EnemyKind.RAT.name
            "e,$kind,${if (c.isElite) 1 else 0}"
        }

    private fun decodeEnemyDeckCardPersist(seg: String): EnemyDeckCard? {
        val p = seg.split(',')
        if (p.isEmpty()) return null
        return when (p[0]) {
            "e" -> {
                if (p.size < 3) return null
                val kind = runCatching { EnemyKind.valueOf(p[1]) }.getOrNull() ?: return null
                EnemyDeckCard(enemyKind = kind, isElite = p[2] == "1")
            }
            "h" -> {
                if (p.size < 2) return null
                val hz = runCatching { ItemType.valueOf(p[1]) }.getOrNull() ?: return null
                val tier = p.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { t ->
                    runCatching { KeyTier.valueOf(t) }.getOrNull()
                }
                EnemyDeckCard(hazardType = hz, hazardTier = tier)
            }
            else -> null
        }
    }

    fun acceptQuest(template: QuestTemplate) {
        pendingQuestOffer = null
        pendingQuestAtCapacity = false
        if (activeQuests.any { it.template.id == template.id }) return
        if (activeQuests.size >= MAX_CONCURRENT_QUESTS) return
        completedQuestIds.remove(template.id)
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
        pendingQuestAtCapacity = false
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
    /** When true, [onSpawnedItemResolved] returns this chest to the enemy discard pile (not the player deck). */
    var spawnedFromEnemyDeck: Boolean = false,
)

data class EnemyCard(
    val kind: EnemyKind,
    var health: Int,
    val attack: Int,
    var gridX: Int,
    var gridY: Int,
    var defeated: Boolean = false,
    /** At most one non-defeated elite on the grid at a time; higher stats; key tile spawns on defeat. */
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

    /** Blank lines between the HUD text block and the deck (stacked in the left column). */
    const val GAP_LINES_HUD_TO_DECK = 1
    /** Character columns between the left column (HUD + deck, [CELL_WIDTH] wide) and the grid. */
    const val GAP_COLUMNS_HUD_TO_GRID = 2

    /**
     * Upper bound for quest lines in [GameState.questHudLines] when sizing the default emuterm window.
     */
    private const val QUEST_HUD_LINES_WORST_CASE = GameState.MAX_CONCURRENT_QUESTS

    /**
     * Spawn queue panel width: same as one grid cell / card ([CELL_WIDTH]).
     * Sits immediately left of the HUD column ([GAP_COLUMNS_SPAWN_QUEUE_TO_HUD] gap).
     */
    val SPAWN_QUEUE_PANEL_WIDTH: Int get() = CELL_WIDTH
    /** Gap between spawn queue panel and HUD/deck column. */
    const val GAP_COLUMNS_SPAWN_QUEUE_TO_HUD = 1

    /**
     * Screen rows between stacked left-column cards (controls / spawn queue / inventory).
     * Three full [CELL_HEIGHT] cards + two gaps must fit in [GRID_TOTAL_HEIGHT] (default gap 0).
     */
    const val GAP_LINES_CONTROLS_TO_SPAWN_QUEUE = 0

    /**
     * Minimum CosPlay emuterm canvas: spawn panel + HUD column + gap + grid, centered.
     */
    val MIN_EMUTERM_COLS: Int
        get() = SPAWN_QUEUE_PANEL_WIDTH + GAP_COLUMNS_SPAWN_QUEUE_TO_HUD + CELL_WIDTH +
            GAP_COLUMNS_HUD_TO_GRID + GRID_TOTAL_WIDTH + 4

    val MIN_EMUTERM_ROWS: Int
        get() = clusterPixelHeight(hudTextLineCount(QUEST_HUD_LINES_WORST_CASE))

    /** HUD text lines (approx.): meta + quest block + footer (I/Z live on the controls card). */
    fun hudTextLineCount(questHudLineCount: Int): Int =
        5 + questHudLineCount.coerceAtLeast(1) + 2

    /** Vertical size of the game cluster (spawn queue is left of the HUD column, not below the grid). */
    fun clusterPixelHeight(questHudLineCount: Int): Int = GRID_TOTAL_HEIGHT

    /** Width of HUD + deck column (one playing-card width). */
    val HUD_COLUMN_WIDTH: Int get() = CELL_WIDTH

    // Dynamic layout — set by [updateClusterLayout] each frame from the main game scene.
    var offsetX = 4
        private set
    var offsetY = 2
        private set

    /**
     * Left edge of the spawn-queue panel (one card wide). HUD/deck starts at
     * [hudColumnLeftX] = [clusterOriginX] + [SPAWN_QUEUE_PANEL_WIDTH] + [GAP_COLUMNS_SPAWN_QUEUE_TO_HUD].
     */
    var clusterOriginX = 4
        private set
    /** Left edge of the HUD + deck column (one card width). */
    var hudColumnLeftX = 4
        private set
    /** Top row of the centered game cluster bounding box (max of left stack vs grid height). */
    var clusterOriginY = 2
        private set

    /** First screen row of HUD text (may be below [clusterOriginY] when the grid is taller than the left stack). */
    var hudTopY = 2
        private set
    /** Top row of the dungeon grid (may be below [clusterOriginY] when the left stack is taller). */
    var gridTopY = 2
        private set

    /** Top-left of deck cards (enemy top + player bottom share the same X). */
    var deckScreenX = 4
        private set
    /** Legacy alias used by older animation code; points to [playerDeckScreenY]. */
    var deckScreenY = 2
        private set
    var enemyDeckScreenY = 2
        private set
    var playerDeckScreenY = 2
        private set

    var lastCanvasWidth = 80
        private set
    var lastCanvasHeight = 50
        private set

    /**
     * Spawn queue strip, then HUD + deck in a [CELL_WIDTH] column, gap, then grid. The cluster is centered
     * horizontally and vertically on the canvas.
     */
    fun updateClusterLayout(canvasWidth: Int, canvasHeight: Int, questHudLineCount: Int) {
        lastCanvasWidth = canvasWidth
        lastCanvasHeight = canvasHeight
        val gridH = GRID_TOTAL_HEIGHT
        val clusterW =
            SPAWN_QUEUE_PANEL_WIDTH + GAP_COLUMNS_SPAWN_QUEUE_TO_HUD + CELL_WIDTH +
                GAP_COLUMNS_HUD_TO_GRID + GRID_TOTAL_WIDTH
        val clusterH = clusterPixelHeight(questHudLineCount)
        clusterOriginX = ((canvasWidth - clusterW).coerceAtLeast(0) + 1) / 2
        clusterOriginY = ((canvasHeight - clusterH).coerceAtLeast(0) + 1) / 2
        hudColumnLeftX = clusterOriginX + SPAWN_QUEUE_PANEL_WIDTH + GAP_COLUMNS_SPAWN_QUEUE_TO_HUD
        hudTopY = clusterOriginY
        gridTopY = clusterOriginY
        val oneThird = (gridH / 3).coerceAtLeast(CELL_HEIGHT)
        deckScreenX = hudColumnLeftX
        enemyDeckScreenY = hudTopY
        playerDeckScreenY = hudTopY + oneThird * 2
        deckScreenY = playerDeckScreenY
        offsetX = hudColumnLeftX + CELL_WIDTH + GAP_COLUMNS_HUD_TO_GRID
        offsetY = gridTopY
    }

    fun cellScreenX(gridX: Int) = offsetX + gridX * CELL_WIDTH
    fun cellScreenY(gridY: Int) = offsetY + gridY * CELL_HEIGHT

    private fun leftColumnStackHeight(): Int {
        val gap = GAP_LINES_CONTROLS_TO_SPAWN_QUEUE
        return 3 * CELL_HEIGHT + 2 * gap
    }

    /**
     * Top screen row of the controls card (left column, above the spawn queue).
     * Stack: controls, spawn queue, inventory ([leftColumnStackHeight]).
     */
    fun controlsPanelTopY(): Int {
        val stackH = leftColumnStackHeight()
        return gridTopY + (GRID_TOTAL_HEIGHT - stackH).coerceAtLeast(0) / 2
    }

    /** Top row of the spawn queue panel (below [controlsPanelTopY]). */
    fun spawnQueuePanelTopY(): Int =
        controlsPanelTopY() + CELL_HEIGHT + GAP_LINES_CONTROLS_TO_SPAWN_QUEUE

    /** Top row of the inventory / resources card (below the spawn queue). */
    fun inventoryPanelTopY(): Int =
        spawnQueuePanelTopY() + CELL_HEIGHT + GAP_LINES_CONTROLS_TO_SPAWN_QUEUE

    /**
     * First character row of the quest block in the middle HUD (CARD … lore, then N quest lines).
     * Matches [com.cardgame.scene.GameScene] `createHudSprite`.
     */
    fun questHudTextScreenRow(debugHud: Boolean): Int {
        val ly = hudTopY
        val oneThird = (GRID_TOTAL_HEIGHT / 3).coerceAtLeast(4)
        val midInteriorTop = ly + oneThird + 1
        val midInteriorH = (oneThird - 2).coerceAtLeast(1)
        val dbg = if (debugHud) 1 else 0
        val linesBeforeQuestBlock = 2 + dbg
        val questCount = GameState.questHudLines().size.coerceAtLeast(1)
        val middleLineCount = linesBeforeQuestBlock + questCount
        val visibleCount = minOf(middleLineCount, midInteriorH)
        val middleStartY = midInteriorTop + (midInteriorH - visibleCount).coerceAtLeast(0) / 2
        return middleStartY + linesBeforeQuestBlock
    }
}

object LevelGenerator {
    /** Bronze keys/chests on early floors, silver mid, gold late ([GameState.currentLevel] bands). */
    fun keyTierForLevel(level: Int): KeyTier = when {
        level <= 3 -> KeyTier.BRONZE
        level <= 6 -> KeyTier.SILVER
        else -> KeyTier.GOLD
    }

    /** Chance to place one hidden secret-room trigger on a normal item/enemy tile. */
    private const val SECRET_ROOM_CHANCE = 0.03f
    /**
     * Share of non-hazard loot rolls that draw from the equipment sub-pool (when it is non-empty).
     * Lower = equipment spawns less often vs consumables/keys/etc.
     */
    private const val EQUIPMENT_LOOT_WEIGHT = 0.18f
    private const val WALL_DURABILITY = 2
    private const val ELITE_CHANCE = 0.18f
    /**
     * Shared sequential-roll probabilities for [buildEnemyDeckForLevel] and [randomItemAt] (after the hazard branch):
     * wall, chest, quest, gambling; remainder is an enemy card or general loot (see each call site).
     */
    private const val SPAWNER_HAZARD_CHANCE = 0.05f
    private const val SPAWNER_WALL_CHANCE = 0.05f
    private const val SPAWNER_CHEST_CHANCE = 0.10f
    private const val SPAWNER_QUEST_CHANCE = 0.05f
    private const val SPAWNER_GAMBLING_CHANCE = 0.05f
    /** When quest offers are still available, ensure at least this many quest cards per full enemy deck build. */
    private const val MIN_QUEST_CARDS_PER_ENEMY_DECK = 3

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
            hazardInPool.isNotEmpty() && Random.nextFloat() < SPAWNER_HAZARD_CHANCE ->
                hazardInPool[Random.nextInt(hazardInPool.size)]
            else -> {
                val tunedPool = lootPoolForNonHazardRoll(nonHazardPool, existingItems)
                val canRollWall = ItemType.WALL in tunedPool
                when {
                    canRollWall && Random.nextFloat() < SPAWNER_WALL_CHANCE -> ItemType.WALL
                    Random.nextFloat() < SPAWNER_CHEST_CHANCE && ItemType.CHEST in tunedPool -> ItemType.CHEST
                    GameState.canSpawnQuestTile() &&
                        !GameState.hasCompletedAllQuestTemplates() &&
                        Random.nextFloat() < SPAWNER_QUEST_CHANCE &&
                        ItemType.QUEST in tunedPool -> ItemType.QUEST
                    Random.nextFloat() < SPAWNER_GAMBLING_CHANCE && ItemType.GAMBLING in pool -> ItemType.GAMBLING
                    else -> {
                        val noWallPool = tunedPool.filter { it != ItemType.WALL }
                        val remainder = noWallPool.filter {
                            it != ItemType.CHEST && it != ItemType.QUEST && it != ItemType.GAMBLING
                        }
                        val basePool =
                            if (remainder.isNotEmpty()) remainder
                            else noWallPool.ifEmpty { tunedPool }
                        QuestSystem.tunedItemTypeFromPool(
                            basePool,
                            canSpawnQuestTile = false,
                            allQuestsCompleted = GameState.hasCompletedAllQuestTemplates(),
                        )
                    }
                }
            }
        }
        val tier = when (type) {
            ItemType.CHEST -> keyTierForLevel(GameState.currentLevel)
            else -> KeyTier.entries.random()
        }
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

    fun spawnChestAtTier(
        tier: KeyTier,
        gridX: Int,
        gridY: Int,
        spawnedFromEnemyDeck: Boolean = false,
    ): GridItem {
        val value = when (tier) {
            KeyTier.BRONZE -> Random.nextInt(15, 33)
            KeyTier.SILVER -> Random.nextInt(26, 52)
            KeyTier.GOLD -> Random.nextInt(38, 76)
        }
        return GridItem(
            ItemType.CHEST,
            value,
            gridX,
            gridY,
            artVariant = tier.ordinal,
            tier = tier,
            secretRoom = false,
            spawnedFromEnemyDeck = spawnedFromEnemyDeck,
        )
    }

    fun spawnKeyAtTier(
        tier: KeyTier,
        gridX: Int,
        gridY: Int,
        existingItems: List<GridItem> = emptyList(),
        existingEnemies: List<EnemyCard> = emptyList(),
    ): GridItem {
        val hasSecretRoomAlready =
            existingItems.any { !it.collected && it.secretRoom } ||
                existingEnemies.any { !it.defeated && it.secretRoom }
        val secretRoom =
            !hasSecretRoomAlready && Random.nextFloat() < SECRET_ROOM_CHANCE
        return GridItem(
            ItemType.KEY,
            0,
            gridX,
            gridY,
            artVariant = tier.ordinal,
            tier = tier,
            secretRoom = secretRoom,
        )
    }

    fun spawnItemFromType(
        type: ItemType,
        gridX: Int,
        gridY: Int,
        existingItems: List<GridItem> = emptyList(),
        existingEnemies: List<EnemyCard> = emptyList(),
        spawnedFromEnemyDeck: Boolean = false,
    ): GridItem {
        if (type == ItemType.CHEST) return randomItemAt(gridX, gridY, existingItems, existingEnemies)
        val base = randomItemAt(gridX, gridY, existingItems, existingEnemies)
        if (base.type == type) return base.copy(spawnedFromEnemyDeck = spawnedFromEnemyDeck)
        val tier = KeyTier.entries.random()
        val value = when (type) {
            ItemType.HEALTH_POTION -> Random.nextInt(3, 8)
            ItemType.ATTACK_BOOST -> Random.nextInt(1, 4)
            ItemType.SHIELD -> Random.nextInt(2, 6)
            ItemType.KEY, ItemType.SHOP, ItemType.GAMBLING, ItemType.SPIKES, ItemType.BOMB,
            ItemType.WALL, ItemType.QUEST, ItemType.REST, ItemType.HELMET, ItemType.NECKLACE,
            ItemType.CHEST_ARMOR, ItemType.LEGGINGS, ItemType.BOOTS_ARMOR -> 0
            ItemType.HAND_ARMOR -> 1
            ItemType.CHEST -> 0
        }
        val variants = CardArt.itemVariantCount(type)
        val artVariant = when (type) {
            ItemType.KEY -> tier.ordinal
            else -> if (variants <= 1) 0 else Random.nextInt(variants)
        }
        val itemTier = if (type == ItemType.KEY) tier else KeyTier.BRONZE
        val bombTicks = if (type == ItemType.BOMB) 5 else 0
        val wallHp = if (type == ItemType.WALL) WALL_DURABILITY else 0
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
            spawnedFromEnemyDeck = spawnedFromEnemyDeck,
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

    fun spawnEnemyFromSpec(
        kind: EnemyKind,
        elite: Boolean,
        gridX: Int,
        gridY: Int,
        existingEnemies: List<EnemyCard> = emptyList(),
        existingItems: List<GridItem> = emptyList(),
    ): EnemyCard {
        val (health, attack) = if (elite) {
            Pair(Random.nextInt(12, 22), Random.nextInt(5, 10))
        } else {
            Pair(Random.nextInt(3, 10), Random.nextInt(2, 6))
        }
        val hasSecretRoomAlready =
            existingItems.any { !it.collected && it.secretRoom } ||
                existingEnemies.any { !it.defeated && it.secretRoom }
        val secretRoom = !hasSecretRoomAlready && Random.nextFloat() < SECRET_ROOM_CHANCE
        return EnemyCard(
            kind = kind,
            health = health,
            attack = attack,
            gridX = gridX,
            gridY = gridY,
            defeated = false,
            isElite = elite,
            secretRoom = secretRoom,
        )
    }

    fun buildEnemyDeckForLevel(level: Int, count: Int): List<GameState.EnemyDeckCard> {
        val deck = mutableListOf<GameState.EnemyDeckCard>()
        val pool = LevelConfig.enemyKindsForLevel(level).ifEmpty { listOf(EnemyKind.RAT) }
        repeat(count.coerceAtLeast(1)) {
            if (Random.nextFloat() < SPAWNER_HAZARD_CHANCE) {
                val hz = if (Random.nextBoolean()) ItemType.SPIKES else ItemType.BOMB
                deck.add(GameState.EnemyDeckCard(hazardType = hz))
            } else if (Random.nextFloat() < SPAWNER_WALL_CHANCE) {
                deck.add(GameState.EnemyDeckCard(hazardType = ItemType.WALL))
            } else if (Random.nextFloat() < SPAWNER_CHEST_CHANCE) {
                deck.add(
                    GameState.EnemyDeckCard(
                        hazardType = ItemType.CHEST,
                        hazardTier = keyTierForLevel(level),
                    )
                )
            } else if (
                GameState.canSpawnQuestTile() &&
                !GameState.hasCompletedAllQuestTemplates() &&
                Random.nextFloat() < SPAWNER_QUEST_CHANCE
            ) {
                deck.add(GameState.EnemyDeckCard(hazardType = ItemType.QUEST))
            } else if (Random.nextFloat() < SPAWNER_GAMBLING_CHANCE) {
                deck.add(GameState.EnemyDeckCard(hazardType = ItemType.GAMBLING))
            } else {
                deck.add(
                    GameState.EnemyDeckCard(
                        enemyKind = pool.random(),
                        isElite = Random.nextFloat() < ELITE_CHANCE,
                    )
                )
            }
        }
        ensureMinimumQuestCardsInEnemyDeck(deck)
        return deck
    }

    /**
     * Quest tiles were only rolled on the player-deck item path; enemy-deck draws never included
     * [ItemType.QUEST]. Replace plain enemy slots until the deck has a floor of quest offers when allowed.
     */
    private fun ensureMinimumQuestCardsInEnemyDeck(deck: MutableList<GameState.EnemyDeckCard>) {
        if (!GameState.canSpawnQuestTile() || GameState.hasCompletedAllQuestTemplates()) return
        var q = deck.count { it.hazardType == ItemType.QUEST }
        if (q >= MIN_QUEST_CARDS_PER_ENEMY_DECK) return
        val enemyOnlyIdx = deck.indices.filter {
            val c = deck[it]
            c.hazardType == null && c.enemyKind != null
        }.shuffled()
        var i = 0
        while (q < MIN_QUEST_CARDS_PER_ENEMY_DECK && i < enemyOnlyIdx.size) {
            deck[enemyOnlyIdx[i]] = GameState.EnemyDeckCard(hazardType = ItemType.QUEST)
            q++
            i++
        }
    }

    /**
     * Shuffled spawn sources for the initial board: [GameState.INITIAL_BOARD_ENEMY_DRAWS] enemy-deck cells
     * and [GameState.INITIAL_BOARD_PLAYER_DRAWS] player-deck cells (order randomized with [rng]).
     */
    fun initialBoardSpawnSources(rng: Random = Random.Default): List<GameState.SpawnSource> {
        val sources = buildList {
            repeat(GameState.INITIAL_BOARD_ENEMY_DRAWS) { add(GameState.SpawnSource.ENEMY) }
            repeat(GameState.INITIAL_BOARD_PLAYER_DRAWS) { add(GameState.SpawnSource.PLAYER) }
        }
        return sources.shuffled(rng)
    }

    /**
     * Fills every grid cell except [playerCell] for a new level: exactly [GameState.INITIAL_BOARD_ENEMY_DRAWS]
     * enemy-deck spawns and [GameState.INITIAL_BOARD_PLAYER_DRAWS] player-deck spawns (order shuffled).
     * Respawns during play still use [GameState.drawSpawnForCell] without overrides (spawn queue).
     */
    fun fillAllCellsExcept(playerCell: Pair<Int, Int>): Pair<List<GridItem>, List<EnemyCard>> {
        require(
            GameState.INITIAL_BOARD_ENEMY_DRAWS + GameState.INITIAL_BOARD_PLAYER_DRAWS ==
                GridConfig.COLS * GridConfig.ROWS - 1,
        ) {
            "INITIAL_BOARD_ENEMY_DRAWS + INITIAL_BOARD_PLAYER_DRAWS must equal non-player cells"
        }
        val items = mutableListOf<GridItem>()
        val enemies = mutableListOf<EnemyCard>()
        val cells = (0 until GridConfig.COLS).flatMap { x ->
            (0 until GridConfig.ROWS).map { y -> Pair(x, y) }
        }.filter { it != playerCell }.shuffled()

        val sources = initialBoardSpawnSources()

        for (i in cells.indices) {
            val pos = cells[i]
            val spawn = GameState.drawSpawnForCell(
                pos.first,
                pos.second,
                items,
                enemies,
                spawnSourceOverride = sources[i],
            )
            if (spawn.first != null) {
                items.add(spawn.first!!)
            } else if (spawn.second != null) {
                enemies.add(spawn.second!!)
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
