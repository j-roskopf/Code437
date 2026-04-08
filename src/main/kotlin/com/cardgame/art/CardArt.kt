package com.cardgame.art

import com.cardgame.game.EnemyKind
import com.cardgame.game.GridItem
import com.cardgame.game.ItemType
import com.cardgame.game.KeyTier
import com.cardgame.game.LevelConfig
import org.cosplay.CPColor

/**
 * Wires tile colors and [AsciiArt] lists to game types.
 *
 * - **Enemies:** [AsciiArt.ENEMY_ARTS] maps each [EnemyKind] to sprite lines; [LevelConfig.enemyKindsForLevel]
 *   decides which kinds spawn per level — every possible kind must still have art here.
 * - **Items:** [AsciiArt.ATTACK_BOOST_ARTS], [AsciiArt.SHIELD_ARTS], etc. — add sprites by appending
 *   to those lists; [GridItem.artVariant] is the index.
 */
object CardArt {

    init {
        val missing = EnemyKind.entries.filter { it !in AsciiArt.ENEMY_ARTS }
        require(missing.isEmpty()) { "ENEMY_ARTS missing keys: $missing" }
    }

    /** One or more sprites per item type; [GridItem.artVariant] picks the index (clamped). */
    private val itemVariants: Map<ItemType, List<List<String>>> = mapOf(
        ItemType.HEALTH_POTION to listOf(AsciiArt.HEALTH_POTION),
        ItemType.ATTACK_BOOST to AsciiArt.ATTACK_BOOST_ARTS,
        ItemType.SHIELD to AsciiArt.SHIELD_ARTS,
        ItemType.HAND_ARMOR to listOf(AsciiArt.HAND_ARMOR_ART),
        ItemType.HELMET to listOf(AsciiArt.HELMET_EQUIP_ART),
        ItemType.NECKLACE to listOf(AsciiArt.NECKLACE_EQUIP_ART),
        ItemType.CHEST_ARMOR to listOf(AsciiArt.CHEST_EQUIP_ART),
        ItemType.LEGGINGS to listOf(AsciiArt.LEGGINGS_EQUIP_ART),
        ItemType.BOOTS_ARMOR to listOf(AsciiArt.BOOTS_EQUIP_ART),
        ItemType.KEY to AsciiArt.KEY_ARTS,
        ItemType.CHEST to AsciiArt.CHEST_ARTS,
        ItemType.SHOP to listOf(AsciiArt.SHOP_STALL),
        ItemType.GAMBLING to listOf(AsciiArt.GAMBLING_TILE),
        ItemType.SPIKES to listOf(AsciiArt.HAZARD_SPIKES),
        ItemType.BOMB to listOf(AsciiArt.HAZARD_BOMB),
        ItemType.WALL to listOf(AsciiArt.HAZARD_WALL),
        ItemType.QUEST to listOf(AsciiArt.QUEST_TILE),
        ItemType.REST to listOf(AsciiArt.REST_TILE),
    )

    private val defaultItemArt: List<String> = AsciiArt.HEALTH_POTION

    fun enemySprite(kind: EnemyKind): List<String> =
        AsciiArt.ENEMY_ARTS.getValue(kind)

    fun itemSprite(item: GridItem): List<String> {
        val variants = itemVariants[item.type] ?: listOf(defaultItemArt)
        val idx = item.artVariant.coerceIn(0, variants.lastIndex.coerceAtLeast(0))
        return variants[idx]
    }

    /** Sprite for a bare [ItemType] (e.g. HUD equipment preview). */
    fun itemSpriteForType(type: ItemType, artVariant: Int = 0): List<String> =
        itemSprite(GridItem(type, 0, 0, 0, artVariant = artVariant))

    fun itemTileColorForType(type: ItemType): CPColor =
        itemTileColor(GridItem(type, 0, 0, 0))

    /** How many art variants exist for this item type (for random rolls in [LevelGenerator]). */
    fun itemVariantCount(type: ItemType): Int =
        itemVariants[type]?.size ?: 1

    fun itemTileColor(item: GridItem): CPColor = when (item.type) {
        ItemType.KEY, ItemType.CHEST -> item.tier.metalColor()
        ItemType.HEALTH_POTION -> CPColor.C_GREEN1()
        ItemType.ATTACK_BOOST -> CPColor.C_ORANGE1()
        ItemType.SHIELD -> CPColor.C_CYAN1()
        ItemType.HAND_ARMOR -> CPColor(140, 155, 175, "hand-armor")
        ItemType.HELMET -> CPColor(155, 165, 185, "helm-equip")
        ItemType.NECKLACE -> CPColor(200, 175, 120, "necklace-equip")
        ItemType.CHEST_ARMOR -> CPColor(130, 145, 165, "chest-equip")
        ItemType.LEGGINGS -> CPColor(125, 130, 155, "leggings-equip")
        ItemType.BOOTS_ARMOR -> CPColor(120, 105, 95, "boots-equip")
        ItemType.SHOP -> CPColor.C_GOLD1()
        ItemType.GAMBLING -> CPColor(200, 140, 255, "gambling")
        ItemType.SPIKES -> CPColor(160, 40, 160, "spikes")
        ItemType.BOMB -> CPColor.C_ORANGE_RED1()
        ItemType.WALL -> CPColor.C_GREY70()
        ItemType.QUEST -> CPColor(165, 120, 255, "quest")
        ItemType.REST -> CPColor.C_GREEN1()
    }
}

/** Bronze / silver / gold for key and chest tile borders and art tint. */
fun KeyTier.metalColor(): CPColor = when (this) {
    KeyTier.BRONZE -> CPColor(184, 115, 51, "bronze")
    KeyTier.SILVER -> CPColor(175, 175, 195, "silver")
    KeyTier.GOLD -> CPColor.C_GOLD1()
}
