package com.vibesync.music.ui.components

import androidx.compose.runtime.getValue

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vibesync.music.data.model.Track
import com.vibesync.music.data.remote.AppError
import com.vibesync.music.playback.PlayerState
import com.vibesync.music.ui.Loadable
import com.vibesync.music.util.formatDuration

/** Opens the track action sheet (play next, queue, favorite, playlist, download...). Provided by the root. */
val LocalTrackMenu = compositionLocalOf<(Track) -> Unit> { { _ -> } }

fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val hi = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -1f, targetValue = 2f, label = "x",
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
    )
    background(Brush.linearGradient(listOf(base, hi, base), start = Offset(x * 500f, 0f), end = Offset(x * 500f + 500f, 200f)))
}

@Composable
fun ArtworkImage(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1B2A6B), Color(0xFF5B2A9B)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, null, Modifier.fillMaxSize(0.4f), tint = Color.White.copy(alpha = 0.45f))
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    dimmed: Boolean = false,
    showMenu: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val menu = LocalTrackMenu.current
    val alpha = if (dimmed) 0.45f else 1f
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(track.artworkUrl, Modifier.size(52.dp).background(Color.Transparent), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                color = (if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (track.isDemo) Badge("DEMO", MaterialTheme.colorScheme.secondary)
                else if (!track.canPlayInApp) Badge("Not playable here", MaterialTheme.colorScheme.outline)
                else if (track.preview) Badge("Preview", MaterialTheme.colorScheme.error)
                Text(
                    listOfNotNull(track.artist, track.durationMs.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" • "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        trailing?.invoke()
        if (showMenu) IconButton(onClick = { menu(track) }) { Icon(Icons.Default.MoreVert, "More options for ${track.title}") }
    }
}

@Composable
fun TrackCard(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 140.dp) {
    Column(modifier.width(width).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        ArtworkImage(track.artworkUrl, Modifier.size(width), RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text(
            track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        action?.invoke()
    }
}

@Composable
fun SkeletonRow(count: Int = 4, size: Dp = 140.dp) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            Column {
                Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).shimmer())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(size * 0.8f).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            }
        }
    }
}

@Composable
fun SkeletonList(count: Int = 5) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).shimmer())
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(180.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Box(Modifier.width(110.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                }
            }
        }
    }
}

@Composable
fun MessageCard(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (isError) Icons.Default.CloudOff else Icons.Default.Warning, null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (primaryLabel != null || secondaryLabel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (primaryLabel != null && onPrimary != null) Button(onClick = onPrimary) { Text(primaryLabel) }
                    if (secondaryLabel != null && onSecondary != null) OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
                }
            }
        }
    }
}

/** Standard loading / error / empty / content wrapper used by every data section. */
@Composable
fun <T> LoadableSection(
    state: Loadable<T>,
    onRetry: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    isEmpty: (T) -> Boolean,
    emptyText: (T) -> String,
    skeleton: @Composable () -> Unit = { SkeletonRow() },
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is Loadable.Loading -> skeleton()
        is Loadable.Failed -> {
            val notConfigured = state.error is AppError.NotConfigured
            MessageCard(
                text = if (notConfigured) "${state.error.message} Add your backend URL in Settings, turn on demo mode, or use the music on your device."
                else state.error.message ?: "Something went wrong.",
                isError = !notConfigured,
                primaryLabel = if (notConfigured && onOpenSettings != null) "Open settings" else "Retry",
                onPrimary = if (notConfigured && onOpenSettings != null) onOpenSettings else onRetry,
                secondaryLabel = if (notConfigured && onOpenSettings != null) "Retry" else null,
                onSecondary = if (notConfigured && onOpenSettings != null) onRetry else null,
            )
        }
        is Loadable.Ready -> if (isEmpty(state.value)) MessageCard(emptyText(state.value)) else content(state.value)
    }
}

@Composable
fun TrackCardRow(tracks: List<Track>, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tracks.size) { i -> TrackCard(tracks[i], onClick = { onPlay(i) }) }
    }
}

@Composable
fun MiniPlayer(state: PlayerState, onToggle: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val track = state.track ?: return
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp,
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(track.artworkUrl, Modifier.size(44.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.error != null) state.error else track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else "Play")
                }
            }
            val p = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(2.dp), trackColor = Color.Transparent)
        }
    }
}

@Composable
fun CircleArtwork(url: String?, size: Dp) = ArtworkImage(url, Modifier.size(size), CircleShape)
