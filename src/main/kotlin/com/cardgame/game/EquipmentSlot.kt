package com.cardgame.game

/** Body slots for equipped items (HUD order matches [GameState.equippedItems] indices). */
enum class EquipmentSlot {
    HEAD,
    NECK,
    CHEST,
    HANDS,
    PANTS,
    BOOTS,
}

/** Grid [ItemType]s that equip into a body slot when picked up. */
fun ItemType.equipmentSlot(): EquipmentSlot? = when (this) {
    ItemType.HELMET -> EquipmentSlot.HEAD
    ItemType.NECKLACE -> EquipmentSlot.NECK
    ItemType.CHEST_ARMOR -> EquipmentSlot.CHEST
    ItemType.HAND_ARMOR -> EquipmentSlot.HANDS
    ItemType.LEGGINGS -> EquipmentSlot.PANTS
    ItemType.BOOTS_ARMOR -> EquipmentSlot.BOOTS
    else -> null
}

/** True if this equipment is already in the matching slot — exclude from random loot spawns. */
fun ItemType.isBlockedByCurrentEquipment(): Boolean {
    val slot = equipmentSlot() ?: return false
    return GameState.equippedItems[slot.ordinal] == this
}

/** Permanent armor from this piece when equipped (ignores [GridItem.value]). */
fun ItemType.equipmentArmorValue(): Int = when (this) {
    ItemType.HELMET -> 2
    ItemType.NECKLACE -> 1
    ItemType.CHEST_ARMOR -> 3
    ItemType.HAND_ARMOR -> 1
    ItemType.LEGGINGS -> 2
    ItemType.BOOTS_ARMOR -> 1
    else -> 0
}
