package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.riverrunner.engine.Cards
import com.riverrunner.engine.LegalActions
import com.riverrunner.engine.Move
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.i18n.Strings
import kotlin.math.roundToInt

/** Live game screen, wired to the view model. */
@Composable
fun TableScreen(vm: AppViewModel) {
    var confirmExit by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    BackHandler { if (showGuide) showGuide = false else confirmExit = true }
    val st = LocalStrings.current
    val ui = vm.tableScreen
    val bb = ui.table?.bigBlind ?: 100
    TableContent(
        ui = ui,
        fmt = { vm.fmt(it, bb) },
        onMove = vm::heroAct,
        onNext = vm::continueToNextHand,
        onFastForward = { vm.fastForward = true },
        onPractice = { vm.practice = it },
        onExit = { confirmExit = true },
        onHelp = { showGuide = true },
    )
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            confirmButton = { TextButton({ confirmExit = false; vm.requestQuit() }) { Text(st.confirm) } },
            dismissButton = { TextButton({ confirmExit = false }) { Text(st.cancel) } },
            title = { Text(st.exitConfirmTitle) },
            text = { Text(st.exitConfirmText) },
        )
    }
    if (showGuide) {
        Dialog(onDismissRequest = { showGuide = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            GuideContent(onClose = { showGuide = false }, startTab = GuideTab.GLOSSARY)
        }
    }
}

@Composable
fun TableContent(
    ui: TableScreenUi,
    fmt: (Int) -> String,
    onMove: (Move) -> Unit,
    onNext: () -> Unit,
    onFastForward: () -> Unit,
    onPractice: (Boolean) -> Unit,
    onExit: () -> Unit,
    onHelp: () -> Unit,
    animate: Boolean = true,
) {
    val st = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    val turnKey = ui.legal?.let { "${ui.handNumber}:${ui.log.size}" }
    LaunchedEffect(turnKey) {
        if (turnKey != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Row(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, top = 4.dp, bottom = 2.dp)) {
            val t = ui.table
            if (t != null) PokerTable(t, fmt, st.pot, Modifier.fillMaxSize(), animateDeal = animate)
        }
        SidePanel(ui, fmt, onMove, onNext, onFastForward, onPractice, onExit, onHelp)
    }
}

@Composable
private fun SidePanel(
    ui: TableScreenUi,
    fmt: (Int) -> String,
    onMove: (Move) -> Unit,
    onNext: () -> Unit,
    onFastForward: () -> Unit,
    onPractice: (Boolean) -> Unit,
    onExit: () -> Unit,
    onHelp: () -> Unit,
) {
    val st = LocalStrings.current
    val heroTurn = ui.legal != null
    val glow = if (heroTurn) pulse() else 0f
    Column(
        Modifier.width(212.dp).fillMaxHeight().background(Palette.Panel)
            .border(if (heroTurn) 2.dp else 0.dp, Palette.Gold.copy(alpha = 0.25f + 0.5f * glow), RoundedCornerShape(0.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(st.handNo(ui.handNumber), color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(ui.blindsText, color = Palette.TextDim, fontSize = 10.sp, maxLines = 1)
                if (ui.levelText != null) Text(ui.levelText, color = Palette.Gold.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1)
            }
            RoundIcon("?", onHelp)
            Spacer(Modifier.width(4.dp))
            RoundIcon("✕", onExit)
        }
        // practice switch
        Row(
            Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(st.practice, color = Palette.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = ui.practice, onCheckedChange = onPractice, modifier = Modifier.scale(0.62f),
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.GoldDim, checkedThumbColor = Palette.Gold, uncheckedTrackColor = Palette.PanelHi2),
            )
        }
        // middle: results, or practice info + action log
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val over = ui.handOver
            if (over != null) {
                HandOverPanel(over, fmt)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val info = ui.practiceInfo
                    if (ui.practice && info != null) PracticeHud(info, fmt)
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        if (maxHeight > 36.dp) ActionLog(ui.log, Modifier.fillMaxSize())
                    }
                }
            }
        }
        // bottom: controls
        AnimatedContent(
            targetState = when {
                ui.legal != null -> 0
                ui.handOver != null -> 1
                else -> 2
            },
            transitionSpec = {
                (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(120))
            },
            label = "controls",
        ) { mode ->
            when (mode) {
                0 -> ui.legal?.let { HeroControls(it, fmt, ui.table?.bigBlind ?: 100, onMove) }
                1 -> ActionButton(
                    if (ui.sessionOver) st.viewResults else st.nextHand + "  ▶", Palette.CallBtn,
                    modifier = Modifier.fillMaxWidth().height(46.dp), onClick = onNext,
                )
                else -> WaitingBar(ui, onFastForward)
            }
        }
    }
}

@Composable
private fun pulse(): Float {
    val tr = rememberInfiniteTransition(label = "panelPulse")
    val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    return v
}

@Composable
private fun RoundIcon(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).pressable(onClick = onClick).clip(CircleShape).background(Palette.PanelHi),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PracticeHud(info: PracticeInfo, fmt: (Int) -> String) {
    val st = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B2A3A))
            .border(1.dp, Palette.Blue.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HudRow(st.pot, fmt(info.pot), st.toCall, if (info.toCall > 0) fmt(info.toCall) else "0")
        if (info.toCall > 0) {
            HudRow(st.potOdds, "1 : %.1f".format(info.pot.toDouble() / info.toCall), st.needEquity, Format.pct(info.potOdds))
        }
        HudRow(st.spr, "%.1f".format(info.spr), null, null)
        Text("${st.yourHand}: ${info.handText}", color = Color(0xFFBFD8F2), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun HudRow(l1: String, v1: String, l2: String?, v2: String?) {
    Row(Modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f)) {
            Text(l1, color = Palette.TextDim, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(v1, color = Palette.Text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f)) {
            if (l2 != null && v2 != null) {
                Text(l2, color = Palette.TextDim, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(v2, color = Palette.Text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
fun ActionLog(lines: List<LogLine>, modifier: Modifier = Modifier, highlightIndex: Int = -1, onClick: ((Int) -> Unit)? = null) {
    val state = rememberLazyListState()
    LaunchedEffect(lines.size, highlightIndex) {
        val target = if (highlightIndex >= 0) highlightIndex else lines.size - 1
        if (target >= 0) state.animateScrollToItem(target)
    }
    LazyColumn(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF101814)).padding(horizontal = 6.dp, vertical = 3.dp),
        state = state,
    ) {
        itemsIndexed(lines) { i, l ->
            val hl = i == highlightIndex
            val mod = Modifier.fillMaxWidth()
                .then(if (hl) Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.GoldDim.copy(alpha = 0.35f)) else Modifier)
                .then(if (onClick != null) Modifier.pressable { onClick(i) } else Modifier)
                .padding(vertical = 1.dp)
            when (l.kind) {
                LogKind.STREET -> Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Palette.Gold, fontWeight = FontWeight.Bold)) { append("— ${l.text} ") }
                        for (c in l.cards) {
                            withStyle(SpanStyle(color = logSuitColor(c), fontWeight = FontWeight.Bold)) { append(rankText(c) + suitText(c) + " ") }
                        }
                    },
                    fontSize = 11.sp, modifier = mod.padding(top = 2.dp),
                )
                LogKind.ACTION -> Text(l.text, color = if (hl) Palette.Gold else Palette.Text, fontSize = 11.sp, maxLines = 2, modifier = mod)
                LogKind.RESULT -> Text(l.text, color = Palette.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = mod)
            }
        }
    }
}

private fun logSuitColor(card: Int) = when (Cards.suit(card)) {
    0 -> Color(0xFF6FD08C); 1 -> Color(0xFF7DB2F0); 2 -> Color(0xFFF07C7C); else -> Color(0xFFE8E8E8)
}

@Composable
private fun HandOverPanel(over: HandOverUi, fmt: (Int) -> String) {
    val st = LocalStrings.current
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFF101814)).padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(st.handResult, color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val net = over.heroNet
            Text(
                (if (net > 0) "+" else if (net < 0) "−" else "") + fmt(kotlin.math.abs(net)),
                color = if (net > 0) Palette.Green else if (net < 0) Palette.Red else Palette.TextDim,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
        }
        for (w in over.winners) {
            Text("🏆 ${st.winsFor(w.name)} ${fmt(w.amount)}", color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (over.byFold) Text(st.othersFolded, color = Palette.TextDim, fontSize = 11.sp)
        if (over.showdown.isNotEmpty()) {
            Text(st.showdown, color = Palette.TextDim, fontSize = 11.sp)
            for (s in over.showdown) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.name, color = if (s.won > 0) Palette.Gold else Palette.Text, fontSize = 11.sp,
                        fontWeight = if (s.won > 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                        modifier = Modifier.width(52.dp),
                    )
                    PlayingCard(s.cards[0], 18.dp)
                    Spacer(Modifier.width(2.dp))
                    PlayingCard(s.cards[1], 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(s.handName, color = if (s.won > 0) Palette.Gold else Palette.TextDim, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WaitingBar(ui: TableScreenUi, onFastForward: () -> Unit) {
    val st = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingDots()
            Spacer(Modifier.width(6.dp))
            Text(
                ui.actingName?.let { st.thinking(it) } ?: st.dealing, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1,
            )
        }
        ActionButton(
            (if (ui.heroInHand) st.fastForward else st.skipHand) + "  ⏩",
            if (ui.fastForward) Palette.GoldDim else Palette.PanelHi2,
            modifier = Modifier.fillMaxWidth().height(40.dp), onClick = onFastForward,
        )
    }
}

@Composable
private fun ThinkingDots() {
    val tr = rememberInfiniteTransition(label = "dots")
    val p by tr.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "d")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 3) {
            val on = p.toInt() == i
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) Palette.Gold else Palette.PanelHi2))
        }
    }
}

// ------------------------------------------------------------------ betting controls

private fun presets(st: Strings, la: LegalActions, bb: Int): List<Pair<String, Int>> {
    val out = ArrayList<Pair<String, Int>>()
    when {
        la.isOpeningBet -> {
            out += "1/3" to (la.pot / 3.0).roundToInt()
            out += "1/2" to (la.pot / 2.0).roundToInt()
            out += "3/4" to (la.pot * 0.75).roundToInt()
            out += st.potShort to la.pot
        }
        la.currentBet <= bb -> {
            // unopened or limped pot preflop
            val limps = ((la.pot - la.currentBet * 2) / bb).coerceAtLeast(0)
            out += "2.5x" to (bb * 2.5).roundToInt() + limps * bb
            out += "3x" to bb * 3 + limps * bb
            out += "4x" to bb * 4 + limps * bb
        }
        else -> {
            out += "2.5x" to (la.currentBet * 2.5).roundToInt()
            out += "3x" to la.currentBet * 3
            out += st.potShort to la.currentBet + la.pot + la.toCall
        }
    }
    val cleaned = out.map { it.first to it.second.coerceIn(la.minRaiseTo, la.maxRaiseTo) }
        .filter { it.second < la.maxRaiseTo }
        .distinctBy { it.second }
    return cleaned + (st.allIn to la.maxRaiseTo)
}

@Composable
private fun HeroControls(la: LegalActions, fmt: (Int) -> String, bb: Int, onMove: (Move) -> Unit) {
    val st = LocalStrings.current
    var target by remember(la) { mutableIntStateOf(defaultTarget(la, bb)) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val p = pulse()
            Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Gold.copy(alpha = 0.4f + 0.6f * p)))
            Spacer(Modifier.width(6.dp))
            Text(st.yourTurn, color = Palette.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (la.canRaise) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                for ((label, v) in presets(st, la, bb)) {
                    val sel = v == target
                    Box(
                        Modifier.weight(1f).height(26.dp).pressable { target = v }.clip(RoundedCornerShape(6.dp))
                            .background(if (sel) Palette.GoldDim else Palette.PanelHi),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = Palette.Text, fontSize = 10.sp, maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            if (la.maxRaiseTo > la.minRaiseTo) {
                val step = (bb / 2).coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton("−") { target = (target - step).coerceIn(la.minRaiseTo, la.maxRaiseTo) }
                    BetSlider(
                        fraction = (target - la.minRaiseTo).toFloat() / (la.maxRaiseTo - la.minRaiseTo),
                        onChange = { f ->
                            val raw = la.minRaiseTo + f * (la.maxRaiseTo - la.minRaiseTo)
                            val snapped = if (f >= 0.995f) la.maxRaiseTo else ((raw / step).roundToInt() * step)
                            target = snapped.coerceIn(la.minRaiseTo, la.maxRaiseTo)
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    StepButton("+") { target = (target + step).coerceIn(la.minRaiseTo, la.maxRaiseTo) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().height(46.dp)) {
            if (la.canCheck) {
                ActionButton(st.check, Palette.CallBtn, modifier = Modifier.weight(1f).fillMaxHeight()) { onMove(Move.CHECK) }
            } else {
                ActionButton(st.fold, Palette.FoldBtn, modifier = Modifier.weight(1f).fillMaxHeight()) { onMove(Move.FOLD) }
                val callAllIn = la.toCall >= la.maxRaiseTo - la.myStreetBet
                ActionButton(
                    if (callAllIn) st.allIn else st.call, if (callAllIn) Palette.AllInBtn else Palette.CallBtn,
                    sub = fmt(la.toCall), modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { onMove(Move.CALL) }
            }
            if (la.canRaise) {
                val isAllIn = target >= la.maxRaiseTo
                ActionButton(
                    when {
                        isAllIn -> st.allIn
                        la.isOpeningBet -> st.bet
                        else -> st.raiseTo
                    },
                    if (isAllIn) Palette.AllInBtn else Palette.RaiseBtn,
                    sub = fmt(target),
                    modifier = Modifier.weight(if (la.canCheck) 1.4f else 1.2f).fillMaxHeight(),
                ) { onMove(Move.raiseTo(target)) }
            }
        }
    }
}

private fun defaultTarget(la: LegalActions, bb: Int): Int {
    val t = when {
        la.isOpeningBet -> (la.pot * 0.5).roundToInt()
        la.currentBet <= bb -> (bb * 2.5).roundToInt()
        else -> la.currentBet * 3
    }
    return t.coerceIn(la.minRaiseTo, la.maxRaiseTo)
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).pressable(onClick = onClick).clip(RoundedCornerShape(6.dp)).background(Palette.PanelHi),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BetSlider(fraction: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    Box(
        modifier.height(26.dp)
            .pointerInput(Unit) { detectTapGestures { cb((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> cb((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val f = fraction.coerceIn(0f, 1f)
            val r = size.height * 0.33f
            val trackH = 6.dp.toPx()
            val y = size.height / 2
            val left = r
            val right = size.width - r
            drawRoundRect(Palette.PanelHi2, Offset(left, y - trackH / 2), Size(right - left, trackH), CornerRadius(trackH / 2))
            val x = left + (right - left) * f
            drawRoundRect(Palette.Gold, Offset(left, y - trackH / 2), Size(x - left, trackH), CornerRadius(trackH / 2))
            drawCircle(Color.White, r + 2.dp.toPx(), Offset(x, y))
            drawCircle(Palette.Gold, r, Offset(x, y))
        }
    }
}
