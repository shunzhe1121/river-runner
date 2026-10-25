package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.HeroStats
import com.riverrunner.engine.SessionConfig
import com.riverrunner.engine.Stats
import com.riverrunner.engine.TableType
import com.riverrunner.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================ Home

@Composable
fun HomeScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    val p = vm.prefs
    var mode by remember { mutableStateOf(p.lastMode) }
    var size by remember { mutableIntStateOf(p.lastSize) }
    var stackBB by remember { mutableIntStateOf(p.lastStackBB) }
    var table by remember { mutableStateOf(p.lastTableType) }
    var hpl by remember { mutableIntStateOf(p.lastHandsPerLevel) }
    var ante by remember { mutableStateOf(p.lastAnte) }
    val stackOptions = if (mode == GameMode.CASH) listOf(50, 100, 150, 200) else listOf(20, 30, 50, 75, 100)

    Row(Modifier.fillMaxSize().background(Palette.Bg).padding(16.dp)) {
        Column(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("♠ ${st.appName}", color = Palette.Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(st.tagline, color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            NavItem(st.history) { vm.screen = Screen.History }
            NavItem(st.stats) { vm.screen = Screen.Stats }
            NavItem(st.settings) { vm.screen = Screen.Settings }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Palette.Panel)
                .padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OptionRow(st.mode) {
                Pill(st.cash, mode == GameMode.CASH, { mode = GameMode.CASH; if (stackBB !in listOf(50, 100, 150, 200)) stackBB = 100 })
                Pill(st.tournament, mode == GameMode.TOURNAMENT, { mode = GameMode.TOURNAMENT; if (stackBB !in listOf(20, 30, 50, 75, 100)) stackBB = 50 })
            }
            OptionRow(st.players) {
                for (n in listOf(2, 5, 6, 7, 8)) Pill("$n", size == n, { size = n })
            }
            OptionRow(st.startingStack) {
                for (b in stackOptions) Pill("$b BB", stackBB == b, { stackBB = b })
            }
            OptionRow(st.tableType) {
                Pill(st.gtoTable, table == TableType.GTO, { table = TableType.GTO })
                Pill(st.mixedTable, table == TableType.MIXED, { table = TableType.MIXED })
            }
            Text(if (table == TableType.GTO) st.gtoHint else st.mixedHint, color = Palette.TextDim, fontSize = 11.sp)
            if (mode == GameMode.TOURNAMENT) {
                OptionRow(st.handsPerLevel) {
                    for (h in listOf(5, 8, 10, 15, 20)) Pill("$h", hpl == h, { hpl = h })
                }
            } else {
                OptionRow(st.ante) {
                    Pill(st.none, !ante, { ante = false })
                    Pill("12.5%", ante, { ante = true })
                }
            }
            ActionButton(st.start, Palette.RaiseBtn, modifier = Modifier.fillMaxWidth()) {
                p.lastMode = mode; p.lastSize = size; p.lastStackBB = stackBB
                p.lastTableType = table; p.lastHandsPerLevel = hpl; p.lastAnte = ante
                vm.startGame(
                    SessionConfig(
                        mode = mode, tableSize = size, startingBB = stackBB, tableType = table,
                        handsPerLevel = hpl, cashAnteFraction = if (ante) 0.125 else 0.0,
                    )
                )
            }
        }
    }
}

@Composable
private fun NavItem(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Panel)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) { Text(text, color = Palette.Text, fontSize = 15.sp) }
}

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

// ============================================================================ Result

@Composable
fun ResultScreen(vm: AppViewModel, summary: com.riverrunner.engine.SessionSummary) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    Row(Modifier.fillMaxSize().background(Palette.Bg).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(st.result, color = Palette.TextDim, fontSize = 14.sp)
            Text(
                if (summary.heroPlace == 1) st.champion else st.placeN(summary.heroPlace),
                color = Palette.Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            )
            if (summary.endedEarly) Text(st.endedEarly, color = Palette.TextDim, fontSize = 13.sp)
            val profit = summary.heroProfit
            val unit = if (summary.mode == GameMode.CASH) " BB" else if (st.isZh) " 积分" else " pts"
            Text(
                "${st.profit}: " + (if (profit >= 0) "+" else "") + "%.1f".format(profit) + unit,
                color = if (profit >= 0) Palette.Green else Palette.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
            Text("${st.handsPlayed}: ${summary.handsPlayed}", color = Palette.Text, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            ActionButton(st.playAgain, Palette.RaiseBtn, modifier = Modifier.fillMaxWidth()) { vm.playAgain() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(st.history, Palette.PanelHi, modifier = Modifier.weight(1f)) { vm.screen = Screen.History }
                ActionButton(st.home, Palette.PanelHi, modifier = Modifier.weight(1f)) { vm.screen = Screen.Home }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Palette.Panel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(summary.standings) { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (s.isHuman) Palette.GoldDim.copy(alpha = 0.35f) else Palette.PanelHi)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#${s.place}", color = Palette.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                    Text(if (s.isHuman) st.you else s.name, color = Palette.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(if (s.stack > 0) Format.amount(s.stack, 100, vm.showInBB && summary.mode == GameMode.CASH) else st.busted, color = Palette.TextDim, fontSize = 14.sp)
                }
            }
        }
    }
}

// ============================================================================ History

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    var favOnly by remember { mutableStateOf(false) }
    val records = remember { mutableStateListOf<HandRecord>() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val all = withContext(Dispatchers.IO) { vm.history.all() }
        records.clear(); records.addAll(all.asReversed())
        loaded = true
    }
    val shown = if (favOnly) records.filter { it.favorite } else records
    val df = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("← " + st.back, { vm.screen = Screen.Home })
            Text(st.history, color = Palette.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Pill(st.all, !favOnly, { favOnly = false })
            Pill("★ " + st.favorites, favOnly, { favOnly = true })
        }
        Text(st.keepHandsNote, color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
        if (loaded && shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(st.noHands, color = Palette.TextDim) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { r ->
                val hero = r.hero
                val net = r.heroNet.toDouble() / r.bigBlind
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Panel)
                        .clickable { vm.screen = Screen.Replay(r) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.width(92.dp)) {
                        Text(df.format(Date(r.time)), color = Palette.TextDim, fontSize = 11.sp)
                        Text(
                            (if (r.mode == GameMode.CASH) st.cash else st.tournament) + " #${r.handNumber}",
                            color = Palette.Text, fontSize = 12.sp,
                        )
                        Text(Stats.positionOf(r), color = Palette.TextDim, fontSize = 11.sp)
                    }
                    if (hero != null) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        PlayingCard(hero.hole[0], 26.dp); PlayingCard(hero.hole[1], 26.dp)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (c in r.board) PlayingCard(c, 22.dp)
                    }
                    Text(
                        (if (net > 0) "+" else "") + "%.1f BB".format(net),
                        color = if (net > 0) Palette.Green else if (net < 0) Palette.Red else Palette.TextDim,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp),
                    )
                    Text(
                        if (r.favorite) "★" else "☆", color = Palette.Gold, fontSize = 22.sp,
                        modifier = Modifier.clickable {
                            val upd = vm.toggleFavorite(r.id)
                            if (upd != null) {
                                val i = records.indexOfFirst { it.id == r.id }
                                if (i >= 0) records[i] = upd
                            }
                        }.padding(6.dp),
                    )
                }
            }
        }
    }
}

// ============================================================================ Stats

@Composable
fun StatsScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    var stats by remember { mutableStateOf<HeroStats?>(null) }
    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.Default) { Stats.compute(vm.history.all()) }
    }
    val p = vm.prefs
    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("← " + st.back, { vm.screen = Screen.Home })
            Text(st.stats, color = Palette.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val s = stats
                SectionTitle(st.heroStatsTitle + (if (s != null) " (${s.hands})" else ""))
                if (s == null) Text(st.computing, color = Palette.TextDim)
                else {
                    StatLine(st.vpip, Format.pct(s.vpip))
                    StatLine(st.pfr, Format.pct(s.pfr))
                    StatLine(st.threeBet, Format.pct(s.threeBet))
                    StatLine(st.wtsd, Format.pct(s.wtsd))
                    StatLine(st.wsd, Format.pct(s.wsd))
                    StatLine(st.bbPer100, "%.1f".format(s.bbPer100), s.bbPer100)
                    StatLine(st.netBB, "%.1f".format(s.netBB), s.netBB)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionTitle(st.byPosition)
                val s = stats
                if (s != null) {
                    val order = listOf("UTG", "UTG+1", "UTG+2", "HJ", "CO", "BTN", "SB", "BB")
                    for (pos in order) s.byPosition[pos]?.let { StatLine(pos, "%.1f".format(it), it) }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionTitle(st.careerTitle)
                StatLine(st.sessions, "${p.cashSessions}")
                StatLine(st.cashProfitBB, "%.1f".format(p.cashProfitBB), p.cashProfitBB)
                StatLine(st.tournamentsPlayed, "${p.tourneys}")
                StatLine(st.tournamentWins, "${p.tourneyWins}")
                StatLine(st.itm, "${p.tourneyItm}")
                StatLine(st.tourneyPoints, "%.0f".format(p.tourneyProfit), p.tourneyProfit)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, signed: Double? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when { signed == null -> Palette.Text; signed > 0 -> Palette.Green; signed < 0 -> Palette.Red; else -> Palette.Text },
        )
    }
}

// ============================================================================ Settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    var practice by remember { mutableStateOf(vm.prefs.practiceDefault) }
    var speed by remember { mutableIntStateOf(vm.prefs.speed) }
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("← " + st.back, { vm.screen = Screen.Home })
            Text(st.settings, color = Palette.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OptionRow(st.language) {
                    Pill("中文", st.isZh, { vm.setChinese(true) })
                    Pill("English", !st.isZh, { vm.setChinese(false) })
                }
                OptionRow(st.speed) {
                    Pill(st.slow, speed == 0, { speed = 0; vm.prefs.speed = 0 })
                    Pill(st.normal, speed == 1, { speed = 1; vm.prefs.speed = 1 })
                    Pill(st.fast, speed == 2, { speed = 2; vm.prefs.speed = 2 })
                }
                ToggleRow(st.practiceDefault, practice) { practice = it; vm.prefs.practiceDefault = it }
                ToggleRow(st.showInBB, vm.showInBB) { vm.setShowBB(it) }
                ActionButton(st.clearHistory, Palette.FoldBtn, modifier = Modifier.fillMaxWidth()) { confirmClear = true }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(14.dp),
            ) {
                SectionTitle(st.about)
                Text(st.aboutText, color = Palette.Text, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(8.dp))
                Text("github.com/shunzhe1121/river-runner", color = Palette.TextDim, fontSize = 12.sp)
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = { TextButton({ confirmClear = false; vm.history.clearNonFavorites() }) { Text(st.confirm) } },
            dismissButton = { TextButton({ confirmClear = false }) { Text(st.cancel) } },
            text = { Text(st.clearHistoryConfirm) },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.GoldDim, checkedThumbColor = Palette.Gold),
        )
    }
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
