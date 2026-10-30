package com.riverrunner.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riverrunner.audio.Sound
import com.riverrunner.audio.SoundPlayer
import com.riverrunner.data.HistoryStore
import com.riverrunner.data.ProfileStore
import com.riverrunner.engine.AchievementDef
import com.riverrunner.engine.Achievements
import com.riverrunner.engine.Facts
import com.riverrunner.engine.Progress
import com.riverrunner.data.Prefs
import com.riverrunner.engine.Action
import com.riverrunner.engine.ActionType
import com.riverrunner.engine.Bot
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.GameSession
import com.riverrunner.engine.HandEngine
import com.riverrunner.engine.HandRecord
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
    data object Saves : Screen()
    data object Achievements : Screen()
    data object Setup : Screen()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)

    // ------------------------------------------------------------------ save slots
    private val profiles = ProfileStore(app.filesDir, prefs) { i -> (if (prefs.chinese) ZH else EN).saveDefaultName(i) }
    var profile by mutableStateOf(profiles.active()); private set
    var profileList by mutableStateOf(profiles.all()); private set
    var history = HistoryStore(profiles.historyFile(profile.id)); private set

    /** Achievement just unlocked, shown as a toast over any screen. */
    var unlockToast by mutableStateOf<AchievementDef?>(null); private set
    private val unlockQueue = ArrayDeque<AchievementDef>()
    private var toastJob: Job? = null

    fun switchProfile(id: String) {
        if (session != null && screen == Screen.Table) return
        profiles.activate(id)
        profile = profiles.active()
        history = HistoryStore(profiles.historyFile(profile.id))
        profileList = profiles.all()
    }

    fun createProfile(name: String): Boolean {
        if (profiles.all().size >= ProfileStore.MAX_SLOTS) return false
        val p = profiles.create(name.trim().ifEmpty { profiles.nextDefaultName() })
        switchProfile(p.id)
        return true
    }

    fun renameProfile(id: String, name: String) {
        if (name.isBlank()) return
        profiles.rename(id, name.trim())
        if (id == profile.id) profile = profiles.active()
        profileList = profiles.all()
    }

    fun deleteProfile(id: String) {
        profiles.delete(id)
        switchProfile(profiles.active().id)
    }

    fun nextProfileName(): String = profiles.nextDefaultName()
    val maxSlots: Int get() = ProfileStore.MAX_SLOTS

    /** Adds counter increments to the active save slot and announces newly unlocked achievements. */
    private fun record(delta: Map<String, Long>) {
        if (delta.isEmpty()) return
        val p = profiles.active()
        val counters = HashMap(p.counters)
        Progress.apply(counters, delta)
        val fresh = Achievements.newlyUnlocked(counters, p.unlocked.keys)
        val unlocked = HashMap(p.unlocked)
        val now = System.currentTimeMillis()
        fresh.forEach { unlocked[it.id] = now }
        val updated = p.copy(counters = counters, unlocked = unlocked)
        profiles.save(updated)
        profile = updated
        profileList = profiles.all()
        if (fresh.isNotEmpty()) {
            unlockQueue.addAll(fresh)
            showNextUnlock()
        }
    }

    private fun showNextUnlock() {
        if (toastJob?.isActive == true) return
        toastJob = viewModelScope.launch {
            while (unlockQueue.isNotEmpty()) {
                unlockToast = unlockQueue.removeFirst()
                sound.play(Sound.UNLOCK, 0.8f, varyPitch = false)
                delay(3200)
                unlockToast = null
                delay(350)
            }
        }
    }

    fun openGuide() {
        record(mapOf(Progress.GUIDE_OPENED to 1L))
        screen = Screen.Guide
    }

    /** Called when the in-game glossary is opened. */
    fun guideOpenedInGame() = record(mapOf(Progress.GUIDE_OPENED to 1L))

    fun openReplay(r: HandRecord) {
        record(mapOf(Progress.REPLAYS to 1L))
        screen = Screen.Replay(r)
    }

    var screen by mutableStateOf<Screen>(Screen.Home)
    var strings by mutableStateOf(if (prefs.chinese) ZH else EN); private set
    var showInBB by mutableStateOf(prefs.showInBB); private set

    fun setChinese(zh: Boolean) { prefs.chinese = zh; strings = if (zh) ZH else EN }
    fun setShowBB(v: Boolean) { prefs.showInBB = v; showInBB = v }
    var deckStyle by mutableStateOf(DeckStyle.fromCode(prefs.deckStyle)); private set
    fun setDeck(d: DeckStyle) { prefs.deckStyle = d.code; deckStyle = d }

    private val sound = SoundPlayer(app).also { it.enabled = prefs.sound }
    var soundOn by mutableStateOf(prefs.sound); private set
    fun setSound(v: Boolean) { prefs.sound = v; soundOn = v; sound.enabled = v }

    /** Plays a game sound (muted while fast-forwarding). */
    private fun sfx(s: Sound, volume: Float = 1f) {
        if (!fastForward) sound.play(s, volume)
    }

    override fun onCleared() {
        sound.release()
        super.onCleared()
    }

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
    private var notice by mutableStateOf<String?>(null)
    private var lastBlinds: Pair<Int, Int>? = null
    private val log = mutableStateListOf<LogLine>()
    var practice by mutableStateOf(prefs.practiceDefault)
    var fastForward by mutableStateOf(false)

    val tableScreen: TableScreenUi
        get() = TableScreenUi(
            table = table, handNumber = handNumber, blindsText = blindsText, levelText = levelText,
            practice = practice, legal = legal, practiceInfo = practiceInfo, log = log.toList(),
            handOver = handOver, actingName = actingName, heroInHand = heroInHand,
            fastForward = fastForward, sessionOver = sessionOver,
            notice = notice, soundOn = soundOn,
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
        notice = null
        lastBlinds = null
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
        when {
            a.type == ActionType.FOLD -> sfx(Sound.FOLD, 0.7f)
            a.type == ActionType.CHECK -> sfx(Sound.CHECK, 0.8f)
            a.allIn -> sfx(Sound.ALLIN)
            else -> sfx(Sound.CHIP, 0.85f)
        }
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
            val lvl = s.currentLevel()
            val prev = lastBlinds
            if (prev != null && lvl.bigBlind > prev.second) {
                notice = strings.blindsUpNotice("%,d/%,d".format(prev.first, prev.second), "%,d/%,d".format(lvl.smallBlind, lvl.bigBlind))
                sound.play(Sound.BLINDS, 0.8f, varyPitch = false)
                val shown = notice
                viewModelScope.launch { delay(4000); if (notice == shown) notice = null }
            }
            lastBlinds = lvl.smallBlind to lvl.bigBlind
            publish(s, e, TableMapper.Stage.LIVE)
            viewModelScope.launch {
                repeat(e.order.size) { sfx(Sound.DEAL, 0.6f); delay(80) }
            }
            pause(1000) // cards being dealt

            while (e.phase != Phase.HAND_DONE) {
                when (e.phase) {
                    Phase.STREET_DONE -> {
                        actingName = null
                        publish(s, e, TableMapper.Stage.LIVE)
                        pause(if (e.isRunout) 900 else 450)
                        val hadBets = e.players.any { it.streetBet > 0 }
                        e.dealNextStreet()
                        if (hadBets) sfx(Sound.COLLECT, 0.6f)
                        val flips = if (e.street == Street.FLOP) 3 else 1
                        viewModelScope.launch {
                            for (i in 0 until flips) { delay(if (i == 0) 170L else 120L); sfx(Sound.FLIP, 0.8f) }
                        }
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
                            sound.play(Sound.TURN, 0.55f, varyPitch = false)
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
            val store = history
            withContext(Dispatchers.IO) { store.add(rec) }
            record(withContext(Dispatchers.Default) { runCatching { Facts.forHand(rec) }.getOrDefault(emptyMap()) })
            val bb = e.bigBlind
            val betsOut = e.players.any { it.streetBet > 0 }
            val wasFast = fastForward
            fastForward = false
            table = TableMapper.build(strings, e, infosBefore, handKey, actionSeq, TableMapper.Stage.COLLECT, { fmt(it, bb) })
            if (betsOut) sfx(Sound.COLLECT, 0.6f)
            val showdown = e.result?.showdown?.isNotEmpty() == true
            if (showdown) sfx(Sound.FLIP, 0.8f)
            delay(if (wasFast) 300L else if (showdown) 1200L else 600L)
            table = TableMapper.build(strings, e, infosBefore, handKey, actionSeq, TableMapper.Stage.FINAL, { fmt(it, bb) })
            if ((e.result?.net?.get(GameSession.HERO_SEAT) ?: 0) > 0) sfx(Sound.WIN, 0.8f) else sfx(Sound.COLLECT, 0.7f)
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
        record(Facts.forSession(summary, s.config))
        table = null
        handOver = null
        screen = Screen.Result(summary)
    }

    // ------------------------------------------------------------------ history helpers
    fun toggleFavorite(id: Long): HandRecord? {
        val r = history.toggleFavorite(id)
        if (r?.favorite == true) record(mapOf(Progress.STARS to 1L))
        return r
    }

    fun actionLabel(a: Action, bb: Int): String = TableMapper.actionLabel(strings, a) { fmt(it, bb) }
}
