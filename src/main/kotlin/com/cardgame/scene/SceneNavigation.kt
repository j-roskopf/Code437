package com.cardgame.scene

import org.cosplay.CPSceneObjectContext

fun CPSceneObjectContext.switchScene(scene: SceneId, rememberScene: Boolean = false) {
    switchScene(scene.id, rememberScene)
}

fun CPSceneObjectContext.deleteScene(scene: SceneId) {
    deleteScene(scene.id)
}
