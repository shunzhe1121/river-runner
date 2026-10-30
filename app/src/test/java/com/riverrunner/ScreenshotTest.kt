package com.riverrunner

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.riverrunner.engine.Action
import com.riverrunner.engine.ActionType
import com.riverrunner.engine.BotStyle
import com.riverrunner.engine.Cards
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.HandEngine
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.HandSetup
import com.riverrunner.engine.Move
import com.riverrunner.engine.Phase
import com.riverrunner.engine.PresetCardSource
import com.riverrunner.engine.SeatSetup
import com.riverrunner.engine.Street
import com.riverrunner.i18n.EN
import com.riverrunner.i18n.Strings
import com.riverrunner.i18n.ZH
import com.riverrunner.ui.AppViewModel
import com.riverrunner.ui.Format
import com.riverrunner.ui.GuideContent
import com.riverrunner.ui.GuideTab
import com.riverrunner.ui.HomeScreen
import com.riverrunner.ui.LogKind
import com.riverrunner.ui.LogLine
import com.riverrunner.ui.PracticeInfo
import com.riverrunner.ui.ReplayContent
import com.riverrunner.ui.RiverTheme
import com.riverrunner.ui.SeatInfo
import com.riverrunner.ui.TableContent
import com.riverrunner.ui.TableMapper
import com.riverrunner.ui.TableScreenUi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val NAMES = listOf("You", "Echo", "Flint", "Ghost", "Jade", "Blaze", "Maverick", "Nova")
private const val BB = 100

/** Small scripted-hand helper. */
private class Scenario(val st: Strings, n: Int, button: Int, holes: List<String>, board: String, stacksBB: List<Int>? = null, ante: Int = 0) {
    val e = HandEngine(
        HandSetup(BB / 2, BB, ante, button, (0 until n).map { SeatSetup(it, name(it), (stacksBB?.get(it) ?: 100) * BB) }),
        PresetCardSource(holes.mapIndexed { i, h -> i to Cards.parseMany(h) }.toMap(), Cards.parseMany(board)),
    )
    val log = ArrayList<LogLine>()
    var seq = 0
    val fmt: (Int) -> String = { Format.amount(it, BB, true) }

    init {
        log += LogLine(LogKind.STREET, st.preflop)
        e.actions.filter { it.type != ActionType.ANTE }.forEach { logAction(it) }
    }

    fun name(i: Int) = if (i == 0) st.you else NAMES[i]

    fun logAction(a: Action) {
        log += LogLine(LogKind.ACTION, "${name(a.seat)} (${e.positionName(a.seat)}) ${TableMapper.actionLabel(st, a, fmt)}", a.seat)
    }

    /** Plays [moves] in order, dealing streets as needed. */
    fun play(vararg moves: Move) {
        for (m in moves) {
            while (e.phase == Phase.STREET_DONE) deal()
            logAction(e.apply(m)); seq++
        }
    }

    fun deal() {
        e.dealNextStreet(); seq++
        log += TableMapper.streetLine(st, e.street, e.board)
    }

    fun seats() = (0 until e.order.size).map { SeatInfo(it, name(it), it == 0, e.player(it).stack, false) }

    fun ui(stage: TableMapper.Stage = TableMapper.Stage.LIVE, practice: Boolean = false, thinking: Long = 900, heroInHand: Boolean = true): TableScreenUi {
        val heroTurn = stage == TableMapper.Stage.LIVE && e.phase == Phase.BETTING && e.toAct == 0
        val la = if (heroTurn) e.legalActions() else null
        val me = e.player(0)
        val info = la?.let {
            PracticeInfo(it.toCall, it.pot, if (it.toCall > 0) it.toCall.toDouble() / (it.pot + it.toCall) else 0.0,
                me.stack.toDouble() / it.pot, TableMapper.handText(st, me.hole, e.board))
        }
        val over = if (stage == TableMapper.Stage.FINAL) TableMapper.handOver(st, e, seats(), 0) else null
        val logOut = if (stage == TableMapper.Stage.FINAL) log + LogLine(LogKind.RESULT, TableMapper.resultBanner(st, e, seats(), fmt) ?: "") else log
        return TableScreenUi(
            table = TableMapper.build(st, e, seats(), 1L, seq, stage, fmt, if (heroTurn) 0 else thinking),
            handNumber = 12,
            blindsText = st.blindsText(BB / 2, BB, e.setup.ante),
            levelText = null,
            practice = practice,
            legal = la,
            practiceInfo = info,
            log = logOut,
            handOver = over,
            actingName = if (!heroTurn && e.phase == Phase.BETTING) name(e.toAct) else null,
            heroInHand = heroInHand,
            fastForward = false,
            sessionOver = false,
        )
    }

    fun finish(): Scenario {
        while (e.phase != Phase.HAND_DONE) {
            if (e.phase == Phase.STREET_DONE) deal() else logAction(e.apply(if (e.legalActions().canCheck) Move.CHECK else Move.CALL))
        }
        return this
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w780dp-h360dp-land-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, strings: Strings = ZH, content: @Composable () -> Unit) {
        compose.setContent { RiverTheme(strings) { content() } }
        repeat(12) {
            compose.mainClock.advanceTimeBy(300)
            Thread.sleep(60)
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun table(name: String, ui: TableScreenUi, strings: Strings = ZH) = shot(name, strings) {
        TableContent(ui, { Format.amount(it, BB, true) }, {}, {}, {}, {}, {}, {}, animate = false)
    }

    @Test fun t01_six_hero_facing_open() {
        // button 5: SB = you (0), BB = 1, UTG = 2, HJ = 3, CO = 4
        val s = Scenario(ZH, 6, 5, listOf("Qh8s", "9c9d", "Kd4c", "7s2h", "AhJc", "Tc6d"), "Ks7d2c5hAs")
        s.play(Move.FOLD, Move.FOLD, Move.raiseTo(250), Move.FOLD)
        table("01_six_hero_facing_open", s.ui())
    }

    @Test fun t02_six_hero_practice() {
        val s = Scenario(ZH, 6, 5, listOf("Qh8s", "9c9d", "Kd4c", "7s2h", "AhJc", "Tc6d"), "Ks7d2c5hAs")
        s.play(Move.FOLD, Move.FOLD, Move.raiseTo(250), Move.FOLD)
        table("02_six_hero_practice", s.ui(practice = true))
    }

    @Test fun t03_eight_flop_bot_turn() {
        // button 2: SB 3, BB 4, UTG 5, UTG+1 6, HJ 7, CO 0 (you), BTN 1? (order from left of button)
        val s = Scenario(ZH, 8, 2, listOf("AsQs", "Kh5c", "9d9h", "Jc8c", "7h6h", "2c3d", "Td4s", "KcJd"), "Qd8s3hTs2s")
        // preflop: UTG fold, UTG+1 fold, HJ raise, CO(you) call, BTN fold, SB call, BB fold
        s.play(Move.FOLD, Move.FOLD, Move.raiseTo(250), Move.CALL, Move.FOLD, Move.FOLD, Move.CALL, Move.FOLD)
        // flop: SB check, HJ bets, ... now you act? -> let SB check, HJ bet, you call -> SB to act (bot)
        s.deal()
        s.play(Move.CHECK, Move.raiseTo(400), Move.CALL)
        table("03_eight_flop_bot_turn", s.ui())
    }

    @Test fun t04_eight_flop_hero_turn_practice() {
        val s = Scenario(ZH, 8, 2, listOf("AsQs", "Kh5c", "9d9h", "Jc8c", "7h6h", "2c3d", "Td4s", "KcJd"), "Qd8s3hTs2s")
        s.play(Move.FOLD, Move.FOLD, Move.raiseTo(250), Move.CALL, Move.FOLD, Move.FOLD, Move.CALL, Move.FOLD)
        s.deal()
        s.play(Move.CHECK, Move.raiseTo(400))
        table("04_eight_flop_hero_turn_practice", s.ui(practice = true))
    }

    @Test fun t05_six_showdown_result() {
        val s = Scenario(ZH, 6, 5, listOf("QhQs", "9c9d", "Kd4c", "7s2h", "AhJc", "Tc6d"), "Ks7d2cQd3h")
        s.play(Move.FOLD, Move.FOLD, Move.raiseTo(250), Move.FOLD, Move.raiseTo(900), Move.FOLD, Move.CALL)
        s.finish()
        table("05_six_showdown_result", s.ui(TableMapper.Stage.FINAL))
    }

    @Test fun t06_heads_up_turn() {
        val s = Scenario(ZH, 2, 0, listOf("AhTd", "Kc9c"), "Ts9h4c2dKs")
        s.play(Move.raiseTo(250), Move.CALL)
        s.deal()
        s.play(Move.raiseTo(200), Move.CALL)
        s.deal()
        s.play(Move.raiseTo(600))
        table("06_heads_up_turn", s.ui(practice = true))
    }

    @Test fun t07_five_hero_folded_waiting() {
        val s = Scenario(ZH, 5, 4, listOf("7c2d", "AsKs", "QcQd", "9h8h", "Jd Td".replace(" ", "")), "Ah5s6cKd2h")
        // button 4: SB 0 (you), BB 1, UTG 2, HJ 3? order [0,1,2,3,4]
        s.play(Move.CALL, Move.raiseTo(300), Move.CALL, Move.FOLD, Move.CALL)
        s.e.legalActions()
        s.play(Move.FOLD) // hero folds to the raise
        s.deal()
        s.play(Move.raiseTo(450))
        table("07_five_hero_folded_waiting", s.ui(heroInHand = false))
    }

    @Test fun t08_seven_allin_runout() {
        val s = Scenario(ZH, 7, 6, listOf("JhJs", "AdKd", "8c8d", "5h4h", "Qc9s", "Th7c", "3s3d"), "Kh8h2sTs4c", stacksBB = listOf(100, 40, 100, 100, 100, 100, 100))
        // button 6: SB 0 (you), BB 1, UTG 2 ... ; you shove from SB, BB calls
        s.play(Move.FOLD, Move.FOLD, Move.FOLD, Move.FOLD, Move.FOLD, Move.raiseTo(10000), Move.CALL)
        while (s.e.phase == Phase.STREET_DONE && s.e.street != Street.TURN) s.deal()
        table("08_seven_allin_runout", s.ui())
    }

    @Test @Config(qualifiers = "w680dp-h320dp-land-xxhdpi")
    fun t09_small_screen_eight_practice() {
        val s = Scenario(ZH, 8, 2, listOf("AsQs", "Kh5c", "9d9h", "Jc8c", "7h6h", "2c3d", "Td4s", "KcJd"), "Qd8s3hTs2s")
        s.play(Move.FOLD, Move.FOLD, Move.raiseTo(250))
        table("09_small_screen_eight_practice", s.ui(practice = true))
    }

    @Test fun t10_english_six_showdown() {
        val s = Scenario(EN, 6, 5, listOf("QhQs", "9c9d", "Kd4c", "7s2h", "AhJc", "Tc6d"), "Ks7d2cQd3h")
        s.play(Move.FOLD, Move.FOLD, Move.raiseTo(250), Move.FOLD, Move.raiseTo(900), Move.FOLD, Move.CALL)
        s.finish()
        table("10_english_six_showdown", s.ui(TableMapper.Stage.FINAL), EN)
    }

    @Test fun t16_blinds_up_notice() {
        val s = Scenario(ZH, 6, 5, listOf("Qh8s", "9c9d", "Kd4c", "7s2h", "AhJc", "Tc6d"), "Ks7d2c5hAs")
        table("16_blinds_up_notice", s.ui().copy(notice = ZH.blindsUpNotice("100/200", "150/300")))
    }

    @Test fun t11_guide_rankings() = shot("11_guide_rankings") { GuideContent(onClose = {}, startTab = GuideTab.RANKINGS) }

    @Test fun t12_guide_positions() = shot("12_guide_positions") { GuideContent(onClose = {}, startTab = GuideTab.POSITIONS) }

    @Test fun t13_guide_glossary() = shot("13_guide_glossary") { GuideContent(onClose = {}, startTab = GuideTab.GLOSSARY) }

    @Test fun t14_replay() {
        val s = Scenario(ZH, 6, 5, listOf("QhQs", "9c9d", "Kd4c", "7s2h", "AhJc", "Tc6d"), "Ks7d2cQd3h")
        s.play(Move.FOLD, Move.FOLD, Move.raiseTo(250), Move.FOLD, Move.raiseTo(900), Move.FOLD, Move.CALL)
        s.finish()
        val rec = HandRecord.from(s.e, GameMode.CASH, 12, 0, 6, (0 until 6).associateWith { if (it == 3) BotStyle.STATION else BotStyle.GTO })
        shot("14_replay") {
            ReplayContent(rec, { Format.amount(it, BB, true) }, {}, {}, startIndex = 9)
        }
    }

    private fun sampleProfile(name: String, emblem: Int, hands: Long, cash: Long, wins: Long, unlocked: List<String>) =
        com.riverrunner.data.Profile(
            "id$name", name, 1_790_000_000_000L, emblem,
            mapOf(
                com.riverrunner.engine.Progress.HANDS to hands, com.riverrunner.engine.Progress.CASH_PROFIT_CENTI_BB to cash,
                com.riverrunner.engine.Progress.TOURNEY_WINS to wins, com.riverrunner.engine.Progress.STEALS to 6,
                com.riverrunner.engine.Progress.POTS_WON to 41, com.riverrunner.engine.Progress.SHOWDOWN_WINS to 17,
            ),
            unlocked.associateWith { 1_792_000_000_000L },
        )

    @Test fun t17_achievements() {
        val p = sampleProfile("阿明", 0, 312, 4550, 1, listOf("first_hand", "first_pot", "hands_100", "pocket_aces", "first_title", "first_itm", "guide", "full_house", "allin_win"))
        shot("17_achievements") { com.riverrunner.ui.AchievementsContent(p, onBack = {}) }
    }

    @Test fun t18_saves() {
        val list = listOf(
            sampleProfile("阿明", 0, 312, 4550, 1, listOf("first_hand", "first_pot", "hands_100", "pocket_aces", "first_title")),
            sampleProfile("练习号", 1, 48, -1220, 0, listOf("first_hand", "guide")),
            sampleProfile("锦标赛专用", 2, 1520, 0, 4, listOf("first_hand", "first_pot", "hands_100", "hands_1000", "first_title", "first_itm", "hu_champ")),
        )
        shot("18_saves") { com.riverrunner.ui.SavesContent(list, list[0].id, 6, "存档 4", {}, {}, {}, { _, _ -> }, {}) }
    }

    @Test fun t19_unlock_toast() {
        val s = Scenario(ZH, 6, 5, listOf("Qh8s", "9c9d", "Kd4c", "7s2h", "AhJc", "Tc6d"), "Ks7d2c5hAs")
        s.play(Move.FOLD, Move.FOLD, Move.raiseTo(250), Move.FOLD)
        shot("19_unlock_toast") {
            androidx.compose.foundation.layout.Box {
                TableContent(s.ui(), { Format.amount(it, BB, true) }, {}, {}, {}, {}, {}, {}, animate = false)
                com.riverrunner.ui.UnlockToast(com.riverrunner.engine.Achievements.byId("steal_10"),
                    androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopCenter))
            }
        }
    }

    @Test fun t20_reported_chip_overlap() {
        // 6 players, you UTG; button (top centre) re-raises next to the small blind's chip
        val s = Scenario(ZH, 6, 3, listOf("Jh7h", "Kc4d", "9s8s", "AdQd", "Tc6c", "5h5d"), "Ks7d2c5hAs")
        s.play(Move.raiseTo(250), Move.FOLD, Move.FOLD, Move.raiseTo(750), Move.FOLD, Move.FOLD)
        table("20_reported_chip_overlap", s.ui(practice = true))
    }

    @Test fun t21_setup() = shot("21_setup") {
        com.riverrunner.ui.SetupContent(com.riverrunner.engine.SessionConfig(GameMode.TOURNAMENT, 8, 50, com.riverrunner.engine.TableType.MIXED), {}, {})
    }

    @Test @Config(qualifiers = "w660dp-h320dp-land-xxhdpi")
    fun t22_setup_narrow() = shot("22_setup_narrow") {
        com.riverrunner.ui.SetupContent(com.riverrunner.engine.SessionConfig(GameMode.CASH, 5, 200, com.riverrunner.engine.TableType.GTO), {}, {})
    }

    @Test @Config(qualifiers = "w660dp-h320dp-land-xxhdpi")
    fun t23_home_narrow() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        shot("23_home_narrow") { HomeScreen(vm) }
    }

    @Test @Config(qualifiers = "w720dp-h360dp-land-xxhdpi")
    fun t24_eight_utg2_your_phone() {
        // button 3: SB 4, BB 5, UTG 6, UTG+1 7, UTG+2 = you (0); UTG raises, UTG+1 folds
        val s = Scenario(ZH, 8, 3, listOf("3c4c", "Kh5c", "9d9h", "Jc8c", "7h6h", "2c3d", "Td4s", "KcJd"), "Qd8s3hTs2s")
        s.play(Move.raiseTo(250), Move.FOLD)
        table("24_eight_utg2_your_phone", s.ui(practice = true).copy(levelText = ZH.levelText(1, 3)))
    }

    @Test @Config(qualifiers = "w720dp-h360dp-land-xxhdpi")
    fun t25_eight_flop_your_phone() {
        val s = Scenario(ZH, 8, 2, listOf("AsQs", "Kh5c", "9d9h", "Jc8c", "7h6h", "2c3d", "Td4s", "KcJd"), "Qd8s3hTs2s")
        s.play(Move.FOLD, Move.CALL, Move.raiseTo(300), Move.CALL, Move.CALL, Move.FOLD, Move.CALL, Move.CALL, Move.CALL)
        s.deal()
        s.play(Move.raiseTo(200), Move.raiseTo(800), Move.CALL)
        table("25_eight_flop_your_phone", s.ui())
    }

    @Test fun t26_deck_styles() = shot("26_deck_styles") {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.fillMaxSize().background(com.riverrunner.ui.Palette.Bg).padding(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            for (style in com.riverrunner.ui.DeckStyle.entries) {
                androidx.compose.runtime.CompositionLocalProvider(com.riverrunner.ui.LocalDeckStyle provides style) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                        for (c in listOf("3c", "4s", "Jc", "Js", "Qd", "Kh")) com.riverrunner.ui.PlayingCard(com.riverrunner.engine.Cards.parse(c), 50.dp)
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(16.dp))
                        for (c in listOf("9c", "9s", "2c", "2s")) com.riverrunner.ui.PlayingCard(com.riverrunner.engine.Cards.parse(c), 24.dp)
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(16.dp))
                        for (c in listOf("7c", "7s")) com.riverrunner.ui.PlayingCard(com.riverrunner.engine.Cards.parse(c), 18.dp)
                    }
                }
            }
        }
    }

    @Test fun t15_home() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        shot("15_home") { HomeScreen(vm) }
    }
}
