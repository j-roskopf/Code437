package com.cardgame.scene

/**
 * CosPlay scene names registered with [org.cosplay.CPScene]. Use [id] when the engine API requires a string.
 */
enum class SceneId(val id: String) {
    MENU("menu"),
    GAME("game"),
    MINIGAMES("minigames"),
    CHARACTER_SELECT("characterselect"),
    LEVEL_SELECT("levelselect"),
    LEVEL_COMPLETE("levelcomplete"),
    QUEST("quest"),
    REST("rest"),
    RUN_SUMMARY("runsummary"),
    GAME_OVER("gameover"),
    INVENTORY("inventory"),
    DEBUG_MENU("debugmenu"),
    SHOP("shop"),
    /** Shop: trim one card from the hero build (arrow grid, same style as character-select deck). */
    SHOP_DECK_TRIM("shopdecktrim"),
    SECRET_ROOM("secretroom"),
    SLOTS("slots"),
    SICBO("sicbo"),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: String): SceneId? = byId[id]
    }
}

/**
 * What happens when the player leaves [ShopScene] via Back / ESC.
 */
sealed class ShopDismissAction {
    data class SwitchTo(val scene: SceneId) : ShopDismissAction()

    /** After level clear: advance floor, rebuild [SceneId.GAME], then switch to it. */
    data object AdvanceLevelRecreateGame : ShopDismissAction()
}
