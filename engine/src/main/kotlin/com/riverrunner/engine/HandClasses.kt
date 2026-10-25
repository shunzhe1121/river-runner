package com.riverrunner.engine

import kotlin.random.Random

/**
 * The 169 starting-hand classes ordered from strongest to weakest for
 * opening purposes. The order blends simulated all-in equity (heads-up and
 * 4-way) with playability adjustments (small pairs, suited connectors and
 * suited wheel aces up; weak offsuit hands down), which reproduces the shape of
 * published solver opening charts: taking the top N% of combos gives a range
 * that looks like a real RFI chart for that width.
 */
object HandClasses {
    private const val ORDER =
        "AA KK QQ JJ TT 99 AKs 88 AQs KQs 77 AKo AJs KJs ATs AQo 66 QJs KTs AJo A9s KQo QTs ATo JTs KJo A8s " +
        "A5s 55 K9s A4s KTo A7s QJo T9s K8s A6s A3s Q9s JTo A2s 44 QTo J9s K7s K6s A9o 33 T8s Q8s J8s Q9o " +
        "98s A8o K5s A7o K4s J9o Q7s K9o 87s K3s A6o A5o T9o 97s Q6s K2s J7s T7s A4o 76s 22 Q8o Q5s K7o K8o " +
        "86s T8o A3o Q4s J6s Q3s A2o J4s 96s Q2s T6s J5s K6o K5o J8o Q6o Q7o 65s 75s T7o T5s 85s J7o J2s J3s " +
        "54s 98o K4o 95s K3o T4s Q5o Q4o K2o 64s T3s 94s Q3o T6o T2s 74s 97o J6o 53s J5o 84s 93s 92s T5o Q2o " +
        "43s J4o 87o 63s J3o 96o 83s T4o 76o 73s 86o T3o 52s 42s J2o 32s T2o 82s 95o 72s 75o 85o 62s 65o 84o " +
        "54o 94o 93o 64o 74o 92o 83o 53o 63o 82o 43o 73o 62o 52o 42o 72o 32o"

    val names: List<String> = ORDER.split(" ")

    /** rank of class by name (0 = AA). */
    private val classRank: Map<String, Int> = names.withIndex().associate { it.value to it.index }

    /** Percentile (0 best .. 1 worst) at the middle of each class's combo block. */
    private val classPercentile: DoubleArray
    /** Fraction of combos at or above (stronger than) the end of each class. */
    private val classCumEnd: DoubleArray

    /** All 1326 combos sorted from strongest to weakest class; each entry packs c1*64+c2. */
    private val sortedCombos: IntArray
    /** Percentile of each combo in sortedCombos. */
    private val sortedComboPct: DoubleArray

    init {
        classPercentile = DoubleArray(names.size)
        classCumEnd = DoubleArray(names.size)
        var cum = 0
        names.forEachIndexed { i, n ->
            val combos = combosOf(n)
            classPercentile[i] = (cum + combos / 2.0) / 1326.0
            cum += combos
            classCumEnd[i] = cum / 1326.0
        }
        val list = ArrayList<Int>(1326)
        for (a in 0 until 52) for (b in a + 1 until 52) list += a * 64 + b
        list.sortBy { classRank.getValue(className(it / 64, it % 64)) }
        sortedCombos = list.toIntArray()
        sortedComboPct = DoubleArray(sortedCombos.size) { percentile(sortedCombos[it] / 64, sortedCombos[it] % 64) }
    }

    private fun combosOf(name: String): Int = when {
        name.length == 2 -> 6
        name.endsWith("s") -> 4
        else -> 12
    }

    fun className(c1: Int, c2: Int): String {
        val r1 = Cards.rank(c1); val r2 = Cards.rank(c2)
        val hi = maxOf(r1, r2); val lo = minOf(r1, r2)
        val h = Cards.RANKS[hi]; val l = Cards.RANKS[lo]
        return when {
            hi == lo -> "$h$l"
            Cards.suit(c1) == Cards.suit(c2) -> "$h${l}s"
            else -> "$h${l}o"
        }
    }

    /** 0.0 = AA (best), 1.0 = 32o (worst). */
    fun percentile(c1: Int, c2: Int): Double = classPercentile[classRank.getValue(className(c1, c2))]

    fun percentileOfClass(name: String): Double = classPercentile[classRank.getValue(name)]

    /**
     * Samples a hand whose percentile lies in [lo, hi), avoiding cards in [dead].
     * Returns packed c1*64+c2, or -1 if nothing could be found.
     */
    fun sample(lo: Double, hi: Double, dead: Long, random: Random): Int {
        val start = lowerIndex(lo)
        var end = lowerIndex(hi)
        if (end <= start) end = minOf(start + 1, sortedCombos.size)
        val span = end - start
        repeat(40) {
            val packed = sortedCombos[start + random.nextInt(span)]
            val a = packed / 64; val b = packed % 64
            if (dead and ((1L shl a) or (1L shl b)) == 0L) return packed
        }
        // Linear fallback
        for (i in start until end) {
            val packed = sortedCombos[i]
            val a = packed / 64; val b = packed % 64
            if (dead and ((1L shl a) or (1L shl b)) == 0L) return packed
        }
        return -1
    }

    private fun lowerIndex(p: Double): Int {
        if (p <= 0.0) return 0
        if (p >= 1.0) return sortedCombos.size
        var lo = 0; var hi = sortedCombos.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedComboPct[mid] < p) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Classes whose percentile lies within [lo, hi) — used to display a range. */
    fun classesIn(lo: Double, hi: Double): List<String> =
        names.filterIndexed { i, _ -> classPercentile[i] >= lo && classPercentile[i] < hi }
}
