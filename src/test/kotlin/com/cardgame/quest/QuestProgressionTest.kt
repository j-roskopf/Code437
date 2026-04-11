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
            val o = QuestSystem.randomOffer(
                excludeQuestIds = emptySet(),
                completedQuestIds = emptySet(),
                currentLevel = 2,
            )
            assertNotNull(o)
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
}
