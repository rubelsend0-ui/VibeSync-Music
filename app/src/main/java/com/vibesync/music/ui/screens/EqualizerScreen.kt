package com.vibesync.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibesync.music.ui.LocalAppContainer
import com.vibesync.music.ui.components.MessageCard

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val eqm = LocalAppContainer.current.equalizer
    val s by eqm.state.collectAsStateWithLifecycle()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Equalizer", style = MaterialTheme.typography.headlineMedium)
        }
        if (!s.available) {
            MessageCard(s.message ?: "Audio effects aren't available on this device.")
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Enable effects", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = s.enabled, onCheckedChange = { eqm.setEnabled(it) })
        }
        if (s.presets.isNotEmpty()) {
            Text("Presets", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(s.presets) { i, name -> FilterChip(selected = false, onClick = { eqm.usePreset(i) }, label = { Text(name) }) }
            }
        }
        Text("Bands", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
        s.bands.forEach { b ->
            val label = if (b.centerHz >= 1000) "${b.centerHz / 1000} kHz" else "${b.centerHz} Hz"
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text("${b.level / 100} dB", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = b.level.toFloat(), onValueChange = { eqm.setBand(b.index, it.toInt()) },
                    valueRange = s.minLevel.toFloat()..s.maxLevel.toFloat(), enabled = s.enabled,
                )
            }
        }
        if (s.bassSupported) {
            Text("Bass boost", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = s.bassStrength.toFloat(), onValueChange = { eqm.setBass(it.toInt()) }, valueRange = 0f..1000f,
                enabled = s.enabled, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (s.virtSupported) {
            Text("Virtualizer (surround)", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = s.virtStrength.toFloat(), onValueChange = { eqm.setVirtualizer(it.toInt()) }, valueRange = 0f..1000f,
                enabled = s.enabled, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (!s.bassSupported) Text("Bass boost isn't supported by this device.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
