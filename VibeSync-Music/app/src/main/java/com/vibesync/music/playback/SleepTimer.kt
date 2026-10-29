package com.vibesync.music.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Pauses playback when the timer expires. (No fade-out: MediaController can't reliably change volume.) */
class SleepTimer(private val scope: CoroutineScope, private val onExpire: () -> Unit) {
    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs
    private var job: Job? = null

    fun start(minutes: Int) {
        job?.cancel()
        val end = System.currentTimeMillis() + minutes * 60_000L
        job = scope.launch {
            while (true) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                _remainingMs.value = left
                delay(1000)
            }
            _remainingMs.value = null
            onExpire()
        }
    }

    fun cancel() {
        job?.cancel()
        _remainingMs.value = null
    }
}
