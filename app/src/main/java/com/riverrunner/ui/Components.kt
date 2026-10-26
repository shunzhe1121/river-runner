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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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

/**
 * Colour tokens. Rule: Gold = chips and money only; Accent (river cyan) = you, selection and
 * anything interactive; Green/Red = results and the call/fold actions. See DESIGN.md.
 */
object Palette {
    val Bg = Color(0xFF0C141C)        // ink
    val Panel = Color(0xFF142231)     // slate
    val PanelHi = Color(0xFF1C2E40)   // raised slate
    val PanelHi2 = Color(0xFF27405A)  // tracks, inactive controls
    val Line = Color(0xFF3D6080)      // outlines that must read as a control
    val FeltCenter = Color(0xFF137A72) // river teal
    val FeltEdge = Color(0xFF09393A)
    val Rail = Color(0xFF1E140D)      // walnut
    val RailHi = Color(0xFF4A3322)
    val Gold = Color(0xFFE3B04B)      // brass: money only
    val GoldDim = Color(0xFF8A6A2C)
    val Accent = Color(0xFF46D3E6)    // river cyan: you / interactive
    val AccentDim = Color(0xFF1E5C6B)
    val Text = Color(0xFFEAF0F4)      // chalk
    val TextDim = Color(0xFF9FB2C3)   // mist
    val Red = Color(0xFFF06A63)
    val Green = Color(0xFF3FCB86)
    val Blue = Color(0xFF5B9BE6)
    val FoldBtn = Color(0xFFB23B35)
    val CallBtn = Color(0xFF1C7A51)
    val RaiseBtn = Color(0xFFE08A2E)
    val AllInBtn = Color(0xFFA82C22)
    val Ink = Color(0xFF0C141C)
}

/** Condensed numerals for chip amounts and scores (Barlow Condensed, SIL OFL). */
val Numeric = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(com.riverrunner.R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(com.riverrunner.R.font.barlow_condensed_bold, FontWeight.Bold),
)

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
        Text(text, color = Palette.Text, fontSize = 13.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
        modifier.pressable(onClick = onClick).heightIn(min = 38.dp).clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Accent else Palette.PanelHi)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Palette.Ink else Palette.Text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sub: String? = null,
    textColor: Color = Palette.Text,
    onClick: () -> Unit,
) {
    Box(
        modifier.pressable(enabled, onClick).clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) Brush.verticalGradient(listOf(color.copy(alpha = 1f), lerpDark(color)))
                else Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.3f)))
            )
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = if (enabled) textColor else Palette.TextDim, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
            if (sub != null) Text(sub, color = textColor.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 16.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private fun lerpDark(c: Color) = Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, c.alpha)

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Box(
        modifier.pressable(onClick = onClick).heightIn(min = 40.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(8.dp))
            .background(if (highlight) Palette.AccentDim else Palette.PanelHi)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Palette.Text, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}
