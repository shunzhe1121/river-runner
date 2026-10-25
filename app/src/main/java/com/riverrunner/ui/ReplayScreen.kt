package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.ActionType
import com.riverrunner.engine.Bot
import com.riverrunner.engine.BotStyle
import com.riverrunner.engine.Equity
import com.riverrunner.engine.HandEvaluator
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.MoveType
import com.riverrunner.engine.Policy
import com.riverrunner.engine.Replayer
import com.riverrunner.engine.Cards
import com.riverrunner.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun ReplayScreen(vm: AppViewModel, initial: HandRecord) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.History }
    var record by remember { mutableStateOf(initial) }
    val replayer = remember(initial.id) { Replayer(initial) }
    val frames = replayer.frames
    var idx by remember { mutableIntStateOf(0) }
    val frame = frames[idx.coerceIn(0, frames.size - 1)]
    val bb = record.bigBlind
    val fmt: (Int) -> String = { vm.fmt(it, bb) }

    // equity per (board size) for players still holding cards
    val equityCache = remember(initial.id) { mutableStateMapOf<String, Map<Int, Double>>() }
    val live = frame.players.filter { !it.folded }
    val eqKey = "${frame.board.size}:" + live.joinToString(",") { it.seat.toString() }
    LaunchedEffect(eqKey) {
        if (equityCache[eqKey] == null && live.size >= 2) {
            val eq = withContext(Dispatchers.Default) {
                Equity.showdown(live.map { it.hole }, frame.board, Random(initial.id), 30000)
            }
            equityCache[eqKey] = live.mapIndexed { i, p -> p.seat to eq[i] }.toMap()
        }
    }
    val equities = equityCache[eqKey]

    // hero decision shown on this frame (the action just taken by the hero, or the one about to be taken)
    val actionsSoFar = frames.take(idx + 1).count { it.action != null }
    val decisionIndex = when {
        frame.action?.seat == record.heroSeat -> actionsSoFar - 1
        frame.toAct == record.heroSeat && !frame.isEnd -> actionsSoFar
        else -> -1
    }
    val heroAction = if (decisionIndex >= 0) record.actions.filter { !it.type.isPost }.getOrNull(decisionIndex) else null
    val policyCache = remember(initial.id) { mutableStateMapOf<Int, Policy>() }
    LaunchedEffect(decisionIndex) {
        if (decisionIndex >= 0 && policyCache[decisionIndex] == null) {
            val p = withContext(Dispatchers.Default) {
                runCatching {
                    val e = replayer.engineBeforeAction(decisionIndex)
                    Bot.decide(e, record.heroSeat, BotStyle.GTO, Random(initial.id + decisionIndex))
                }.getOrNull()
            }
            if (p != null) policyCache[decisionIndex] = p
        }
    }
    val policy = if (decisionIndex >= 0) policyCache[decisionIndex] else null

    val names = record.players.associate { it.seat to if (it.seat == record.heroSeat) st.you else it.name }
    val styles = record.players.associate { it.seat to it.style }
    val seatsInOrder = record.players.map { it.seat }.sorted()
    val btnIdx = seatsInOrder.indexOf(record.buttonSeat)
    val order = List(seatsInOrder.size) { seatsInOrder[(btnIdx + 1 + it) % seatsInOrder.size] }

    val view = TableView(
        seats = frame.players.sortedBy { it.seat }.map { p ->
            SeatView(
                seat = p.seat, name = names.getValue(p.seat), stack = p.stack,
                streetBet = if (frame.isEnd) 0 else p.streetBet,
                folded = p.folded, allIn = p.allIn, isHero = p.seat == record.heroSeat,
                isButton = p.seat == record.buttonSeat, isActing = frame.toAct == p.seat,
                hole = p.hole, faceUp = true,
                position = com.riverrunner.engine.HandEngine.positionName(order.indexOf(p.seat), order.size),
                bubble = if (frame.action?.seat == p.seat) vm.actionLabel(frame.action!!, bb) else null,
                won = if (frame.isEnd) maxOf(0, p.stack - p.startStack) else 0,
                busted = false,
                handName = if (frame.isEnd && !p.folded && frame.board.size == 5)
                    HandEvaluator.categoryName(HandEvaluator.evaluate(Cards.mask(frame.board.toIntArray()) or Cards.mask(p.hole)), st.isZh) else null,
                equity = if (!p.folded && !frame.isEnd) equities?.get(p.seat) else null,
            )
        },
        board = frame.board,
        pot = if (frame.isEnd) 0 else frame.pot,
        bigBlind = bb,
    )

    Row(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(4.dp)) {
            PokerTable(
                view, fmt, if (frame.isEnd) st.showdown else streetName(st, frame.street), Modifier.fillMaxSize(),
                styleLabel = { s -> if (!s.isHero && styles[s.seat] != BotStyle.GTO) st.styleName(styles.getValue(s.seat)) else null },
            )
        }
        Column(
            Modifier.width(280.dp).fillMaxHeight().background(Palette.Panel).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("←", { vm.screen = Screen.History })
                Text("${st.replay} #${record.handNumber}", color = Palette.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (record.favorite) "★" else "☆", color = Palette.Gold, fontSize = 22.sp,
                    modifier = Modifier.clickable { vm.toggleFavorite(record.id)?.let { record = it } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                SmallButton("⏮", { idx = 0 }, Modifier.weight(1f))
                SmallButton("◀ " + st.prev, { if (idx > 0) idx-- }, Modifier.weight(1.4f))
                SmallButton(st.next + " ▶", { if (idx < frames.size - 1) idx++ }, Modifier.weight(1.4f), highlight = true)
                SmallButton("⏭", { idx = frames.size - 1 }, Modifier.weight(1f))
            }
            // analysis card
            if (heroAction != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(8.dp)) {
                    val decided = frame.action?.seat == record.heroSeat
                    if (decided) {
                        Text("${st.yourAction}: ${vm.actionLabel(heroAction, bb)}", color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (policy == null) Text(st.computing, color = Palette.TextDim, fontSize = 12.sp)
                    else {
                        val dist = policy.options.groupBy { it.first.type }.mapValues { e -> e.value.sumOf { it.second } }
                        val parts = listOf(MoveType.FOLD, MoveType.CHECK, MoveType.CALL, MoveType.RAISE).mapNotNull { t ->
                            dist[t]?.let { p ->
                                val name = when (t) { MoveType.FOLD -> st.fold; MoveType.CHECK -> st.check; MoveType.CALL -> st.call; MoveType.RAISE -> st.raise }
                                "$name ${Format.pct(p)}"
                            }
                        }
                        Text("${st.gtoSuggest}: " + parts.joinToString(" · "), color = Palette.Gold, fontSize = 12.sp)
                        if (decided) {
                            val t = when (heroAction.type) {
                                ActionType.FOLD -> MoveType.FOLD; ActionType.CHECK -> MoveType.CHECK
                                ActionType.CALL -> MoveType.CALL; else -> MoveType.RAISE
                            }
                            val p = dist[t] ?: 0.0
                            val ok = p >= 0.15
                            Text(
                                if (ok) "✓ ${st.inLine}" else "⚠ ${st.deviation}",
                                color = if (ok) Palette.Green else Palette.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        policy.equity?.let { Text("${st.estEquity}: ${Format.pct(it)}", color = Palette.TextDim, fontSize = 11.sp) }
                        if (policy.potOdds > 0) Text("${st.needEquity}: ${Format.pct(policy.potOdds)}", color = Palette.TextDim, fontSize = 11.sp)
                    }
                }
            }
            // action log
            Text(st.actionLog, color = Palette.TextDim, fontSize = 12.sp)
            val listState = rememberLazyListState()
            LaunchedEffect(idx) { listState.animateScrollToItem(idx.coerceAtLeast(0)) }
            LazyColumn(Modifier.weight(1f), state = listState) {
                itemsIndexed(frames) { i, f ->
                    val act = f.action
                    val text = when {
                        act != null -> "${names.getValue(act.seat)}: ${vm.actionLabel(act, bb)}"
                        i == 0 -> streetName(st, f.street)
                        else -> "— ${streetName(st, f.street)} " + f.board.drop(if (f.board.size <= 3) 0 else f.board.size - 1).joinToString(" ") { Cards.toString(it) }
                    }
                    Text(
                        text, fontSize = 12.sp,
                        color = if (i == idx) Palette.Gold else if (f.action == null) Palette.TextDim else Palette.Text,
                        fontWeight = if (i == idx) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { idx = i }.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
