package com.riverrunner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.i18n.Strings

@Composable
fun RiverTheme(strings: Strings, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Palette.Gold, background = Palette.Bg, surface = Palette.Panel)) {
        CompositionLocalProvider(LocalStrings provides strings) { content() }
    }
}
