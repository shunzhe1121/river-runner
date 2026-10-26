package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.data.Profile
import com.riverrunner.engine.AchCategory
import com.riverrunner.engine.AchievementDef
import com.riverrunner.engine.Achievements
import com.riverrunner.engine.Progress
import com.riverrunner.i18n.LocalStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun categoryColor(c: AchCategory): Color = when (c) {
    AchCategory.START -> Palette.Accent
    AchCategory.SKILL -> Color(0xFF9B83FF)
    AchCategory.HANDS -> Color(0xFFF0708F)
    AchCategory.TOURNAMENT -> Palette.Gold
    AchCategory.CASH -> Palette.Green
    AchCategory.STUDY -> Palette.Blue
}

private val SUITS = listOf("♠", "♥", "♦", "♣")

/**
 * Achievement badge drawn as a casino chip: coloured rim with six edge inserts,
 * a dashed inner ring and the glyph in the middle. Locked chips are slate with a progress arc.
 */
@Composable
fun AchievementChip(def: AchievementDef, unlocked: Boolean, progress: Double, size: Dp) {
    val base = categoryColor(def.category)
    val rim = if (unlocked) base else Palette.PanelHi2
    val face = if (unlocked) lerp(base, Palette.Ink, 0.55f) else Palette.PanelHi
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val c = Offset(this.size.width / 2, this.size.height / 2)
            drawCircle(rim, r, c)
            // edge inserts
            for (i in 0 until 6) {
                rotate(i * 60f, c) {
                    drawRect(
                        if (unlocked) Color.White.copy(alpha = 0.9f) else Palette.Line,
                        topLeft = Offset(c.x - r * 0.13f, c.y - r),
                        size = Size(r * 0.26f, r * 0.2f),
                    )
                }
            }
            drawCircle(face, r * 0.72f, c)
            drawCircle(
                if (unlocked) Color.White.copy(alpha = 0.55f) else Palette.Line, r * 0.62f, c,
                style = Stroke(r * 0.04f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(r * 0.12f, r * 0.1f))),
            )
            if (!unlocked && progress > 0) {
                drawArc(
                    Palette.Accent, -90f, (360 * progress).toFloat(), false,
                    topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.86f), size = Size(r * 1.72f, r * 1.72f),
                    style = Stroke(r * 0.1f, cap = StrokeCap.Round),
                )
            }
        }
        val numeric = def.glyph.any { it.isDigit() } || def.glyph in setOf("$", "$$", "+50", "×2", "AA", "72", "1v1", "★")
        Text(
            def.glyph,
            color = if (unlocked) Color.White else Palette.TextDim,
            fontSize = (size.value * (if (def.glyph.length >= 3) 0.24f else 0.3f)).sp,
            fontFamily = if (numeric) Numeric else null,
            fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

// ============================================================================ toast

@Composable
fun UnlockToast(def: AchievementDef?, modifier: Modifier = Modifier) {
    val st = LocalStrings.current
    var last by remember { mutableStateOf<AchievementDef?>(null) }
    if (def != null) last = def
    AnimatedVisibility(
        def != null, modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val d = last ?: return@AnimatedVisibility
        Row(
            Modifier.padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(Palette.PanelHi)
                .border(1.5.dp, categoryColor(d.category), RoundedCornerShape(14.dp))
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .semantics { contentDescription = "${st.achievementUnlocked}: ${if (st.isZh) d.nameZh else d.nameEn}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AchievementChip(d, true, 1.0, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(st.achievementUnlocked, color = categoryColor(d.category), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (st.isZh) d.nameZh else d.nameEn, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (st.isZh) d.descZh else d.descEn, color = Palette.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(260.dp))
            }
        }
    }
}

// ============================================================================ player card

@Composable
fun PlayerCard(profile: Profile, slots: Int, maxSlots: Int, onSwitch: () -> Unit, modifier: Modifier = Modifier) {
    val st = LocalStrings.current
    val unlocked = profile.unlocked.size
    val cashBB = profile.counter(Progress.CASH_PROFIT_CENTI_BB) / 100.0
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Panel)
            .border(1.dp, Palette.PanelHi2, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Text),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    SUITS[profile.emblem % 4], fontSize = 28.sp,
                    color = if (profile.emblem % 4 == 1 || profile.emblem % 4 == 2) Color(0xFFC62828) else Palette.Ink,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(st.saveSlotCount(slots, maxSlots), color = Palette.TextDim, fontSize = 12.sp)
            }
            SmallButton(st.switchSave, onSwitch)
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            CardStat("%,d".format(profile.counter(Progress.HANDS)), st.handsLabel, Palette.Text)
            CardStat(
                (if (cashBB > 0) "+" else "") + "%.0f".format(cashBB), st.cashBBLabel,
                if (cashBB > 0) Palette.Green else if (cashBB < 0) Palette.Red else Palette.Text,
            )
            CardStat("${profile.counter(Progress.TOURNEY_WINS)}", st.titlesLabel, Palette.Gold)
            CardStat("$unlocked/${Achievements.all.size}", st.achievements, Palette.Accent)
        }
    }
}

@Composable
private fun CardStat(value: String, label: String, color: Color) {
    Column {
        Text(value, color = color, fontSize = 24.sp, lineHeight = 26.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1)
    }
}

// ============================================================================ achievements screen

@Composable
fun AchievementsScreen(vm: AppViewModel) {
    BackHandler { vm.screen = Screen.Home }
    AchievementsContent(vm.profile, onBack = { vm.screen = Screen.Home })
}

@Composable
fun AchievementsContent(profile: Profile, onBack: () -> Unit) {
    val st = LocalStrings.current
    val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("←", onBack)
            Text(st.achievements, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(st.unlockedCount(profile.unlocked.size, Achievements.all.size), color = Palette.Accent, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(profile.name, color = Palette.TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (cat in AchCategory.entries) {
                val defs = Achievements.all.filter { it.category == cat }
                item(key = cat.name) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(categoryColor(cat)))
                            Spacer(Modifier.width(8.dp))
                            Text(st.categoryName(cat), color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("${defs.count { it.id in profile.unlocked }}/${defs.size}", color = Palette.TextDim, fontSize = 13.sp, fontFamily = Numeric)
                        }
                        // flowing rows of tiles
                        defs.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { d -> AchievementTile(d, profile, df, Modifier.weight(1f)) }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementTile(d: AchievementDef, profile: Profile, df: SimpleDateFormat, modifier: Modifier) {
    val st = LocalStrings.current
    val at = profile.unlocked[d.id]
    val done = at != null
    val value = profile.counter(d.counter).coerceAtMost(d.target)
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (done) Palette.PanelHi else Palette.Panel)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AchievementChip(d, done, Achievements.progress(d, profile.counters), 52.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (st.isZh) d.nameZh else d.nameEn, color = if (done) Palette.Text else Palette.TextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (st.isZh) d.descZh else d.descEn, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                if (done) st.unlockedOn(df.format(Date(at!!))) else "$value / ${d.target}",
                color = if (done) categoryColor(d.category) else Palette.Accent, fontSize = 12.sp,
                fontFamily = if (done) null else Numeric, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ============================================================================ saves screen

@Composable
fun SavesScreen(vm: AppViewModel) {
    BackHandler { vm.screen = Screen.Home }
    SavesContent(
        profiles = vm.profileList, activeId = vm.profile.id, maxSlots = vm.maxSlots,
        defaultName = vm.nextProfileName(),
        onBack = { vm.screen = Screen.Home },
        onUse = { vm.switchProfile(it); vm.screen = Screen.Home },
        onCreate = { vm.createProfile(it) },
        onRename = vm::renameProfile,
        onDelete = vm::deleteProfile,
    )
}

@Composable
fun SavesContent(
    profiles: List<Profile>,
    activeId: String,
    maxSlots: Int,
    defaultName: String,
    onBack: () -> Unit,
    onUse: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val st = LocalStrings.current
    var naming by remember { mutableStateOf<Pair<String?, String>?>(null) }  // (id or null for new, initial name)
    var deleting by remember { mutableStateOf<Profile?>(null) }
    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("←", onBack)
            Text(st.saves, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(st.saveSlotCount(profiles.size, maxSlots), color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            val full = profiles.size >= maxSlots
            if (full) Text(st.slotsFull(maxSlots), color = Palette.TextDim, fontSize = 12.sp)
            else ActionButton("+ " + st.newSave, Palette.AccentDim, modifier = Modifier.height(40.dp)) { naming = null to defaultName }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { p ->
                        SaveCard(
                            p, p.id == activeId, Modifier.weight(1f),
                            onUse = { onUse(p.id) }, onRename = { naming = p.id to p.name }, onDelete = { deleting = p },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    naming?.let { (id, initial) ->
        var text by remember(id) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(if (id == null) st.newSave else st.rename) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { if (it.length <= 12) text = it },
                    label = { Text(st.saveName) }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, focusedLabelColor = Palette.Accent, cursorColor = Palette.Accent),
                )
            },
            confirmButton = {
                TextButton({
                    if (id == null) onCreate(text) else onRename(id, text)
                    naming = null
                }, enabled = text.isNotBlank()) { Text(if (id == null) st.create else st.saveAction) }
            },
            dismissButton = { TextButton({ naming = null }) { Text(st.cancel) } },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(st.deleteSaveTitle(p.name)) },
            text = { Text(st.deleteSaveText) },
            confirmButton = { TextButton({ onDelete(p.id); deleting = null }) { Text(st.deleteSave, color = Palette.Red) } },
            dismissButton = { TextButton({ deleting = null }) { Text(st.keep) } },
        )
    }
}

@Composable
private fun SaveCard(p: Profile, active: Boolean, modifier: Modifier, onUse: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val st = LocalStrings.current
    val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val cashBB = p.counter(Progress.CASH_PROFIT_CENTI_BB) / 100.0
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Panel)
            .border(if (active) 2.dp else 1.dp, if (active) Palette.Accent else Palette.PanelHi2, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Text), contentAlignment = Alignment.Center) {
                Text(SUITS[p.emblem % 4], fontSize = 22.sp, color = if (p.emblem % 4 == 1 || p.emblem % 4 == 2) Color(0xFFC62828) else Palette.Ink)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            st.currentSave, color = Palette.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Accent).padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(df.format(Date(p.created)), color = Palette.TextDim, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            CardStat("%,d".format(p.counter(Progress.HANDS)), st.handsLabel, Palette.Text)
            CardStat((if (cashBB > 0) "+" else "") + "%.0f".format(cashBB), st.cashBBLabel, if (cashBB > 0) Palette.Green else if (cashBB < 0) Palette.Red else Palette.Text)
            CardStat("${p.counter(Progress.TOURNEY_WINS)}", st.titlesLabel, Palette.Gold)
            CardStat("${p.unlocked.size}/${Achievements.all.size}", st.achievements, Palette.Accent)
        }
        // recently unlocked badges
        val recent = p.unlocked.entries.sortedByDescending { it.value }.take(6).mapNotNull { Achievements.byId(it.key) }
        if (recent.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { recent.forEach { AchievementChip(it, true, 1.0, 28.dp) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!active) ActionButton(st.useSave, Palette.AccentDim, modifier = Modifier.weight(1.4f).height(40.dp), onClick = onUse)
            ActionButton(st.rename, Palette.PanelHi2, modifier = Modifier.weight(1f).height(40.dp), onClick = onRename)
            ActionButton(st.deleteSave, Palette.PanelHi, modifier = Modifier.weight(1f).height(40.dp), onClick = onDelete)
        }
    }
}
