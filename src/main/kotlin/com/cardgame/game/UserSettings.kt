package com.cardgame.game

import java.util.prefs.Preferences

enum class DisplayModeSetting {
    FULLSCREEN,
    WINDOWED,
}

object UserSettings {
    private const val KEY_DISPLAY_MODE = "display_mode"
    private val prefs: Preferences = Preferences.userNodeForPackage(UserSettings::class.java)

    var displayMode: DisplayModeSetting
        get() {
            val raw = prefs.get(KEY_DISPLAY_MODE, DisplayModeSetting.FULLSCREEN.name)
            return DisplayModeSetting.entries.firstOrNull { it.name == raw } ?: DisplayModeSetting.FULLSCREEN
        }
        set(value) {
            prefs.put(KEY_DISPLAY_MODE, value.name)
        }
}
