package com.sukistaite.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sukistaite.reader.ui.BookmarkPage
import com.sukistaite.reader.ui.ChaptersPage
import com.sukistaite.reader.ui.ReaderPage
import com.sukistaite.reader.ui.SearchPage
import com.sukistaite.reader.ui.theme.SukiReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SukiReaderTheme {
                SukiReaderApp()
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
fun SukiReaderApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute?.startsWith("reader") != true) {
                NavigationBar {
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
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "chapters",
            modifier = Modifier.padding(padding)
        ) {
            composable("chapters") { ChaptersPage(onOpen = { navController.navigate("reader/$it") }) }
            composable("search") { SearchPage(onJump = { line -> navController.navigate("reader/line-$line") }) }
            composable("bookmarks") { BookmarkPage(onOpen = { navController.navigate("reader/$it") }) }
            composable("reader/{chapterId}") { entry ->
                val id = entry.arguments?.getString("chapterId") ?: "0"
                ReaderPage(chapterId = id, onBack = { navController.popBackStack() })
            }
        }
    }
}
