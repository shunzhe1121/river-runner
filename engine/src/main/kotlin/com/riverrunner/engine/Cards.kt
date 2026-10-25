package com.riverrunner.engine

import kotlin.random.Random

/**
 * A card is an Int in 0..51: id = suit * 13 + rank.
 * rank 0 = '2' ... 12 = 'A'; suit 0 = clubs, 1 = diamonds, 2 = hearts, 3 = spades.
 * This layout lets a set of cards be stored as a Long bitmask whose 13-bit
 * slices are the per-suit rank masks.
 */
object Cards {
    const val RANKS = "23456789TJQKA"
    const val SUITS = "cdhs"

    fun rank(card: Int): Int = card % 13
    fun suit(card: Int): Int = card / 13
    fun of(rank: Int, suit: Int): Int = suit * 13 + rank

    fun toString(card: Int): String = "${RANKS[rank(card)]}${SUITS[suit(card)]}"

    fun parse(s: String): Int {
        require(s.length == 2) { "Bad card '$s'" }
        val r = RANKS.indexOf(s[0].uppercaseChar())
        val su = SUITS.indexOf(s[1].lowercaseChar())
        require(r >= 0 && su >= 0) { "Bad card '$s'" }
        return of(r, su)
    }

    /** Parses "AhKd" or "Ah Kd" into card ids. */
    fun parseMany(s: String): IntArray {
        val t = s.replace(" ", "")
        require(t.length % 2 == 0) { "Bad card list '$s'" }
        return IntArray(t.length / 2) { parse(t.substring(it * 2, it * 2 + 2)) }
    }

    fun format(cards: IntArray): String = cards.joinToString("") { toString(it) }

    fun mask(cards: IntArray): Long {
        var m = 0L
        for (c in cards) m = m or (1L shl c)
        return m
    }
}

/** A shuffled 52-card deck. */
class Deck(private val random: Random) {
    private val cards = IntArray(52) { it }
    private var next = 0

    init { shuffle() }

    fun shuffle() {
        for (i in 51 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = cards[i]; cards[i] = cards[j]; cards[j] = t
        }
        next = 0
    }

    fun draw(): Int = cards[next++]
}
