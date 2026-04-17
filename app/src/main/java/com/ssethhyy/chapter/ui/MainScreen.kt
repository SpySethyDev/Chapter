package com.ssethhyy.chapter.ui

import androidx.compose.animation.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.ssethhyy.chapter.data.repository.SettingsRepository
import com.ssethhyy.chapter.ui.library.HomeScreen
import com.ssethhyy.chapter.ui.library.LibraryScreen
import com.ssethhyy.chapter.ui.library.LibraryViewModel
import com.ssethhyy.chapter.ui.player.MiniPlayer
import com.ssethhyy.chapter.ui.player.PlayerScreen
import com.ssethhyy.chapter.ui.player.PlayerViewModel
import com.ssethhyy.chapter.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Rounded.Home)
    object Library : Screen("library", "Library", Icons.AutoMirrored.Rounded.LibraryBooks)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isPlayerScreen = currentDestination?.route?.startsWith("player/") == true
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val libraryViewModel: LibraryViewModel = viewModel()
    val playerViewModel: PlayerViewModel = viewModel()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val allBooks by libraryViewModel.allBooks.collectAsState()

    val items = listOf(
        Screen.Home,
        Screen.Library,
    )

    // Handle initial book restoration
    val lastBookId by settingsRepository.lastPlayedBookId.collectAsState(initial = null)
    var hasRestored by remember { mutableStateOf(false) }
    
    LaunchedEffect(lastBookId, allBooks) {
        if (!hasRestored && lastBookId != null && allBooks.isNotEmpty()) {
            val book = allBooks.find { it.id == lastBookId }
            if (book != null) {
                playerViewModel.loadBook(book, false)
                hasRestored = true
            }
        }
    }

    SharedTransitionLayout {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (currentDestination?.route != "settings") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    ) {
                        Column {
                            // Floating Mini Player
                            AnimatedVisibility(
                                visible = playerUiState.book != null && !isPlayerScreen,
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut()
                            ) {
                                MiniPlayer(
                                    viewModel = playerViewModel,
                                    onExpand = {
                                        playerUiState.book?.let {
                                            navController.navigate("player/${it.id}")
                                        }
                                    },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedVisibility
                                )
                            }

                            if (!isPlayerScreen) {
                                NavigationBar(
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp
                                ) {
                                    items.forEach { screen ->
                                        NavigationBarItem(
                                            icon = { Icon(screen.icon, contentDescription = null) },
                                            label = { Text(screen.label) },
                                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                            onClick = {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onBookClick = { book, prefix ->
                            playerViewModel.loadBook(book, true)
                            navController.navigate("player/${book.id}?sharedKeyPrefix=$prefix")
                        },
                        onSettingsClick = {
                            navController.navigate("settings")
                        },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }
                composable(Screen.Library.route) {
                    LibraryScreen(
                        onBookClick = { book ->
                            playerViewModel.loadBook(book, true)
                            navController.navigate("player/${book.id}?sharedKeyPrefix=lib")
                        },
                        onSettingsClick = {
                            navController.navigate("settings")
                        },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }
                composable(
                    route = "player/{bookId}?sharedKeyPrefix={sharedKeyPrefix}",
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.LongType },
                        navArgument("sharedKeyPrefix") { 
                            type = NavType.StringType
                            defaultValue = "mini"
                        }
                    )
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getLong("bookId")
                    val sharedKeyPrefix = backStackEntry.arguments?.getString("sharedKeyPrefix") ?: "mini"
                    val books by libraryViewModel.allBooks.collectAsState()
                    val book = books.find { it.id == bookId }

                    if (book != null) {
                        PlayerScreen(
                            book = book,
                            sharedKeyPrefix = sharedKeyPrefix,
                            onDismiss = { navController.popBackStack() },
                            viewModel = playerViewModel,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    } else if (books.isNotEmpty()) {
                        LaunchedEffect(Unit) {
                            navController.popBackStack()
                        }
                    }
                }
                composable("settings") {
                    SettingsScreen(
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

