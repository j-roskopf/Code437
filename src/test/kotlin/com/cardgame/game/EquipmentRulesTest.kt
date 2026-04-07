package com.cardgame.game

import com.cardgame.testsupport.TestFixtures.withFreshState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EquipmentRulesTest {
    @Test
    fun equipmentSlot_mapping_isStable() {
        assertEquals(EquipmentSlot.HEAD, ItemType.HELMET.equipmentSlot())
        assertEquals(EquipmentSlot.NECK, ItemType.NECKLACE.equipmentSlot())
        assertEquals(EquipmentSlot.CHEST, ItemType.CHEST_ARMOR.equipmentSlot())
        assertEquals(EquipmentSlot.HANDS, ItemType.HAND_ARMOR.equipmentSlot())
        assertEquals(EquipmentSlot.PANTS, ItemType.LEGGINGS.equipmentSlot())
        assertEquals(EquipmentSlot.BOOTS, ItemType.BOOTS_ARMOR.equipmentSlot())
        assertNull(ItemType.SHIELD.equipmentSlot())
    }

    @Test
    fun equipmentArmor_values_matchDesign() {
        assertEquals(2, ItemType.HELMET.equipmentArmorValue())
        assertEquals(1, ItemType.NECKLACE.equipmentArmorValue())
        assertEquals(3, ItemType.CHEST_ARMOR.equipmentArmorValue())
        assertEquals(1, ItemType.HAND_ARMOR.equipmentArmorValue())
        assertEquals(2, ItemType.LEGGINGS.equipmentArmorValue())
        assertEquals(1, ItemType.BOOTS_ARMOR.equipmentArmorValue())
    }

    @Test
    fun totalEquipmentArmor_sumsAcrossEquippedSlots() = withFreshState {
        GameState.setEquippedItem(EquipmentSlot.HEAD, ItemType.HELMET)
        GameState.setEquippedItem(EquipmentSlot.NECK, ItemType.NECKLACE)
        GameState.setEquippedItem(EquipmentSlot.CHEST, ItemType.CHEST_ARMOR)
        GameState.setEquippedItem(EquipmentSlot.HANDS, ItemType.HAND_ARMOR)
        GameState.setEquippedItem(EquipmentSlot.PANTS, ItemType.LEGGINGS)
        GameState.setEquippedItem(EquipmentSlot.BOOTS, ItemType.BOOTS_ARMOR)
        assertEquals(10, GameState.totalEquipmentArmor())
    }
}
