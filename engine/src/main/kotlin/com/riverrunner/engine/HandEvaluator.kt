package com.riverrunner.engine

/**
 * Evaluates the best 5-card hand out of 5..7 cards given as a Long bitmask.
 * The returned score is comparable: a higher score is a stronger hand.
 * Layout: category shl 20, then up to five 4-bit rank kickers.
 */
object HandEvaluator {
    const val HIGH_CARD = 0
    const val PAIR = 1
    const val TWO_PAIR = 2
    const val TRIPS = 3
    const val STRAIGHT = 4
    const val FLUSH = 5
    const val FULL_HOUSE = 6
    const val QUADS = 7
    const val STRAIGHT_FLUSH = 8

    /** For every 13-bit rank mask, the high rank of the best straight it contains, or -1. */
    private val straightHigh = IntArray(8192) { m -> computeStraight(m) }

    private fun computeStraight(m: Int): Int {
        for (high in 12 downTo 4) {
            val need = 0x1F shl (high - 4)
            if (m and need == need) return high
        }
        // Wheel: A-2-3-4-5
        val wheel = (1 shl 12) or 0xF
        if (m and wheel == wheel) return 3
        return -1
    }

    fun category(score: Int): Int = score ushr 20

    fun evaluate(cards: IntArray): Int = evaluate(Cards.mask(cards))

    fun evaluate(mask: Long): Int {
        val s0 = (mask and 0x1FFF).toInt()
        val s1 = ((mask ushr 13) and 0x1FFF).toInt()
        val s2 = ((mask ushr 26) and 0x1FFF).toInt()
        val s3 = ((mask ushr 39) and 0x1FFF).toInt()

        // Flush / straight flush
        var flushMask = -1
        if (Integer.bitCount(s0) >= 5) flushMask = s0
        else if (Integer.bitCount(s1) >= 5) flushMask = s1
        else if (Integer.bitCount(s2) >= 5) flushMask = s2
        else if (Integer.bitCount(s3) >= 5) flushMask = s3
        if (flushMask >= 0) {
            val sf = straightHigh[flushMask]
            if (sf >= 0) return (STRAIGHT_FLUSH shl 20) or (sf shl 16)
        }

        val quads = s0 and s1 and s2 and s3
        val all = s0 or s1 or s2 or s3
        // count per rank via bit arithmetic
        val trips = ((s0 and s1 and s2) or (s0 and s1 and s3) or (s0 and s2 and s3) or (s1 and s2 and s3)) and quads.inv()
        val pairsOrMore = (s0 and s1) or (s0 and s2) or (s0 and s3) or (s1 and s2) or (s1 and s3) or (s2 and s3)
        val pairs = pairsOrMore and trips.inv() and quads.inv()

        if (quads != 0) {
            val q = highBit(quads)
            val kicker = highBit(all and (1 shl q).inv())
            return (QUADS shl 20) or (q shl 16) or (kicker shl 12)
        }
        if (trips != 0) {
            val t = highBit(trips)
            val restTrips = trips and (1 shl t).inv()
            val pairCandidates = pairs or restTrips
            if (pairCandidates != 0) {
                val p = highBit(pairCandidates)
                return (FULL_HOUSE shl 20) or (t shl 16) or (p shl 12)
            }
        }
        if (flushMask >= 0) {
            return (FLUSH shl 20) or topN(flushMask, 5)
        }
        val st = straightHigh[all]
        if (st >= 0) return (STRAIGHT shl 20) or (st shl 16)
        if (trips != 0) {
            val t = highBit(trips)
            val kick = topN(all and (1 shl t).inv(), 2)
            return (TRIPS shl 20) or (t shl 16) or (kick ushr 4)
        }
        if (Integer.bitCount(pairs) >= 2) {
            val p1 = highBit(pairs)
            val p2 = highBit(pairs and (1 shl p1).inv())
            val kicker = highBit(all and (1 shl p1).inv() and (1 shl p2).inv())
            return (TWO_PAIR shl 20) or (p1 shl 16) or (p2 shl 12) or (kicker shl 8)
        }
        if (pairs != 0) {
            val p = highBit(pairs)
            val kick = topN(all and (1 shl p).inv(), 3)
            return (PAIR shl 20) or (p shl 16) or (kick ushr 4)
        }
        return (HIGH_CARD shl 20) or topN(all, 5)
    }

    private fun highBit(m: Int): Int = 31 - Integer.numberOfLeadingZeros(m)

    /** Packs the top n ranks of mask into nibbles starting at bit 16 going down. */
    private fun topN(mask: Int, n: Int): Int {
        var m = mask
        var out = 0
        var shift = 16
        var k = 0
        while (k < n && m != 0) {
            val h = highBit(m)
            out = out or (h shl shift)
            m = m and (1 shl h).inv()
            shift -= 4
            k++
        }
        return out
    }

    private val categoryNamesEn = arrayOf(
        "High card", "Pair", "Two pair", "Three of a kind", "Straight",
        "Flush", "Full house", "Four of a kind", "Straight flush"
    )
    private val categoryNamesZh = arrayOf(
        "高牌", "一对", "两对", "三条", "顺子", "同花", "葫芦", "四条", "同花顺"
    )

    fun categoryName(score: Int, chinese: Boolean): String {
        val c = category(score)
        if (c == STRAIGHT_FLUSH && ((score ushr 16) and 0xF) == 12) return if (chinese) "皇家同花顺" else "Royal flush"
        return if (chinese) categoryNamesZh[c] else categoryNamesEn[c]
    }
}
