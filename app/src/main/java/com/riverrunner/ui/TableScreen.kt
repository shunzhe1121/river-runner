package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.riverrunner.engine.LegalActions
import com.riverrunner.engine.Move
import com.riverrunner.i18n.LocalStrings
import kotlin.math.roundToInt

@Composable
fun TableScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { confirmExit = true }
    val view = vm.table
    val bb = view?.bigBlind ?: 100
    val fmt: (Int) -> String = { vm.fmt(it, bb) }

    Row(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(4.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    enabled = vm.waitingNextHand,
                ) { vm.continueToNextHand() },
        ) {
            if (view != null) PokerTable(view, fmt, vm.resultBanner, Modifier.fillMaxSize())
        }
        Column(
            Modifier.width(236.dp).fillMaxHeight().background(Palette.Panel).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${st.hand} ${vm.handNumber}" + if (st.isZh) " 手" else "", color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val lvl = vm.level
                    if (lvl != null) {
                        val blinds = "${fmtChips(lvl.smallBlind)}/${fmtChips(lvl.bigBlind)}" + if (lvl.ante > 0) " (${fmtChips(lvl.ante)})" else ""
                        Text(
                            if (vm.mode == GameMode.TOURNAMENT) "${st.level} ${lvl.level} · $blinds" else blinds,
                            color = Palette.TextDim, fontSize = 11.sp,
                        )
                        vm.handsToNextLevel?.let { Text(st.nextLevelIn(it), color = Palette.TextDim, fontSize = 11.sp) }
                    }
                }
                SmallButton(st.exit, { confirmExit = true })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.practice, color = Palette.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = vm.practice, onCheckedChange = { vm.practice = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.GoldDim, checkedThumbColor = Palette.Gold),
                )
            }
            val legal = vm.heroLegal
            val info = vm.practiceInfo
            if (vm.practice && info != null && legal != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(8.dp),
                ) {
                    InfoRow(st.pot, fmt(info.pot))
                    if (info.toCall > 0) {
                        InfoRow(st.toCall, fmt(info.toCall))
                        InfoRow(st.potOdds, "1 : %.1f".format((info.pot.toDouble() / info.toCall)))
                        InfoRow(st.needEquity, Format.pct(info.potOdds))
                    }
                    InfoRow(st.spr, "%.1f".format(info.spr))
                }
            }
            Spacer(Modifier.weight(1f))
            when {
                legal != null -> HeroControls(vm, legal, fmt, bb)
                vm.waitingNextHand -> ActionButton(st.nextHand, Palette.CallBtn, modifier = Modifier.fillMaxWidth()) { vm.continueToNextHand() }
                vm.table != null -> ActionButton(
                    st.fastForward + " ⏩", if (vm.fastForward) Palette.GoldDim else Palette.PanelHi,
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.fastForward = true }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            confirmButton = { TextButton({ confirmExit = false; vm.requestQuit() }) { Text(st.confirm) } },
            dismissButton = { TextButton({ confirmExit = false }) { Text(st.cancel) } },
            title = { Text(st.exitConfirmTitle) },
            text = { Text(st.exitConfirmText) },
        )
    }
}

private fun fmtChips(c: Int) = "%,d".format(c)

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroControls(vm: AppViewModel, la: LegalActions, fmt: (Int) -> String, bb: Int) {
    val st = LocalStrings.current
    var amount by remember(la) { mutableFloatStateOf(la.minRaiseTo.toFloat()) }
    val target = amount.roundToInt().coerceIn(la.minRaiseTo, la.maxRaiseTo)

    if (la.canRaise) {
        // presets
        val presets = ArrayList<Pair<String, Int>>()
        val potAfterCall = la.pot + la.toCall
        if (la.isOpeningBet) {
            for (f in listOf(0.33, 0.5, 0.75, 1.0)) presets += "${(f * 100).roundToInt()}%" to (la.pot * f).roundToInt()
        } else if (la.currentBet <= bb) {
            // unopened preflop pot
            presets += "2.2x" to (bb * 2.2).roundToInt()
            presets += "2.5x" to (bb * 2.5).roundToInt()
            presets += "3x" to bb * 3
        } else {
            presets += "2.5x" to (la.currentBet * 2.5).roundToInt()
            presets += "3x" to la.currentBet * 3
            presets += st.pot to la.currentBet + potAfterCall
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            for ((label, t) in presets.take(4)) {
                val v = t.coerceIn(la.minRaiseTo, la.maxRaiseTo)
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Palette.PanelHi)
                        .clickable { amount = v.toFloat() }.padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = Palette.Text, fontSize = 11.sp, maxLines = 1) }
            }
        }
        if (la.maxRaiseTo > la.minRaiseTo) {
            Slider(
                value = amount.coerceIn(la.minRaiseTo.toFloat(), la.maxRaiseTo.toFloat()),
                onValueChange = { amount = it },
                valueRange = la.minRaiseTo.toFloat()..la.maxRaiseTo.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Palette.Gold, activeTrackColor = Palette.Gold),
                modifier = Modifier.height(28.dp),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        if (la.canCheck) {
            ActionButton(st.check, Palette.CallBtn, modifier = Modifier.weight(1f)) { vm.heroAct(Move.CHECK) }
        } else {
            ActionButton(st.fold, Palette.FoldBtn, modifier = Modifier.weight(1f)) { vm.heroAct(Move.FOLD) }
            val callAllIn = la.toCall >= la.maxRaiseTo - la.myStreetBet
            ActionButton(
                if (callAllIn) "${st.allIn}\n${fmt(la.toCall)}" else "${st.call}\n${fmt(la.toCall)}",
                Palette.CallBtn, modifier = Modifier.weight(1f),
            ) { vm.heroAct(Move.CALL) }
        }
    }
    if (la.canRaise) {
        val isAllIn = target >= la.maxRaiseTo
        val label = when {
            isAllIn -> "${st.allIn} ${fmt(la.maxRaiseTo)}"
            la.isOpeningBet -> "${st.bet} ${fmt(target)}"
            else -> "${st.raiseTo} ${fmt(target)}"
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(label, Palette.RaiseBtn, modifier = Modifier.weight(1f)) { vm.heroAct(Move.raiseTo(target)) }
            if (!isAllIn) {
                ActionButton(st.allIn, Color(0xFF7A2E1E), modifier = Modifier.width(64.dp)) { vm.heroAct(Move.raiseTo(la.maxRaiseTo)) }
            }
        }
    }
}
