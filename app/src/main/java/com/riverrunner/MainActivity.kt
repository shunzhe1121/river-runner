package com.riverrunner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riverrunner.ui.AppViewModel
import com.riverrunner.ui.GuideScreen
import com.riverrunner.ui.SavesScreen
import com.riverrunner.ui.SetupScreen
import com.riverrunner.ui.AchievementsScreen
import com.riverrunner.ui.UnlockToast
import androidx.compose.ui.Alignment
import com.riverrunner.ui.HistoryScreen
import com.riverrunner.ui.HomeScreen
import com.riverrunner.ui.Palette
import com.riverrunner.ui.ReplayScreen
import com.riverrunner.ui.ResultScreen
import com.riverrunner.ui.RiverTheme
import com.riverrunner.ui.Screen
import com.riverrunner.ui.SettingsScreen
import com.riverrunner.ui.StatsScreen
import com.riverrunner.ui.TableScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val vm: AppViewModel = viewModel()
            RiverTheme(vm.strings) {
                run {
                    Box(Modifier.fillMaxSize().background(Palette.Bg).windowInsetsPadding(WindowInsets.displayCutout)) {
                        when (val s = vm.screen) {
                            Screen.Home -> HomeScreen(vm)
                            Screen.Table -> TableScreen(vm)
                            is Screen.Result -> ResultScreen(vm, s.summary)
                            Screen.History -> HistoryScreen(vm)
                            is Screen.Replay -> ReplayScreen(vm, s.record)
                            Screen.Stats -> StatsScreen(vm)
                            Screen.Settings -> SettingsScreen(vm)
                            Screen.Guide -> GuideScreen(vm)
                            Screen.Saves -> SavesScreen(vm)
                            Screen.Achievements -> AchievementsScreen(vm)
                            Screen.Setup -> SetupScreen(vm)
                        }
                        UnlockToast(vm.unlockToast, Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}
