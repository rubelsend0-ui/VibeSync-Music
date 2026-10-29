package com.vibesync.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.vibesync.music.data.model.AppJson
import com.vibesync.music.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class EqBand(val index: Int, val centerHz: Int, val level: Int)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevel: Int = 0,
    val maxLevel: Int = 0,
    val presets: List<String> = emptyList(),
    val bassSupported: Boolean = false,
    val bassStrength: Int = 0,
    val virtSupported: Boolean = false,
    val virtStrength: Int = 0,
    val message: String? = null,
)

@Serializable
private data class EqSaved(val enabled: Boolean = false, val levels: List<Int> = emptyList(), val bass: Int = 0, val virt: Int = 0)

/** Real android.media.audiofx effects attached to the player's audio session. Nothing is simulated. */
class EqualizerManager(private val scope: CoroutineScope, private val settings: SettingsRepository) {
    private val _state = MutableStateFlow(EqState(message = "Start playing a track to enable the equalizer."))
    val state: StateFlow<EqState> = _state

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var sessionId = 0

    fun start() {
        scope.launch {
            PlaybackService.audioSessionIdFlow.collect { id ->
                if (id != 0 && id != sessionId) attach(id)
            }
        }
    }

    private suspend fun attach(id: Int) {
        release()
        sessionId = id
        try {
            val e = Equalizer(0, id)
            eq = e
            bass = runCatching { BassBoost(0, id) }.getOrNull()?.takeIf { it.strengthSupported }
            virt = runCatching { Virtualizer(0, id) }.getOrNull()?.takeIf { it.strengthSupported }
            val saved = settings.eqState.first()
                ?.let { runCatching { AppJson.json.decodeFromString(EqSaved.serializer(), it) }.getOrNull() } ?: EqSaved()
            saved.levels.forEachIndexed { i, l ->
                if (i < e.numberOfBands) runCatching { e.setBandLevel(i.toShort(), l.toShort()) }
            }
            runCatching { bass?.setStrength(saved.bass.toShort()) }
            runCatching { virt?.setStrength(saved.virt.toShort()) }
            applyEnabled(saved.enabled)
            publish(null)
        } catch (t: Throwable) {
            release()
            _state.value = EqState(available = false, message = "Audio effects aren't supported on this device or output.")
        }
    }

    private fun applyEnabled(on: Boolean) {
        runCatching { eq?.enabled = on }
        runCatching { bass?.enabled = on }
        runCatching { virt?.enabled = on }
    }

    private fun publish(message: String?) {
        val e = eq ?: return
        val range = e.bandLevelRange
        _state.value = EqState(
            available = true,
            enabled = e.enabled,
            bands = (0 until e.numberOfBands).map { i ->
                EqBand(i, e.getCenterFreq(i.toShort()) / 1000, e.getBandLevel(i.toShort()).toInt())
            },
            minLevel = range[0].toInt(), maxLevel = range[1].toInt(),
            presets = (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) },
            bassSupported = bass != null, bassStrength = bass?.roundedStrength?.toInt() ?: 0,
            virtSupported = virt != null, virtStrength = virt?.roundedStrength?.toInt() ?: 0,
            message = message,
        )
    }

    private fun persist() {
        val s = _state.value
        val json = AppJson.json.encodeToString(
            EqSaved.serializer(), EqSaved(s.enabled, s.bands.map { it.level }, s.bassStrength, s.virtStrength),
        )
        scope.launch { settings.saveEqState(json) }
    }

    fun setEnabled(on: Boolean) { applyEnabled(on); publish(null); persist() }
    fun setBand(index: Int, level: Int) {
        runCatching { eq?.setBandLevel(index.toShort(), level.toShort()) }
        publish(null); persist()
    }
    fun usePreset(index: Int) {
        runCatching { eq?.usePreset(index.toShort()) }
        publish(null); persist()
    }
    fun setBass(strength: Int) { runCatching { bass?.setStrength(strength.toShort()) }; publish(null); persist() }
    fun setVirtualizer(strength: Int) { runCatching { virt?.setStrength(strength.toShort()) }; publish(null); persist() }

    private fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { virt?.release() }
        eq = null; bass = null; virt = null
    }
}
