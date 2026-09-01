package com.earlln.pianocode.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.earlln.pianocode.BuildConfig
import com.earlln.pianocode.music.Note
import com.earlln.pianocode.ui.screens.AboutScreen
import com.earlln.pianocode.ui.screens.ChordDetailScreen
import com.earlln.pianocode.ui.screens.ChordListScreen
import com.earlln.pianocode.ui.screens.HomeScreen
import com.earlln.pianocode.ui.screens.ScaleScreen
import com.earlln.pianocode.ui.screens.SettingsScreen
import com.earlln.pianocode.ui.screens.SheetConverterScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PianoCodeApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destination = Destination.entries.firstOrNull { it.route == currentRoute }
    val isDetail = currentRoute?.startsWith("chord/") == true

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text(
                    "PianoCode",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
                Text(
                    "피아노 코드 사전 & 악보 코드 변환기",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Destination.entries.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = {
                            Column {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        selected = item.route == currentRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (item.route != currentRoute) {
                                navController.navigate(item.route) {
                                    popUpTo(Destination.HOME.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Text(
                    "버전 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(destination?.title ?: "코드 상세")
                            Text(
                                "PianoCode v${BuildConfig.VERSION_NAME} by Earlln.com",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        if (isDetail) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "메뉴 열기")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.HOME.route,
        modifier = modifier,
    ) {
        composable(Destination.HOME.route) {
            HomeScreen(
                contentPadding = contentPadding,
                onNavigate = { navController.navigate(it.route) { launchSingleTop = true } },
            )
        }
        composable(Destination.CHORDS.route) {
            ChordListScreen(
                contentPadding = contentPadding,
                onChordClick = { chord ->
                    navController.navigate(Routes.chordDetail(chord.root, chord.quality.id))
                },
            )
        }
        composable(Destination.SCALES.route) {
            ScaleScreen(
                contentPadding = contentPadding,
                onChordClick = { chord ->
                    navController.navigate(Routes.chordDetail(chord.root, chord.quality.id))
                },
            )
        }
        composable(Destination.CONVERTER.route) {
            SheetConverterScreen(contentPadding = contentPadding)
        }
        composable(Destination.SETTINGS.route) {
            SettingsScreen(contentPadding = contentPadding)
        }
        composable(Destination.ABOUT.route) {
            AboutScreen(contentPadding = contentPadding)
        }
        composable(
            route = Routes.CHORD_DETAIL,
            arguments = listOf(
                navArgument("letter") { type = NavType.IntType },
                navArgument("accidental") { type = NavType.IntType },
                navArgument("qualityId") { type = NavType.StringType },
            ),
        ) { entry ->
            val letter = entry.arguments?.getInt("letter") ?: 0
            val accidental = (entry.arguments?.getInt("accidental") ?: Routes.ACCIDENTAL_OFFSET) -
                Routes.ACCIDENTAL_OFFSET
            val qualityId = entry.arguments?.getString("qualityId").orEmpty()
            ChordDetailScreen(
                root = Note(letter, accidental),
                qualityId = qualityId,
                contentPadding = contentPadding,
                onChordClick = { chord ->
                    navController.navigate(Routes.chordDetail(chord.root, chord.quality.id))
                },
            )
        }
    }
}
