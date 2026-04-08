package com.cardgame.minigames

/**
 * Sic bo–style resolution for three dice. Sum bets (Big/Small/Odd/Even) lose on **any triple**, as on a real table.
 * Single-die wagers pay by how many dice show the chosen face (1:1 / 2:1 / 3:1 profit vs stake).
 *
 * Two-dice combination and three-dice “specific triple” wagers are not implemented in the mini game UI;
 * see the in-game rules panel for how those work in full Sic bo.
 */
object SicBoRules {

    sealed class Wager {
        /** Sum 11–17 inclusive; loses on any triple. */
        data object SumBig : Wager()

        /** Sum 4–10 inclusive; loses on any triple. */
        data object SumSmall : Wager()

        /** Odd total; loses on any triple. */
        data object SumOdd : Wager()

        /** Even total; loses on any triple. */
        data object SumEven : Wager()

        /** One die: pays if [face] appears on 1+ dice; payout scales with matches (triple on that face pays best). */
        data class SingleDie(val face: Int) : Wager() {
            init {
                require(face in 1..6) { "face 1..6" }
            }
        }
    }

    fun diceSum(d1: Int, d2: Int, d3: Int): Int = d1 + d2 + d3

    fun isTriple(d1: Int, d2: Int, d3: Int): Boolean = d1 == d2 && d2 == d3

    /**
     * Returns total gold to credit back on a win (stake + winnings), or `null` if the bet loses.
     * Caller has already deducted [bet]; on win, add the returned amount to gold.
     */
    fun payoutOnWin(bet: Int, wager: Wager, d1: Int, d2: Int, d3: Int): Int? {
        val sum = diceSum(d1, d2, d3)
        val triple = isTriple(d1, d2, d3)
        return when (wager) {
            is Wager.SumBig ->
                if (!triple && sum in 11..17) bet * 2 else null
            is Wager.SumSmall ->
                if (!triple && sum in 4..10) bet * 2 else null
            is Wager.SumOdd ->
                if (!triple && sum % 2 == 1) bet * 2 else null
            is Wager.SumEven ->
                if (!triple && sum % 2 == 0) bet * 2 else null
            is Wager.SingleDie -> {
                val matches = listOf(d1, d2, d3).count { it == wager.face }
                if (matches == 0) null
                else bet * (1 + matches)
            }
        }
    }

    fun wagerShortLabel(w: Wager): String = when (w) {
        is Wager.SumBig -> "Big"
        is Wager.SumSmall -> "Small"
        is Wager.SumOdd -> "Odd"
        is Wager.SumEven -> "Even"
        is Wager.SingleDie -> "Die ${w.face}"
    }
}
