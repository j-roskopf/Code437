package com.cardgame.game

import org.cosplay.CPColor

/** Playable hero; sprite lines live in [com.cardgame.art.AsciiArt]. */
enum class PlayerCharacter(val label: String) {
    KNIGHT("Knight"),
    THIEF("Thief"),
    MAGE("Mage");

    /** Same tint for every class on the grid and character screen. */
    val spriteColor: CPColor get() = CPColor.C_CORNFLOWER_BLUE()
}
