package com.sukistaite.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sukistaite.reader.data.AppSettings
import com.sukistaite.reader.data.SettingsStore
import com.sukistaite.reader.data.VolumeKeyBus
import com.sukistaite.reader.ui.AboutPage
import com.sukistaite.reader.ui.BookmarkPage
import com.sukistaite.reader.ui.ChaptersPage
import com.sukistaite.reader.ui.ReaderPage
import com.sukistaite.reader.ui.SearchPage
import com.sukistaite.reader.ui.SettingsPage
import com.sukistaite.reader.ui.StatsPage
import com.sukistaite.reader.ui.components.BackgroundLayer
import com.sukistaite.reader.ui.theme.AppFonts
import com.sukistaite.reader.ui.theme.SukiReaderTheme

class MainActivity : ComponentActivity() {
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // 音量键翻页：仅当音量键翻页开启时拦截（阅读器内）
        if (VolumeKeyBus.enabled) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> { VolumeKeyBus.push(1); return true }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> { VolumeKeyBus.push(2); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore(applicationContext)
        setContent {
            val settings by store.settings.collectAsState(initial = AppSettings())
            SukiReaderTheme(settings) {
                Surface(Modifier.fillMaxSize()) {
                    SukiReaderApp(settings, store)
                }
            }
        }
    }
}

data class NavItem(val route: String, val label: String)

val NAV_ITEMS = listOf(
    NavItem("chapters", "章节"),
    NavItem("search", "搜索"),
    NavItem("bookmarks", "书签"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SukiReaderApp(settings: AppSettings, store: SettingsStore) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val font = AppFonts.resolve(settings.fontFamily)

    val inReader = currentRoute == "reader/{chapterId}"
    val inSub = inReader || currentRoute == "settings" || currentRoute == "about" || currentRoute == "stats"

    // 全局背景层（设置里自定义的照片/视频）
    Box(Modifier.fillMaxSize()) {
        BackgroundLayer(
            bgUri = settings.bgUri,
            bgIsVideo = settings.bgIsVideo,
            opacity = settings.bgOpacity,
            modifier = Modifier.fillMaxSize()
        )
        AppNavHost(
            navController = navController,
            settings = settings,
            store = store,
            font = font,
            modifier = Modifier.fillMaxSize()
        )
    }

    // 底部导航（阅读器和子页面里隐藏）
    if (!inSub) {
        Box(Modifier.fillMaxSize()) {
            NavigationBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                NAV_ITEMS.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            when (item.route) {
                                "chapters" -> Icon(Icons.AutoMirrored.Filled.MenuBook, null)
                                "search" -> Icon(Icons.Default.Search, null)
                                else -> Icon(Icons.Default.Bookmark, null)
                            }
                        },
                        label = { Text(item.label, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    settings: AppSettings,
    store: SettingsStore,
    font: FontFamily?,
    modifier: Modifier = Modifier
) {
    val anim: Boolean = settings.animations
    NavHost(
        navController = navController,
        startDestination = "chapters",
        modifier = modifier,
        enterTransition = {
            if (anim) slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260))
            else fadeIn(tween(0))
        },
        exitTransition = {
            if (anim) fadeOut(tween(200)) else fadeOut(tween(0))
        },
        popEnterTransition = {
            if (anim) fadeIn(tween(220)) else fadeIn(tween(0))
        },
        popExitTransition = {
            if (anim) slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(220))
            else fadeOut(tween(0))
        }
    ) {
        composable("chapters") { ChaptersPage(onOpen = { navController.navigate("reader/$it") }, onSettings = { navController.navigate("settings") }) }
        composable("search") {
            SearchPage(
                settings = settings,
                settingsStore = store,
                onJump = { line, kw -> navController.navigate("reader/line-$line?hl=$kw") }
            )
        }
        composable("bookmarks") { BookmarkPage(onOpen = { navController.navigate("reader/$it") }) }
        composable("reader/{chapterId}?hl={hl}") { entry ->
            val id = entry.arguments?.getString("chapterId") ?: "0"
            ReaderPage(
                chapterId = id,
                settings = settings,
                settingsStore = store,
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate("settings") },
                onJumpToLine = { line -> navController.navigate("reader/${line + 1}") }
            )
        }
        composable("settings") { SettingsPage(onBack = { navController.popBackStack() }, onAbout = { navController.navigate("about") }, onStats = { navController.navigate("stats") }) }
        composable("stats") { StatsPage(onBack = { navController.popBackStack() }) }
        composable("about") { AboutPage(onBack = { navController.popBackStack() }) }
    }
}
