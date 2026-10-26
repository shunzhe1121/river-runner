package com.riverrunner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** All positions on the table in dp, for a table area of [w] x [h] with [n] seats (hero at the bottom). */
class TableGeometry(val w: Float, val h: Float, val n: Int) {
    val cx = w / 2f
    val cy = h * 0.42f
    val podW = 118f
    val podH = 48f
    val boardCardW = if (w < 520f || h < 320f) 34f else 38f
    val boardCardH = boardCardW * 1.4f
    val boardGap = 4f
    val boardW = boardCardW * 5 + boardGap * 4
    val boardTop = cy - boardCardH / 2
    val boardBottom = cy + boardCardH / 2
    val heroCardW = if (h < 330f) 44f else 50f
    val heroCardH = heroCardW * 1.4f
    val heroCardsW = heroCardW * 1.8f
    val heroPod = Offset(cx, h - podH / 2 - 2f)
    val heroCardsTop = heroPod.y - podH / 2 + 10f - heroCardH
    val potCenter = Offset(cx, boardTop - 15f)
    val bannerCenter = Offset(cx, boardBottom + 16f)

    fun seatCenter(rel: Int): Offset {
        if (rel == 0) return heroPod
        val ang = Math.toRadians(90.0 + rel * 360.0 / n)
        val x = cx + w * 0.405f * cos(ang).toFloat()
        val y = cy + h * 0.45f * sin(ang).toFloat()
        return Offset(
            x.coerceIn(podW / 2 + 8f, w - podW / 2 - 4f),
            y.coerceIn(podH / 2 + 20f, h - podH / 2 - 18f),
        )
    }

    /** Where a seat's bet sits: just outside its pod, towards the middle, never over the board. */
    fun betPos(rel: Int): Offset {
        if (rel == 0) return Offset(cx, heroCardsTop - 28f)
        val s = seatCenter(rel)
        val dx = cx - s.x
        val dy = cy - s.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val ux = dx / len
        val uy = dy / len
        val tx = if (abs(ux) < 1e-3f) Float.MAX_VALUE else (podW / 2) / abs(ux)
        val ty = if (abs(uy) < 1e-3f) Float.MAX_VALUE else (podH / 2 + 18f) / abs(uy)
        val t = min(tx, ty) + 24f
        var x = s.x + ux * t
        var y = s.y + uy * t
        val hw = 34f
        val hh = 11f
        val bl = cx - boardW / 2 - 4f
        val br = cx + boardW / 2 + 4f
        // board overlap -> move above or below the board
        if (x + hw > bl && x - hw < br && y + hh > boardTop - 2f && y - hh < boardBottom + 2f) {
            y = if (s.y <= cy) boardTop - hh - 4f else boardBottom + hh + 4f
        }
        // pot label overlap -> push sideways
        val pl = cx - 56f
        val pr = cx + 56f
        if (x + hw > pl && x - hw < pr && abs(y - potCenter.y) < 22f) {
            x = if (s.x < cx) pl - hw - 2f else pr + hw + 2f
        }
        return Offset(x.coerceIn(hw, w - hw), y.coerceIn(hh, h - hh))
    }
}

private fun Modifier.centerAt(p: Offset): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(
            (p.x.dp.toPx() - placeable.width / 2f).roundToInt(),
            (p.y.dp.toPx() - placeable.height / 2f).roundToInt(),
        )
    }
}

@Composable
fun PokerTable(
    t: TableUi,
    fmt: (Int) -> String,
    potLabel: String,
    modifier: Modifier = Modifier,
    animateDeal: Boolean = true,
) {
    BoxWithConstraints(modifier) {
        val n = t.seats.size
        val g = TableGeometry(maxWidth.value, maxHeight.value, n)
        val heroIndex = t.seats.indexOfFirst { it.isHero }.coerceAtLeast(0)
        fun rel(i: Int) = (i - heroIndex + n) % n

        Felt(g)

        // pot
        val potShown by animateIntAsState(t.pot, tween(450), label = "pot")
        Box(Modifier.fillMaxSize().centerAt(g.potCenter)) {
            AnimatedVisibility(t.pot > 0, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                ChipLabel("$potLabel ${fmt(potShown)}")
            }
        }

        // board
        Row(
            Modifier.fillMaxSize().centerAt(Offset(g.cx, g.cy)).wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(g.boardGap.dp),
        ) {
            for (i in 0 until 5) {
                val c = t.board.getOrNull(i)
                BoardCard(c, g.boardCardW.dp, if (i < 3) i * 120 else 0, dim = t.highlight.isNotEmpty() && c !in t.highlight)
            }
        }

        // result banner
        Box(Modifier.fillMaxSize().centerAt(g.bannerCenter)) {
            AnimatedVisibility(
                t.banner != null,
                enter = fadeIn(tween(300, 350)) + slideInVertically(tween(300, 350)) { it / 2 },
                exit = fadeOut(),
            ) {
                Text(
                    t.banner ?: "", color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xE6000000))
                        .border(1.dp, Palette.Gold.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }

        // seats
        for ((i, s) in t.seats.withIndex()) {
            val r = rel(i)
            val center = g.seatCenter(r)
            val dealDelay = 80 * ((r + n - 1) % n)
            val dealFrom = Offset(g.cx - center.x, g.cy - center.y)
            if (s.isHero) {
                HeroSeat(s, g, t, fmt, dealDelay, dealFrom, animateDeal)
            } else {
                Box(Modifier.fillMaxSize().centerAt(center)) {
                    SeatPod(s, g, t, fmt, dealDelay, dealFrom, animateDeal)
                }
            }
        }

        // bets (animated between seat, bet spot and pot)
        for ((i, s) in t.seats.withIndex()) {
            val r = rel(i)
            BetChip(s.streetBet, g.seatCenter(r), g.betPos(r), g.potCenter, fmt)
        }
        // winnings flying from the pot to the winner
        for ((i, s) in t.seats.withIndex()) {
            if (s.won > 0) WinChip(t.handKey, s.won, g.potCenter, g.seatCenter(rel(i)), fmt)
        }
    }
}

@Composable
private fun Felt(g: TableGeometry) {
    Canvas(Modifier.fillMaxSize()) {
        val d = density
        val c = Offset(g.cx * d, g.cy * d)
        val rx = g.w * 0.43f * d
        val ry = g.h * 0.39f * d
        val rail = 12f * d
        drawOval(
            Brush.verticalGradient(listOf(Palette.RailHi, Palette.Rail), startY = c.y - ry - rail, endY = c.y + ry + rail),
            topLeft = Offset(c.x - rx - rail, c.y - ry - rail), size = Size((rx + rail) * 2, (ry + rail) * 2),
        )
        drawOval(
            Brush.radialGradient(listOf(Palette.FeltCenter, Palette.FeltEdge), center = c, radius = rx * 1.05f),
            topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2, ry * 2),
        )
        drawOval(
            Color(0x1FFFFFFF), topLeft = Offset(c.x - rx * 0.80f, c.y - ry * 0.74f),
            size = Size(rx * 1.6f, ry * 1.48f), style = Stroke(1.5f * d),
        )
    }
}

/** Board card that flips face-up when it is dealt. */
@Composable
private fun BoardCard(card: Int?, width: Dp, delayMs: Int, dim: Boolean) {
    if (card == null) {
        Box(
            Modifier.size(width, width * 1.4f).clip(RoundedCornerShape(width * 0.12f))
                .background(Color(0x14000000)).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(width * 0.12f))
        )
        return
    }
    val rot = remember(card) { Animatable(180f) }
    LaunchedEffect(card) {
        delay(delayMs.toLong())
        rot.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { rotationY = rot.value; cameraDistance = 14f * density }) {
        if (rot.value > 90f) CardBack(width) else PlayingCard(card, width, dim)
    }
}

/** A card that flips between back and face. */
@Composable
fun FlipCard(card: Int, faceUp: Boolean, width: Dp, dim: Boolean = false) {
    val rot by animateFloatAsState(if (faceUp) 0f else 180f, tween(450), label = "flip")
    Box(Modifier.graphicsLayer { rotationY = rot; cameraDistance = 14f * density }) {
        if (rot > 90f) CardBack(width) else PlayingCard(card, width, dim)
    }
}

/** Animates content flying in from the middle of the table at the start of a hand. */
@Composable
private fun DealIn(key: Long, delayMs: Int, from: Offset, enabled: Boolean, content: @Composable () -> Unit) {
    val p = remember(key) { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(key) {
        if (enabled) {
            delay(delayMs.toLong())
            p.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            val k = 1f - p.value
            translationX = from.x.dp.toPx() * k
            translationY = from.y.dp.toPx() * k
            rotationZ = -25f * k
            alpha = if (p.value < 0.02f) 0f else 1f
            scaleX = 0.7f + 0.3f * p.value
            scaleY = 0.7f + 0.3f * p.value
        }
    ) { content() }
}

@Composable
private fun actingPulse(active: Boolean): Float {
    if (!active) return 0f
    val tr = rememberInfiniteTransition(label = "pulse")
    val v by tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "pulseV")
    return v
}

@Composable
private fun ThinkingBar(key: Int, durationMs: Long, modifier: Modifier) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key, durationMs) {
        if (durationMs > 0) p.animateTo(1f, tween(durationMs.toInt(), easing = LinearEasing))
    }
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p.value).background(Palette.Accent))
    }
}

@Composable
private fun ActionBadge(text: String?, kind: ActKind?, gold: Boolean = false) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f)) + fadeIn()) togetherWith fadeOut(tween(120))
        },
        label = "badge",
    ) { txt ->
        if (txt != null) {
            Text(
                txt, color = if (gold) Palette.Ink else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (gold) Palette.Gold else actColor(kind))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun DealerButton() {
    Box(
        Modifier.size(17.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White)
            .border(1.5.dp, Color(0xFF333333), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("D", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, lineHeight = 10.sp) }
}

@Composable
private fun PodFrame(s: SeatUi, pulse: Float, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        s.won > 0 -> Palette.Gold
        s.isActing -> Palette.Accent.copy(alpha = pulse)
        else -> Color(0x33FFFFFF)
    }
    val borderW = if (s.isActing || s.won > 0) 2.dp else 1.dp
    Box(
        modifier
            .shadow(if (s.isActing || s.won > 0) 10.dp else 3.dp, shape, ambientColor = if (s.won > 0) Palette.Gold else Palette.Accent, spotColor = if (s.won > 0) Palette.Gold else if (s.isActing) Palette.Accent else Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF2223129), Color(0xF2141D18))))
            .border(borderW, borderColor, shape),
    ) { content() }
}

@Composable
private fun SeatPod(s: SeatUi, g: TableGeometry, t: TableUi, fmt: (Int) -> String, dealDelay: Int, dealFrom: Offset, animateDeal: Boolean) {
    val pulse = actingPulse(s.isActing)
    val dimAlpha by animateFloatAsState(if (s.busted) 0.3f else if (s.folded) 0.5f else 1f, tween(300), label = "fold")
    Box(Modifier.size((g.podW + 12).dp, (g.podH + 36).dp), contentAlignment = Alignment.Center) {
        PodFrame(s, pulse, Modifier.size(g.podW.dp, g.podH.dp).alpha(dimAlpha)) {
            Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = Palette.Text, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (s.busted) "—" else fmt(s.stack), maxLines = 1,
                        color = if (s.allIn && !s.busted) Palette.Red else Palette.Gold, fontSize = 17.sp, lineHeight = 18.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold,
                    )
                }
                val hole = s.hole
                if (hole != null && !s.busted) {
                    AnimatedVisibility(!s.folded || s.faceUp, enter = fadeIn(), exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.4f)) {
                        DealIn(t.handKey, dealDelay, dealFrom, animateDeal) {
                            Box(Modifier.size(41.dp, 34.dp)) {
                                val dim = t.highlight.isNotEmpty()
                                FlipCard(hole[0], s.faceUp, 24.dp, dim && hole[0] !in t.highlight)
                                Box(Modifier.offset(x = 16.dp, y = 0.dp)) {
                                    FlipCard(hole[1], s.faceUp, 24.dp, dim && hole[1] !in t.highlight)
                                }
                            }
                        }
                    }
                }
            }
            if (s.isActing) {
                ThinkingBar(t.actionSeq, t.thinkingMs, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        // dealer button (top-left)
        if (s.isButton) Box(Modifier.align(Alignment.TopStart).offset(x = 0.dp, y = 11.dp)) { DealerButton() }
        // action / hand badge (top-centre)
        Box(Modifier.align(Alignment.TopCenter)) {
            ActionBadge(s.handName ?: s.action, s.actKind, gold = s.handName != null)
        }
        // position (+ style in replay) (bottom-left)
        val posText = listOfNotNull(s.position.takeIf { it.isNotEmpty() }, s.tag).joinToString(" · ")
        if (posText.isNotEmpty()) {
            Text(
                posText, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = 12.dp, y = (-2).dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xCC0B1210)).padding(horizontal = 4.dp),
            )
        }
        // equity (replay) or winnings (bottom-right)
        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = 0.dp)) {
            WonOrEquity(s, fmt)
        }
    }
}

@Composable
private fun WonOrEquity(s: SeatUi, fmt: (Int) -> String) {
    AnimatedVisibility(s.won > 0, enter = scaleIn(tween(300, 550)) + fadeIn(tween(300, 550)), exit = fadeOut()) {
        Text(
            "+" + fmt(s.won), color = Palette.Ink, fontSize = 14.sp, fontFamily = Numeric, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Green).padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
    val eq = s.equity
    if (eq != null && s.won == 0) {
        Text(
            "%.1f%%".format(eq * 100), color = Color.White, fontSize = 14.sp, fontFamily = Numeric, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Blue).padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun HeroSeat(s: SeatUi, g: TableGeometry, t: TableUi, fmt: (Int) -> String, dealDelay: Int, dealFrom: Offset, animateDeal: Boolean) {
    val pulse = actingPulse(s.isActing)
    val lift by animateFloatAsState(if (s.isActing) -10f else 0f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "lift")
    val cardsAlpha by animateFloatAsState(if (s.folded) 0.45f else 1f, tween(300), label = "heroFold")
    // cards
    val hole = s.hole
    if (hole != null && !s.busted) {
        val cardsCenter = Offset(g.cx, g.heroCardsTop + g.heroCardH / 2)
        Box(Modifier.fillMaxSize().centerAt(cardsCenter)) {
            DealIn(t.handKey, dealDelay, Offset(g.cx - cardsCenter.x, g.cy - cardsCenter.y), animateDeal) {
                Box(Modifier.graphicsLayer { translationY = lift.dp.toPx(); alpha = cardsAlpha }.size(g.heroCardsW.dp, g.heroCardH.dp)) {
                    val dim = t.highlight.isNotEmpty()
                    Box(Modifier.graphicsLayer { rotationZ = -5f }) {
                        PlayingCard(hole[0], g.heroCardW.dp, dim && hole[0] !in t.highlight)
                    }
                    Box(Modifier.offset(x = (g.heroCardW * 0.8f).dp).graphicsLayer { rotationZ = 5f }) {
                        PlayingCard(hole[1], g.heroCardW.dp, dim && hole[1] !in t.highlight)
                    }
                }
            }
        }
        // badges to the right of the cards
        val badgeAnchor = Offset(g.cx + g.heroCardsW / 2 + 52f, g.heroCardsTop + g.heroCardH / 2 + 8f)
        Box(Modifier.fillMaxSize().centerAt(badgeAnchor)) {
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ActionBadge(s.handName ?: s.action, s.actKind, gold = s.handName != null)
                WonOrEquity(s, fmt)
            }
        }
    }
    // pod
    Box(Modifier.fillMaxSize().centerAt(g.heroPod)) {
        Box {
            PodFrame(s, pulse, Modifier.size((g.podW + 16).dp, g.podH.dp)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (s.position.isNotEmpty()) {
                            Spacer(Modifier.width(5.dp))
                            Text(s.position, color = Palette.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        if (s.busted) "—" else fmt(s.stack), color = if (s.allIn) Palette.Red else Palette.Gold,
                        fontSize = 19.sp, lineHeight = 20.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                }
            }
            if (s.isButton) Box(Modifier.align(Alignment.CenterStart).offset(x = (-22).dp)) { DealerButton() }
        }
    }
}

/** A bet that slides out of the seat, then into the pot when the street ends. */
@Composable
private fun BetChip(amount: Int, seat: Offset, spot: Offset, pot: Offset, fmt: (Int) -> String) {
    val pos = remember { Animatable(seat, Offset.VectorConverter) }
    val alpha = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(amount, spot) {
        if (amount > 0) {
            if (shown == 0) {
                pos.snapTo(seat)
                alpha.snapTo(0f)
            }
            shown = amount
            launch { alpha.animateTo(1f, tween(160)) }
            pos.animateTo(spot, tween(320, easing = FastOutSlowInEasing))
        } else if (shown > 0) {
            launch { pos.animateTo(pot, tween(380, easing = FastOutSlowInEasing)) }
            alpha.animateTo(0f, tween(420, easing = LinearEasing))
            shown = 0
        }
    }
    if (shown > 0) {
        Box(Modifier.fillMaxSize().centerAt(pos.value).alpha(alpha.value)) {
            ChipLabel(fmt(shown))
        }
    }
}

@Composable
private fun WinChip(key: Long, amount: Int, pot: Offset, seat: Offset, fmt: (Int) -> String) {
    val pos = remember(key, amount) { Animatable(pot, Offset.VectorConverter) }
    val alpha = remember(key, amount) { Animatable(1f) }
    LaunchedEffect(key, amount) {
        pos.animateTo(seat, tween(600, easing = FastOutSlowInEasing))
        alpha.animateTo(0f, tween(250))
    }
    if (alpha.value > 0f) {
        Box(Modifier.fillMaxSize().centerAt(pos.value).alpha(alpha.value)) {
            ChipLabel(fmt(amount), color = Palette.Green)
        }
    }
}
