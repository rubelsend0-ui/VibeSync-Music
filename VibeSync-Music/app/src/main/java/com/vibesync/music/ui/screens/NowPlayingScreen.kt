package com.vibesync.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.vibesync.music.data.model.Track
import com.vibesync.music.ui.LocalAppContainer
import com.vibesync.music.ui.components.ArtworkImage
import com.vibesync.music.ui.components.Badge
import com.vibesync.music.ui.components.MessageCard
import com.vibesync.music.util.formatBytes
import com.vibesync.music.util.formatDuration

private fun providerLabel(p: String) = when (p) {
    "local" -> "On this device"; "jamendo" -> "Jamendo"; "internet_archive" -> "Internet Archive"
    "youtube" -> "YouTube"; "demo" -> "Demo"; else -> p
}

/** Honest description of what the source actually provides. */
private fun sourceInfo(t: Track): String = listOfNotNull(
    providerLabel(t.provider),
    t.format,
    t.bitrateKbps?.let { "$it kbps" },
    t.sizeBytes?.takeIf { it > 0 }?.let { formatBytes(it) },
    if (t.preview) "Preview only" else if (t.canPlayInApp) "Full track" else "Not playable in app",
).joinToString(" • ")

@Composable
private fun rememberArtworkColor(url: String?, fallback: Color): Color {
    val ctx = LocalContext.current
    var color by remember(url) { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(128).build()
        val res = ctx.imageLoader.execute(req)
        if (res is SuccessResult) {
            val bmp = runCatching { res.drawable.toBitmap() }.getOrNull() ?: return@LaunchedEffect
            val swatch = Palette.from(bmp).generate().let { it.vibrantSwatch ?: it.dominantSwatch }
            if (swatch != null) color = Color(swatch.rgb)
        }
    }
    return color
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(onBack: () -> Unit, onOpenEqualizer: () -> Unit) {
    val c = LocalAppContainer.current
    val st by c.player.state.collectAsStateWithLifecycle()
    val favKeys by c.library.favoriteKeys.collectAsStateWithLifecycle(emptySet())
    val sleepLeft by c.sleepTimer.remainingMs.collectAsStateWithLifecycle()
    val track = st.track
    if (track == null) { LaunchedEffect(Unit) { onBack() }; return }

    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val base = MaterialTheme.colorScheme.background
    val accent by animateColorAsState(rememberArtworkColor(track.artworkUrl, MaterialTheme.colorScheme.primary), tween(600), label = "bg")
    val duration = st.durationMs.coerceAtLeast(1L)
    val position = if (dragging) dragValue else st.positionMs.toFloat().coerceIn(0f, duration.toFloat())

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), base, base))),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Close player") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(providerLabel(track.provider), style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Open queue") }
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = track.artworkUrl to track.key,
                transitionSpec = { (fadeIn(tween(350)) + scaleIn(initialScale = 0.92f)) togetherWith (fadeOut(tween(250)) + scaleOut(targetScale = 0.92f)) },
                label = "artwork",
            ) { (art, _) ->
                ArtworkImage(art, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(24.dp))
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineMedium)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val fav = track.key in favKeys
                IconButton(onClick = { c.actions.toggleFavorite(track) }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (fav) "Remove from favorites" else "Add to favorites",
                        tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(sourceInfo(track), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (track.isDemo) Badge("DEMO DATA")
            st.error?.let {
                Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = position,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { c.player.seekTo(dragValue.toLong()); dragging = false },
                valueRange = 0f..duration.toFloat(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(position.toLong()), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("-" + formatDuration((duration - position.toLong()).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { c.player.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, if (st.shuffle) "Shuffle on" else "Shuffle off", tint = if (st.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { c.player.previous() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                FilledIconButton(onClick = { c.player.togglePlay() }, modifier = Modifier.size(72.dp)) {
                    if (st.isBuffering && !st.isPlaying) CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                    else Icon(if (st.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (st.isPlaying) "Pause" else "Play", Modifier.size(40.dp))
                }
                IconButton(onClick = { c.player.next() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp)) }
                IconButton(onClick = { c.player.cycleRepeat() }) {
                    val on = st.repeat != Player.REPEAT_MODE_OFF
                    Icon(
                        if (st.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        when (st.repeat) { Player.REPEAT_MODE_ONE -> "Repeat one"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat off" },
                        tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { showSleep = true }) {
                    Icon(Icons.Default.Timer, null); Text(sleepLeft?.let { " ${formatDuration(it)}" } ?: " Sleep timer")
                }
                TextButton(onClick = onOpenEqualizer) { Icon(Icons.Default.GraphicEq, null); Text(" Equalizer") }
            }

            Surface(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Lyrics", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Lyrics aren't available for this track. VibeSync only shows lyrics from lawful sources, and no lyrics provider is configured.",
                        Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showQueue) ModalBottomSheet(onDismissRequest = { showQueue = false }) {
        Text("Queue • ${st.queue.size}", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        if (st.queue.isEmpty()) MessageCard("The queue is empty.")
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            itemsIndexed(st.queue, key = { i, t -> "$i-${t.key}" }) { i, t ->
                ListItem(
                    headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (i == st.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    supportingContent = { Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { ArtworkImage(t.artworkUrl, Modifier.size(44.dp), RoundedCornerShape(8.dp)) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { c.player.moveInQueue(i, i - 1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { c.player.moveInQueue(i, i + 1) }, enabled = i < st.queue.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { c.player.removeFromQueue(i) }) { Icon(Icons.Default.Close, "Remove from queue") }
                        }
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).let { it },
                )
            }
        }
    }

    if (showSleep) AlertDialog(
        onDismissRequest = { showSleep = false },
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                    TextButton(onClick = { c.sleepTimer.start(m); showSleep = false }) { Text("$m minutes") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { c.sleepTimer.cancel(); showSleep = false }, enabled = sleepLeft != null) { Text("Cancel timer") } },
        dismissButton = { TextButton(onClick = { showSleep = false }) { Text("Close") } },
    )
}
