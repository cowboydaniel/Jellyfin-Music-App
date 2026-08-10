package com.jellyfinmusic

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayerConnection
import com.jellyfinmusic.ui.LibraryViewModel
import com.jellyfinmusic.ui.PlayerViewModel
import com.jellyfinmusic.ui.components.ActionSheetHost
import com.jellyfinmusic.ui.components.MiniPlayer
import com.jellyfinmusic.ui.screens.AlbumDetailScreen
import com.jellyfinmusic.ui.screens.ArtistDetailScreen
import com.jellyfinmusic.ui.screens.ExploreScreen
import com.jellyfinmusic.ui.screens.HomeScreen
import com.jellyfinmusic.ui.screens.LibraryScreen
import com.jellyfinmusic.ui.screens.LikedSongsScreen
import com.jellyfinmusic.ui.screens.LoginScreen
import com.jellyfinmusic.ui.screens.NowPlayingScreen
import com.jellyfinmusic.ui.screens.SearchScreen
import com.jellyfinmusic.ui.screens.SettingsScreen
import com.jellyfinmusic.ui.theme.AppColors
import com.jellyfinmusic.ui.theme.JellyfinMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore

    @Inject lateinit var playerConnection: PlayerConnection

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Playback still works without it; only the notification is suppressed. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Declared explicitly so the system bars stay transparent with light
        // icons over the dark UI, rather than relying on the platform default.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            JellyfinMusicTheme {
                AppRoot(
                    startLoggedIn = settings.current.isLoggedIn,
                    player = playerConnection
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }

    override fun onDestroy() {
        // The controller is a client of the service, not the player itself, so
        // releasing it here does not stop background playback.
        if (isFinishing) playerConnection.release()
        super.onDestroy()
    }
}

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ALBUM = "album/{id}/{title}/{isPlaylist}"
    const val ARTIST = "artist/{id}/{name}"
    const val LIKED = "liked"

    fun album(id: String, title: String, isPlaylist: Boolean) =
        "album/$id/${title.encode()}/$isPlaylist"

    fun artist(id: String, name: String) = "artist/$id/${name.encode()}"

    private fun String.encode(): String = URLEncoder.encode(ifBlank { " " }, "UTF-8")
}

private fun String?.decode(): String =
    if (this.isNullOrBlank()) "" else URLDecoder.decode(this, "UTF-8")

private val TAB_ROUTES = setOf(Routes.HOME, Routes.EXPLORE, Routes.LIBRARY, Routes.SEARCH)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(startLoggedIn: Boolean, player: PlayerConnection) {
    val navController = rememberNavController()
    val playerState by player.state.collectAsStateWithLifecycle()
    var showNowPlaying by remember { mutableStateOf(false) }

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val favoriteIds by playerViewModel.favoriteIds.collectAsStateWithLifecycle()
    val lyricsState by playerViewModel.lyrics.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isLoginRoute = currentRoute == Routes.LOGIN
    val isTabRoute = currentRoute in TAB_ROUTES

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        Scaffold(
            containerColor = AppColors.Background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isLoginRoute) {
                    TopAppBar(
                        title = {
                            Text(
                                titleFor(currentRoute, backStackEntry?.arguments),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            if (!isTabRoute) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable { navController.popBackStack() }
                                        .padding(8.dp)
                                )
                            }
                        },
                        actions = {
                            if (isTabRoute) {
                                // Account avatar in the corner, which is where
                                // YouTube Music keeps settings.
                                AccountAvatar(
                                    onClick = { navController.navigate(Routes.SETTINGS) }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = AppColors.Background,
                            titleContentColor = AppColors.OnBackground
                        )
                    )
                }
            },
            bottomBar = {
                if (!isLoginRoute) BottomBar(navController, currentRoute)
            }
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                val extraBottom = if (playerState.hasTrack) 68.dp else 0.dp
                val layoutDirection = LocalLayoutDirection.current
                val contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + extraBottom
                )

                NavGraph(navController, startLoggedIn, contentPadding)

                AnimatedVisibility(
                    visible = playerState.hasTrack && !isLoginRoute,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = padding.calculateBottomPadding() + 4.dp)
                ) {
                    MiniPlayer(
                        state = playerState,
                        onPlayPause = player::playPause,
                        onNext = player::next,
                        onExpand = { showNowPlaying = true }
                    )
                }
            }
        }

        // One host for every context sheet, mounted above the nav graph so it
        // survives navigation and can be opened from any screen.
        if (!isLoginRoute) {
            ActionSheetHost(onShowMessage = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            })
        }

        // The full player slides up over everything, including the nav bar.
        AnimatedVisibility(
            visible = showNowPlaying && playerState.hasTrack,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlayingScreen(
                state = playerState,
                player = player,
                lyrics = lyricsState,
                onCollapse = { showNowPlaying = false },
                isFavorite = playerState.currentItemId in favoriteIds,
                onToggleFavorite = {
                    playerState.currentItemId?.let(playerViewModel::toggleFavorite)
                },
                onShowMenu = playerViewModel::showMenuForCurrent,
                onAddToPlaylist = playerViewModel::addCurrentToPlaylist,
                onStartRadio = playerViewModel::startRadioFromCurrent,
                onLyricsRequested = { playerViewModel.loadLyrics(playerState.currentItemId) }
            )
        }
    }

    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
}

@Composable
private fun AccountAvatar(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 12.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(AppColors.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Person,
            contentDescription = "Settings",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    startLoggedIn: Boolean,
    contentPadding: PaddingValues
) {
    val openAlbum: (BaseItem) -> Unit = { item ->
        navController.navigate(
            Routes.album(item.id, item.name.orEmpty(), item.type == "Playlist")
        )
    }
    val openArtist: (BaseItem) -> Unit = { item ->
        navController.navigate(Routes.artist(item.id, item.name.orEmpty()))
    }

    NavHost(
        navController = navController,
        startDestination = if (startLoggedIn) Routes.HOME else Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(Routes.HOME) {
            HomeScreen(
                contentPadding = contentPadding,
                onAlbumClick = openAlbum,
                onArtistClick = openArtist,
                onPlaylistClick = openAlbum
            )
        }

        composable(Routes.EXPLORE) {
            ExploreScreen(contentPadding = contentPadding, onAlbumClick = openAlbum)
        }

        composable(Routes.LIBRARY) {
            val vm: LibraryViewModel = hiltViewModel()
            LibraryScreen(
                viewModel = vm,
                contentPadding = contentPadding,
                onAlbumClick = openAlbum,
                onArtistClick = openArtist,
                onPlaylistClick = openAlbum,
                onLikedSongsClick = { navController.navigate(Routes.LIKED) }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                contentPadding = contentPadding,
                onAlbumClick = openAlbum,
                onArtistClick = openArtist
            )
        }

        composable(Routes.ALBUM) { entry ->
            AlbumDetailScreen(
                albumId = entry.arguments?.getString("id").orEmpty(),
                isPlaylist = entry.arguments?.getString("isPlaylist").toBoolean(),
                fallbackTitle = entry.arguments?.getString("title").decode(),
                contentPadding = contentPadding,
                onDeleted = { navController.popBackStack() }
            )
        }

        composable(Routes.LIKED) {
            LikedSongsScreen(contentPadding = contentPadding)
        }

        composable(Routes.ARTIST) { entry ->
            ArtistDetailScreen(
                artistId = entry.arguments?.getString("id").orEmpty(),
                fallbackName = entry.arguments?.getString("name").decode(),
                contentPadding = contentPadding,
                onAlbumClick = openAlbum
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                contentPadding = contentPadding,
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    data class Tab(
        val route: String,
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    val tabs = listOf(
        Tab(Routes.HOME, "Home", Icons.Filled.Home),
        Tab(Routes.EXPLORE, "Explore", Icons.Filled.Explore),
        Tab(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic),
        Tab(Routes.SEARCH, "Search", Icons.Filled.Search)
    )

    NavigationBar(containerColor = AppColors.Surface, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppColors.OnBackground,
                    selectedTextColor = AppColors.OnBackground,
                    unselectedIconColor = AppColors.Secondary,
                    unselectedTextColor = AppColors.Secondary,
                    indicatorColor = AppColors.SurfaceVariant
                )
            )
        }
    }
}

private fun titleFor(route: String?, args: android.os.Bundle?): String = when (route) {
    Routes.HOME -> "Jellyfin Music"
    Routes.EXPLORE -> "Explore"
    Routes.LIBRARY -> "Library"
    Routes.SEARCH -> "Search"
    Routes.SETTINGS -> "Settings"
    Routes.ALBUM -> args?.getString("title").decode().ifBlank { "Album" }
    Routes.ARTIST -> args?.getString("name").decode().ifBlank { "Artist" }
    Routes.LIKED -> "Liked songs"
    else -> "Jellyfin Music"
}
