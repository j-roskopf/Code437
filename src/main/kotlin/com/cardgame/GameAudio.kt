package com.cardgame

import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.random.Random

object GameAudio {
    private const val PLAYER_ATTACK_RESOURCE = "sounds/playerAttack.wav"
    private const val MONEY_PICKUP_RESOURCE = "sounds/moneyPickup.wav"
    private const val POTION_PICKUP_RESOURCE = "sounds/potionPickup.wav"
    private const val ARMOR_PICKUP_RESOURCE = "sounds/armorPickup.wav"
    private const val CHEST_UNLOCK_RESOURCE = "sounds/chestUnlock.wav"
    private const val BUFF_PICKUP_RESOURCE = "sounds/buffPickup.wav"
    private const val DICE_SHAKE_LOOP_RESOURCE = "sounds/diceShakeLoop.wav"
    private const val CASINO_WIN_RESOURCE = "sounds/casinoWin.wav"
    private const val CASINO_LOSE_RESOURCE = "sounds/casinoLose.wav"
    private const val SLOT_SPIN_LOOP_RESOURCE = "sounds/slotSpinLoop.wav"
    private const val PLAYER_ATTACK_POOL_SIZE = 4
    private const val MONEY_PICKUP_POOL_SIZE = 3
    private const val POTION_PICKUP_POOL_SIZE = 3
    private const val ARMOR_PICKUP_POOL_SIZE = 3
    private const val CHEST_UNLOCK_POOL_SIZE = 2
    private const val BUFF_PICKUP_POOL_SIZE = 3
    private const val CASINO_WIN_POOL_SIZE = 2
    private const val CASINO_LOSE_POOL_SIZE = 2
    private const val PLAYER_ATTACK_BASE_GAIN_DB = -2f
    private const val PLAYER_ATTACK_GAIN_VARIATION_DB = 1.25f
    private const val MONEY_PICKUP_BASE_GAIN_DB = -4f
    private const val MONEY_PICKUP_GAIN_VARIATION_DB = 1.0f
    private const val POTION_PICKUP_BASE_GAIN_DB = -6f
    private const val POTION_PICKUP_GAIN_VARIATION_DB = 1.0f
    private const val ARMOR_PICKUP_BASE_GAIN_DB = -3f
    private const val ARMOR_PICKUP_GAIN_VARIATION_DB = 1.0f
    private const val CHEST_UNLOCK_BASE_GAIN_DB = -5f
    private const val CHEST_UNLOCK_GAIN_VARIATION_DB = 0.75f
    private const val BUFF_PICKUP_BASE_GAIN_DB = -4f
    private const val BUFF_PICKUP_GAIN_VARIATION_DB = 1.0f
    private const val DICE_SHAKE_LOOP_GAIN_DB = -8f
    private const val CASINO_WIN_BASE_GAIN_DB = -4f
    private const val CASINO_WIN_GAIN_VARIATION_DB = 0.75f
    private const val CASINO_LOSE_BASE_GAIN_DB = -5f
    private const val CASINO_LOSE_GAIN_VARIATION_DB = 0.75f
    private const val SLOT_SPIN_LOOP_GAIN_DB = -8f

    private var playerAttackSounds: List<Clip>? = null
    private var moneyPickupSounds: List<Clip>? = null
    private var potionPickupSounds: List<Clip>? = null
    private var armorPickupSounds: List<Clip>? = null
    private var chestUnlockSounds: List<Clip>? = null
    private var buffPickupSounds: List<Clip>? = null
    private var diceShakeLoop: Clip? = null
    private var casinoWinSounds: List<Clip>? = null
    private var casinoLoseSounds: List<Clip>? = null
    private var slotSpinLoop: Clip? = null
    private var nextPlayerAttackClipIdx = 0
    private var nextMoneyPickupClipIdx = 0
    private var nextPotionPickupClipIdx = 0
    private var nextArmorPickupClipIdx = 0
    private var nextChestUnlockClipIdx = 0
    private var nextBuffPickupClipIdx = 0
    private var nextCasinoWinClipIdx = 0
    private var nextCasinoLoseClipIdx = 0

    fun preload() {
        kotlin.runCatching {
            loadPlayerAttackSounds()
            loadMoneyPickupSounds()
            loadPotionPickupSounds()
            loadArmorPickupSounds()
            loadChestUnlockSounds()
            loadBuffPickupSounds()
            loadDiceShakeLoop()
            loadCasinoWinSounds()
            loadCasinoLoseSounds()
            loadSlotSpinLoop()
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Game audio preload failed",
                throwable = it,
            )
        }
    }

    fun playPlayerAttack() {
        kotlin.runCatching {
            synchronized(this) {
                val clips = loadPlayerAttackSounds()
                val clip = clips[nextPlayerAttackClipIdx]
                nextPlayerAttackClipIdx = (nextPlayerAttackClipIdx + 1) % clips.size
                val gainOffset = Random.nextDouble(
                    -PLAYER_ATTACK_GAIN_VARIATION_DB.toDouble(),
                    PLAYER_ATTACK_GAIN_VARIATION_DB.toDouble()
                ).toFloat()
                replayClip(clip, gainDb = PLAYER_ATTACK_BASE_GAIN_DB + gainOffset)
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Player attack sound failed",
                throwable = it,
            )
        }
    }

    fun playMoneyPickup() {
        kotlin.runCatching {
            synchronized(this) {
                val clips = loadMoneyPickupSounds()
                val clip = clips[nextMoneyPickupClipIdx]
                nextMoneyPickupClipIdx = (nextMoneyPickupClipIdx + 1) % clips.size
                val gainOffset = Random.nextDouble(
                    -MONEY_PICKUP_GAIN_VARIATION_DB.toDouble(),
                    MONEY_PICKUP_GAIN_VARIATION_DB.toDouble()
                ).toFloat()
                replayClip(clip, gainDb = MONEY_PICKUP_BASE_GAIN_DB + gainOffset)
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Money pickup sound failed",
                throwable = it,
            )
        }
    }

    fun playPotionPickup() {
        kotlin.runCatching {
            synchronized(this) {
                val clips = loadPotionPickupSounds()
                val clip = clips[nextPotionPickupClipIdx]
                nextPotionPickupClipIdx = (nextPotionPickupClipIdx + 1) % clips.size
                val gainOffset = Random.nextDouble(
                    -POTION_PICKUP_GAIN_VARIATION_DB.toDouble(),
                    POTION_PICKUP_GAIN_VARIATION_DB.toDouble()
                ).toFloat()
                replayClip(clip, gainDb = POTION_PICKUP_BASE_GAIN_DB + gainOffset)
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Potion pickup sound failed",
                throwable = it,
            )
        }
    }

    fun playArmorPickup() {
        kotlin.runCatching {
            synchronized(this) {
                val clips = loadArmorPickupSounds()
                val clip = clips[nextArmorPickupClipIdx]
                nextArmorPickupClipIdx = (nextArmorPickupClipIdx + 1) % clips.size
                val gainOffset = Random.nextDouble(
                    -ARMOR_PICKUP_GAIN_VARIATION_DB.toDouble(),
                    ARMOR_PICKUP_GAIN_VARIATION_DB.toDouble()
                ).toFloat()
                replayClip(clip, gainDb = ARMOR_PICKUP_BASE_GAIN_DB + gainOffset)
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Armor pickup sound failed",
                throwable = it,
            )
        }
    }

    fun playChestUnlock() {
        kotlin.runCatching {
            synchronized(this) {
                val clips = loadChestUnlockSounds()
                val clip = clips[nextChestUnlockClipIdx]
                nextChestUnlockClipIdx = (nextChestUnlockClipIdx + 1) % clips.size
                val gainOffset = Random.nextDouble(
                    -CHEST_UNLOCK_GAIN_VARIATION_DB.toDouble(),
                    CHEST_UNLOCK_GAIN_VARIATION_DB.toDouble()
                ).toFloat()
                replayClip(clip, gainDb = CHEST_UNLOCK_BASE_GAIN_DB + gainOffset)
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Chest unlock sound failed",
                throwable = it,
            )
        }
    }

    fun playBuffPickup() {
        kotlin.runCatching {
            synchronized(this) {
                val clips = loadBuffPickupSounds()
                val clip = clips[nextBuffPickupClipIdx]
                nextBuffPickupClipIdx = (nextBuffPickupClipIdx + 1) % clips.size
                val gainOffset = Random.nextDouble(
                    -BUFF_PICKUP_GAIN_VARIATION_DB.toDouble(),
                    BUFF_PICKUP_GAIN_VARIATION_DB.toDouble()
                ).toFloat()
                replayClip(clip, gainDb = BUFF_PICKUP_BASE_GAIN_DB + gainOffset)
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Buff pickup sound failed",
                throwable = it,
            )
        }
    }

    fun startDiceShakeLoop() {
        kotlin.runCatching {
            synchronized(this) {
                val clip = loadDiceShakeLoop()
                if (!clip.isRunning) {
                    clip.framePosition = 0
                    clip.loop(Clip.LOOP_CONTINUOUSLY)
                }
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Dice shake loop start failed",
                throwable = it,
            )
        }
    }

    fun playCasinoWin() {
        kotlin.runCatching {
            synchronized(this) {
                val clips = loadCasinoWinSounds()
                val clip = clips[nextCasinoWinClipIdx]
                nextCasinoWinClipIdx = (nextCasinoWinClipIdx + 1) % clips.size
                val gainOffset = Random.nextDouble(
                    -CASINO_WIN_GAIN_VARIATION_DB.toDouble(),
                    CASINO_WIN_GAIN_VARIATION_DB.toDouble()
                ).toFloat()
                replayClip(clip, gainDb = CASINO_WIN_BASE_GAIN_DB + gainOffset)
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Casino win sound failed",
                throwable = it,
            )
        }
    }

    fun playCasinoLose() {
        kotlin.runCatching {
            synchronized(this) {
                val clips = loadCasinoLoseSounds()
                val clip = clips[nextCasinoLoseClipIdx]
                nextCasinoLoseClipIdx = (nextCasinoLoseClipIdx + 1) % clips.size
                val gainOffset = Random.nextDouble(
                    -CASINO_LOSE_GAIN_VARIATION_DB.toDouble(),
                    CASINO_LOSE_GAIN_VARIATION_DB.toDouble()
                ).toFloat()
                replayClip(clip, gainDb = CASINO_LOSE_BASE_GAIN_DB + gainOffset)
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Casino lose sound failed",
                throwable = it,
            )
        }
    }

    fun startSlotSpinLoop() {
        kotlin.runCatching {
            synchronized(this) {
                val clip = loadSlotSpinLoop()
                if (!clip.isRunning()) {
                    clip.framePosition = 0
                    clip.loop(Clip.LOOP_CONTINUOUSLY)
                }
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Slot spin loop start failed",
                throwable = it,
            )
        }
    }

    fun stopDiceShakeLoop() {
        kotlin.runCatching {
            synchronized(this) {
                val clip = diceShakeLoop ?: return
                if (clip.isRunning) {
                    clip.stop()
                }
                clip.flush()
                clip.microsecondPosition = 0L
                clip.framePosition = 0
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Dice shake loop stop failed",
                throwable = it,
            )
        }
    }

    fun stopSlotSpinLoop() {
        kotlin.runCatching {
            synchronized(this) {
                val clip = slotSpinLoop ?: return
                if (clip.isRunning) {
                    clip.stop()
                }
                clip.flush()
                clip.microsecondPosition = 0L
                clip.framePosition = 0
            }
        }.onFailure {
            SentryBootstrap.captureCaughtError(
                message = "Slot spin loop stop failed",
                throwable = it,
            )
        }
    }

    private fun loadPlayerAttackSounds(): List<Clip> =
        playerAttackSounds ?: List(PLAYER_ATTACK_POOL_SIZE) {
            buildClip(PLAYER_ATTACK_RESOURCE, gainDb = PLAYER_ATTACK_BASE_GAIN_DB)
        }.also {
            playerAttackSounds = it
        }

    private fun loadMoneyPickupSounds(): List<Clip> =
        moneyPickupSounds ?: List(MONEY_PICKUP_POOL_SIZE) {
            buildClip(MONEY_PICKUP_RESOURCE, gainDb = MONEY_PICKUP_BASE_GAIN_DB)
        }.also {
            moneyPickupSounds = it
        }

    private fun loadPotionPickupSounds(): List<Clip> =
        potionPickupSounds ?: List(POTION_PICKUP_POOL_SIZE) {
            buildClip(POTION_PICKUP_RESOURCE, gainDb = POTION_PICKUP_BASE_GAIN_DB)
        }.also {
            potionPickupSounds = it
        }

    private fun loadArmorPickupSounds(): List<Clip> =
        armorPickupSounds ?: List(ARMOR_PICKUP_POOL_SIZE) {
            buildClip(ARMOR_PICKUP_RESOURCE, gainDb = ARMOR_PICKUP_BASE_GAIN_DB)
        }.also {
            armorPickupSounds = it
        }

    private fun loadChestUnlockSounds(): List<Clip> =
        chestUnlockSounds ?: List(CHEST_UNLOCK_POOL_SIZE) {
            buildClip(CHEST_UNLOCK_RESOURCE, gainDb = CHEST_UNLOCK_BASE_GAIN_DB)
        }.also {
            chestUnlockSounds = it
        }

    private fun loadBuffPickupSounds(): List<Clip> =
        buffPickupSounds ?: List(BUFF_PICKUP_POOL_SIZE) {
            buildClip(BUFF_PICKUP_RESOURCE, gainDb = BUFF_PICKUP_BASE_GAIN_DB)
        }.also {
            buffPickupSounds = it
        }

    private fun loadDiceShakeLoop(): Clip =
        diceShakeLoop ?: buildClip(DICE_SHAKE_LOOP_RESOURCE, gainDb = DICE_SHAKE_LOOP_GAIN_DB).also {
            diceShakeLoop = it
        }

    private fun loadCasinoWinSounds(): List<Clip> =
        casinoWinSounds ?: List(CASINO_WIN_POOL_SIZE) {
            buildClip(CASINO_WIN_RESOURCE, gainDb = CASINO_WIN_BASE_GAIN_DB)
        }.also {
            casinoWinSounds = it
        }

    private fun loadCasinoLoseSounds(): List<Clip> =
        casinoLoseSounds ?: List(CASINO_LOSE_POOL_SIZE) {
            buildClip(CASINO_LOSE_RESOURCE, gainDb = CASINO_LOSE_BASE_GAIN_DB)
        }.also {
            casinoLoseSounds = it
        }

    private fun loadSlotSpinLoop(): Clip =
        slotSpinLoop ?: buildClip(SLOT_SPIN_LOOP_RESOURCE, gainDb = SLOT_SPIN_LOOP_GAIN_DB).also {
            slotSpinLoop = it
        }

    private fun buildClip(resourcePath: String, gainDb: Float): Clip {
        val resource = checkNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Missing audio resource: $resourcePath"
        }
        BufferedInputStream(resource).use { buffered ->
            AudioSystem.getAudioInputStream(buffered).use { audioStream ->
                return AudioSystem.getClip().also { clip ->
                    clip.open(audioStream)
                    setClipGain(clip, gainDb = gainDb)
                }
            }
        }
    }

    private fun replayClip(clip: Clip, gainDb: Float) {
        if (clip.isRunning) {
            clip.stop()
        }
        clip.flush()
        clip.microsecondPosition = 0L
        clip.framePosition = 0
        setClipGain(clip, gainDb)
        clip.start()
    }

    private fun setClipGain(clip: Clip, gainDb: Float) {
        val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl ?: return
        gain.value = gainDb.coerceIn(gain.minimum, gain.maximum)
    }
}
