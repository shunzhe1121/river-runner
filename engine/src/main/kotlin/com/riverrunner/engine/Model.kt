package com.riverrunner.engine

enum class Street { PREFLOP, FLOP, TURN, RIVER;
    val boardCount: Int get() = when (this) { PREFLOP -> 0; FLOP -> 3; TURN -> 4; RIVER -> 5 }
}

enum class ActionType { ANTE, SMALL_BLIND, BIG_BLIND, FOLD, CHECK, CALL, BET, RAISE;
    val isPost: Boolean get() = this == ANTE || this == SMALL_BLIND || this == BIG_BLIND
    val isAggressive: Boolean get() = this == BET || this == RAISE
}

/**
 * One action in a hand.
 * [amount] = chips this action put into the pot; [streetTotal] = the player's
 * total bet on this street after the action.
 */
data class Action(
    val street: Street,
    val seat: Int,
    val type: ActionType,
    val amount: Int,
    val streetTotal: Int,
    val allIn: Boolean,
)

/**
 * Bet-size class, measured against the pot before the bet. [center] is the
 * nominal pot fraction the bot aims for; [of] buckets any amount into a class.
 */
enum class SizeClass(val center: Double) {
    SMALL(0.33), MEDIUM(0.5), LARGE(0.75), POT(1.0), OVER(1.5);

    companion object {
        fun of(chips: Int, potBefore: Int): SizeClass {
            val r = chips.toDouble() / kotlin.math.max(1, potBefore)
            return when {
                r < 0.42 -> SMALL
                r < 0.62 -> MEDIUM
                r < 0.87 -> LARGE
                r < 1.2 -> POT
                else -> OVER
            }
        }
    }
}

enum class MoveType { FOLD, CHECK, CALL, RAISE }

/** A decision taken by a player. For RAISE (also used for an opening bet), [raiseTo] is the street total. */
data class Move(val type: MoveType, val raiseTo: Int = 0) {
    companion object {
        val FOLD = Move(MoveType.FOLD)
        val CHECK = Move(MoveType.CHECK)
        val CALL = Move(MoveType.CALL)
        fun raiseTo(amount: Int) = Move(MoveType.RAISE, amount)
    }
}

data class LegalActions(
    val seat: Int,
    /** chips needed to call (already capped at the player's stack) */
    val toCall: Int,
    val canCheck: Boolean,
    val canCall: Boolean,
    val canRaise: Boolean,
    /** true when there is no bet on this street yet (the raise is an opening bet) */
    val isOpeningBet: Boolean,
    val minRaiseTo: Int,
    val maxRaiseTo: Int,
    val currentBet: Int,
    val myStreetBet: Int,
    val pot: Int,
)

data class SeatSetup(val seat: Int, val name: String, val stack: Int)

data class HandSetup(
    val smallBlind: Int,
    val bigBlind: Int,
    val ante: Int,
    val buttonSeat: Int,
    /** players dealt in, any seat order */
    val seats: List<SeatSetup>,
)

data class Pot(val amount: Int, val eligible: List<Int>, val winners: List<Int>)

data class HandResult(
    /** total chips each seat collected from pots (includes returned uncalled bets) */
    val won: Map<Int, Int>,
    /** net change of each seat's stack for the hand */
    val net: Map<Int, Int>,
    val pots: List<Pot>,
    /** seats whose cards are shown at showdown (empty when the hand ended by folds) */
    val showdown: List<Int>,
    /** hand scores of shown hands */
    val scores: Map<Int, Int>,
    val board: List<Int>,
    val uncalledReturned: Pair<Int, Int>?,
)
