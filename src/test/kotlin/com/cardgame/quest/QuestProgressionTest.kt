package com.cardgame.quest

import com.cardgame.game.EnemyKind
import com.cardgame.game.GameState
import com.cardgame.game.LevelConfig
import com.cardgame.quest.QuestTargetType
import com.cardgame.testsupport.TestFixtures.withFreshState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestProgressionTest {
    @Test
    fun acceptedQuest_startsFromBaseline_notPastActions() = withFreshState {
        repeat(2) { GameState.registerEnemyDefeat(EnemyKind.RAT, elite = false) }
        val ratQuest = QuestSystem.templates.first { it.id == "rat_hunter" } // needs 3 rats

        GameState.acceptQuest(ratQuest)
        val hud = GameState.questHudLines().first()
        assertTrue(hud.contains("0/3"), "Quest progress should start at 0 after accept, got: $hud")

        GameState.registerEnemyDefeat(EnemyKind.RAT, elite = false)
        val hud2 = GameState.questHudLines().first()
        assertTrue(hud2.contains("1/3"), "Expected only post-accept kills to count, got: $hud2")
    }

    @Test
    fun multipleQuests_trackAndCompleteIndependently() = withFreshState {
        val killAny = QuestSystem.templates.first { it.id == "skirmisher" } // 6 any
        val elite = QuestSystem.templates.first { it.id == "first_bloodline" } // 1 elite
        val startMoney = GameState.money

        GameState.acceptQuest(killAny)
        GameState.acceptQuest(elite)
        assertEquals(2, GameState.activeQuests.size)

        GameState.registerEnemyDefeat(EnemyKind.IMP, elite = true) // progresses both, completes elite
        assertTrue(GameState.completedQuestIds().contains("first_bloodline"))
        assertEquals(1, GameState.activeQuests.size)
        assertTrue(GameState.money > startMoney)

        repeat(5) { GameState.registerEnemyDefeat(EnemyKind.BAT, elite = false) }
        assertTrue(GameState.completedQuestIds().contains("skirmisher"))
        assertTrue(GameState.activeQuests.isEmpty())
    }

    @Test
    fun collectGoldQuest_countsOnlyGoldAfterAcceptance() = withFreshState {
        val coin = QuestSystem.templates.first { it.id == "coin_chaser" } // 60 gold
        GameState.addMoney(100) // should not count before accept
        GameState.acceptQuest(coin)

        val line0 = GameState.questHudLines().first()
        assertTrue(line0.contains("0/60"), "Gold quest should start from 0 after accept: $line0")

        GameState.addMoney(25)
        val line1 = GameState.questHudLines().first()
        assertTrue(line1.contains("25/60"), "Expected post-accept gold to count: $line1")
    }

    @Test
    fun randomOffer_excludesActiveAndCompletedQuestIds() = withFreshState {
        val active = QuestSystem.templates.first { it.id == "rat_hunter" }
        GameState.acceptQuest(active)
        repeat(3) { GameState.registerEnemyDefeat(EnemyKind.RAT, elite = false) } // complete rat_hunter
        assertTrue(GameState.completedQuestIds().contains("rat_hunter"))

        val offer = QuestSystem.randomOffer(
            excludeQuestIds = GameState.activeIncompleteQuestTemplateIds(),
            completedQuestIds = GameState.completedQuestIds(),
            currentLevel = GameState.currentLevel,
        )
        assertNotNull(offer)
        assertFalse(offer.id == "rat_hunter")
    }

    @Test
    fun randomOffer_killKindOnlyTargetsEnemiesOnCurrentFloor() = withFreshState {
        GameState.resetForLevel(2)
        val onFloor = LevelConfig.enemyKindsForLevel(2).toSet()
        repeat(200) {
            val o = requireNotNull(
                QuestSystem.randomOffer(
                    excludeQuestIds = emptySet(),
                    completedQuestIds = emptySet(),
                    currentLevel = 2,
                ),
            )
            if (o.targetType == QuestTargetType.KILL_KIND) {
                assertTrue(
                    o.targetKind in onFloor,
                    "${o.id} should only hunt enemies that spawn on floor 2, got ${o.targetKind}"
                )
            }
        }
    }

    @Test
    fun activeQuests_capAtFiveFurtherAcceptsIgnored() = withFreshState {
        val ids = listOf(
            "rat_hunter",
            "slime_cleanup",
            "first_bloodline",
            "skirmisher",
            "coin_chaser",
            "haunted_clearout",
        )
        val byId = QuestSystem.templates.associateBy { it.id }
        for (id in ids.take(5)) {
            GameState.acceptQuest(byId.getValue(id))
        }
        assertEquals(GameState.MAX_CONCURRENT_QUESTS, GameState.activeQuests.size)
        assertFalse(GameState.canAcceptNewQuest())
        assertFalse(GameState.canSpawnQuestTile())
        GameState.acceptQuest(byId.getValue("haunted_clearout"))
        assertEquals(GameState.MAX_CONCURRENT_QUESTS, GameState.activeQuests.size)
    }

    @Test
    fun randomOffer_level3_neverOffersRatSlimeOrGhostKillQuests() = withFreshState {
        GameState.resetForLevel(3)
        val forbidden = setOf("rat_hunter", "slime_cleanup", "haunted_clearout")
        repeat(300) {
            val o = QuestSystem.randomOffer(
                excludeQuestIds = emptySet(),
                completedQuestIds = emptySet(),
                currentLevel = 3,
            )
            assertNotNull(o)
            assertFalse(o.id in forbidden, "got ${o.id} on floor 3")
            if (o.targetType == QuestTargetType.KILL_KIND) {
                assertTrue(o.targetKind in setOf(EnemyKind.GOBLIN, EnemyKind.IMP), o.id)
            }
        }
    }

    @Test
    fun dropActiveQuestAt_removesQuestWithoutAcceptingOffer() = withFreshState {
        val rat = QuestSystem.templates.first { it.id == "rat_hunter" }
        GameState.acceptQuest(rat)
        assertEquals(1, GameState.activeQuests.size)
        assertTrue(GameState.dropActiveQuestAt(0))
        assertTrue(GameState.activeQuests.isEmpty())
    }

    @Test
    fun atCapacity_abandonSlotThenAccept_offerRemainsUntilAccepted() = withFreshState {
        val ids = listOf(
            "rat_hunter",
            "slime_cleanup",
            "first_bloodline",
            "skirmisher",
            "coin_chaser",
            "haunted_clearout",
        )
        val byId = QuestSystem.templates.associateBy { it.id }
        for (id in ids.take(GameState.MAX_CONCURRENT_QUESTS)) {
            GameState.acceptQuest(byId.getValue(id))
        }
        val pending = byId.getValue(ids.last())
        GameState.pendingQuestOffer = pending
        GameState.pendingQuestAtCapacity = true

        assertTrue(GameState.dropActiveQuestAt(0))
        assertFalse(GameState.pendingQuestAtCapacity)
        assertEquals(pending.id, GameState.pendingQuestOffer?.id)
        assertTrue(GameState.canAcceptNewQuest())

        GameState.acceptQuest(pending)
        assertEquals(GameState.MAX_CONCURRENT_QUESTS, GameState.activeQuests.size)
        assertTrue(GameState.activeQuests.any { it.template.id == pending.id })
        assertEquals(null, GameState.pendingQuestOffer)
    }

    @Test
    fun dropActiveQuestAndAcceptPending_swapsQuestWhenAtCapacity() = withFreshState {
        val ids = listOf(
            "rat_hunter",
            "slime_cleanup",
            "first_bloodline",
            "skirmisher",
            "coin_chaser",
            "haunted_clearout",
        )
        val byId = QuestSystem.templates.associateBy { it.id }
        for (id in ids.take(GameState.MAX_CONCURRENT_QUESTS)) {
            GameState.acceptQuest(byId.getValue(id))
        }
        val droppedId = GameState.activeQuests.first().template.id
        val pending = byId.getValue(ids.last())
        GameState.pendingQuestOffer = pending
        GameState.pendingQuestAtCapacity = true

        assertTrue(GameState.dropActiveQuestAndAcceptPending(0))
        assertEquals(GameState.MAX_CONCURRENT_QUESTS, GameState.activeQuests.size)
        assertTrue(GameState.activeQuests.any { it.template.id == pending.id })
        assertFalse(GameState.activeQuests.any { it.template.id == droppedId })
        assertFalse(GameState.pendingQuestAtCapacity)
        assertEquals(null, GameState.pendingQuestOffer)
    }

    @Test
    fun refreshPendingQuestOfferAfterBoardAbandon_rollsWhenTileHadNoOfferAtFull() = withFreshState {
        GameState.resetForLevel(1)
        val floor1BeatableIds = setOf(
            "rat_hunter",
            "slime_cleanup",
            "first_bloodline",
            "skirmisher",
            "coin_chaser",
        )
        val byId = QuestSystem.templates.associateBy { it.id }
        for (id in floor1BeatableIds) {
            GameState.acceptQuest(byId.getValue(id))
        }
        GameState.pendingQuestOffer = null
        GameState.pendingQuestAtCapacity = true

        assertTrue(GameState.dropActiveQuestAt(0))
        GameState.refreshPendingQuestOfferAfterBoardAbandon()
        assertNotNull(GameState.pendingQuestOffer)
    }

    @Test
    fun randomOffer_returnsNullWhenAllFloorBeatableTemplatesAreActive() = withFreshState {
        GameState.resetForLevel(1)
        val floor1BeatableIds = setOf(
            "rat_hunter",
            "slime_cleanup",
            "first_bloodline",
            "skirmisher",
            "coin_chaser",
        )
        val byId = QuestSystem.templates.associateBy { it.id }
        for (id in floor1BeatableIds) {
            GameState.acceptQuest(byId.getValue(id))
        }
        assertEquals(GameState.MAX_CONCURRENT_QUESTS, GameState.activeQuests.size)
        assertNull(
            QuestSystem.randomOffer(
                excludeQuestIds = GameState.activeIncompleteQuestTemplateIds(),
                completedQuestIds = GameState.completedQuestIds(),
                currentLevel = 1,
            ),
        )
    }

    @Test
    fun randomOffer_floor1_killKindOnlySpawnsOnThatFloor() = withFreshState {
        GameState.resetForLevel(1)
        val onFloor = LevelConfig.enemyKindsForLevel(1).toSet()
        repeat(400) {
            val o = requireNotNull(
                QuestSystem.randomOffer(
                    excludeQuestIds = emptySet(),
                    completedQuestIds = emptySet(),
                    currentLevel = 1,
                ),
            )
            if (o.targetType == QuestTargetType.KILL_KIND) {
                assertTrue(o.targetKind in onFloor, "${o.id} targets ${o.targetKind}, not on floor 1")
            }
        }
    }

    @Test
    fun randomOffer_afterCompletingFloor1KillQuests_neverOffersOffFloorKillKind() = withFreshState {
        GameState.resetForLevel(1)
        val byId = QuestSystem.templates.associateBy { it.id }
        GameState.acceptQuest(byId.getValue("rat_hunter"))
        repeat(3) { GameState.registerEnemyDefeat(EnemyKind.RAT, elite = false) }
        GameState.acceptQuest(byId.getValue("slime_cleanup"))
        repeat(4) { GameState.registerEnemyDefeat(EnemyKind.SLIME, elite = false) }
        val onFloor = LevelConfig.enemyKindsForLevel(1).toSet()
        repeat(250) {
            val o = requireNotNull(
                QuestSystem.randomOffer(
                    excludeQuestIds = GameState.activeIncompleteQuestTemplateIds(),
                    completedQuestIds = GameState.completedQuestIds(),
                    currentLevel = 1,
                ),
            )
            if (o.targetType == QuestTargetType.KILL_KIND) {
                assertTrue(o.targetKind in onFloor, "${o.id} ${o.targetKind} not on floor 1")
            }
        }
    }
}
