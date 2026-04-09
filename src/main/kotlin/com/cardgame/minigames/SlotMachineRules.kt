package com.cardgame.minigames

import com.cardgame.art.SlotMachineArt

/**
 * Pure payout and payline logic for the slot mini game (no CosPlay / UI).
 */
object SlotMachineRules {

    fun tripleMult(s: SlotMachineArt.Symbol): Int = when (s) {
        SlotMachineArt.Symbol.DICE -> 55
        SlotMachineArt.Symbol.ROCKET -> 38
        SlotMachineArt.Symbol.FOSSIL -> 28
        SlotMachineArt.Symbol.PEAR -> 22
        SlotMachineArt.Symbol.APPLE -> 17
        SlotMachineArt.Symbol.CARROT -> 12
        SlotMachineArt.Symbol.TOILET -> 9
        SlotMachineArt.Symbol.QUESTION -> 6
    }

    /** Per payline: two-of-a-kind pays **2× bet** (stake back + profit), so a win beats the cost of the spin. */
    fun pairPayout(bet: Int): Int = bet * 2

    /** One payline (three cells); triple uses symbol mult, any pair pays [pairPayout]. */
    fun spinPayout(
        bet: Int,
        a: SlotMachineArt.Symbol,
        b: SlotMachineArt.Symbol,
        c: SlotMachineArt.Symbol,
    ): Int {
        if (a == b && b == c) return bet * tripleMult(a)
        if (a == b || b == c || a == c) return pairPayout(bet)
        return 0
    }

    enum class PaylineKind {
        MIDDLE,
        DIAG_TL_BR,
        DIAG_BL_TR,
    }

    data class PaylineHit(
        val kind: PaylineKind,
        val lineTitle: String,
        val left: SlotMachineArt.Symbol,
        val mid: SlotMachineArt.Symbol,
        val right: SlotMachineArt.Symbol,
        val payout: Int,
    ) {
        fun symbolsPipe(): String = "${left.label}|${mid.label}|${right.label}"
    }

    /** Winning paylines with per-line payouts; total is sum of line payouts (stacked wins). */
    fun collectPaylineHits(
        bet: Int,
        symAt: (col: Int, row: Int) -> SlotMachineArt.Symbol,
    ): Pair<Int, List<PaylineHit>> {
        var total = 0
        val hits = mutableListOf<PaylineHit>()
        fun consider(
            kind: PaylineKind,
            lineTitle: String,
            c0: Int, r0: Int,
            c1: Int, r1: Int,
            c2: Int, r2: Int,
        ) {
            val a = symAt(c0, r0)
            val b = symAt(c1, r1)
            val c = symAt(c2, r2)
            val p = spinPayout(bet, a, b, c)
            if (p > 0) {
                total += p
                hits += PaylineHit(kind, lineTitle, a, b, c, p)
            }
        }
        consider(PaylineKind.MIDDLE, "Middle row", 0, 1, 1, 1, 2, 1)
        consider(PaylineKind.DIAG_TL_BR, "Top-left to bottom-right", 0, 0, 1, 1, 2, 2)
        consider(PaylineKind.DIAG_BL_TR, "Bottom-left to top-right", 0, 2, 1, 1, 2, 0)
        return total to hits
    }
}
