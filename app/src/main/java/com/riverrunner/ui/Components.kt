package com.riverrunner.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards

object Palette {
    val Bg = Color(0xFF0B1210)
    val Panel = Color(0xFF141E19)
    val PanelHi = Color(0xFF1F2C25)
    val PanelHi2 = Color(0xFF2A3A31)
    val FeltCenter = Color(0xFF1C6B45)
    val FeltEdge = Color(0xFF0D3A25)
    val Rail = Color(0xFF2B1D12)
    val RailHi = Color(0xFF4A3220)
    val Gold = Color(0xFFE9B949)
    val GoldDim = Color(0xFF8F7130)
    val Text = Color(0xFFEFF2EC)
    val TextDim = Color(0xFF9AA59E)
    val Red = Color(0xFFE0605A)
    val Green = Color(0xFF55C189)
    val Blue = Color(0xFF5B9BE6)
    val FoldBtn = Color(0xFF6B2F2B)
    val CallBtn = Color(0xFF226546)
    val RaiseBtn = Color(0xFFA77B1E)
    val AllInBtn = Color(0xFF9A3322)
}

fun actColor(k: ActKind?): Color = when (k) {
    ActKind.FOLD -> Color(0xFF5A615D)
    ActKind.CHECK -> Color(0xFF3F7FC4)
    ActKind.CALL -> Color(0xFF2E8B5A)
    ActKind.BET -> Color(0xFFC08A1E)
    ActKind.ALLIN -> Color(0xFFC0392B)
    ActKind.POST, null -> Color(0xFF3A4741)
}

private fun suitColor(suit: Int): Color = when (suit) {
    0 -> Color(0xFF1E7D3A)  // clubs: green (4-colour deck)
    1 -> Color(0xFF1F5FBF)  // diamonds: blue
    2 -> Color(0xFFC62828)  // hearts: red
    else -> Color(0xFF1A1A1A) // spades: black
}

private val SUIT_SYMBOL = charArrayOf('♣', '♦', '♥', '♠')

fun rankText(card: Int): String = Cards.RANKS[Cards.rank(card)].let { if (it == 'T') "10" else it.toString() }
fun suitText(card: Int): String = SUIT_SYMBOL[Cards.suit(card)].toString()

/** Card face (or back when [card] is null). Height = 1.4 x width. */
@Composable
fun PlayingCard(card: Int?, width: Dp, dim: Boolean = false, modifier: Modifier = Modifier) {
    val h = width * 1.4f
    val shape = RoundedCornerShape(width * 0.12f)
    if (card == null) {
        CardBack(width, modifier)
        return
    }
    val color = suitColor(Cards.suit(card))
    Box(
        modifier.size(width, h).clip(shape)
            .background(if (dim) Color(0xFF9EA39C) else Color(0xFFFBF9F3))
            .border(1.dp, Color(0x40000000), shape)
    ) {
        Column(Modifier.padding(start = width * 0.09f, top = width * 0.02f)) {
            Text(
                rankText(card), color = color, fontWeight = FontWeight.Bold,
                fontSize = (width.value * 0.44f).sp, lineHeight = (width.value * 0.48f).sp,
            )
            Text(suitText(card), color = color, fontSize = (width.value * 0.34f).sp, lineHeight = (width.value * 0.36f).sp)
        }
        Text(
            suitText(card), color = color.copy(alpha = 0.92f),
            fontSize = (width.value * 0.62f).sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = width * 0.07f),
        )
    }
}

@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.12f)
    Box(
        modifier.size(width, width * 1.4f).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF8A2232), Color(0xFF4B0F1A))))
            .border(1.dp, Color(0xFFE8D9B0).copy(alpha = 0.75f), shape)
    ) {
        Canvas(Modifier.fillMaxSize().padding(width * 0.1f)) {
            val step = size.width / 3.5f
            var x = -size.height
            while (x < size.width) {
                drawLine(Color(0x30FFFFFF), Offset(x, size.height), Offset(x + size.height, 0f), 1.2f)
                x += step
            }
            drawRoundRect(Color(0x60FFFFFF), style = Stroke(1f), size = Size(size.width, size.height), cornerRadius = CornerRadius(3f, 3f))
        }
    }
}

/** Small chip stack + amount. */
@Composable
fun ChipLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.Gold) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xD9091310))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(start = 4.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(color).border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Adds a small "press down" scale to any clickable. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(stiffness = 900f), label = "press")
    return this.scale(scale).clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.pressable(onClick = onClick).clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Gold else Palette.PanelHi)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color(0xFF1A1405) else Palette.Text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sub: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier.pressable(enabled, onClick).clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) Brush.verticalGradient(listOf(color.copy(alpha = 1f), lerpDark(color)))
                else Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.3f)))
            )
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = if (enabled) Palette.Text else Palette.TextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
            if (sub != null) Text(sub, color = Palette.Text.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

private fun lerpDark(c: Color) = Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, c.alpha)

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Box(
        modifier.pressable(onClick = onClick).clip(RoundedCornerShape(8.dp))
            .background(if (highlight) Palette.GoldDim else Palette.PanelHi)
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
