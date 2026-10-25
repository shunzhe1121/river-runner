package com.riverrunner.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Player styles. GTO approximates solver-like play; the others deviate in
 * recognisable, exploitable ways so a "mixed" table resembles a real game.
 */
enum class BotStyle(
    val code: String,
    /** multiplies preflop range widths */
    val looseness: Double,
    /** multiplies value raising / betting frequencies */
    val aggression: Double,
    /** multiplies bluffing frequencies */
    val bluff: Double,
    /** added to effective equity when deciding to call (positive = calls more) */
    val callBias: Double,
    /** extra width multiplier for flat calls preflop */
    val preflopCall: Double,
) {
    GTO("G", 1.0, 1.0, 1.0, 0.0, 1.0),
    TAG("T", 0.85, 1.1, 0.75, -0.02, 0.85),
    LAG("L", 1.35, 1.25, 1.5, 0.01, 1.1),
    STATION("S", 1.5, 0.45, 0.25, 0.12, 2.2),
    NIT("N", 0.6, 0.8, 0.35, -0.06, 0.7),
    MANIAC("M", 1.9, 1.8, 2.3, 0.04, 1.4);

    companion object {
        fun fromCode(c: String): BotStyle = entries.firstOrNull { it.code == c } ?: GTO
    }
}

/** Tournament info a bot may use for ICM decisions. */
class TournamentContext(
    /** stacks (start of this hand) of every player still in the tournament, by seat */
    val stacks: Map<Int, Int>,
    val payouts: DoubleArray,
)

/** A mixed strategy over legal moves. */
class Policy(
    val options: List<Pair<Move, Double>>,
    /** estimated equity vs. opponents' ranges, when computed */
    val equity: Double?,
    /** pot odds faced (0 when nothing to call) */
    val potOdds: Double,
    /** required equity under ICM when it applied */
    val icmRequired: Double?,
) {
    fun sample(random: Random): Move {
        var r = random.nextDouble()
        for ((m, p) in options) {
            r -= p
            if (r <= 0) return m
        }
        return options.last().first
    }

    fun probability(type: MoveType): Double = options.filter { it.first.type == type }.sumOf { it.second }

    /** The single most likely move. */
    val best: Move get() = options.maxBy { it.second }.first
}

/** A percentile interval [lo, hi) of the starting-hand order describing a range. */
data class RangeEstimate(val lo: Double, val hi: Double)

object Bot {

    // ------------------------------------------------------------------ tables

    /**
     * Raise-first-in width by number of players left to act behind.
     * Based on common solver opening charts (100bb, ~2.5x opens):
     * BTN ~44%, CO ~28%, HJ ~21%, LJ ~17.5%, UTG(7-8 max) ~12-14%. SB raises ~42%.
     */
    fun rfiWidth(behind: Int, headsUp: Boolean): Double {
        if (headsUp) return 0.82
        return when (behind) {
            0 -> 0.0
            1 -> 0.42  // SB vs BB
            2 -> 0.44  // BTN
            3 -> 0.28  // CO
            4 -> 0.21  // HJ
            5 -> 0.175
            6 -> 0.145
            else -> 0.12
        }
    }

    /**
     * Approximate Nash push width (fraction of hands) by effective stack in big
     * blinds and players left to act. Reference points: SB 10bb ~58%,
     * BTN 10bb ~40%, CO 10bb ~29%, UTG(6-max) 10bb ~17%; heads-up SB 10bb ~62%.
     */
    fun pushWidth(effBB: Double, behind: Int): Double {
        val base10 = when (behind) {
            0 -> 0.0
            1 -> 0.58
            2 -> 0.40
            3 -> 0.29
            4 -> 0.22
            5 -> 0.17
            6 -> 0.14
            else -> 0.12
        }
        // widen as stacks get shorter, tighten as they get deeper
        val factor = (10.0 / effBB.coerceAtLeast(1.0)).let { Math.pow(it, 0.85) }
        return (base10 * factor).coerceIn(0.0, 1.0)
    }

    // ------------------------------------------------------------------ helpers

    private fun smooth(x: Double, threshold: Double, band: Double): Double =
        1.0 / (1.0 + exp((x - threshold) / band))   // 1 when x << threshold

    private fun above(x: Double, threshold: Double, band: Double): Double =
        1.0 / (1.0 + exp(-(x - threshold) / band))  // 1 when x >> threshold

    private fun isSuited(h: IntArray) = Cards.suit(h[0]) == Cards.suit(h[1])
    private fun isSuitedWheelAce(h: IntArray): Boolean {
        if (!isSuited(h)) return false
        val r = listOf(Cards.rank(h[0]), Cards.rank(h[1])).sorted()
        return r[1] == 12 && r[0] <= 3
    }

    // ------------------------------------------------------------------ ranges

    /** Estimates every player's preflop range from the public action sequence. */
    fun preflopRanges(engine: HandEngine): Map<Int, RangeEstimate> {
        val n = engine.order.size
        val hu = n == 2
        val ranges = HashMap<Int, RangeEstimate>()
        for (s in engine.order) ranges[s] = RangeEstimate(0.0, 1.0)
        var raises = 0
        var limpers = 0
        var lastRaiserWidth = 1.0
        val bb = engine.bigBlind.toDouble()
        for (a in engine.actions) {
            if (a.street != Street.PREFLOP || a.type.isPost) continue
            val behind = engine.playersBehindPreflop(a.seat)
            val isBB = a.seat == engine.bigBlindSeat
            when (a.type) {
                ActionType.FOLD -> {}
                ActionType.CHECK -> ranges[a.seat] = RangeEstimate(0.06, 1.0)
                ActionType.CALL -> {
                    ranges[a.seat] = when (raises) {
                        0 -> RangeEstimate(0.06, 0.6)
                        1 -> if (isBB) RangeEstimate(min(0.03, lastRaiserWidth * 0.25), min(0.8, lastRaiserWidth * 2.0 + 0.1))
                             else RangeEstimate(lastRaiserWidth * 0.2, min(0.7, lastRaiserWidth * 0.9))
                        2 -> RangeEstimate(0.012, max(0.05, lastRaiserWidth * 1.4))
                        else -> RangeEstimate(0.0, max(0.03, lastRaiserWidth * 1.5))
                    }
                }
                ActionType.BET, ActionType.RAISE -> {
                    val stackBB = (engine.player(a.seat).startStack) / bb
                    val w = when (raises) {
                        0 -> if (a.allIn && stackBB <= 15) pushWidth(stackBB, behind)
                             else if (limpers > 0) rfiWidth(behind, hu) * 0.65
                             else rfiWidth(behind, hu)
                        1 -> if (a.allIn && stackBB <= 20) max(0.06, lastRaiserWidth * 0.5)
                             else max(0.03, lastRaiserWidth * 0.33)
                        2 -> max(0.025, lastRaiserWidth * 0.4)
                        else -> max(0.02, lastRaiserWidth * 0.6)
                    }
                    ranges[a.seat] = RangeEstimate(0.0, w.coerceIn(0.015, 1.0))
                    lastRaiserWidth = w
                    raises++
                }
                else -> {}
            }
            if (a.type == ActionType.CALL && raises == 0) limpers++
        }
        return ranges
    }

    /** Coarse made-hand class: 0 air, 1 draw, 2 weak made, 3 strong one-pair, 4 two pair or better. */
    fun handClass(hole: IntArray, board: List<Int>): Int {
        if (board.size < 3) return 0
        val boardMask = Cards.mask(board.toIntArray())
        val all = boardMask or Cards.mask(hole)
        val heroCat = HandEvaluator.category(HandEvaluator.evaluate(all))
        val boardCat = HandEvaluator.category(HandEvaluator.evaluate(boardMask))
        val boardRanks = board.map { Cards.rank(it) }
        val topBoard = boardRanks.max()
        val h0 = Cards.rank(hole[0]); val h1 = Cards.rank(hole[1])
        if (heroCat > boardCat) {
            when {
                heroCat >= HandEvaluator.STRAIGHT -> return 4
                heroCat == HandEvaluator.TRIPS -> return 4
                heroCat == HandEvaluator.TWO_PAIR && boardCat == HandEvaluator.HIGH_CARD -> return 4
                heroCat == HandEvaluator.TWO_PAIR && boardCat == HandEvaluator.PAIR -> {
                    val pr = if (h0 == h1) h0 else if (h0 in boardRanks) h0 else h1
                    return if (pr >= topBoard) 3 else 2
                }
                heroCat == HandEvaluator.PAIR -> {
                    if (h0 == h1) return if (h0 > topBoard) 3 else 2
                    val pr = if (h0 in boardRanks) h0 else h1
                    val kicker = if (pr == h0) h1 else h0
                    return if (pr == topBoard && (kicker >= 8 || pr >= 11)) 3 else 2
                }
                else -> return 2
            }
        }
        return if (board.size < 5 && drawOuts(hole, board) >= 8) 1 else if (board.size < 5 && drawOuts(hole, board) >= 4 && h0 >= 9 && h1 >= 9) 1 else 0
    }

    /** Rough count of outs to a flush or straight (only draws using a hole card). */
    fun drawOuts(hole: IntArray, board: List<Int>): Int {
        var outs = 0
        val cards = board + hole.toList()
        for (s in 0 until 4) {
            val cnt = cards.count { Cards.suit(it) == s }
            if (cnt == 4 && hole.any { Cards.suit(it) == s }) outs += 9
        }
        val ranks = cards.map { Cards.rank(it) }.toSet()
        val boardRanks = board.map { Cards.rank(it) }.toSet()
        fun hasStraight(rs: Set<Int>): Boolean {
            var m = 0
            for (r in rs) m = m or (1 shl r)
            if (m and ((1 shl 12) or 0xF) == ((1 shl 12) or 0xF)) return true
            for (hi in 4..12) { val need = 0x1F shl (hi - 4); if (m and need == need) return true }
            return false
        }
        if (!hasStraight(ranks)) {
            var straightRanks = 0
            for (r in 0..12) {
                if (r in ranks) continue
                if (hasStraight(ranks + r) && !hasStraight(boardRanks + r)) straightRanks++
            }
            outs += min(straightRanks, 2) * 4
        }
        return outs
    }

    /** 0 = very dry, 1 = very wet. */
    fun wetness(board: List<Int>): Double {
        if (board.size < 3) return 0.0
        val suits = IntArray(4)
        board.forEach { suits[Cards.suit(it)]++ }
        val maxSuit = suits.max()
        val ranks = board.map { Cards.rank(it) }.distinct().sorted()
        var connected = 0
        for (i in 1 until ranks.size) if (ranks[i] - ranks[i - 1] <= 2) connected++
        val paired = ranks.size < board.size
        var w = 0.0
        if (maxSuit >= 3) w += 0.45 else if (maxSuit == 2) w += 0.2
        w += connected * 0.18
        if (ranks.size >= 3 && ranks.last() - ranks[ranks.size - 3] <= 4) w += 0.15
        if (paired) w -= 0.2
        if (ranks.last() >= 12 && connected == 0) w -= 0.1
        return w.coerceIn(0.0, 1.0)
    }

    private fun weightFor(cls: Int, action: ActionType?): Double = when (action) {
        ActionType.BET, ActionType.RAISE -> when (cls) { 0 -> 0.3; 1 -> 0.85; 2 -> 0.45; else -> 1.0 }
        ActionType.CALL -> when (cls) { 0 -> 0.12; 1 -> 1.0; 2 -> 0.9; 3 -> 0.9; else -> 0.6 }
        ActionType.CHECK -> when (cls) { 0 -> 1.0; 1 -> 0.9; 2 -> 1.0; 3 -> 0.7; else -> 0.45 }
        else -> 1.0
    }

    /** Builds a sampler for an opponent's hand using preflop range + postflop action weights. */
    private fun samplerFor(engine: HandEngine, seat: Int, range: RangeEstimate): Equity.HandSampler {
        val board = engine.board
        // strongest action per postflop street for this opponent
        val streetActions = HashMap<Street, ActionType>()
        for (a in engine.actions) {
            if (a.seat != seat || a.street == Street.PREFLOP) continue
            val prev = streetActions[a.street]
            if (prev == null || a.type.isAggressive || (prev == ActionType.CHECK && a.type == ActionType.CALL)) streetActions[a.street] = a.type
        }
        return Equity.HandSampler { dead, random ->
            var lastMask = 0L
            for (attempt in 0 until 25) {
                val packed = HandClasses.sample(range.lo, range.hi, dead, random)
                    .let { if (it < 0) HandClasses.sample(0.0, 1.0, dead, random) else it }
                if (packed < 0) return@HandSampler 0L
                val c1 = packed / 64; val c2 = packed % 64
                val mask = (1L shl c1) or (1L shl c2)
                lastMask = mask
                if (streetActions.isEmpty()) return@HandSampler mask
                var w = 1.0
                val hole = intArrayOf(c1, c2)
                for ((st, act) in streetActions) {
                    val prefix = board.take(st.boardCount)
                    if (prefix.size < 3) continue
                    w *= weightFor(handClass(hole, prefix), act)
                }
                if (random.nextDouble() < w) return@HandSampler mask
            }
            lastMask
        }
    }

    fun equityVsField(engine: HandEngine, seat: Int, random: Random, iters: Int = 300): Double {
        val ranges = preflopRanges(engine)
        val me = engine.player(seat)
        val opps = engine.players.filter { it.seat != seat && !it.folded }
        val samplers = opps.map { samplerFor(engine, it.seat, ranges.getValue(it.seat)) }
        return Equity.vsRanges(me.hole, engine.board, samplers, iters, random)
    }

    // ------------------------------------------------------------------ decision

    /**
     * Decides for [seat] using only information that seat legitimately has:
     * its own hole cards, the board, stacks and the public action history.
     */
    fun decide(
        engine: HandEngine,
        seat: Int,
        style: BotStyle,
        random: Random,
        tournament: TournamentContext? = null,
    ): Policy {
        val la = engine.legalActions()
        require(la.seat == seat)
        return if (engine.street == Street.PREFLOP) preflop(engine, seat, style, la, random, tournament)
        else postflop(engine, seat, style, la, random, tournament)
    }

    private fun normalized(opts: List<Pair<Move, Double>>): List<Pair<Move, Double>> {
        val merged = LinkedHashMap<Move, Double>()
        for ((m, p) in opts) if (p > 0) merged[m] = (merged[m] ?: 0.0) + p
        val total = merged.values.sum()
        if (total <= 0) return listOf(Move.CHECK to 1.0)
        // drop tiny frequencies to keep play clean
        val cleaned = merged.filter { it.value / total >= 0.03 }
        val t2 = cleaned.values.sum()
        return cleaned.map { it.key to it.value / t2 }
    }

    private fun raiseMove(la: LegalActions, target: Int): Move {
        var t = target.coerceIn(la.minRaiseTo, la.maxRaiseTo)
        // if the raise would leave a tiny stack behind, just shove
        if (la.maxRaiseTo - t < (la.maxRaiseTo - la.myStreetBet) * 0.25) t = la.maxRaiseTo
        return Move.raiseTo(t)
    }

    private fun passiveMove(la: LegalActions): Move = if (la.canCheck) Move.CHECK else Move.FOLD

    /** Equity needed to call: pot odds, or ICM when it is a big tournament decision. */
    private fun requiredEquity(engine: HandEngine, seat: Int, la: LegalActions, tournament: TournamentContext?): Pair<Double, Double?> {
        val potOdds = la.toCall.toDouble() / (la.pot + la.toCall)
        if (tournament == null) return potOdds to null
        val me = engine.player(seat)
        val bigDecision = la.toCall >= me.stack * 0.5 || la.toCall == me.stack
        val live = engine.players.filter { !it.folded && it.seat != seat }
        if (!bigDecision || live.size != 1) return potOdds to null
        val v = live[0]
        val seats = tournament.stacks.keys.sorted()
        val idx = seats.indexOf(seat)
        if (idx < 0) return potOdds to null
        val call = min(la.toCall, me.stack)
        val myTotal = me.committed + call
        val vMatched = min(v.committed, myTotal)
        val dead = engine.players.filter { it.seat != seat && it.seat != v.seat }.sumOf { it.committed }
        fun stacksWith(f: (Int, Int) -> Int): IntArray = IntArray(seats.size) { i ->
            val s = seats[i]
            val ps = engine.players.firstOrNull { it.seat == s }
            val now = ps?.stack ?: tournament.stacks.getValue(s)
            f(s, now)
        }
        val ifFold = stacksWith { s, now -> if (s == v.seat) now + engine.pot else now }
        val ifWin = stacksWith { s, now ->
            when (s) {
                seat -> now - call + myTotal + vMatched + dead
                v.seat -> now + (v.committed - vMatched)
                else -> now
            }
        }
        val ifLose = stacksWith { s, now ->
            when (s) {
                seat -> now - call
                v.seat -> now + myTotal + v.committed + dead
                else -> now
            }
        }
        val req = Icm.requiredEquity(idx, ifFold, ifWin, ifLose, tournament.payouts)
        return max(req, potOdds) to req
    }

    private fun preflop(engine: HandEngine, seat: Int, style: BotStyle, la: LegalActions, random: Random, t: TournamentContext?): Policy {
        val me = engine.player(seat)
        val bb = engine.bigBlind
        val pct = HandClasses.percentile(me.hole[0], me.hole[1])
        val hu = engine.order.size == 2
        val behind = engine.playersBehindPreflop(seat)
        val acts = engine.actions.filter { it.street == Street.PREFLOP && !it.type.isPost }
        val raises = acts.count { it.type.isAggressive }
        val callers = acts.count { it.type == ActionType.CALL }
        val othersMax = engine.players.filter { it.seat != seat && !it.folded }.maxOfOrNull { it.stack + it.streetBet } ?: 0
        val effBB = min(me.stack + me.streetBet, othersMax).toDouble() / bb
        val opts = ArrayList<Pair<Move, Double>>()
        val ranges = preflopRanges(engine)
        val iWasRaiser = acts.any { it.seat == seat && it.type.isAggressive }

        // ---- big calls (facing a shove or a raise that commits a large part of the stack)
        if (la.toCall > 0 && (la.toCall >= me.stack * 0.4 || engine.players.any { it.allIn && !it.folded && it.seat != seat && it.streetBet >= la.currentBet })) {
            val opps = engine.players.filter { !it.folded && it.seat != seat }
            val samplers = opps.map { samplerFor(engine, it.seat, ranges.getValue(it.seat)) }
            val eq = Equity.vsRanges(me.hole, emptyList(), samplers, 500, random)
            val (req, icm) = requiredEquity(engine, seat, la, t)
            val margin = eq + style.callBias * 0.5 - req
            val pCall = above(margin, 0.0, 0.02)
            val canJam = la.canRaise && raises <= 3 && pct < 0.03 * style.aggression
            if (canJam) opts += Move.raiseTo(la.maxRaiseTo) to pCall
            else opts += Move.CALL to pCall
            opts += Move.FOLD to (1 - pCall)
            return Policy(normalized(opts), eq, la.toCall.toDouble() / (la.pot + la.toCall), icm)
        }

        if (raises == 0) {
            // ---- unopened (possibly limped) pot
            val limpers = callers
            if (la.canCheck) {
                // big blind option after limps / folds
                val thr = 0.12 * style.looseness * style.aggression
                val pR = smooth(pct, thr, 0.02)
                val size = bb * (3 + limpers)
                if (la.canRaise) opts += raiseMove(la, size) to pR
                opts += Move.CHECK to (1 - pR)
                return Policy(normalized(opts), null, 0.0, null)
            }
            if (effBB <= 13 && t != null || effBB <= 10) {
                val w = pushWidth(effBB, behind) * (if (hu) 1.07 else 1.0) * style.looseness
                val pJam = smooth(pct, w, 0.012)
                opts += Move.raiseTo(la.maxRaiseTo) to pJam
                opts += Move.FOLD to (1 - pJam)
                return Policy(normalized(opts), null, 0.0, null)
            }
            var thr = rfiWidth(behind, hu) * style.looseness
            if (limpers > 0) thr *= 0.65
            val pRaise = smooth(pct, thr, 0.02)
            val openBB = when {
                hu -> 2.5
                seat == engine.smallBlindSeat -> 3.0
                effBB < 30 -> 2.2
                else -> 2.5
            } + limpers
            opts += raiseMove(la, (openBB * bb).toInt()) to pRaise
            // passive styles sometimes limp
            if (style == BotStyle.STATION || style == BotStyle.MANIAC) {
                val pLimp = smooth(pct, thr * 1.5, 0.03) * (1 - pRaise) * 0.6
                opts += Move.CALL to pLimp
                opts += Move.FOLD to (1 - pRaise - pLimp)
            } else {
                opts += Move.FOLD to (1 - pRaise)
            }
            return Policy(normalized(opts), null, 0.0, null)
        }

        val potOdds = la.toCall.toDouble() / (la.pot + la.toCall)
        val lastRaise = acts.last { it.type.isAggressive }
        val raiserWidth = ranges.getValue(lastRaise.seat).hi
        val isBB = seat == engine.bigBlindSeat
        val isSB = seat == engine.smallBlindSeat && !hu
        // in position = acts after the raiser postflop
        val ip = engine.order.indexOf(seat) > engine.order.indexOf(lastRaise.seat)
        val callersAfterRaise = acts.dropWhile { it !== lastRaise }.count { it.type == ActionType.CALL }

        when (raises) {
            1 -> {
                var threeBet = max(0.022, raiserWidth * 0.30) * style.aggression
                var callTop = when {
                    isBB -> min(0.75, raiserWidth * 1.9 + 0.08 - (potOdds - 0.25))
                    isSB -> raiserWidth * 0.45
                    hu -> min(0.75, raiserWidth * 0.8)
                    else -> raiserWidth * 0.85
                } * style.preflopCall
                if (isSB) threeBet *= 1.35
                if (callersAfterRaise > 0) { callTop *= 0.8; threeBet *= 1.1 }
                if (!ip && !isBB) callTop *= 0.85
                callTop = callTop.coerceIn(threeBet, 0.85)
                val pValue = smooth(pct, threeBet, 0.008)
                var pBluff = 0.0
                if (pct > callTop && pct < callTop + 0.09 && (isSuited(me.hole))) pBluff = 0.35 * style.bluff
                if (isSuitedWheelAce(me.hole) && pct > threeBet) pBluff = max(pBluff, 0.4 * style.bluff)
                val p3 = (pValue + (1 - pValue) * pBluff).coerceAtMost(1.0)
                val pCall = (smooth(pct, callTop, 0.02) * (1 - p3)).coerceAtLeast(0.0)
                val mult = (if (ip) 3.0 else 3.8) + callersAfterRaise
                if (la.canRaise) opts += raiseMove(la, (lastRaise.streetTotal * mult).toInt()) to p3
                else opts += Move.CALL to p3
                opts += Move.CALL to pCall
                opts += Move.FOLD to (1 - p3 - pCall).coerceAtLeast(0.0)
            }
            2 -> {
                val fourBet: Double
                val callTop: Double
                if (iWasRaiser) {
                    fourBet = max(0.02, raiserWidth * 0.33) * style.aggression
                    callTop = max(0.06, raiserWidth * (if (ip) 1.05 else 0.8)) * style.preflopCall
                } else {
                    fourBet = 0.02 * style.aggression
                    callTop = 0.04 * style.preflopCall
                }
                val pValue = smooth(pct, fourBet, 0.006)
                val pBluff = if (iWasRaiser && isSuitedWheelAce(me.hole)) 0.3 * style.bluff else 0.0
                val p4 = (pValue + (1 - pValue) * pBluff).coerceAtMost(1.0)
                val pCall = (smooth(pct, callTop, 0.012) * (1 - p4))
                val target = (lastRaise.streetTotal * (if (ip) 2.3 else 2.6)).toInt()
                if (la.canRaise) opts += raiseMove(la, target) to p4 else opts += Move.CALL to p4
                opts += Move.CALL to pCall
                opts += Move.FOLD to (1 - p4 - pCall).coerceAtLeast(0.0)
            }
            else -> {
                val jam = 0.022 * style.aggression
                val pJam = smooth(pct, jam, 0.005)
                val pCall = smooth(pct, 0.045 * style.preflopCall, 0.008) * (1 - pJam)
                if (la.canRaise) opts += Move.raiseTo(la.maxRaiseTo) to pJam else opts += Move.CALL to pJam
                opts += Move.CALL to pCall
                opts += Move.FOLD to (1 - pJam - pCall).coerceAtLeast(0.0)
            }
        }
        return Policy(normalized(opts), null, potOdds, null)
    }

    private fun postflop(engine: HandEngine, seat: Int, style: BotStyle, la: LegalActions, random: Random, t: TournamentContext?): Policy {
        val me = engine.player(seat)
        val board = engine.board
        val street = engine.street
        val opps = engine.players.filter { !it.folded && it.seat != seat }
        val nOpp = opps.size
        val eq = equityVsField(engine, seat, random, if (nOpp >= 3) 220 else 320)
        val rel = eq * (nOpp + 1) / 2.0   // 1.0 = "fair share" heads-up equivalent
        val cls = handClass(me.hole, board)
        val wet = wetness(board)
        val pot = la.pot
        val spr = me.stack.toDouble() / max(1, pot)
        val order = engine.order
        val myIdx = order.indexOf(seat)
        val ip = opps.filter { !it.allIn }.all { order.indexOf(it.seat) < myIdx }
        // initiative: last aggressor on the previous street
        val prevStreet = Street.entries[street.ordinal - 1]
        val prevAggressor = engine.actions.lastOrNull { it.street == prevStreet && it.type.isAggressive }?.seat
        val initiative = prevAggressor == seat
        val opts = ArrayList<Pair<Move, Double>>()
        val multi = if (nOpp >= 2) 0.35 else 1.0

        if (la.toCall == 0) {
            // ---------------- we can check or bet
            if (!la.canRaise) return Policy(listOf(Move.CHECK to 1.0), eq, 0.0, null)
            val frac = when (street) {
                Street.FLOP -> if (nOpp >= 2) 0.5 else if (wet < 0.3) 0.33 else if (wet < 0.6) 0.5 else 0.75
                Street.TURN -> if (wet >= 0.5) 0.75 else 0.66
                else -> if (rel > 1.75) 1.0 else 0.75
            }
            val valueThr = when (street) { Street.FLOP -> 1.22; Street.TURN -> 1.3; else -> 1.4 }
            var pBet = above(rel, valueThr, 0.06) * min(1.0, 0.85 * style.aggression + 0.1)
            if (cls == 4 && street == Street.FLOP && wet < 0.3 && nOpp == 1) pBet *= 0.7 // some slow-playing
            if (rel < valueThr) {
                val bluff: Double = when {
                    cls == 1 && street != Street.RIVER -> 0.5
                    initiative && street == Street.FLOP && nOpp == 1 -> if (wet < 0.3) 0.65 else if (wet < 0.6) 0.45 else 0.3
                    initiative && street == Street.TURN && nOpp == 1 -> if (cls == 0) 0.3 else 0.15
                    initiative && street == Street.RIVER && nOpp == 1 -> if (cls == 0) 0.22 else 0.05
                    !initiative && ip && nOpp == 1 && prevAggressor == null -> 0.35  // stab when checked to
                    else -> 0.06
                }
                val medium = if (cls == 2 || cls == 3) 0.6 else 1.0  // medium hands prefer pot control
                pBet = max(pBet, bluff * style.bluff * multi * medium)
            }
            pBet = pBet.coerceIn(0.0, 0.97)
            val sizeChips = (pot * frac).toInt().coerceAtLeast(engine.bigBlind)
            val betTo = if (spr < 1.3 && rel > valueThr) la.maxRaiseTo else sizeChips
            opts += raiseMove(la, betTo) to pBet
            opts += Move.CHECK to (1 - pBet)
            return Policy(normalized(opts), eq, 0.0, null)
        }

        // ---------------- facing a bet
        val (req, icm) = requiredEquity(engine, seat, la, t)
        val potOdds = la.toCall.toDouble() / (pot + la.toCall)
        val realization = if (ip) 1.0 else 0.9
        val implied = if (cls == 1 && street != Street.RIVER && spr > 1.5) 0.05 else 0.0
        val eqEff = eq * realization + implied + style.callBias
        val raiseThr = when (street) { Street.FLOP -> 1.45; Street.TURN -> 1.52; else -> 1.6 }
        var pRaise = 0.0
        if (la.canRaise) {
            pRaise = above(rel, raiseThr, 0.05) * min(1.0, 0.65 * style.aggression)
            if (cls == 4 && street == Street.FLOP && wet < 0.3) pRaise *= 0.6
            val bluffRaise = when {
                cls == 1 && street == Street.FLOP -> 0.12
                cls == 1 && street == Street.TURN -> 0.06
                cls == 0 && street == Street.RIVER -> 0.03
                else -> 0.0
            } * style.bluff * multi
            if (rel < raiseThr) pRaise = max(pRaise, bluffRaise)
        }
        var pCall = above(eqEff - req, 0.0, 0.03)
        // minimum-defence guard vs small bets with real hands (GTO-ish)
        if (potOdds < 0.22 && cls >= 2) pCall = max(pCall, 0.85)
        pCall *= (1 - pRaise)
        val raiseTarget = when (street) {
            Street.RIVER -> (la.currentBet * 2.5).toInt()
            else -> (la.currentBet * 3).toInt()
        }.coerceAtLeast(la.minRaiseTo)
        if (pRaise > 0) opts += raiseMove(la, if (spr < 2.0) la.maxRaiseTo else raiseTarget) to pRaise
        opts += Move.CALL to pCall
        opts += Move.FOLD to (1 - pRaise - pCall).coerceAtLeast(0.0)
        return Policy(normalized(opts), eq, potOdds, icm)
    }
}
