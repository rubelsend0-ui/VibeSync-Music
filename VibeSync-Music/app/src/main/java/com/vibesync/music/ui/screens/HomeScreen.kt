package com.vibesync.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibesync.music.data.model.SearchPage
import com.vibesync.music.data.model.SearchType
import com.vibesync.music.data.model.Track
import com.vibesync.music.data.repo.LocalMediaRepository
import com.vibesync.music.ui.HomeViewModel
import com.vibesync.music.ui.Loadable
import com.vibesync.music.ui.LocalAppContainer
import com.vibesync.music.ui.appViewModel
import com.vibesync.music.ui.components.ArtworkImage
import com.vibesync.music.ui.components.CircleArtwork
import com.vibesync.music.ui.components.LoadableSection
import com.vibesync.music.ui.components.MessageCard
import com.vibesync.music.ui.components.SectionHeader
import com.vibesync.music.ui.components.TrackCardRow
import java.time.LocalTime

@Composable
fun rememberAudioPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return remember(launcher) { { launcher.launch(LocalMediaRepository.permission) } }
}

@Composable
fun HomeScreen(
    onOpenSearch: (String?, SearchType) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = appViewModel<HomeViewModel>()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val personal by vm.personal.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val local by vm.localTracks.collectAsStateWithLifecycle()
    val player by c.player.state.collectAsStateWithLifecycle()
    var hasPerm by remember { mutableStateOf(c.localMedia.hasPermission()) }
    val askAudio = rememberAudioPermissionRequest { granted -> hasPerm = granted; if (granted) vm.refreshLocal() }
    LaunchedEffect(Unit) { vm.load() }

    val hour = remember { LocalTime.now().hour }
    val greeting = when { hour < 5 -> "Late night vibes"; hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
    val play: (List<Track>, Int) -> Unit = { list, i -> c.player.playTracks(list, i) }
    val shuffle: (List<Track>) -> Unit = { list -> c.player.playTracks(list, 0, shuffle = true) }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.headlineLarge)
                    Text("What do you want to hear?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
            }
            Surface(
                Modifier.fillMaxWidth().padding(16.dp).clickable { onOpenSearch(null, SearchType.TRACK) },
                shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Songs, artists, albums, playlists", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Continue listening
        item {
            val last = player.track ?: recent.firstOrNull()
            if (last != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    onClick = { if (player.track != null) onOpenPlayer() else play(recent, 0) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtworkImage(last.artworkUrl, Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Continue listening", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            Text(last.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(last.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                        Icon(Icons.Default.PlayArrow, "Resume", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        item { SectionHeader("Recently played") }
        item {
            if (recent.isEmpty()) MessageCard("Nothing played yet. Tracks you listen to will show up here.")
            else TrackCardRow(recent.take(15)) { play(recent.take(15), it) }
        }

        item { SectionHeader("Recently added on this device") }
        item {
            when {
                !hasPerm -> MessageCard(
                    "Allow access to audio files to list music stored on your device. VibeSync only reads audio metadata.",
                    primaryLabel = "Allow access", onPrimary = askAudio,
                )
                local.isEmpty() -> MessageCard("No music files found on this device.")
                else -> TrackCardRow(local.take(15)) { play(local.take(15), it) }
            }
        }

        item { SectionHeader(if (personal.artist != null) "Because you listen to ${personal.artist}" else "Made for you") }
        item {
            LoadableSection(
                state = personal.data, onRetry = { vm.load(true) }, onOpenSettings = onOpenSettings,
                isEmpty = { it.items.isEmpty() },
                emptyText = {
                    if (personal.artist == null) "Play a few songs and VibeSync will recommend more from your listening history (stored only on this device)."
                    else it.notices.firstOrNull() ?: "No recommendations found for ${personal.artist} yet."
                },
            ) { page -> TrackCardRow(page.items) { play(page.items, it) } }
        }

        categorySection("Trending now", sections["trending"], vm, onOpenSettings, play, shuffle)
        categorySection("New releases", sections["new_releases"], vm, onOpenSettings, play, shuffle)

        item { SectionHeader("Popular artists") }
        item {
            LoadableSection(
                state = sections["popular"] ?: Loadable.Loading, onRetry = { vm.load(true) }, onOpenSettings = onOpenSettings,
                isEmpty = { it.items.isEmpty() }, emptyText = { it.notices.firstOrNull() ?: "No artists to show yet." },
            ) { page -> ArtistRow(page.items) { onOpenSearch(it, SearchType.ARTIST) } }
        }

        item { SectionHeader("Album collections") }
        item {
            val merged = listOfNotNull(sections["new_releases"], sections["trending"], sections["popular"])
                .flatMap { (it as? Loadable.Ready<SearchPage>)?.value?.items.orEmpty() }
            val state: Loadable<List<Track>> =
                if (sections["new_releases"] is Loadable.Loading && merged.isEmpty()) Loadable.Loading else Loadable.Ready(merged)
            LoadableSection(
                state = state, onRetry = { vm.load(true) }, onOpenSettings = onOpenSettings,
                isEmpty = { l -> l.none { !it.album.isNullOrBlank() } }, emptyText = { "No album information available from your providers." },
            ) { l -> AlbumRow(l, play) }
        }

        categorySection("Focus & study", sections["focus"], vm, onOpenSettings, play, shuffle)
        categorySection("Chill & relax", sections["chill"], vm, onOpenSettings, play, shuffle)
        categorySection("Workout", sections["workout"], vm, onOpenSettings, play, shuffle)
        categorySection("Romantic", sections["romantic"], vm, onOpenSettings, play, shuffle)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categorySection(
    title: String,
    state: Loadable<SearchPage>?,
    vm: HomeViewModel,
    onOpenSettings: () -> Unit,
    play: (List<Track>, Int) -> Unit,
    shuffle: (List<Track>) -> Unit,
) {
    item {
        val items = (state as? Loadable.Ready<SearchPage>)?.value?.items.orEmpty()
        SectionHeader(title) {
            if (items.any { it.canPlayInApp }) {
                IconButton(onClick = { shuffle(items) }) { Icon(Icons.Default.Shuffle, "Shuffle $title") }
                IconButton(onClick = { play(items, 0) }) { Icon(Icons.Default.PlayArrow, "Play all $title") }
            }
        }
    }
    item {
        LoadableSection(
            state = state ?: Loadable.Loading, onRetry = { vm.load(true) }, onOpenSettings = onOpenSettings,
            isEmpty = { it.items.isEmpty() },
            emptyText = { it.notices.joinToString("\n").ifBlank { "Nothing here yet." } },
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page.demo) Text(
                    page.notices.firstOrNull() ?: "DEMO DATA", Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary,
                )
                TrackCardRow(page.items) { play(page.items, it) }
            }
        }
    }
}

@Composable
private fun ArtistRow(tracks: List<Track>, onClick: (String) -> Unit) {
    val artists = remember(tracks) { tracks.groupBy { it.artist }.entries.take(12).map { it.key to it.value.first() } }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(artists) { (name, t) ->
            Column(Modifier.width(96.dp).clickable { onClick(name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                CircleArtwork(t.artworkUrl, 96.dp)
                Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun AlbumRow(tracks: List<Track>, play: (List<Track>, Int) -> Unit) {
    val albums = remember(tracks) { tracks.filter { !it.album.isNullOrBlank() }.groupBy { it.album!! }.entries.take(12).toList() }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums) { e ->
            val first = e.value.first()
            Column(Modifier.width(150.dp).clickable { play(e.value, 0) }) {
                ArtworkImage(first.artworkUrl, Modifier.size(150.dp), RoundedCornerShape(14.dp))
                Text(e.key, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Text("${first.artist} • ${e.value.size} tracks", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
