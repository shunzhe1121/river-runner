package com.riverrunner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp

/** How card faces are coloured. */
enum class DeckStyle(val code: String) {
    /** ♣ green, ♦ blue, ♥ red, ♠ black on white cards */
    FOUR_COLOR("4"),
    /** whole card face tinted by suit, white symbols */
    FOUR_COLOR_FILLED("F"),
    /** traditional red / black */
    CLASSIC("2");

    companion object { fun fromCode(c: String?) = entries.firstOrNull { it.code == c } ?: FOUR_COLOR }
}

val LocalDeckStyle = staticCompositionLocalOf { DeckStyle.FOUR_COLOR }

/** Ink colour of a suit in the 4-colour deck (also the face colour of filled cards). */
fun fourColor(suit: Int): Color = when (suit) {
    0 -> Color(0xFF15924A)  // clubs: clear grass green (old dark green read as black)
    1 -> Color(0xFF1D63D6)  // diamonds: blue
    2 -> Color(0xFFD32F2F)  // hearts: red
    else -> Color(0xFF16181A) // spades: black
}

fun classicColor(suit: Int): Color = if (suit == 1 || suit == 2) Color(0xFFD32F2F) else Color(0xFF16181A)

/**
 * Suits drawn as shapes instead of font glyphs, so ♣ (three round lobes) and ♠ (one pointed leaf)
 * stay distinct at any size. Coordinates are in a unit square.
 */
@Composable
fun SuitIcon(suit: Int, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val ox = (this.size.width - s) / 2
        val oy = (this.size.height - s) / 2
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        when (suit) {
            0 -> { // clubs
                val r = 0.235f * s
                drawCircle(color, r, p(0.5f, 0.27f))
                drawCircle(color, r, p(0.25f, 0.58f))
                drawCircle(color, r, p(0.75f, 0.58f))
                drawCircle(color, 0.14f * s, p(0.5f, 0.52f))
                drawPath(Path().apply {
                    moveTo(p(0.5f, 0.55f).x, p(0.5f, 0.55f).y)
                    quadraticTo(p(0.53f, 0.85f).x, p(0.53f, 0.85f).y, p(0.68f, 1f).x, p(0.68f, 1f).y)
                    lineTo(p(0.32f, 1f).x, p(0.32f, 1f).y)
                    quadraticTo(p(0.47f, 0.85f).x, p(0.47f, 0.85f).y, p(0.5f, 0.55f).x, p(0.5f, 0.55f).y)
                    close()
                }, color)
            }
            1 -> drawPath(Path().apply { // diamonds
                moveTo(p(0.5f, 0f).x, p(0.5f, 0f).y)
                quadraticTo(p(0.7f, 0.28f).x, p(0.7f, 0.28f).y, p(0.9f, 0.5f).x, p(0.9f, 0.5f).y)
                quadraticTo(p(0.7f, 0.72f).x, p(0.7f, 0.72f).y, p(0.5f, 1f).x, p(0.5f, 1f).y)
                quadraticTo(p(0.3f, 0.72f).x, p(0.3f, 0.72f).y, p(0.1f, 0.5f).x, p(0.1f, 0.5f).y)
                quadraticTo(p(0.3f, 0.28f).x, p(0.3f, 0.28f).y, p(0.5f, 0f).x, p(0.5f, 0f).y)
                close()
            }, color)
            2 -> drawPath(Path().apply { // hearts
                moveTo(p(0.5f, 0.98f).x, p(0.5f, 0.98f).y)
                cubicTo(p(0.2f, 0.74f).x, p(0.2f, 0.74f).y, p(0f, 0.55f).x, p(0f, 0.55f).y, p(0f, 0.32f).x, p(0f, 0.32f).y)
                cubicTo(p(0f, 0.13f).x, p(0f, 0.13f).y, p(0.14f, 0.02f).x, p(0.14f, 0.02f).y, p(0.28f, 0.02f).x, p(0.28f, 0.02f).y)
                cubicTo(p(0.4f, 0.02f).x, p(0.4f, 0.02f).y, p(0.47f, 0.1f).x, p(0.47f, 0.1f).y, p(0.5f, 0.19f).x, p(0.5f, 0.19f).y)
                cubicTo(p(0.53f, 0.1f).x, p(0.53f, 0.1f).y, p(0.6f, 0.02f).x, p(0.6f, 0.02f).y, p(0.72f, 0.02f).x, p(0.72f, 0.02f).y)
                cubicTo(p(0.86f, 0.02f).x, p(0.86f, 0.02f).y, p(1f, 0.13f).x, p(1f, 0.13f).y, p(1f, 0.32f).x, p(1f, 0.32f).y)
                cubicTo(p(1f, 0.55f).x, p(1f, 0.55f).y, p(0.8f, 0.74f).x, p(0.8f, 0.74f).y, p(0.5f, 0.98f).x, p(0.5f, 0.98f).y)
                close()
            }, color)
            else -> { // spades
                drawPath(Path().apply {
                    moveTo(p(0.5f, 0f).x, p(0.5f, 0f).y)
                    cubicTo(p(0.62f, 0.16f).x, p(0.62f, 0.16f).y, p(1f, 0.36f).x, p(1f, 0.36f).y, p(1f, 0.6f).x, p(1f, 0.6f).y)
                    cubicTo(p(1f, 0.77f).x, p(1f, 0.77f).y, p(0.83f, 0.86f).x, p(0.83f, 0.86f).y, p(0.69f, 0.85f).x, p(0.69f, 0.85f).y)
                    cubicTo(p(0.6f, 0.84f).x, p(0.6f, 0.84f).y, p(0.54f, 0.79f).x, p(0.54f, 0.79f).y, p(0.5f, 0.72f).x, p(0.5f, 0.72f).y)
                    cubicTo(p(0.46f, 0.79f).x, p(0.46f, 0.79f).y, p(0.4f, 0.84f).x, p(0.4f, 0.84f).y, p(0.31f, 0.85f).x, p(0.31f, 0.85f).y)
                    cubicTo(p(0.17f, 0.86f).x, p(0.17f, 0.86f).y, p(0f, 0.77f).x, p(0f, 0.77f).y, p(0f, 0.6f).x, p(0f, 0.6f).y)
                    cubicTo(p(0f, 0.36f).x, p(0f, 0.36f).y, p(0.38f, 0.16f).x, p(0.38f, 0.16f).y, p(0.5f, 0f).x, p(0.5f, 0f).y)
                    close()
                }, color)
                drawPath(Path().apply {
                    moveTo(p(0.5f, 0.66f).x, p(0.5f, 0.66f).y)
                    quadraticTo(p(0.53f, 0.88f).x, p(0.53f, 0.88f).y, p(0.68f, 1f).x, p(0.68f, 1f).y)
                    lineTo(p(0.32f, 1f).x, p(0.32f, 1f).y)
                    quadraticTo(p(0.47f, 0.88f).x, p(0.47f, 0.88f).y, p(0.5f, 0.66f).x, p(0.5f, 0.66f).y)
                    close()
                }, color)
            }
        }
    }
}
