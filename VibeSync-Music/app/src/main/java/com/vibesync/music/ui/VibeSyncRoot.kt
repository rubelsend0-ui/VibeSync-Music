package com.vibesync.music.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vibesync.music.AppContainer
import com.vibesync.music.data.model.AppSettings
import com.vibesync.music.data.model.SearchType
import com.vibesync.music.data.model.Track
import com.vibesync.music.ui.components.LocalTrackMenu
import com.vibesync.music.ui.components.MiniPlayer
import com.vibesync.music.ui.screens.DownloadsScreen
import com.vibesync.music.ui.screens.EqualizerScreen
import com.vibesync.music.ui.screens.HomeScreen
import com.vibesync.music.ui.screens.LibraryScreen
import com.vibesync.music.ui.screens.NamePlaylistDialog
import com.vibesync.music.ui.screens.NowPlayingScreen
import com.vibesync.music.ui.screens.PlaylistDetailScreen
import com.vibesync.music.ui.screens.SearchScreen
import com.vibesync.music.ui.screens.SettingsScreen
import com.vibesync.music.ui.theme.VibeSyncTheme

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel("home", "Home", Icons.Default.Home),
    TopLevel("search", "Search", Icons.Default.Search),
    TopLevel("library", "Library", Icons.Default.LibraryMusic),
    TopLevel("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun VibeSyncContent(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    CompositionLocalProvider(LocalAppContainer provides container) {
        VibeSyncTheme(settings.theme, settings.accentIndex) { Root(container) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(c: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val route = dest?.route.orEmpty()
    val onPlayer = route == "player"
    val player by c.player.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val context = LocalContext.current

    // Snackbar messages from actions and player.
    LaunchedEffect(Unit) { c.actions.messages.collect { snackbar.showSnackbar(it) } }

    // Ask for notification permission (Android 13+) the first time playback starts, so media controls can show.
    var askedNotif by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(player.isPlaying) {
        if (player.isPlaying && !askedNotif && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotif = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openPlayer() { nav.navigate("player") { launchSingleTop = true } }
    fun openSearch(q: String?, type: SearchType) {
        nav.navigate("search?q=${Uri.encode(q.orEmpty())}&type=${type.name}") {
            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
        }
    }

    CompositionLocalProvider(LocalTrackMenu provides { t -> menuTrack = t }) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!onPlayer) Column {
                    MiniPlayer(player, onToggle = { c.player.togglePlay() }, onOpen = ::openPlayer)
                    NavigationBar {
                        topLevel.forEach { item ->
                            NavigationBarItem(
                                selected = dest?.hierarchy?.any { it.route?.startsWith(item.route) == true } == true,
                                onClick = {
                                    nav.navigate(item.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav, startDestination = "home", modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(tween(200)) }, exitTransition = { fadeOut(tween(150)) },
            ) {
                composable("home") {
                    HomeScreen(
                        onOpenSearch = ::openSearch, onOpenPlayer = ::openPlayer,
                        onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                    )
                }
                composable(
                    "search?q={q}&type={type}",
                    arguments = listOf(
                        navArgument("q") { type = NavType.StringType; defaultValue = "" },
                        navArgument("type") { type = NavType.StringType; defaultValue = "TRACK" },
                    ),
                ) { e ->
                    SearchScreen(
                        initialQuery = e.arguments?.getString("q").orEmpty(),
                        initialType = runCatching { SearchType.valueOf(e.arguments?.getString("type").orEmpty()) }.getOrDefault(SearchType.TRACK),
                        onOpenPlaylist = { nav.navigate("playlist/$it") },
                        onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                    )
                }
                composable("library") {
                    LibraryScreen(onOpenPlaylist = { nav.navigate("playlist/$it") }, onOpenDownloads = { nav.navigate("downloads") })
                }
                composable("settings") { SettingsScreen(onOpenDownloads = { nav.navigate("downloads") }) }
                composable("downloads") { DownloadsScreen(onBack = { nav.popBackStack() }) }
                composable("equalizer") { EqualizerScreen(onBack = { nav.popBackStack() }) }
                composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    PlaylistDetailScreen(id = e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                }
                composable(
                    "player",
                    enterTransition = { slideInVertically(tween(300)) { it } },
                    exitTransition = { fadeOut(tween(150)) },
                    popEnterTransition = { fadeIn(tween(150)) },
                    popExitTransition = { slideOutVertically(tween(300)) { it } },
                ) {
                    NowPlayingScreen(onBack = { nav.popBackStack() }, onOpenEqualizer = { nav.navigate("equalizer") })
                }
            }
        }
    }

    menuTrack?.let { TrackMenuSheet(it, c, context) { menuTrack = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackMenuSheet(track: Track, c: AppContainer, context: android.content.Context, onDismiss: () -> Unit) {
    val favs by c.library.favoriteKeys.collectAsStateWithLifecycle(emptySet())
    val playlists by c.library.playlists.collectAsStateWithLifecycle(emptyList())
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val isFav = track.key in favs

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(track.title, Modifier.padding(horizontal = 24.dp), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(track.artist, Modifier.padding(horizontal = 24.dp, vertical = 2.dp), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        fun act(block: () -> Unit) { block(); onDismiss() }
        if (track.canPlayInApp) {
            MenuRow("Play now", Icons.Default.PlayArrow) { act { c.player.playTracks(listOf(track), 0) } }
            MenuRow("Play next", Icons.AutoMirrored.Filled.QueueMusic) { act { c.player.playNext(track) } }
            MenuRow("Add to queue", Icons.AutoMirrored.Filled.PlaylistAdd) { act { c.player.addToQueue(track) } }
        }
        MenuRow(if (isFav) "Remove from favorites" else "Add to favorites", if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder) {
            act { c.actions.toggleFavorite(track) }
        }
        MenuRow("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { picking = true }
        if (track.downloadable && !track.downloadUrl.isNullOrBlank()) {
            MenuRow("Download (${track.license ?: "permitted"})", Icons.Default.Download) { act { c.actions.download(track) } }
        }
        track.externalUrl?.let { link ->
            MenuRow("Open in browser", Icons.AutoMirrored.Filled.OpenInNew) {
                act { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    if (picking) AlertDialog(
        onDismissRequest = { picking = false }, title = { Text("Add to playlist") },
        text = {
            Column {
                if (playlists.isEmpty()) Text("You have no playlists yet.")
                playlists.forEach { p ->
                    TextButton(onClick = { c.actions.addToPlaylist(p.id, track); picking = false; onDismiss() }) { Text("${p.name} (${p.trackCount})") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = false; creating = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
    )
    if (creating) NamePlaylistDialog("New playlist", "", { creating = false }) {
        c.actions.createPlaylistAndAdd(it, track); creating = false; onDismiss()
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
