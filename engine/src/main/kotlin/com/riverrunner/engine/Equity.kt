package com.riverrunner.engine

import kotlin.random.Random

object Equity {

    /**
     * Showdown equity of each known hand given the current board.
     * Enumerates every remaining runout when the flop is out; uses Monte Carlo preflop.
     * Returns fractions summing to 1 (ties split).
     */
    fun showdown(holes: List<IntArray>, board: List<Int>, random: Random = Random.Default, preflopIters: Int = 30000): DoubleArray {
        val n = holes.size
        val eq = DoubleArray(n)
        if (n == 0) return eq
        if (n == 1) { eq[0] = 1.0; return eq }
        var dead = Cards.mask(board.toIntArray())
        for (h in holes) dead = dead or Cards.mask(h)
        val remaining = (0 until 52).filter { dead and (1L shl it) == 0L }.toIntArray()
        val need = 5 - board.size
        val baseBoard = Cards.mask(board.toIntArray())
        val holeMasks = LongArray(n) { Cards.mask(holes[it]) }
        val scores = IntArray(n)
        var total = 0.0

        fun score(boardMask: Long) {
            var best = Int.MIN_VALUE; var cnt = 0
            for (i in 0 until n) {
                val s = HandEvaluator.evaluate(boardMask or holeMasks[i])
                scores[i] = s
                if (s > best) { best = s; cnt = 1 } else if (s == best) cnt++
            }
            val share = 1.0 / cnt
            for (i in 0 until n) if (scores[i] == best) eq[i] += share
            total += 1.0
        }

        when (need) {
            0 -> score(baseBoard)
            1 -> for (a in remaining) score(baseBoard or (1L shl a))
            2 -> for (i in remaining.indices) for (j in i + 1 until remaining.size)
                score(baseBoard or (1L shl remaining[i]) or (1L shl remaining[j]))
            else -> {
                val arr = remaining.copyOf()
                repeat(preflopIters) {
                    var m = baseBoard
                    // partial Fisher-Yates for `need` cards
                    for (k in 0 until need) {
                        val j = k + random.nextInt(arr.size - k)
                        val t = arr[k]; arr[k] = arr[j]; arr[j] = t
                        m = m or (1L shl arr[k])
                    }
                    score(m)
                }
            }
        }
        for (i in 0 until n) eq[i] /= total
        return eq
    }

    /** Samples a two-card hand (as a card mask) that avoids [dead]; returns 0 when impossible. */
    fun interface HandSampler {
        fun sample(dead: Long, random: Random): Long
    }

    /**
     * Hero's equity against opponents whose hands are drawn from [opponents] samplers.
     */
    fun vsRanges(hero: IntArray, board: List<Int>, opponents: List<HandSampler>, iters: Int, random: Random): Double {
        if (opponents.isEmpty()) return 1.0
        val heroMask = Cards.mask(hero)
        val boardMask = Cards.mask(board.toIntArray())
        val need = 5 - board.size
        var win = 0.0
        var count = 0
        val oppMasks = LongArray(opponents.size)
        repeat(iters) {
            var dead = heroMask or boardMask
            var ok = true
            for (i in opponents.indices) {
                val m = opponents[i].sample(dead, random)
                if (m == 0L) { ok = false; break }
                oppMasks[i] = m
                dead = dead or m
            }
            if (!ok) return@repeat
            var b = boardMask
            var k = 0
            while (k < need) {
                val c = random.nextInt(52)
                val bit = 1L shl c
                if (dead and bit == 0L) { dead = dead or bit; b = b or bit; k++ }
            }
            val hs = HandEvaluator.evaluate(b or heroMask)
            var best = hs; var ties = 1; var heroBest = true
            for (i in opponents.indices) {
                val s = HandEvaluator.evaluate(b or oppMasks[i])
                if (s > best) { best = s; heroBest = false; ties = 1 }
                else if (s == best) ties++
            }
            if (heroBest) win += 1.0 / ties
            count++
        }
        return if (count == 0) 0.5 else win / count
    }
}

/** Independent Chip Model (Malmuth–Harville) for tournament prize equity. */
object Icm {
    /** Returns each player's expected share of the prize pool. payouts are fractions (e.g. 0.5, 0.3, 0.2). */
    fun equities(stacks: IntArray, payouts: DoubleArray): DoubleArray {
        val n = stacks.size
        val res = DoubleArray(n)
        val total = stacks.sum().toDouble()
        if (total <= 0) return res
        val places = minOf(payouts.size, stacks.count { it > 0 })
        fun rec(place: Int, used: Int, remaining: Double, prob: Double) {
            if (place >= places) return
            for (i in 0 until n) {
                if (used and (1 shl i) != 0 || stacks[i] <= 0) continue
                val p = prob * stacks[i] / remaining
                res[i] += p * payouts[place]
                rec(place + 1, used or (1 shl i), remaining - stacks[i], p)
            }
        }
        rec(0, 0, total, 1.0)
        return res
    }

    /**
     * Equity needed to call an all-in under ICM.
     * [stacksIfFold], [stacksIfWin], [stacksIfLose]: everyone's stacks after each outcome.
     */
    fun requiredEquity(hero: Int, stacksIfFold: IntArray, stacksIfWin: IntArray, stacksIfLose: IntArray, payouts: DoubleArray): Double {
        val f = equities(stacksIfFold, payouts)[hero]
        val w = equities(stacksIfWin, payouts)[hero]
        val l = equities(stacksIfLose, payouts)[hero]
        if (w - l <= 1e-9) return 1.0
        return ((f - l) / (w - l)).coerceIn(0.0, 1.0)
    }

    fun payoutsFor(players: Int): DoubleArray = when {
        players <= 3 -> doubleArrayOf(1.0)
        players <= 6 -> doubleArrayOf(0.65, 0.35)
        else -> doubleArrayOf(0.5, 0.3, 0.2)
    }
}
