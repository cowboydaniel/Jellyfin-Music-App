package com.jellyfinmusic

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.playback.PlayerConnection
import com.jellyfinmusic.ui.LibraryViewModel
import com.jellyfinmusic.ui.components.MiniPlayer
import com.jellyfinmusic.ui.screens.AlbumsScreen
import com.jellyfinmusic.ui.screens.ArtistsScreen
import com.jellyfinmusic.ui.screens.LoginScreen
import com.jellyfinmusic.ui.screens.NowPlayingScreen
import com.jellyfinmusic.ui.screens.PlaylistsScreen
import com.jellyfinmusic.ui.screens.SearchScreen
import com.jellyfinmusic.ui.screens.SettingsScreen
import com.jellyfinmusic.ui.screens.TracksScreen
import com.jellyfinmusic.ui.theme.JellyfinMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore

    @Inject lateinit var playerConnection: PlayerConnection

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Playback still works without it; only the notification is suppressed. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
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
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ALBUMS = "albums/{artistId}/{artistName}"
    const val TRACKS = "tracks/{sourceId}/{title}/{isPlaylist}"

    fun albums(id: String, name: String) = "albums/$id/${name.encode()}"
    fun tracks(id: String, title: String, isPlaylist: Boolean) =
        "tracks/$id/${title.encode()}/$isPlaylist"

    private fun String.encode(): String = URLEncoder.encode(ifBlank { " " }, "UTF-8")
}

private fun String?.decode(): String =
    if (this.isNullOrBlank()) "" else URLDecoder.decode(this, "UTF-8")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(startLoggedIn: Boolean, player: PlayerConnection) {
    val navController = rememberNavController()
    val playerState by player.state.collectAsStateWithLifecycle()
    var showNowPlaying by remember { mutableStateOf(false) }

    if (showNowPlaying && playerState.hasTrack) {
        NowPlayingScreen(
            state = playerState,
            player = player,
            onCollapse = { showNowPlaying = false }
        )
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isLoginRoute = currentRoute == Routes.LOGIN

    Scaffold(
        topBar = {
            if (!isLoginRoute) {
                TopAppBar(title = { Text(titleFor(currentRoute, backStackEntry?.arguments)) })
            }
        },
        bottomBar = {
            if (!isLoginRoute) {
                BottomBar(navController, currentRoute)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // The mini player floats above the content, so list content gets extra
            // bottom padding while it is visible to keep the last row reachable.
            val extraBottom = if (playerState.hasTrack) 72.dp else 0.dp
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
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(bottom = padding.calculateBottomPadding())
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
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    startLoggedIn: Boolean,
    contentPadding: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = if (startLoggedIn) Routes.ARTISTS else Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.ARTISTS) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(Routes.ARTISTS) {
            val vm: LibraryViewModel = hiltViewModel()
            ArtistsScreen(
                viewModel = vm,
                onArtistClick = { id, name -> navController.navigate(Routes.albums(id, name)) },
                contentPadding = contentPadding
            )
        }

        composable(Routes.ALBUMS) { entry ->
            val vm: LibraryViewModel = hiltViewModel()
            val artistId = entry.arguments?.getString("artistId").orEmpty()
            AlbumsScreen(
                viewModel = vm,
                artistId = artistId,
                onAlbumClick = { id, name ->
                    navController.navigate(Routes.tracks(id, name, isPlaylist = false))
                },
                contentPadding = contentPadding
            )
        }

        composable(Routes.TRACKS) { entry ->
            val vm: LibraryViewModel = hiltViewModel()
            TracksScreen(
                viewModel = vm,
                sourceId = entry.arguments?.getString("sourceId").orEmpty(),
                isPlaylist = entry.arguments?.getString("isPlaylist").toBoolean(),
                contentPadding = contentPadding
            )
        }

        composable(Routes.PLAYLISTS) {
            val vm: LibraryViewModel = hiltViewModel()
            PlaylistsScreen(
                viewModel = vm,
                onPlaylistClick = { id, name ->
                    navController.navigate(Routes.tracks(id, name, isPlaylist = true))
                },
                contentPadding = contentPadding
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(contentPadding = contentPadding)
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
    data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

    val tabs = listOf(
        Tab(Routes.ARTISTS, "Library") {
            Icon(Icons.Filled.LibraryMusic, contentDescription = null)
        },
        Tab(Routes.PLAYLISTS, "Playlists") {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
        },
        Tab(Routes.SEARCH, "Search") {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        Tab(Routes.SETTINGS, "Settings") {
            Icon(Icons.Filled.Settings, contentDescription = null)
        }
    )

    NavigationBar {
        tabs.forEach { tab ->
            // Album and track routes live under the Library tab, so it stays
            // selected while browsing deeper.
            val selected = currentRoute == tab.route ||
                (tab.route == Routes.ARTISTS && currentRoute?.startsWith("albums/") == true)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = tab.icon,
                label = { Text(tab.label) }
            )
        }
    }
}

private fun titleFor(route: String?, args: android.os.Bundle?): String = when {
    route == null -> "Jellyfin Music"
    route == Routes.ARTISTS -> "Artists"
    route == Routes.PLAYLISTS -> "Playlists"
    route == Routes.SEARCH -> "Search & request"
    route == Routes.SETTINGS -> "Settings"
    route == Routes.ALBUMS -> args?.getString("artistName").decode().ifBlank { "Albums" }
    route == Routes.TRACKS -> args?.getString("title").decode().ifBlank { "Tracks" }
    else -> "Jellyfin Music"
}
