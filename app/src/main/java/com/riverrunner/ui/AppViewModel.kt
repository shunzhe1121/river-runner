package com.riverrunner.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riverrunner.data.HistoryStore
import com.riverrunner.data.Prefs
import com.riverrunner.engine.Action
import com.riverrunner.engine.ActionType
import com.riverrunner.engine.Bot
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.GameSession
import com.riverrunner.engine.HandEngine
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.Icm
import com.riverrunner.engine.LegalActions
import com.riverrunner.engine.Move
import com.riverrunner.engine.Phase
import com.riverrunner.engine.SessionConfig
import com.riverrunner.engine.SessionSummary
import com.riverrunner.engine.Street
import com.riverrunner.i18n.EN
import com.riverrunner.i18n.ZH
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    data object Home : Screen()
    data object Table : Screen()
    data class Result(val summary: SessionSummary) : Screen()
    data object History : Screen()
    data class Replay(val record: HandRecord) : Screen()
    data object Stats : Screen()
    data object Settings : Screen()
    data object Guide : Screen()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val history = HistoryStore(app)

    var screen by mutableStateOf<Screen>(Screen.Home)
    var strings by mutableStateOf(if (prefs.chinese) ZH else EN); private set
    var showInBB by mutableStateOf(prefs.showInBB); private set

    fun setChinese(zh: Boolean) { prefs.chinese = zh; strings = if (zh) ZH else EN }
    fun setShowBB(v: Boolean) { prefs.showInBB = v; showInBB = v }

    // ------------------------------------------------------------------ live game state
    private var table by mutableStateOf<TableUi?>(null)
    private var legal by mutableStateOf<LegalActions?>(null)
    private var practiceInfo by mutableStateOf<PracticeInfo?>(null)
    private var handNumber by mutableStateOf(0)
    private var blindsText by mutableStateOf("")
    private var levelText by mutableStateOf<String?>(null)
    private var handOver by mutableStateOf<HandOverUi?>(null)
    private var actingName by mutableStateOf<String?>(null)
    private var heroInHand by mutableStateOf(true)
    private var sessionOver by mutableStateOf(false)
    private val log = mutableStateListOf<LogLine>()
    var practice by mutableStateOf(prefs.practiceDefault)
    var fastForward by mutableStateOf(false)

    val tableScreen: TableScreenUi
        get() = TableScreenUi(
            table = table, handNumber = handNumber, blindsText = blindsText, levelText = levelText,
            practice = practice, legal = legal, practiceInfo = practiceInfo, log = log.toList(),
            handOver = handOver, actingName = actingName, heroInHand = heroInHand,
            fastForward = fastForward, sessionOver = sessionOver,
        )

    private var session: GameSession? = null
    private var lastConfig: SessionConfig? = null
    private var gameJob: Job? = null
    private var heroMove: CompletableDeferred<Move>? = null
    private var nextHandSignal: CompletableDeferred<Unit>? = null
    private var quitRequested = false
    private var handKey = 0L
    private var actionSeq = 0

    private val botDelay: Long get() = when (prefs.speed) { 0 -> 1500L; 2 -> 450L; else -> 900L }

    fun startGame(config: SessionConfig) {
        gameJob?.cancel()
        lastConfig = config
        val s = GameSession(config)
        session = s
        quitRequested = false
        practice = prefs.practiceDefault
        sessionOver = false
        table = null
        screen = Screen.Table
        gameJob = viewModelScope.launch { runGame(s) }
    }

    fun playAgain() {
        val c = lastConfig ?: return
        startGame(c.copy(seed = System.nanoTime()))
    }

    fun heroAct(move: Move) { heroMove?.complete(move) }

    fun continueToNextHand() { nextHandSignal?.complete(Unit) }

    fun requestQuit() {
        quitRequested = true
        fastForward = true
        heroMove?.complete(Move.FOLD)
        nextHandSignal?.complete(Unit)
    }

    private suspend fun pause(ms: Long) {
        if (!fastForward && ms > 0) delay(ms)
    }

    /** Formats chips either as big blinds or as raw chips. */
    fun fmt(chips: Int, bb: Int): String = Format.amount(chips, bb, showInBB)

    private fun seatInfos(s: GameSession): List<SeatInfo> = s.players.map {
        SeatInfo(it.seat, if (it.isHuman) strings.you else it.name, it.isHuman, it.stack, !it.alive && it.place > 0)
    }

    private fun publish(s: GameSession, e: HandEngine, stage: TableMapper.Stage, thinking: Long = 0) {
        val bb = e.bigBlind
        table = TableMapper.build(strings, e, seatInfos(s), handKey, actionSeq, stage, { fmt(it, bb) }, thinking)
    }

    private fun logAction(e: HandEngine, s: GameSession, a: Action) {
        if (a.type == ActionType.ANTE) return
        val name = if (a.seat == GameSession.HERO_SEAT) strings.you else s.players[a.seat].name
        val bb = e.bigBlind
        log += LogLine(LogKind.ACTION, "$name (${e.positionName(a.seat)}) ${TableMapper.actionLabel(strings, a) { fmt(it, bb) }}", a.seat)
    }

    private fun apply(e: HandEngine, s: GameSession, move: Move) {
        val a = e.apply(move)
        actionSeq++
        logAction(e, s, a)
    }

    private fun updateBlinds(s: GameSession) {
        val lvl = s.currentLevel()
        blindsText = strings.blindsText(lvl.smallBlind, lvl.bigBlind, lvl.ante)
        levelText = if (s.config.mode == GameMode.TOURNAMENT) {
            val left = s.config.handsPerLevel - (s.handNumber % s.config.handsPerLevel)
            strings.levelText(lvl.level, left)
        } else null
    }

    private suspend fun runGame(s: GameSession) {
        while (!s.isOver && !quitRequested) {
            val e = s.startHand()
            handKey = System.nanoTime()
            actionSeq = 0
            handNumber = s.handNumber + 1
            updateBlinds(s)
            log.clear()
            handOver = null
            heroInHand = true
            fastForward = false
            legal = null
            practiceInfo = null
            actingName = null
            log += LogLine(LogKind.STREET, strings.preflop)
            for (a in e.actions) logAction(e, s, a)
            publish(s, e, TableMapper.Stage.LIVE)
            pause(1000) // cards being dealt

            while (e.phase != Phase.HAND_DONE) {
                when (e.phase) {
                    Phase.STREET_DONE -> {
                        actingName = null
                        publish(s, e, TableMapper.Stage.LIVE)
                        pause(if (e.isRunout) 900 else 450)
                        e.dealNextStreet()
                        actionSeq++
                        log += TableMapper.streetLine(strings, e.street, e.board)
                        publish(s, e, TableMapper.Stage.LIVE)
                        pause(if (e.street == Street.FLOP) 800 else 550)
                    }
                    Phase.BETTING -> {
                        val seat = e.toAct
                        if (seat == GameSession.HERO_SEAT) {
                            if (quitRequested) { apply(e, s, Move.FOLD); continue }
                            fastForward = false
                            actingName = null
                            val la = e.legalActions()
                            val me = e.player(seat)
                            practiceInfo = PracticeInfo(
                                la.toCall, la.pot,
                                if (la.toCall > 0) la.toCall.toDouble() / (la.pot + la.toCall) else 0.0,
                                me.stack.toDouble() / maxOf(1, la.pot),
                                TableMapper.handText(strings, me.hole, e.board),
                            )
                            legal = la
                            publish(s, e, TableMapper.Stage.LIVE)
                            val d = CompletableDeferred<Move>()
                            heroMove = d
                            val move = d.await()
                            heroMove = null
                            legal = null
                            practiceInfo = null
                            apply(e, s, move)
                            if (e.player(seat).folded) heroInHand = false
                        } else {
                            val name = s.players[seat].name
                            actingName = name
                            val think = if (fastForward) 0L else botDelay
                            publish(s, e, TableMapper.Stage.LIVE, think)
                            val started = System.currentTimeMillis()
                            val style = s.players[seat].style
                            val ctx = s.tournamentContext()
                            val move = withContext(Dispatchers.Default) {
                                Bot.decide(e, seat, style, s.random, ctx).sample(s.random)
                            }
                            pause(think - (System.currentTimeMillis() - started))
                            apply(e, s, move)
                        }
                        actingName = null
                        publish(s, e, TableMapper.Stage.LIVE)
                        pause(250)
                    }
                    Phase.HAND_DONE -> {}
                }
            }

            // ---- hand finished: gather the bets, reveal, then pay the winners
            legal = null
            actingName = null
            val infosBefore = seatInfos(s)
            val rec = s.completeHand(e)
            withContext(Dispatchers.IO) { history.add(rec) }
            val bb = e.bigBlind
            table = TableMapper.build(strings, e, infosBefore, handKey, actionSeq, TableMapper.Stage.COLLECT, { fmt(it, bb) })
            val showdown = e.result?.showdown?.isNotEmpty() == true
            delay(if (showdown) 1200 else 600)
            table = TableMapper.build(strings, e, infosBefore, handKey, actionSeq, TableMapper.Stage.FINAL, { fmt(it, bb) })
            TableMapper.resultBanner(strings, e, infosBefore) { fmt(it, bb) }?.let { log += LogLine(LogKind.RESULT, it) }
            handOver = TableMapper.handOver(strings, e, infosBefore, GameSession.HERO_SEAT)
            sessionOver = s.isOver
            fastForward = false
            if (!quitRequested) {
                val sig = CompletableDeferred<Unit>()
                nextHandSignal = sig
                sig.await()
                nextHandSignal = null
            }
        }
        val summary = s.finish(early = quitRequested && !(s.alivePlayers.size <= 1 || !s.hero.alive))
        prefs.recordSession(summary, Icm.payoutsFor(s.config.tableSize).size)
        table = null
        handOver = null
        screen = Screen.Result(summary)
    }

    // ------------------------------------------------------------------ history helpers
    fun toggleFavorite(id: Long): HandRecord? = history.toggleFavorite(id)

    fun actionLabel(a: Action, bb: Int): String = TableMapper.actionLabel(strings, a) { fmt(it, bb) }
}
