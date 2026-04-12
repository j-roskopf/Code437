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

/** Permanent armor from this piece when equipped (ignores [GridItem.value]); each slot is +1 SHD before deck upgrades. */
fun ItemType.equipmentArmorValue(): Int = when (this) {
    ItemType.HELMET,
    ItemType.NECKLACE,
    ItemType.CHEST_ARMOR,
    ItemType.HAND_ARMOR,
    ItemType.LEGGINGS,
    ItemType.BOOTS_ARMOR,
    -> 1
    else -> 0
}

/** Short HUD / card-footer tag (head, neck, chest, arms, legs, boots). */
fun ItemType.equipmentShortTag(): String = when (this) {
    ItemType.HELMET -> "HEAD"
    ItemType.NECKLACE -> "NECK"
    ItemType.CHEST_ARMOR -> "CHEST"
    ItemType.HAND_ARMOR -> "ARMS"
    ItemType.LEGGINGS -> "LEGS"
    ItemType.BOOTS_ARMOR -> "BOOT"
    else -> ""
}
