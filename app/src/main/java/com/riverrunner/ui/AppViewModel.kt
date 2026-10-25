package com.riverrunner.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riverrunner.data.HistoryStore
import com.riverrunner.data.Prefs
import com.riverrunner.engine.Action
import com.riverrunner.engine.ActionType
import com.riverrunner.engine.Bot
import com.riverrunner.engine.BlindLevel
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.GameSession
import com.riverrunner.engine.HandEngine
import com.riverrunner.engine.HandEvaluator
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.Icm
import com.riverrunner.engine.LegalActions
import com.riverrunner.engine.Move
import com.riverrunner.engine.Phase
import com.riverrunner.engine.SessionConfig
import com.riverrunner.engine.SessionSummary
import com.riverrunner.engine.Street
import com.riverrunner.i18n.EN
import com.riverrunner.i18n.Strings
import com.riverrunner.i18n.ZH
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed class Screen {
    data object Home : Screen()
    data object Table : Screen()
    data class Result(val summary: SessionSummary) : Screen()
    data object History : Screen()
    data class Replay(val record: HandRecord) : Screen()
    data object Stats : Screen()
    data object Settings : Screen()
}

/** One seat as drawn on the table (live game or replay). */
data class SeatView(
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
    val bubble: String?,
    val won: Int,
    val busted: Boolean,
    val handName: String?,
    val equity: Double? = null,
)

data class TableView(
    val seats: List<SeatView>,
    val board: List<Int>,
    val pot: Int,
    val bigBlind: Int,
)

data class PracticeInfo(val toCall: Int, val pot: Int, val potOdds: Double, val spr: Double)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val history = HistoryStore(app)

    var screen by mutableStateOf<Screen>(Screen.Home)
    var strings by mutableStateOf(if (prefs.chinese) ZH else EN); private set
    var showInBB by mutableStateOf(prefs.showInBB); private set

    fun setChinese(zh: Boolean) { prefs.chinese = zh; strings = if (zh) ZH else EN }
    fun setShowBB(v: Boolean) { prefs.showInBB = v; showInBB = v }

    // ------------------------------------------------------------------ live game state
    var table by mutableStateOf<TableView?>(null); private set
    var heroLegal by mutableStateOf<LegalActions?>(null); private set
    var practice by mutableStateOf(prefs.practiceDefault)
    var practiceInfo by mutableStateOf<PracticeInfo?>(null); private set
    var level by mutableStateOf<BlindLevel?>(null); private set
    var handsToNextLevel by mutableStateOf<Int?>(null); private set
    var handNumber by mutableStateOf(0); private set
    var mode by mutableStateOf(GameMode.CASH); private set
    var waitingNextHand by mutableStateOf(false); private set
    var resultBanner by mutableStateOf<String?>(null); private set
    var fastForward by mutableStateOf(false)
    var heroFoldedThisHand by mutableStateOf(false); private set

    private var session: GameSession? = null
    private var lastConfig: SessionConfig? = null
    private var gameJob: Job? = null
    private var heroMove: CompletableDeferred<Move>? = null
    private var nextHandSignal: CompletableDeferred<Unit>? = null
    private var quitRequested = false
    private val bubbles = HashMap<Int, String>()

    private val botDelay: Long get() = when (prefs.speed) { 0 -> 1300L; 2 -> 380L; else -> 750L }

    fun startGame(config: SessionConfig) {
        gameJob?.cancel()
        lastConfig = config
        val s = GameSession(config)
        session = s
        mode = config.mode
        quitRequested = false
        practice = prefs.practiceDefault
        screen = Screen.Table
        gameJob = viewModelScope.launch { runGame(s) }
    }

    fun playAgain() {
        val c = lastConfig ?: return
        startGame(c.copy(seed = System.nanoTime()))
    }

    fun heroAct(move: Move) {
        heroMove?.complete(move)
    }

    fun continueToNextHand() {
        nextHandSignal?.complete(Unit)
    }

    fun requestQuit() {
        quitRequested = true
        fastForward = true
        heroMove?.complete(Move.FOLD)
        nextHandSignal?.complete(Unit)
    }

    fun abandonToHome() {
        gameJob?.cancel()
        session = null
        table = null
        screen = Screen.Home
    }

    private suspend fun pause(ms: Long) {
        if (!fastForward) delay(ms)
    }

    private suspend fun runGame(s: GameSession) {
        while (!s.isOver && !quitRequested) {
            val e = s.startHand()
            handNumber = s.handNumber + 1
            level = s.currentLevel()
            handsToNextLevel = if (s.config.mode == GameMode.TOURNAMENT)
                s.config.handsPerLevel - (s.handNumber % s.config.handsPerLevel) else null
            bubbles.clear()
            resultBanner = null
            heroFoldedThisHand = false
            fastForward = false
            publish(s, e)
            pause(450)
            while (e.phase != Phase.HAND_DONE) {
                when (e.phase) {
                    Phase.STREET_DONE -> {
                        pause(if (e.isRunout) 900 else 500)
                        e.dealNextStreet()
                        bubbles.clear()
                        publish(s, e)
                    }
                    Phase.BETTING -> {
                        val seat = e.toAct
                        if (seat == GameSession.HERO_SEAT) {
                            if (quitRequested) {
                                record(e, e.apply(Move.FOLD)); continue
                            }
                            fastForward = false
                            val la = e.legalActions()
                            heroLegal = la
                            val me = e.player(seat)
                            practiceInfo = PracticeInfo(
                                la.toCall, la.pot,
                                if (la.toCall > 0) la.toCall.toDouble() / (la.pot + la.toCall) else 0.0,
                                me.stack.toDouble() / maxOf(1, la.pot),
                            )
                            publish(s, e)
                            val d = CompletableDeferred<Move>()
                            heroMove = d
                            val move = d.await()
                            heroMove = null
                            heroLegal = null
                            practiceInfo = null
                            record(e, e.apply(move))
                            if (move.type == com.riverrunner.engine.MoveType.FOLD && e.phase != Phase.HAND_DONE) heroFoldedThisHand = true
                        } else {
                            publish(s, e)
                            pause(botDelay)
                            val style = s.players[seat].style
                            val ctx = s.tournamentContext()
                            val move = withContext(Dispatchers.Default) {
                                Bot.decide(e, seat, style, s.random, ctx).sample(s.random)
                            }
                            record(e, e.apply(move))
                        }
                        publish(s, e)
                    }
                    Phase.HAND_DONE -> {}
                }
            }
            // ---- hand finished
            val rec = s.completeHand(e)
            withContext(Dispatchers.IO) { history.add(rec) }
            val res = e.result!!
            val showdown = res.showdown.toSet()
            val names = e.players.associate { it.seat to if (it.seat == GameSession.HERO_SEAT) strings.you else it.name }
            val winners = res.pots.flatMap { it.winners }.distinct()
            val main = res.pots.firstOrNull()
            resultBanner = buildString {
                append(winners.joinToString(" / ") { names.getValue(it) })
                append(' ')
                append(if ((main?.winners?.size ?: 1) > 1) strings.split else strings.wins)
                if (showdown.isNotEmpty() && main != null) {
                    val sc = res.scores[main.winners.first()]
                    if (sc != null) append(" · ").append(HandEvaluator.categoryName(sc, strings.isZh))
                }
            }
            publish(s, e, final = true)
            fastForward = false
            if (!quitRequested) {
                val sig = CompletableDeferred<Unit>()
                nextHandSignal = sig
                waitingNextHand = true
                withTimeoutOrNull(if (showdown.isNotEmpty()) 4500L else 2200L) { sig.await() }
                waitingNextHand = false
                nextHandSignal = null
            }
        }
        val summary = s.finish(early = quitRequested && !s.isOverNaturally())
        prefs.recordSession(summary, Icm.payoutsFor(s.config.tableSize).size)
        table = null
        screen = Screen.Result(summary)
    }

    private fun GameSession.isOverNaturally(): Boolean = alivePlayers.size <= 1 || !hero.alive

    private fun record(e: HandEngine, a: Action) {
        bubbles[a.seat] = actionLabel(a, e.bigBlind)
    }

    fun actionLabel(a: Action, bb: Int): String {
        val st = strings
        return when (a.type) {
            ActionType.FOLD -> st.fold
            ActionType.CHECK -> st.check
            ActionType.CALL -> if (a.allIn) st.allIn else "${st.call} ${fmt(a.streetTotal, bb)}"
            ActionType.BET -> if (a.allIn) st.allIn else "${st.bet} ${fmt(a.streetTotal, bb)}"
            ActionType.RAISE -> if (a.allIn) st.allIn else "${st.raise} ${fmt(a.streetTotal, bb)}"
            ActionType.SMALL_BLIND -> "SB"
            ActionType.BIG_BLIND -> "BB"
            ActionType.ANTE -> st.ante
        }
    }

    /** Formats chips either as big blinds or as raw chips. */
    fun fmt(chips: Int, bb: Int): String = Format.amount(chips, bb, showInBB)

    private fun publish(s: GameSession, e: HandEngine, final: Boolean = false) {
        val res = e.result
        val showdown = res?.showdown?.toSet() ?: emptySet()
        val inHand = e.players.associateBy { it.seat }
        val seats = s.players.map { sp ->
            val p = inHand[sp.seat]
            if (p == null) {
                SeatView(sp.seat, displayName(sp.seat, sp.name), sp.stack, 0, true, false, sp.isHuman,
                    false, false, null, false, "", null, 0, busted = true, handName = null)
            } else {
                val faceUp = sp.isHuman || (final && sp.seat in showdown)
                val score = res?.scores?.get(sp.seat)
                SeatView(
                    seat = sp.seat,
                    name = displayName(sp.seat, sp.name),
                    stack = p.stack,
                    streetBet = if (final) 0 else p.streetBet,
                    folded = p.folded,
                    allIn = p.allIn,
                    isHero = sp.isHuman,
                    isButton = sp.seat == e.buttonSeat,
                    isActing = !final && e.phase == Phase.BETTING && e.toAct == sp.seat,
                    hole = p.hole,
                    faceUp = faceUp,
                    position = e.positionName(sp.seat),
                    bubble = if (final) null else bubbles[sp.seat],
                    won = if (final) wonPots(res, sp.seat) else 0,
                    busted = false,
                    handName = if (final && score != null && sp.seat in showdown) HandEvaluator.categoryName(score, strings.isZh) else null,
                )
            }
        }
        table = TableView(seats, e.board.toList(), if (final) 0 else e.pot, e.bigBlind)
    }

    private fun wonPots(res: com.riverrunner.engine.HandResult?, seat: Int): Int {
        if (res == null) return 0
        val ret = res.uncalledReturned
        val back = if (ret != null && ret.first == seat) ret.second else 0
        return (res.won[seat] ?: 0) - back
    }

    private fun displayName(seat: Int, name: String) = if (seat == GameSession.HERO_SEAT) strings.you else name

    // ------------------------------------------------------------------ history helpers
    fun toggleFavorite(id: Long): HandRecord? = history.toggleFavorite(id)
}

object Format {
    fun amount(chips: Int, bb: Int, inBB: Boolean): String {
        if (!inBB || bb <= 0) return "%,d".format(chips)
        val v = chips.toDouble() / bb
        return if (v >= 100 || v == Math.floor(v)) "%.0f BB".format(v) else "%.1f BB".format(v)
    }

    fun pct(x: Double): String = "%.0f%%".format(x * 100)
}

fun streetName(st: Strings, s: Street) = when (s) {
    Street.PREFLOP -> st.preflop; Street.FLOP -> st.flop; Street.TURN -> st.turn; Street.RIVER -> st.river
}
