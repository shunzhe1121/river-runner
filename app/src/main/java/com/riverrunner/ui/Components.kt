package com.riverrunner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object Palette {
    val Bg = Color(0xFF0E1512)
    val Panel = Color(0xFF17211C)
    val PanelHi = Color(0xFF223029)
    val FeltCenter = Color(0xFF1F6B47)
    val FeltEdge = Color(0xFF0F3D28)
    val Rail = Color(0xFF3A2A1C)
    val Gold = Color(0xFFE2B34A)
    val GoldDim = Color(0xFF8C6E2C)
    val Text = Color(0xFFEDEFE9)
    val TextDim = Color(0xFF9AA59E)
    val Red = Color(0xFFD9534F)
    val Green = Color(0xFF4CAF7A)
    val Blue = Color(0xFF4F8FD9)
    val FoldBtn = Color(0xFF5B2E2B)
    val CallBtn = Color(0xFF24543C)
    val RaiseBtn = Color(0xFF8A6A20)
}

private fun suitColor(suit: Int): Color = when (suit) {
    0 -> Color(0xFF1E7D3A)  // clubs: green (4-colour deck)
    1 -> Color(0xFF1F5FBF)  // diamonds: blue
    2 -> Color(0xFFC62828)  // hearts: red
    else -> Color(0xFF1A1A1A) // spades: black
}

private val SUIT_SYMBOL = charArrayOf('♣', '♦', '♥', '♠')

@Composable
fun PlayingCard(card: Int?, width: Dp, faceUp: Boolean = true, dim: Boolean = false) {
    val h = width * 1.4f
    val shape = RoundedCornerShape(width * 0.12f)
    if (card == null || !faceUp) {
        Box(
            Modifier.size(width, h).clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF7A1F2B), Color(0xFF4A1018))))
                .border(1.5.dp, Color(0xFFE8D9B0).copy(alpha = 0.7f), shape)
        ) {
            Canvas(Modifier.fillMaxSize().padding(width * 0.12f)) {
                val step = size.width / 4
                var x = -size.height
                while (x < size.width) {
                    drawLine(Color(0x33FFFFFF), Offset(x, size.height), Offset(x + size.height, 0f), 1.5f)
                    x += step
                }
                drawRoundRect(Color(0x55FFFFFF), style = Stroke(1.2f), size = Size(size.width, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
            }
        }
        return
    }
    val color = suitColor(Cards.suit(card))
    Box(
        Modifier.size(width, h).clip(shape)
            .background(if (dim) Color(0xFFB9BCB6) else Color(0xFFFAF8F2))
            .border(1.dp, Color(0x33000000), shape)
    ) {
        Column(Modifier.padding(start = width * 0.1f, top = width * 0.04f)) {
            Text(
                Cards.RANKS[Cards.rank(card)].let { if (it == 'T') "10" else it.toString() },
                color = color, fontWeight = FontWeight.Bold,
                fontSize = (width.value * 0.42f).sp, lineHeight = (width.value * 0.46f).sp,
            )
            Text(SUIT_SYMBOL[Cards.suit(card)].toString(), color = color, fontSize = (width.value * 0.34f).sp, lineHeight = (width.value * 0.36f).sp)
        }
        Text(
            SUIT_SYMBOL[Cards.suit(card)].toString(), color = color.copy(alpha = 0.9f),
            fontSize = (width.value * 0.62f).sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = width * 0.08f),
        )
    }
}

@Composable
fun ChipLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xCC0B120E)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Palette.Gold).border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Palette.Text, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun SeatBox(s: SeatView, fmt: (Int) -> String, cardWidth: Dp, showStyle: String? = null) {
    val borderColor = when {
        s.won > 0 -> Palette.Gold
        s.isActing -> Palette.Gold.copy(alpha = 0.9f)
        else -> Color(0x33FFFFFF)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // cards
        Box(Modifier.height(cardWidth * 1.4f)) {
            if (s.hole != null && !s.busted && !(s.folded && !s.isHero && !s.faceUp)) {
                Box(Modifier.width(cardWidth * 1.75f)) {
                    PlayingCard(s.hole[0], cardWidth, s.faceUp, dim = s.folded)
                    Box(Modifier.offset(x = cardWidth * 0.75f)) {
                        PlayingCard(s.hole[1], cardWidth, s.faceUp, dim = s.folded)
                    }
                }
            }
        }
        Box(
            Modifier.offset(y = (-6).dp).widthIn(min = 84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (s.folded || s.busted) Color(0xE0121A16) else Color(0xF0182520))
                .border(if (s.isActing || s.won > 0) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.isButton) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Text("D", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        s.name, color = if (s.folded || s.busted) Palette.TextDim else Palette.Text,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 80.dp),
                    )
                    if (s.position.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Text(s.position, color = Palette.TextDim, fontSize = 9.sp)
                    }
                }
                Text(
                    if (s.busted) "—" else fmt(s.stack) + if (s.allIn && !s.busted) " ●" else "",
                    color = if (s.allIn) Palette.Red else Palette.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                val sub = s.handName ?: s.bubble ?: showStyle
                if (sub != null) {
                    Text(sub, color = if (s.handName != null) Palette.Gold else Palette.TextDim, fontSize = 10.sp, maxLines = 1)
                }
                if (s.won > 0) Text("+" + fmt(s.won), color = Palette.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (s.equity != null) Text("%.1f%%".format(s.equity * 100), color = Palette.Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Draws the felt, the board and every seat on an ellipse with the hero at the bottom.
 */
@Composable
fun PokerTable(
    view: TableView,
    fmt: (Int) -> String,
    centerText: String?,
    modifier: Modifier = Modifier,
    styleLabel: (SeatView) -> String? = { null },
) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        val n = view.seats.size
        val cardW = if (n >= 7) 30.dp else 34.dp
        Canvas(Modifier.fillMaxSize()) {
            val rx = size.width * 0.44f
            val ry = size.height * 0.36f
            val c = Offset(size.width / 2, size.height * 0.47f)
            drawOval(Palette.Rail, topLeft = Offset(c.x - rx - 10f, c.y - ry - 10f), size = Size((rx + 10f) * 2, (ry + 10f) * 2))
            drawOval(
                Brush.radialGradient(listOf(Palette.FeltCenter, Palette.FeltEdge), center = c, radius = rx),
                topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2, ry * 2),
            )
            drawOval(Color(0x22FFFFFF), topLeft = Offset(c.x - rx * 0.86f, c.y - ry * 0.80f), size = Size(rx * 1.72f, ry * 1.6f), style = Stroke(2f))
        }
        // board + pot in the middle
        Column(
            Modifier.align(Alignment.Center).offset(y = -(h * 0.03f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (view.pot > 0) {
                ChipLabel(LocalPot.current + " " + fmt(view.pot))
                Spacer(Modifier.height(4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 5) {
                    val c = view.board.getOrNull(i)
                    if (c != null) PlayingCard(c, 38.dp)
                    else Box(Modifier.size(38.dp, 53.dp).clip(RoundedCornerShape(5.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(5.dp)))
                }
            }
            if (centerText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    centerText, color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        // seats: hero (seat 0) at the bottom, others clockwise
        val heroIndex = view.seats.indexOfFirst { it.isHero }.coerceAtLeast(0)
        for ((i, s) in view.seats.withIndex()) {
            val rel = (i - heroIndex + n) % n
            val angle = Math.toRadians(90.0 + rel * 360.0 / n)
            val rx = w.value * 0.44
            val ry = h.value * 0.40
            val cx = w.value / 2 + rx * cos(angle)
            val cy = h.value * 0.47 + ry * sin(angle)
            val boxW = 110f
            val boxH = if (s.isHero) 100f else 92f
            val x = (cx - boxW / 2).coerceIn(0.0, (w.value - boxW).toDouble())
            val y = (cy - boxH / 2 - 8).coerceIn(0.0, (h.value - boxH).toDouble())
            Box(
                Modifier.offset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }.width(boxW.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                SeatBox(s, fmt, if (s.isHero) cardW + 6.dp else cardW, styleLabel(s))
            }
            // bet in front of the seat, towards the centre
            if (s.streetBet > 0) {
                val bx = w.value / 2 + rx * 0.58 * cos(angle)
                val by = h.value * 0.47 + ry * 0.52 * sin(angle)
                Box(Modifier.offset { IntOffset((bx - 30).dp.roundToPx(), (by - 10).dp.roundToPx()) }) {
                    ChipLabel(fmt(s.streetBet))
                }
            }
        }
    }
}

/** "Pot" label, provided from the current language. */
val LocalPot = androidx.compose.runtime.staticCompositionLocalOf { "Pot" }

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Gold else Palette.PanelHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color(0xFF1A1405) else Palette.Text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ActionButton(text: String, color: Color, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color else color.copy(alpha = 0.3f))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Palette.Text else Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 15.sp)
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(if (highlight) Palette.GoldDim else Palette.PanelHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Palette.Text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Palette.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}
