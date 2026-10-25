package com.riverrunner.engine

import kotlin.random.Random

/** Supplies cards for a hand. Lets the same engine run live games and replays. */
interface CardSource {
    fun hole(seat: Int): IntArray
    /** board card with index 0..4 */
    fun board(index: Int): Int
}

class RandomCardSource(random: Random) : CardSource {
    private val deck = Deck(random)
    private val holes = HashMap<Int, IntArray>()
    private val boardCards = ArrayList<Int>()
    override fun hole(seat: Int): IntArray = holes.getOrPut(seat) { intArrayOf(deck.draw(), deck.draw()) }
    override fun board(index: Int): Int {
        while (boardCards.size <= index) boardCards += deck.draw()
        return boardCards[index]
    }
}

class PresetCardSource(private val holes: Map<Int, IntArray>, private val boardCards: IntArray) : CardSource {
    override fun hole(seat: Int): IntArray = holes.getValue(seat)
    override fun board(index: Int): Int = boardCards[index]
}

/** Immutable view of one player during a hand. */
data class PlayerState(
    val seat: Int,
    val name: String,
    val startStack: Int,
    val stack: Int,
    val hole: IntArray,
    val folded: Boolean,
    val allIn: Boolean,
    val streetBet: Int,
    val committed: Int,
)

enum class Phase { BETTING, STREET_DONE, HAND_DONE }

/**
 * No-limit Texas Hold'em hand state machine.
 *
 * Usage: create, then loop: while phase == BETTING -> apply(move) for [toAct];
 * when STREET_DONE -> dealNextStreet(); when HAND_DONE -> read [result].
 */
class HandEngine(val setup: HandSetup, private val cards: CardSource) {

    private class P(
        val seat: Int, val name: String, val startStack: Int,
        var stack: Int, val hole: IntArray,
    ) {
        var folded = false
        var allIn = false
        var streetBet = 0
        var committed = 0
        var hasActed = false
        var actedAtRaise = -1
    }

    private val ps: List<P>
    /** seat order starting left of the button (index 0 = SB for 3+ players). Button is last. */
    val order: List<Int>
    val smallBlindSeat: Int
    val bigBlindSeat: Int
    val bigBlind = setup.bigBlind

    var street: Street = Street.PREFLOP; private set
    var phase: Phase = Phase.BETTING; private set
    var toAct: Int = -1; private set
    var result: HandResult? = null; private set

    private val _actions = ArrayList<Action>()
    val actions: List<Action> get() = _actions
    private val _board = ArrayList<Int>()
    val board: List<Int> get() = _board

    private var currentBet = 0
    private var lastRaiseSize = 0
    private var raiseCounter = 0
    /** seat of the last player who made a full bet/raise on the current street */
    var lastAggressor: Int = -1; private set

    init {
        require(setup.seats.size >= 2) { "Need at least 2 players" }
        val sorted = setup.seats.sortedBy { it.seat }
        val seatsInOrder = sorted.map { it.seat }
        val btnIdx = seatsInOrder.indexOf(setup.buttonSeat)
        require(btnIdx >= 0) { "Button must be a dealt-in seat" }
        order = List(sorted.size) { seatsInOrder[(btnIdx + 1 + it) % sorted.size] }
        ps = order.map { s ->
            val ss = sorted.first { it.seat == s }
            P(s, ss.name, ss.stack, ss.stack, cards.hole(s))
        }
        if (ps.size == 2) {
            smallBlindSeat = setup.buttonSeat
            bigBlindSeat = order[0]
        } else {
            smallBlindSeat = order[0]
            bigBlindSeat = order[1]
        }
        // Antes
        if (setup.ante > 0) {
            for (p in ps) post(p, setup.ante, ActionType.ANTE)
        }
        post(p(smallBlindSeat), setup.smallBlind, ActionType.SMALL_BLIND)
        post(p(bigBlindSeat), setup.bigBlind, ActionType.BIG_BLIND)
        // Antes are dead money: they don't count as street bets.
        currentBet = ps.maxOf { it.streetBet }
        lastRaiseSize = setup.bigBlind
        raiseCounter = 0
        // First to act preflop: left of big blind (heads-up: the button/SB)
        val first = if (ps.size == 2) smallBlindSeat else order[2 % ps.size]
        startBettingFrom(first, inclusive = true)
    }

    private fun p(seat: Int): P = ps.first { it.seat == seat }

    private fun post(p: P, amount: Int, type: ActionType) {
        val a = minOf(amount, p.stack)
        p.stack -= a
        p.committed += a
        if (type != ActionType.ANTE) p.streetBet += a
        if (p.stack == 0) p.allIn = true
        _actions += Action(Street.PREFLOP, p.seat, type, a, p.streetBet, p.allIn)
    }

    // ---------------------------------------------------------------- queries

    val players: List<PlayerState>
        get() = ps.map { PlayerState(it.seat, it.name, it.startStack, it.stack, it.hole, it.folded, it.allIn, it.streetBet, it.committed) }

    fun player(seat: Int): PlayerState = players.first { it.seat == seat }

    val pot: Int get() = ps.sumOf { it.committed }

    val buttonSeat: Int get() = setup.buttonSeat

    /** Number of players still holding cards. */
    val activeCount: Int get() = ps.count { !it.folded }

    fun legalActions(): LegalActions {
        check(phase == Phase.BETTING) { "Not in betting phase" }
        val p = p(toAct)
        val toCall = minOf(currentBet - p.streetBet, p.stack)
        val othersCanRespond = ps.any { it !== p && !it.folded && !it.allIn }
        val reopened = !p.hasActed || p.actedAtRaise < raiseCounter
        val canRaise = p.stack > toCall && reopened && othersCanRespond
        val maxTo = p.streetBet + p.stack
        var minTo = if (currentBet == 0) setup.bigBlind else currentBet + lastRaiseSize
        if (minTo > maxTo) minTo = maxTo
        return LegalActions(
            seat = p.seat,
            toCall = toCall,
            canCheck = toCall == 0,
            canCall = toCall > 0,
            canRaise = canRaise,
            isOpeningBet = currentBet == 0,
            minRaiseTo = minTo,
            maxRaiseTo = maxTo,
            currentBet = currentBet,
            myStreetBet = p.streetBet,
            pot = pot,
        )
    }

    // ---------------------------------------------------------------- actions

    /** Applies [move] for the player to act. Invalid moves are coerced to the closest legal move. */
    fun apply(move: Move): Action {
        check(phase == Phase.BETTING) { "Not in betting phase" }
        val la = legalActions()
        val p = p(toAct)
        val action: Action = when (move.type) {
            MoveType.FOLD -> {
                if (la.canCheck) return apply(Move.CHECK) // never fold when checking is free
                p.folded = true
                Action(street, p.seat, ActionType.FOLD, 0, p.streetBet, false)
            }
            MoveType.CHECK -> {
                if (!la.canCheck) return apply(Move.CALL)
                Action(street, p.seat, ActionType.CHECK, 0, p.streetBet, false)
            }
            MoveType.CALL -> {
                if (la.canCheck) return apply(Move.CHECK)
                val a = la.toCall
                p.stack -= a; p.committed += a; p.streetBet += a
                if (p.stack == 0) p.allIn = true
                Action(street, p.seat, ActionType.CALL, a, p.streetBet, p.allIn)
            }
            MoveType.RAISE -> {
                if (!la.canRaise) return apply(if (la.canCheck) Move.CHECK else Move.CALL)
                val target = move.raiseTo.coerceIn(la.minRaiseTo, la.maxRaiseTo)
                val add = target - p.streetBet
                p.stack -= add; p.committed += add; p.streetBet = target
                if (p.stack == 0) p.allIn = true
                val raiseSize = target - currentBet
                val type = if (currentBet == 0) ActionType.BET else ActionType.RAISE
                if (raiseSize >= lastRaiseSize) {
                    lastRaiseSize = raiseSize
                    raiseCounter++
                }
                currentBet = target
                lastAggressor = p.seat
                Action(street, p.seat, type, add, p.streetBet, p.allIn)
            }
        }
        p.hasActed = true
        p.actedAtRaise = raiseCounter
        _actions += action
        afterAction(p.seat)
        return action
    }

    private fun needsToAct(p: P): Boolean =
        !p.folded && !p.allIn && (!p.hasActed || p.streetBet < currentBet)

    private fun afterAction(lastSeat: Int) {
        if (ps.count { !it.folded } == 1) {
            finishHand()
            return
        }
        val idx = order.indexOf(lastSeat)
        for (k in 1..ps.size) {
            val s = order[(idx + k) % ps.size]
            if (needsToAct(p(s))) { toAct = s; return }
        }
        // Betting round complete
        toAct = -1
        if (street == Street.RIVER) {
            finishHand()
        } else {
            phase = Phase.STREET_DONE
        }
    }

    /** Whether the rest of the board will be dealt without further betting (all-in run out). */
    val isRunout: Boolean get() = ps.count { !it.folded && !it.allIn } <= 1

    fun dealNextStreet() {
        check(phase == Phase.STREET_DONE) { "Street not complete" }
        street = Street.entries[street.ordinal + 1]
        while (_board.size < street.boardCount) _board += cards.board(_board.size)
        for (p in ps) { p.streetBet = 0; p.hasActed = false; p.actedAtRaise = -1 }
        currentBet = 0
        lastRaiseSize = setup.bigBlind
        raiseCounter = 0
        lastAggressor = -1
        phase = Phase.BETTING
        if (isRunout) {
            toAct = -1
            if (street == Street.RIVER) finishHand() else phase = Phase.STREET_DONE
            return
        }
        // first to act postflop: first live player starting at order[0]
        startBettingFrom(order[0], inclusive = true)
    }

    private fun startBettingFrom(seat: Int, inclusive: Boolean) {
        val idx = order.indexOf(seat)
        val startK = if (inclusive) 0 else 1
        for (k in startK until startK + ps.size) {
            val s = order[(idx + k) % ps.size]
            if (needsToAct(p(s))) { toAct = s; phase = Phase.BETTING; return }
        }
        // nobody can act (e.g. everyone all-in from blinds)
        toAct = -1
        if (ps.count { !it.folded } == 1) finishHand()
        else if (street == Street.RIVER) finishHand()
        else phase = Phase.STREET_DONE
    }

    // ---------------------------------------------------------------- settlement

    private fun finishHand() {
        toAct = -1
        val live = ps.filter { !it.folded }
        // Return uncalled chips: the top contributor beyond the second-highest contribution.
        var returned: Pair<Int, Int>? = null
        val byCommit = ps.sortedByDescending { it.committed }
        if (byCommit.size >= 2 && byCommit[0].committed > byCommit[1].committed) {
            val extra = byCommit[0].committed - byCommit[1].committed
            byCommit[0].committed -= extra
            byCommit[0].stack += extra
            returned = byCommit[0].seat to extra
        }

        val won = HashMap<Int, Int>()
        val pots = ArrayList<Pot>()
        val scores = HashMap<Int, Int>()
        val showdown: List<Int>

        if (live.size == 1) {
            // Everyone else folded: no need to deal the rest of the board.
            val w = live[0]
            val total = ps.sumOf { it.committed }
            pots += Pot(total, listOf(w.seat), listOf(w.seat))
            won[w.seat] = total
            showdown = emptyList()
        } else {
            while (_board.size < 5) _board += cards.board(_board.size)
            val boardMask = Cards.mask(_board.toIntArray())
            for (p in live) scores[p.seat] = HandEvaluator.evaluate(boardMask or Cards.mask(p.hole))
            showdown = order.filter { s -> live.any { it.seat == s } }
            val contrib = HashMap<Int, Int>()
            for (p in ps) contrib[p.seat] = p.committed
            while (contrib.values.any { it > 0 }) {
                val liveWithChips = live.filter { contrib.getValue(it.seat) > 0 }
                if (liveWithChips.isEmpty()) {
                    // Only dead money left (shouldn't happen after uncalled return); add to last pot.
                    val rest = contrib.values.sum()
                    if (pots.isNotEmpty()) {
                        val last = pots.removeAt(pots.size - 1)
                        pots += last.copy(amount = last.amount + rest)
                        val share = rest / last.winners.size
                        last.winners.forEach { won[it] = (won[it] ?: 0) + share }
                        won[last.winners[0]] = won.getValue(last.winners[0]) + rest - share * last.winners.size
                    }
                    break
                }
                val level = liveWithChips.minOf { contrib.getValue(it.seat) }
                var amount = 0
                for (s in contrib.keys.toList()) {
                    val c = contrib.getValue(s)
                    val take = minOf(c, level)
                    amount += take
                    contrib[s] = c - take
                }
                val eligible = liveWithChips.map { it.seat }
                val best = eligible.maxOf { scores.getValue(it) }
                // winners ordered from the left of the button for odd-chip distribution
                val winners = order.filter { it in eligible && scores.getValue(it) == best }
                val share = amount / winners.size
                var odd = amount - share * winners.size
                for (wSeat in winners) {
                    var get = share
                    if (odd > 0) { get++; odd-- }
                    won[wSeat] = (won[wSeat] ?: 0) + get
                }
                pots += Pot(amount, eligible, winners)
            }
            // Merge consecutive pots with identical eligibility (cosmetic)
        }
        for ((s, amt) in won) p(s).stack += amt
        val wonWithReturn = HashMap(won)
        if (returned != null) wonWithReturn[returned.first] = (wonWithReturn[returned.first] ?: 0) + returned.second
        val net = ps.associate { it.seat to (it.stack - it.startStack) }
        result = HandResult(
            won = wonWithReturn, net = net, pots = pots, showdown = showdown,
            scores = scores, board = _board.toList(), uncalledReturned = returned,
        )
        phase = Phase.HAND_DONE
    }

    // ---------------------------------------------------------------- positions

    /** Position label for a seat in this hand. */
    fun positionName(seat: Int): String = positionName(order.indexOf(seat), ps.size)

    /** Number of players that act after [seat] preflop in an unopened pot. */
    fun playersBehindPreflop(seat: Int): Int {
        val n = ps.size
        if (n == 2) return if (seat == smallBlindSeat) 1 else 0
        val idx = order.indexOf(seat) // 0 = SB, 1 = BB, 2.. = UTG..BTN
        return if (idx >= 2) (n - 1 - idx) + 2 else if (idx == 0) 1 else 0
    }

    companion object {
        fun positionName(idx: Int, n: Int): String {
            if (n == 2) return if (idx == 1) "BTN" else "BB"
            if (idx == 0) return "SB"
            if (idx == 1) return "BB"
            if (idx == n - 1) return "BTN"
            if (idx == 2) return "UTG"
            if (idx == n - 2) return "CO"
            if (idx == n - 3) return "HJ"
            return "UTG+${idx - 2}"
        }
    }
}
