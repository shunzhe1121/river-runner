package com.riverrunner.ui

import com.riverrunner.engine.Action
import com.riverrunner.engine.ActionType
import com.riverrunner.engine.HandClasses
import com.riverrunner.engine.HandEngine
import com.riverrunner.engine.HandEvaluator
import com.riverrunner.engine.LegalActions
import com.riverrunner.engine.Phase
import com.riverrunner.engine.Street
import com.riverrunner.i18n.Strings

enum class ActKind { POST, FOLD, CHECK, CALL, BET, ALLIN }

/** One seat as drawn on the table (live game or replay). */
data class SeatUi(
    val seat: Int,
    val name: String,
    val stack: Int,
    val streetBet: Int,
    val folded: Boolean,
    val allIn: Boolean,
    val isHero: Boolean,
    val isButton: Boolean,
    val isActing: Boolean,
    val hole: IntArray?,
    val faceUp: Boolean,
    val position: String,
    /** last action label shown as a badge, e.g. "加注 2.5 BB" */
    val action: String? = null,
    val actKind: ActKind? = null,
    /** chips won this hand (shown at hand end) */
    val won: Int = 0,
    val busted: Boolean = false,
    /** made hand name shown at showdown */
    val handName: String? = null,
    /** replay only: equity on this street */
    val equity: Double? = null,
    /** replay only: style label of the bot */
    val tag: String? = null,
)

data class TableUi(
    val seats: List<SeatUi>,
    val board: List<Int>,
    val pot: Int,
    val bigBlind: Int,
    /** changes for every new hand (restarts the dealing animation) */
    val handKey: Long,
    /** changes after every action (restarts the "thinking" bar) */
    val actionSeq: Int = 0,
    /** winning five cards; others are dimmed when non-empty */
    val highlight: Set<Int> = emptySet(),
    /** result text shown under the board at the end of a hand */
    val banner: String? = null,
    /** how long the acting bot "thinks" (for the progress bar) */
    val thinkingMs: Long = 0,
)

enum class LogKind { STREET, ACTION, RESULT }

data class LogLine(val kind: LogKind, val text: String, val seat: Int = -1, val cards: List<Int> = emptyList())

data class WinLine(val name: String, val amount: Int, val handName: String?)
data class ShowLine(val name: String, val cards: IntArray, val handName: String, val won: Int, val isHero: Boolean)
data class HandOverUi(val winners: List<WinLine>, val showdown: List<ShowLine>, val heroNet: Int, val byFold: Boolean)

data class PracticeInfo(
    val toCall: Int,
    val pot: Int,
    val potOdds: Double,
    val spr: Double,
    /** made hand / starting hand description */
    val handText: String,
)

/** Everything the table screen draws; built by the view model, also used by screenshot tests. */
data class TableScreenUi(
    val table: TableUi?,
    val handNumber: Int,
    val blindsText: String,
    val levelText: String?,
    val practice: Boolean,
    val legal: LegalActions?,
    val practiceInfo: PracticeInfo?,
    val log: List<LogLine>,
    val handOver: HandOverUi?,
    val actingName: String?,
    val heroInHand: Boolean,
    val fastForward: Boolean,
    val sessionOver: Boolean,
)

/** Static info about a seat that lives outside a single hand. */
data class SeatInfo(val seat: Int, val name: String, val isHero: Boolean, val stack: Int, val busted: Boolean)

object TableMapper {

    fun actKind(a: Action): ActKind = when {
        a.type.isPost -> ActKind.POST
        a.allIn && a.type != ActionType.FOLD && a.type != ActionType.CHECK -> ActKind.ALLIN
        a.type == ActionType.FOLD -> ActKind.FOLD
        a.type == ActionType.CHECK -> ActKind.CHECK
        a.type == ActionType.CALL -> ActKind.CALL
        else -> ActKind.BET
    }

    fun actionLabel(st: Strings, a: Action, fmt: (Int) -> String): String = when (a.type) {
        ActionType.FOLD -> st.fold
        ActionType.CHECK -> st.check
        ActionType.CALL -> if (a.allIn) st.allIn else "${st.call} ${fmt(a.amount)}"
        ActionType.BET -> if (a.allIn) "${st.allIn} ${fmt(a.streetTotal)}" else "${st.bet} ${fmt(a.streetTotal)}"
        ActionType.RAISE -> if (a.allIn) "${st.allIn} ${fmt(a.streetTotal)}" else "${st.raiseTo} ${fmt(a.streetTotal)}"
        ActionType.SMALL_BLIND -> "${st.smallBlindShort} ${fmt(a.amount)}"
        ActionType.BIG_BLIND -> "${st.bigBlindShort} ${fmt(a.amount)}"
        ActionType.ANTE -> "${st.ante} ${fmt(a.amount)}"
    }

    /** Latest non-post action of each seat on the current street. */
    fun lastActions(e: HandEngine): Map<Int, Action> {
        val m = HashMap<Int, Action>()
        for (a in e.actions) {
            if (a.street != e.street) continue
            if (a.type == ActionType.ANTE) continue
            m[a.seat] = a
        }
        return m
    }

    /**
     * Builds the table picture.
     * [stage]: LIVE during play, COLLECT when the hand just ended (bets gathered, showdown cards
     * revealed, winnings not yet paid), FINAL once the winners are paid.
     */
    enum class Stage { LIVE, COLLECT, FINAL }

    fun build(
        st: Strings,
        e: HandEngine,
        seats: List<SeatInfo>,
        handKey: Long,
        actionSeq: Int,
        stage: Stage,
        fmt: (Int) -> String,
        thinkingMs: Long = 0,
    ): TableUi {
        val res = e.result
        val showdown = if (stage != Stage.LIVE) res?.showdown?.toSet() ?: emptySet() else emptySet()
        val inHand = e.players.associateBy { it.seat }
        val last = if (stage == Stage.LIVE) lastActions(e) else emptyMap()
        // all-in with no more betting: everyone still in the hand turns their cards over
        val runoutReveal = stage == Stage.LIVE && e.phase != Phase.BETTING && e.activeCount >= 2 && e.isRunout
        val views = seats.map { info ->
            val p = inHand[info.seat]
            if (p == null) {
                SeatUi(info.seat, info.name, info.stack, 0, true, false, info.isHero, false, false,
                    null, false, "", busted = true)
            } else {
                val won = res?.won?.get(p.seat) ?: 0
                val returned = res?.uncalledReturned?.takeIf { it.first == p.seat }?.second ?: 0
                val score = res?.scores?.get(p.seat)
                val a = last[p.seat]
                SeatUi(
                    seat = p.seat,
                    name = info.name,
                    stack = when (stage) {
                        Stage.LIVE, Stage.FINAL -> p.stack
                        Stage.COLLECT -> p.stack - won + returned
                    },
                    streetBet = if (stage == Stage.LIVE) p.streetBet else 0,
                    folded = p.folded,
                    allIn = p.allIn,
                    isHero = info.isHero,
                    isButton = p.seat == e.buttonSeat,
                    isActing = stage == Stage.LIVE && e.phase == Phase.BETTING && e.toAct == p.seat,
                    hole = p.hole,
                    faceUp = info.isHero || p.seat in showdown || (runoutReveal && !p.folded),
                    position = e.positionName(p.seat),
                    action = a?.let { actionLabel(st, it, fmt) },
                    actKind = a?.let { actKind(it) },
                    won = if (stage == Stage.FINAL) won - returned else 0,
                    handName = if (score != null && p.seat in showdown) HandEvaluator.categoryName(score, st.isZh) else null,
                )
            }
        }
        var highlight = emptySet<Int>()
        var banner: String? = null
        if (stage == Stage.FINAL && res != null) {
            val mainWinner = res.pots.firstOrNull()?.winners?.firstOrNull()
            if (mainWinner != null && res.showdown.isNotEmpty()) {
                val hole = inHand.getValue(mainWinner).hole
                highlight = HandEvaluator.bestFive((e.board + hole.toList()).toIntArray()).toSet()
            }
            banner = resultBanner(st, e, seats, fmt)
        }
        val pot = when (stage) {
            Stage.LIVE -> e.pot - e.players.sumOf { it.streetBet }
            Stage.COLLECT -> e.players.sumOf { it.committed }
            Stage.FINAL -> 0
        }
        return TableUi(views, e.board.toList(), pot, e.bigBlind, handKey, actionSeq, highlight, banner, thinkingMs)
    }

    fun resultBanner(st: Strings, e: HandEngine, seats: List<SeatInfo>, fmt: (Int) -> String): String? {
        val res = e.result ?: return null
        val names = seats.associate { it.seat to it.name }
        val main = res.pots.firstOrNull() ?: return null
        val winners = main.winners.joinToString(" / ") { names[it] ?: "?" }
        val amount = res.pots.filter { it.winners == main.winners }.sumOf { it.amount }
        val per = amount / main.winners.size
        return if (res.showdown.isEmpty()) {
            "${st.winsFor(winners)} ${fmt(per)} · ${st.othersFolded}"
        } else {
            val name = res.scores[main.winners.first()]?.let { HandEvaluator.categoryName(it, st.isZh) } ?: ""
            if (main.winners.size > 1) "$winners ${st.split} ${fmt(amount)} · $name"
            else "${st.winsFor(winners)} ${fmt(per)} · $name"
        }
    }

    fun handOver(st: Strings, e: HandEngine, seats: List<SeatInfo>, heroSeat: Int): HandOverUi {
        val res = e.result!!
        val names = seats.associate { it.seat to it.name }
        val returned = res.uncalledReturned
        fun wonOf(s: Int) = (res.won[s] ?: 0) - (if (returned?.first == s) returned.second else 0)
        val winners = res.pots.flatMap { it.winners }.distinct().map { s ->
            WinLine(names[s] ?: "?", wonOf(s), res.scores[s]?.let { HandEvaluator.categoryName(it, st.isZh) })
        }
        val shows = res.showdown.map { s ->
            val p = e.player(s)
            ShowLine(names[s] ?: "?", p.hole, HandEvaluator.categoryName(res.scores.getValue(s), st.isZh), wonOf(s), s == heroSeat)
        }
        return HandOverUi(winners, shows, res.net[heroSeat] ?: 0, res.showdown.isEmpty())
    }

    /** Practice-mode description of the hero's holding: starting hand class preflop, made hand afterwards. */
    fun handText(st: Strings, hole: IntArray, board: List<Int>): String {
        if (board.size < 3) {
            val cls = HandClasses.className(hole[0], hole[1])
            val top = (HandClasses.percentile(hole[0], hole[1]) * 100).toInt().coerceAtLeast(1)
            return "$cls · ${st.topPercent(top)}"
        }
        val score = HandEvaluator.evaluate((board + hole.toList()).toIntArray())
        val cat = HandEvaluator.category(score)
        val name = HandEvaluator.categoryName(score, st.isZh)
        fun r(x: Int) = com.riverrunner.engine.Cards.RANKS[x].let { if (it == 'T') "10" else it.toString() }
        val boardRanks = board.map { com.riverrunner.engine.Cards.rank(it) }
        val h0 = com.riverrunner.engine.Cards.rank(hole[0]); val h1 = com.riverrunner.engine.Cards.rank(hole[1])
        val boardCat = HandEvaluator.category(HandEvaluator.evaluate(board.toIntArray()))
        val primary = (score ushr 16) and 0xF
        return when {
            cat == HandEvaluator.PAIR && boardCat == HandEvaluator.PAIR -> st.boardPair
            cat == HandEvaluator.PAIR && h0 == h1 && h0 > boardRanks.max() -> "${st.overPair} ${r(h0)}"
            cat == HandEvaluator.PAIR && primary == boardRanks.max() && (h0 == primary || h1 == primary) -> "${st.topPair} ${r(primary)}"
            cat == HandEvaluator.PAIR -> "$name ${r(primary)}"
            cat == HandEvaluator.TWO_PAIR -> "$name ${r(primary)} & ${r((score ushr 12) and 0xF)}"
            cat == HandEvaluator.TRIPS && h0 == h1 -> "${st.set} ${r(primary)}"
            cat == HandEvaluator.TRIPS -> "$name ${r(primary)}"
            cat == HandEvaluator.HIGH_CARD -> "$name ${r(primary)}"
            else -> name
        }
    }

    fun streetLine(st: Strings, s: Street, board: List<Int>): LogLine {
        val cards = when (s) {
            Street.FLOP -> board.take(3)
            Street.TURN, Street.RIVER -> listOf(board.last())
            Street.PREFLOP -> emptyList()
        }
        return LogLine(LogKind.STREET, streetName(st, s), cards = cards)
    }
}

fun streetName(st: Strings, s: Street) = when (s) {
    Street.PREFLOP -> st.preflop; Street.FLOP -> st.flop; Street.TURN -> st.turn; Street.RIVER -> st.river
}

object Format {
    fun amount(chips: Int, bb: Int, inBB: Boolean): String {
        if (!inBB || bb <= 0) return "%,d".format(chips)
        val v = chips.toDouble() / bb
        return if (v >= 100 || v == Math.floor(v)) "%.0f BB".format(v) else "%.1f BB".format(v)
    }

    fun pct(x: Double): String = "%.0f%%".format(x * 100)
}
