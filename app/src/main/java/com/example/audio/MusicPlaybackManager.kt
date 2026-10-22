package com.example.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class RepeatMode {
    NONE, ALL, ONE
}

enum class DjCurveType(val displayName: String) {
    EQUAL_POWER("Equal-Power (DJ Standard)"),
    EXPONENTIAL("Smooth Exponential"),
    LINEAR("Linear Transition")
}

data class CrossfadeGains(val outGain: Float, val inGain: Float)

class MusicPlaybackManager {

    companion object {
        const val SAMPLE_RATE = 22050
        private const val BUFFER_SIZE = 1024
        private const val PREFS_NAME = "musiplay_dj_playback_prefs"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_SEC = "crossfade_duration_sec"
        private const val KEY_DJ_MODE = "dj_mode_enabled"
        private const val KEY_DJ_CURVE = "dj_curve_type"

        @Volatile
        private var instance: MusicPlaybackManager? = null

        fun getInstance(): MusicPlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlaybackManager().also { instance = it }
            }
        }

        fun calculateCrossfadeGains(t: Float, curve: String): CrossfadeGains {
            val clampedT = t.coerceIn(0f, 1f)
            return when (curve.uppercase()) {
                DjCurveType.LINEAR.name -> {
                    CrossfadeGains(
                        outGain = 1.0f - clampedT,
                        inGain = clampedT
                    )
                }
                DjCurveType.EXPONENTIAL.name -> {
                    CrossfadeGains(
                        outGain = sqrt(1.0f - clampedT),
                        inGain = sqrt(clampedT)
                    )
                }
                else -> {
                    val angle = clampedT * (Math.PI / 2.0)
                    CrossfadeGains(
                        outGain = cos(angle).toFloat().coerceIn(0f, 1f),
                        inGain = sin(angle).toFloat().coerceIn(0f, 1f)
                    )
                }
            }
        }
    }

    private var appContext: Context? = null
    private var sharedPrefs: SharedPreferences? = null

    // Dual-Player Audio Architecture
    private var mediaPlayer: MediaPlayer? = null
    private var incomingMediaPlayer: MediaPlayer? = null
    private var preloadedMediaPlayer: MediaPlayer? = null
    private var preloadedTrackUri: String? = null

    // Hardware Audio synthesis track
    private var audioTrack: AudioTrack? = null
    private var synthesisThread: Thread? = null
    private var isThreadRunning = false

    // Hardware Audio effects handles
    private var equalizerFx: Equalizer? = null
    private var bassBoostFx: BassBoost? = null
    private var presetReverbFx: PresetReverb? = null
    private var virtualizerFx: Virtualizer? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val crossfadeLock = Any()

    // Crossfade runtime tracking
    private var crossfadeStartTimeMs = 0L
    private var crossfadeTotalDurationMs = 0L
    private var crossfadePausedAtMs = 0L

    // Synthetic audio transition states
    private var synthIncomingTrack: MusicTrack? = null
    private var synthPhase = 0.0
    private var synthBeatPosition = 0.0
    private var synthIncomingPhase = 0.0
    private var synthIncomingBeatPosition = 0.0

    // UI state flows
    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0)
    val currentPositionSec = _currentPositionSec.asStateFlow()

    // Playback modes
    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle = _isShuffle.asStateFlow()

    // Professional DJ Crossfade settings (Default 20 seconds per user requirement)
    private val _crossfadeEnabled = MutableStateFlow(true)
    val crossfadeEnabled = _crossfadeEnabled.asStateFlow()

    private val _crossfadeSec = MutableStateFlow(20)
    val crossfadeSec = _crossfadeSec.asStateFlow()

    private val _djModeEnabled = MutableStateFlow(true)
    val djModeEnabled = _djModeEnabled.asStateFlow()

    private val _djCurveType = MutableStateFlow(DjCurveType.EQUAL_POWER.name)
    val djCurveType = _djCurveType.asStateFlow()

    // Real-time DJ crossfade transition telemetry
    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading = _isCrossfading.asStateFlow()

    private val _crossfadeProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val crossfadeProgress = _crossfadeProgress.asStateFlow()

    private val _incomingTrack = MutableStateFlow<MusicTrack?>(null)
    val incomingTrack = _incomingTrack.asStateFlow()

    private val _gaplessEnabled = MutableStateFlow(true)
    val gaplessEnabled = _gaplessEnabled.asStateFlow()

    // Sound adjustments
    private val _speed = MutableStateFlow(1.0f)
    val speed = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch = _pitch.asStateFlow()

    private val _eqGains = MutableStateFlow(floatArrayOf(0f, 0f, 0f, 0f, 0f))
    val eqGains = _eqGains.asStateFlow()

    // Audio Effects
    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength = _virtualizerStrength.asStateFlow()

    private val _reverbPreset = MutableStateFlow("None")
    val reverbPreset = _reverbPreset.asStateFlow()

    private val _balance = MutableStateFlow(0f)
    val balance = _balance.asStateFlow()

    // Sleep timer in minutes remaining (0 means disabled)
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes = _sleepTimerMinutes.asStateFlow()

    // Current queue tracking
    var currentIndex: Int = 0
    private val _activeQueueList = MutableStateFlow<List<MusicTrack>>(emptyList())
    val activeQueueList = _activeQueueList.asStateFlow()

    init {
        initAudioTrack()
        startSynthesisLoop()
        startCrossfadeAndProgressLoop()
    }

    fun setContext(context: Context) {
        val app = context.applicationContext
        appContext = app
        sharedPrefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPersistedSettings()
    }

    private fun loadPersistedSettings() {
        val prefs = sharedPrefs ?: return
        _crossfadeEnabled.value = prefs.getBoolean(KEY_CROSSFADE_ENABLED, true)
        val defaultSec = 20
        val savedSec = if (prefs.contains(KEY_CROSSFADE_SEC)) {
            val v = prefs.getInt(KEY_CROSSFADE_SEC, defaultSec)
            if (v == 8) defaultSec else v
        } else {
            defaultSec
        }
        _crossfadeSec.value = savedSec.coerceIn(0, 20)
        _djModeEnabled.value = prefs.getBoolean(KEY_DJ_MODE, true)
        _djCurveType.value = prefs.getString(KEY_DJ_CURVE, DjCurveType.EQUAL_POWER.name) ?: DjCurveType.EQUAL_POWER.name
    }

    private fun persistSettings() {
        val editor = sharedPrefs?.edit() ?: return
        editor.putBoolean(KEY_CROSSFADE_ENABLED, _crossfadeEnabled.value)
        editor.putInt(KEY_CROSSFADE_SEC, _crossfadeSec.value)
        editor.putBoolean(KEY_DJ_MODE, _djModeEnabled.value)
        editor.putString(KEY_DJ_CURVE, _djCurveType.value)
        editor.apply()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Equal-Power Math & DJ Gain Calculations
    // ─────────────────────────────────────────────────────────────────────────────

    fun calculateCrossfadeGains(t: Float, curve: String): CrossfadeGains {
        val clampedT = t.coerceIn(0f, 1f)
        return when (curve.uppercase()) {
            DjCurveType.LINEAR.name -> {
                CrossfadeGains(
                    outGain = 1.0f - clampedT,
                    inGain = clampedT
                )
            }
            DjCurveType.EXPONENTIAL.name -> {
                // Square root curve provides constant power with gentle center curve
                CrossfadeGains(
                    outGain = sqrt(1.0f - clampedT),
                    inGain = sqrt(clampedT)
                )
            }
            else -> {
                // EQUAL_POWER (DJ Standard: cos(t * pi/2) and sin(t * pi/2))
                // cos^2 + sin^2 = 1.0, strictly maintaining perceived acoustic energy
                val angle = clampedT * (Math.PI / 2.0)
                CrossfadeGains(
                    outGain = cos(angle).toFloat().coerceIn(0f, 1f),
                    inGain = sin(angle).toFloat().coerceIn(0f, 1f)
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // High-Precision Crossfade & Playback Progress Engine
    // ─────────────────────────────────────────────────────────────────────────────

    private fun startCrossfadeAndProgressLoop() {
        scope.launch {
            while (true) {
                // 35ms loop provides silky 28fps volume interpolation without excessive CPU/battery drain
                delay(35)
                val mp = mediaPlayer
                val track = _currentTrack.value

                if (_isPlaying.value) {
                    if (_isCrossfading.value) {
                        handleActiveCrossfadeTick()
                    } else if (mp != null && track != null && track.contentUri.isNotBlank()) {
                        try {
                            if (mp.isPlaying && mp.duration > 0) {
                                val posMs = mp.currentPosition
                                val durMs = mp.duration
                                _playbackProgress.value = (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
                                _currentPositionSec.value = posMs / 1000

                                // Check if we entered the crossfade window
                                val remMs = durMs - posMs
                                val cfSec = _crossfadeSec.value
                                val enabled = _crossfadeEnabled.value

                                if (enabled && cfSec > 0 && durMs > 2000) {
                                    val effectiveCfMs = if (durMs < (cfSec * 1000)) {
                                        (durMs * 0.45).toInt().coerceAtLeast(1000)
                                    } else {
                                        cfSec * 1000
                                    }

                                    if (remMs <= effectiveCfMs) {
                                        val nextTrack = getNextTrack()
                                        if (nextTrack != null) {
                                            triggerDjCrossfade(effectiveCfMs, nextTrack)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Catch transient state errors gracefully
                        }
                    }
                }
            }
        }
    }

    private fun handleActiveCrossfadeTick() {
        synchronized(crossfadeLock) {
            if (!_isCrossfading.value) return
            val now = System.currentTimeMillis()
            val elapsed = (now - crossfadeStartTimeMs).coerceAtLeast(0L)
            val duration = crossfadeTotalDurationMs.coerceAtLeast(1000L)
            val t = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            _crossfadeProgress.value = t

            val gains = calculateCrossfadeGains(t, _djCurveType.value)
            val bal = _balance.value
            val balLeft = if (bal > 0) 1.0f - bal else 1.0f
            val balRight = if (bal < 0) 1.0f + bal else 1.0f

            // Apply crossfade gains independently without altering master volume
            mediaPlayer?.let { outMp ->
                try {
                    outMp.setVolume(gains.outGain * balLeft, gains.outGain * balRight)
                } catch (_: Exception) {}
            }

            incomingMediaPlayer?.let { inMp ->
                try {
                    inMp.setVolume(gains.inGain * balLeft, gains.inGain * balRight)
                } catch (_: Exception) {}
            }

            // Also update playback position tracking to reflect the outgoing song until swap
            mediaPlayer?.let { outMp ->
                try {
                    if (outMp.duration > 0) {
                        _playbackProgress.value = (outMp.currentPosition.toFloat() / outMp.duration.toFloat()).coerceIn(0f, 1f)
                        _currentPositionSec.value = outMp.currentPosition / 1000
                    }
                } catch (_: Exception) {}
            }

            if (t >= 1f) {
                completeDjCrossfade()
            }
        }
    }

    private fun triggerDjCrossfade(durationMs: Int, nextTrack: MusicTrack) {
        synchronized(crossfadeLock) {
            if (_isCrossfading.value) return
            _isCrossfading.value = true
            _incomingTrack.value = nextTrack
            _crossfadeProgress.value = 0f
            crossfadeTotalDurationMs = durationMs.toLong()
            crossfadeStartTimeMs = System.currentTimeMillis()
            crossfadePausedAtMs = 0L

            val ctx = appContext
            if (nextTrack.contentUri.isNotBlank() && ctx != null) {
                try {
                    // Direct outgoing player completion to completeDjCrossfade so it cannot trigger playNext()
                    mediaPlayer?.let { outMp ->
                        outMp.setOnCompletionListener {
                            completeDjCrossfade()
                        }
                        outMp.setOnErrorListener { _, _, _ ->
                            completeDjCrossfade()
                            true
                        }
                    }

                    val preloaded = preloadedMediaPlayer
                    if (preloaded != null && preloadedTrackUri == nextTrack.contentUri) {
                        incomingMediaPlayer = preloaded
                        preloadedMediaPlayer = null
                        preloadedTrackUri = null
                    } else {
                        releasePreloadedPlayer()
                        incomingMediaPlayer = MediaPlayer().apply {
                            setDataSource(ctx, Uri.parse(nextTrack.contentUri))
                            prepare() // Fast prepare for local audio storage
                        }
                    }

                    incomingMediaPlayer?.apply {
                        seekTo(0)
                        setVolume(0f, 0f)
                        if (_isPlaying.value) {
                            start()
                        }
                        setOnCompletionListener {
                            completeDjCrossfade()
                        }
                        setOnErrorListener { _, _, _ ->
                            cancelCrossfadeSafely()
                            false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlaybackManager", "Failed to start incoming player: ${e.message}")
                    cancelCrossfadeSafely()
                }
            } else {
                // Built-in real-time audio synthesizer crossfade initialization
                synthIncomingTrack = nextTrack
                synthIncomingPhase = 0.0
                synthIncomingBeatPosition = 0.0
            }
        }
    }

    private fun completeDjCrossfade() {
        synchronized(crossfadeLock) {
            if (!_isCrossfading.value && incomingMediaPlayer == null && synthIncomingTrack == null) return

            val nextTrack = _incomingTrack.value
            val nextIndex = getNextTrackIndex()

            // 1. Release outgoing player cleanly after clearing listeners to avoid stale completion callbacks
            mediaPlayer?.let { oldMp ->
                try {
                    oldMp.setOnCompletionListener(null)
                    oldMp.setOnErrorListener(null)
                    if (oldMp.isPlaying) oldMp.stop()
                    oldMp.release()
                } catch (_: Exception) {}
            }

            // 2. Promote incoming player to active primary player
            val newMp = incomingMediaPlayer
            mediaPlayer = newMp
            incomingMediaPlayer = null

            if (newMp != null) {
                applyVolumeAndBalance(newMp)
                attachAudioEffects(newMp.audioSessionId)
                applyPlaybackParams()
                newMp.setOnCompletionListener {
                    onTrackCompletion()
                }
                newMp.setOnErrorListener { _, _, _ ->
                    onTrackCompletion()
                    true
                }
                if (_isPlaying.value && !newMp.isPlaying) {
                    try { newMp.start() } catch (_: Exception) {}
                }
            }

            // 3. Update current track and queue metadata, preserving incoming playback position (DO NOT reset to 0:00)
            if (nextTrack != null) {
                _currentTrack.value = nextTrack
                currentIndex = nextIndex
                if (newMp != null && newMp.duration > 0) {
                    val pos = newMp.currentPosition
                    _playbackProgress.value = (pos.toFloat() / newMp.duration.toFloat()).coerceIn(0f, 1f)
                    _currentPositionSec.value = pos / 1000
                } else {
                    _playbackProgress.value = 0f
                    _currentPositionSec.value = 0
                }
            }

            // 4. Reset crossfade status
            _isCrossfading.value = false
            _crossfadeProgress.value = 0f
            _incomingTrack.value = null
            synthIncomingTrack = null

            // 5. Pre-prepare upcoming track for the next transition
            prepareNextTrack()
        }
    }

    private fun cancelCrossfadeSafely() {
        synchronized(crossfadeLock) {
            if (!_isCrossfading.value && incomingMediaPlayer == null) return
            _isCrossfading.value = false
            _crossfadeProgress.value = 0f
            _incomingTrack.value = null
            synthIncomingTrack = null

            incomingMediaPlayer?.let { inMp ->
                try {
                    inMp.setOnCompletionListener(null)
                    inMp.setOnErrorListener(null)
                    if (inMp.isPlaying) inMp.stop()
                    inMp.release()
                } catch (_: Exception) {}
            }
            incomingMediaPlayer = null

            // Restore primary player volume and callbacks
            mediaPlayer?.let { mp ->
                applyVolumeAndBalance(mp)
                mp.setOnCompletionListener { onTrackCompletion() }
                mp.setOnErrorListener { _, _, _ -> false }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Pre-buffering & Gapless / DJ Preparation
    // ─────────────────────────────────────────────────────────────────────────────

    private fun prepareNextTrack() {
        val ctx = appContext ?: return
        val nextTrack = getNextTrack() ?: return
        if (nextTrack.contentUri.isBlank()) return
        if (preloadedTrackUri == nextTrack.contentUri && preloadedMediaPlayer != null) return

        try {
            releasePreloadedPlayer()
            preloadedMediaPlayer = MediaPlayer().apply {
                setDataSource(ctx, Uri.parse(nextTrack.contentUri))
                prepareAsync()
                setOnPreparedListener { nmp ->
                    nmp.setVolume(0f, 0f)
                    preloadedTrackUri = nextTrack.contentUri
                    // If crossfade is disabled and gapless is enabled, use standard setNextMediaPlayer
                    if (!_crossfadeEnabled.value && _gaplessEnabled.value) {
                        try {
                            mediaPlayer?.setNextMediaPlayer(nmp)
                        } catch (_: Exception) {}
                    }
                }
                setOnErrorListener { _, _, _ ->
                    releasePreloadedPlayer()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("MusicPlaybackManager", "Error preloading next track: ${e.message}")
            releasePreloadedPlayer()
        }
    }

    private fun releasePreloadedPlayer() {
        try {
            preloadedMediaPlayer?.release()
        } catch (_: Exception) {}
        preloadedMediaPlayer = null
        preloadedTrackUri = null
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Queue & Track Navigation
    // ─────────────────────────────────────────────────────────────────────────────

    fun getNextTrackIndex(): Int {
        val list = _activeQueueList.value
        if (list.isEmpty()) return 0
        if (_repeatMode.value == RepeatMode.ONE) {
            return currentIndex
        }
        if (_isShuffle.value && list.size > 1) {
            var nextIdx = Random.nextInt(list.size)
            if (nextIdx == currentIndex) nextIdx = (currentIndex + 1) % list.size
            return nextIdx
        }
        if (_repeatMode.value == RepeatMode.NONE && currentIndex >= list.size - 1) {
            return -1 // No next track at the end of queue in NONE repeat mode
        }
        return (currentIndex + 1) % list.size
    }

    fun getNextTrack(): MusicTrack? {
        val list = _activeQueueList.value
        if (list.isEmpty()) return null
        val nextIdx = getNextTrackIndex()
        return if (nextIdx in list.indices) list[nextIdx] else null
    }

    fun setQueue(queueList: List<MusicTrack>, index: Int = 0) {
        _activeQueueList.value = queueList
        currentIndex = if (queueList.isEmpty()) 0 else index.coerceIn(0, queueList.size - 1)
        prepareNextTrack()
    }

    fun clearQueue() {
        _activeQueueList.value = emptyList()
        currentIndex = 0
        releasePreloadedPlayer()
        cancelCrossfadeSafely()
    }

    fun playTrack(track: MusicTrack, queueList: List<MusicTrack>, index: Int) {
        cancelCrossfadeSafely()
        _currentTrack.value = track
        _activeQueueList.value = queueList
        currentIndex = index
        _playbackProgress.value = 0f
        _currentPositionSec.value = 0

        appContext?.let { ctx ->
            com.example.service.MusicPlaybackService.startService(ctx)
        }

        if (track.contentUri.isNotBlank() && appContext != null) {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(appContext!!, Uri.parse(track.contentUri))
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        applyVolumeAndBalance(mp)
                        attachAudioEffects(mp.audioSessionId)
                        applyPlaybackParams()
                        mp.start()
                        _isPlaying.value = true
                        prepareNextTrack()
                    }
                    setOnCompletionListener {
                        onTrackCompletion()
                    }
                    setOnErrorListener { _, _, _ ->
                        _isPlaying.value = true
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlaybackManager", "MediaPlayer play error: ${e.message}")
                _isPlaying.value = true
            }
        } else {
            if (audioTrack == null) {
                initAudioTrack()
            }
            synthPhase = 0.0
            synthBeatPosition = 0.0
            _isPlaying.value = true
        }
    }

    private fun onTrackCompletion() {
        synchronized(crossfadeLock) {
            if (_isCrossfading.value) {
                completeDjCrossfade()
                return
            }
        }
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentTrack.value?.let { track ->
                    playTrack(track, _activeQueueList.value, currentIndex)
                }
            }
            else -> playNext()
        }
    }

    fun togglePlayPause() {
        val list = _activeQueueList.value
        if (_currentTrack.value == null && list.isNotEmpty()) {
            playTrack(list.first(), list, 0)
            return
        }
        if (_currentTrack.value != null) {
            val playing = !_isPlaying.value
            _isPlaying.value = playing

            if (playing) {
                // Resuming from pause: adjust crossfade timestamp so progress stays synchronized
                if (crossfadePausedAtMs > 0L) {
                    val pauseDuration = System.currentTimeMillis() - crossfadePausedAtMs
                    crossfadeStartTimeMs += pauseDuration
                    crossfadePausedAtMs = 0L
                }
                mediaPlayer?.let { mp -> try { if (!mp.isPlaying) mp.start() } catch (_: Exception) {} }
                incomingMediaPlayer?.let { inMp -> try { if (!inMp.isPlaying) inMp.start() } catch (_: Exception) {} }
            } else {
                // Pausing: suspend crossfade transition
                if (_isCrossfading.value) {
                    crossfadePausedAtMs = System.currentTimeMillis()
                }
                mediaPlayer?.let { mp -> try { if (mp.isPlaying) mp.pause() } catch (_: Exception) {} }
                incomingMediaPlayer?.let { inMp -> try { if (inMp.isPlaying) inMp.pause() } catch (_: Exception) {} }
            }
        }
    }

    fun playNext() {
        cancelCrossfadeSafely()
        val list = _activeQueueList.value
        if (list.isNotEmpty()) {
            val nextIdx = getNextTrackIndex()
            if (nextIdx in list.indices) {
                currentIndex = nextIdx
                playTrack(list[currentIndex], list, currentIndex)
            } else {
                // End of queue in RepeatMode.NONE
                _isPlaying.value = false
                mediaPlayer?.pause()
            }
        }
    }

    fun playPrev() {
        cancelCrossfadeSafely()
        val list = _activeQueueList.value
        if (list.isNotEmpty()) {
            val prevIdx = if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
            currentIndex = prevIdx
            playTrack(list[currentIndex], list, currentIndex)
        }
    }

    fun playNextInQueue(track: MusicTrack) {
        val currentList = _activeQueueList.value.toMutableList()
        if (currentList.isEmpty()) {
            playTrack(track, listOf(track), 0)
            return
        }
        currentList.add(currentIndex + 1, track)
        _activeQueueList.value = currentList
        prepareNextTrack()
    }

    fun addToQueue(track: MusicTrack) {
        val currentList = _activeQueueList.value.toMutableList()
        currentList.add(track)
        _activeQueueList.value = currentList
        prepareNextTrack()
    }

    fun removeFromQueue(index: Int) {
        val currentList = _activeQueueList.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _activeQueueList.value = currentList
            if (index < currentIndex) {
                currentIndex--
            } else if (index == currentIndex && currentList.isNotEmpty()) {
                val newIndex = currentIndex.coerceAtMost(currentList.size - 1)
                playTrack(currentList[newIndex], currentList, newIndex)
            }
            prepareNextTrack()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Playback Modes & Settings Toggles
    // ─────────────────────────────────────────────────────────────────────────────

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        prepareNextTrack()
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        prepareNextTrack()
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        if (!enabled) {
            cancelCrossfadeSafely()
        }
        persistSettings()
        prepareNextTrack()
    }

    fun setCrossfadeSec(seconds: Int) {
        val clamped = seconds.coerceIn(0, 20)
        _crossfadeSec.value = clamped
        _crossfadeEnabled.value = clamped > 0
        persistSettings()
        prepareNextTrack()
    }

    fun setCrossfade(seconds: Int) {
        setCrossfadeSec(seconds)
    }

    fun setDjModeEnabled(enabled: Boolean) {
        _djModeEnabled.value = enabled
        persistSettings()
    }

    fun setDjCurveType(curve: String) {
        _djCurveType.value = curve
        persistSettings()
    }

    fun toggleGapless() {
        _gaplessEnabled.value = !_gaplessEnabled.value
        prepareNextTrack()
    }

    fun setGapless(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        prepareNextTrack()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Seeking & Audio Controls
    // ─────────────────────────────────────────────────────────────────────────────

    fun seekToProgress(progress: Float) {
        val currentTrackDuration = _currentTrack.value?.durationSec ?: 100
        val targetProgress = progress.coerceIn(0f, 1f)
        _playbackProgress.value = targetProgress
        _currentPositionSec.value = (targetProgress * currentTrackDuration).toInt()

        mediaPlayer?.let { mp ->
            try {
                if (mp.duration > 0) {
                    val targetMs = (targetProgress * mp.duration).toInt()
                    val remMs = mp.duration - targetMs
                    val cfMs = _crossfadeSec.value * 1000

                    // If user seeks backwards out of the crossfade window, cancel incoming transition
                    if (_isCrossfading.value && remMs > cfMs) {
                        cancelCrossfadeSafely()
                    }
                    mp.seekTo(targetMs)
                }
            } catch (_: Exception) {}
        }
    }

    fun setBalance(bal: Float) {
        _balance.value = bal.coerceIn(-1.0f, 1.0f)
        if (!_isCrossfading.value) {
            mediaPlayer?.let { applyVolumeAndBalance(it) }
        }
    }

    private fun applyVolumeAndBalance(mp: MediaPlayer) {
        val bal = _balance.value
        val left = if (bal > 0) 1.0f - bal else 1.0f
        val right = if (bal < 0) 1.0f + bal else 1.0f
        try {
            mp.setVolume(left, right)
        } catch (_: Exception) {}
    }

    fun setSpeed(value: Float) {
        _speed.value = value.coerceIn(0.25f, 3.0f)
        applyPlaybackParams()
    }

    fun setPitch(value: Float) {
        _pitch.value = value.coerceIn(0.5f, 2.0f)
        applyPlaybackParams()
    }

    private fun applyPlaybackParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                try {
                    val params = mp.playbackParams
                    params.speed = _speed.value
                    params.pitch = _pitch.value
                    mp.playbackParams = params
                } catch (e: Exception) {
                    Log.e("MusicPlaybackManager", "Error setting PlaybackParams: ${e.message}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Audio DSP Effects (Equalizer, BassBoost, Virtualizer, Reverb)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun attachAudioEffects(audioSessionId: Int) {
        try {
            releaseAudioEffects()
            equalizerFx = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            applyEqualizerGainsToFx()

            bassBoostFx = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength((_bassBoostStrength.value * 10).toShort())
            }
            presetReverbFx = PresetReverb(0, audioSessionId).apply {
                enabled = true
                preset = getReverbPresetShort(_reverbPreset.value)
            }
            virtualizerFx = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength((_virtualizerStrength.value * 10).toShort())
            }
        } catch (e: Exception) {
            Log.e("MusicPlaybackManager", "Failed attaching audio effects: ${e.message}")
        }
    }

    private fun releaseAudioEffects() {
        try {
            equalizerFx?.release()
            bassBoostFx?.release()
            presetReverbFx?.release()
            virtualizerFx?.release()
        } catch (_: Exception) {}
        equalizerFx = null
        bassBoostFx = null
        presetReverbFx = null
        virtualizerFx = null
    }

    private fun getReverbPresetShort(presetName: String): Short {
        return when (presetName.lowercase()) {
            "small room" -> PresetReverb.PRESET_SMALLROOM
            "medium room" -> PresetReverb.PRESET_MEDIUMROOM
            "large hall" -> PresetReverb.PRESET_LARGEHALL
            "plate" -> PresetReverb.PRESET_PLATE
            else -> PresetReverb.PRESET_NONE
        }
    }

    fun setBassBoost(strength: Int) {
        _bassBoostStrength.value = strength.coerceIn(0, 100)
        try {
            bassBoostFx?.setStrength((_bassBoostStrength.value * 10).toShort())
        } catch (_: Exception) {}
    }

    fun setVirtualizer(strength: Int) {
        _virtualizerStrength.value = strength.coerceIn(0, 100)
        try {
            virtualizerFx?.setStrength((_virtualizerStrength.value * 10).toShort())
        } catch (_: Exception) {}
    }

    fun setReverbPreset(preset: String) {
        _reverbPreset.value = preset
        try {
            presetReverbFx?.preset = getReverbPresetShort(preset)
        } catch (_: Exception) {}
    }

    fun setEqualizerPreset(preset: String) {
        val gains = when (preset.lowercase()) {
            "flat" -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
            "bass booster" -> floatArrayOf(8f, 5f, 0f, -2f, -4f)
            "vocal booster" -> floatArrayOf(-3f, 1f, 6f, 5f, 1f)
            "pop" -> floatArrayOf(-1.5f, 2f, 5f, 3f, -1f)
            "rock" -> floatArrayOf(5f, 3f, -1f, 3f, 6f)
            "classical" -> floatArrayOf(4f, 3f, 1f, 3f, 4f)
            "hip hop" -> floatArrayOf(6f, 4f, 0f, 2f, 3f)
            "jazz" -> floatArrayOf(4f, 2f, -1f, 2f, 5f)
            else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        }
        updateEqualizerGains(gains)
    }

    fun updateEqualizerGains(gains: FloatArray) {
        _eqGains.value = gains.copyOf()
        applyEqualizerGainsToFx()
    }

    private fun applyEqualizerGainsToFx() {
        val eq = equalizerFx ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val gains = _eqGains.value
            val range = eq.bandLevelRange
            val minMb = range[0]
            val maxMb = range[1]

            for (i in 0 until numBands) {
                if (i < gains.size) {
                    val db = gains[i]
                    val mB = (db * 100).toInt().coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
                    eq.setBandLevel(i.toShort(), mB)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicPlaybackManager", "Error applying EQ band levels: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Real-Time Audio Synthesizer with DJ Crossfade Support
    // ─────────────────────────────────────────────────────────────────────────────

    private fun initAudioTrack() {
        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE * 2,
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
        } catch (e: Throwable) {
            try {
                Log.e("MusicPlaybackManager", "Error initializing AudioTrack: ${e.message}")
            } catch (_: Throwable) {}
        }
    }

    private fun computeSynthTrackSample(track: MusicTrack, currentPhase: Double, currentBeat: Double): Double {
        val pitchFactor = _pitch.value
        val speedFactor = _speed.value
        val eqMultipliers = _eqGains.value.map { db -> Math.pow(10.0, db / 20.0).toFloat() }.toFloatArray()

        val tempoBpm = when (track.id % 5) {
            0 -> 120.0
            1 -> 80.0
            2 -> 65.0
            3 -> 95.0
            else -> 110.0
        } * speedFactor

        val keyRootFreq = when (track.id % 4) {
            0 -> 130.81
            1 -> 146.83
            2 -> 110.00
            else -> 116.54
        } * pitchFactor

        val barStep = (currentBeat * 4.0).toInt() % 16
        val melodyIntervalMultiplier = when (barStep) {
            0 -> 1.0
            2 -> 1.25
            4 -> 1.5
            6 -> 1.66
            8 -> 2.0
            10 -> 2.5
            12 -> 1.5
            14 -> 1.8
            else -> 0.0
        }

        var sample = 0.0
        when {
            track.genre.contains("Ambient", ignoreCase = true) -> {
                val carrier = sin(currentPhase * keyRootFreq * 2.0 * Math.PI / SAMPLE_RATE)
                val subOsc = sin(currentPhase * (keyRootFreq * 0.5) * 2.0 * Math.PI / SAMPLE_RATE) * 0.6
                var lead = 0.0
                if (melodyIntervalMultiplier > 0.0) {
                    val leadFreq = keyRootFreq * 2.0 * melodyIntervalMultiplier
                    lead = sin(currentPhase * leadFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.4
                }
                sample = (carrier + subOsc + lead) * 0.35
            }
            track.genre.contains("Synthwave", ignoreCase = true) -> {
                val bassFreq = if ((currentBeat.toInt() % 2) == 0) keyRootFreq else keyRootFreq * 1.12
                val bass = if (sin(currentPhase * bassFreq * 2.0 * Math.PI / SAMPLE_RATE) > 0.0) 0.35 else -0.35
                var lead = 0.0
                if (melodyIntervalMultiplier > 0.0 && (barStep % 2 == 0)) {
                    val leadFreq = keyRootFreq * 3.0 * melodyIntervalMultiplier
                    val leadPhase = (currentPhase * leadFreq / SAMPLE_RATE) % 1.0
                    lead = (leadPhase - 0.5) * 0.3
                }
                sample = (bass + lead) * 0.3
            }
            else -> {
                val fundamental = sin(currentPhase * keyRootFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.4
                val overtone = sin(currentPhase * keyRootFreq * 3.0 * 2.0 * Math.PI / SAMPLE_RATE) * 0.15
                var lead = 0.0
                if (melodyIntervalMultiplier > 0.0) {
                    val leadFreq = keyRootFreq * 2.0 * melodyIntervalMultiplier
                    lead = sin(currentPhase * leadFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.25
                }
                sample = (fundamental + overtone + lead) * 0.4
            }
        }

        val bassFreqCut = 120.0
        val trebleFreqCut = 1500.0
        val currentSampleFreq = keyRootFreq * (if (melodyIntervalMultiplier > 0.0) melodyIntervalMultiplier else 1.0)
        val sampleGainFactor = when {
            currentSampleFreq < bassFreqCut -> eqMultipliers[0] * 0.8f + eqMultipliers[1] * 0.2f
            currentSampleFreq > trebleFreqCut -> eqMultipliers[4] * 0.7f + eqMultipliers[3] * 0.3f
            else -> eqMultipliers[2]
        }

        sample *= sampleGainFactor
        return sample.coerceIn(-1.0, 1.0)
    }

    private fun startSynthesisLoop() {
        isThreadRunning = true
        synthesisThread = Thread {
            val buffer = ShortArray(BUFFER_SIZE)
            while (isThreadRunning) {
                val track = _currentTrack.value
                val isPlay = _isPlaying.value

                if (isPlay && track != null && track.contentUri.isBlank()) {
                    val speedFactor = _speed.value
                    val tempoBpmA = 100.0 * speedFactor
                    val tempoBpmB = 105.0 * speedFactor
                    val cfProgress = _crossfadeProgress.value
                    val isCf = _isCrossfading.value
                    val gains = if (isCf) calculateCrossfadeGains(cfProgress, _djCurveType.value) else CrossfadeGains(1f, 0f)

                    for (i in 0 until BUFFER_SIZE) {
                        synthBeatPosition += (1.0 / SAMPLE_RATE) * (tempoBpmA / 60.0)
                        if (synthBeatPosition >= 16.0) synthBeatPosition -= 16.0

                        var sampleA = computeSynthTrackSample(track, synthPhase, synthBeatPosition)

                        var sample = sampleA * gains.outGain

                        val incoming = synthIncomingTrack
                        if (isCf && incoming != null && incoming.contentUri.isBlank()) {
                            synthIncomingBeatPosition += (1.0 / SAMPLE_RATE) * (tempoBpmB / 60.0)
                            if (synthIncomingBeatPosition >= 16.0) synthIncomingBeatPosition -= 16.0
                            val sampleB = computeSynthTrackSample(incoming, synthIncomingPhase, synthIncomingBeatPosition)
                            sample += (sampleB * gains.inGain)
                            synthIncomingPhase += 1.0
                            if (synthIncomingPhase > SAMPLE_RATE * 1000) synthIncomingPhase = 0.0
                        }

                        sample = sample.coerceIn(-1.0, 1.0)
                        buffer[i] = (sample * 32767.0).toInt().toShort()

                        synthPhase += 1.0
                        if (synthPhase > SAMPLE_RATE * 1000) synthPhase = 0.0
                    }

                    try {
                        audioTrack?.write(buffer, 0, BUFFER_SIZE)
                    } catch (e: Exception) {
                        Log.e("MusicPlaybackManager", "PCM write error: ${e.message}")
                    }

                    scope.launch {
                        val currentProgress = _playbackProgress.value
                        val newProgress = currentProgress + (BUFFER_SIZE.toFloat() / SAMPLE_RATE) / track.durationSec
                        val durMs = track.durationSec * 1000
                        val posMs = (newProgress * durMs).toInt()
                        val remMs = durMs - posMs

                        val cfSec = _crossfadeSec.value
                        val enabled = _crossfadeEnabled.value

                        if (enabled && cfSec > 0 && !_isCrossfading.value && remMs <= (cfSec * 1000) && durMs > 2000) {
                            val next = getNextTrack()
                            if (next != null) {
                                triggerDjCrossfade(cfSec * 1000, next)
                            }
                        }

                        if (newProgress >= 1f) {
                            _playbackProgress.value = 0f
                            _currentPositionSec.value = 0
                            if (_isCrossfading.value) {
                                completeDjCrossfade()
                            } else {
                                onTrackCompletion()
                            }
                        } else {
                            _playbackProgress.value = newProgress
                            _currentPositionSec.value = (newProgress * track.durationSec).toInt()
                        }
                    }
                } else {
                    try {
                        Thread.sleep(60)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Sleep Timer & Teardown
    // ─────────────────────────────────────────────────────────────────────────────

    fun startSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        if (minutes > 0) {
            scope.launch {
                while (_sleepTimerMinutes.value > 0 && _isPlaying.value) {
                    delay(60000)
                    val nextMinutes = _sleepTimerMinutes.value - 1
                    _sleepTimerMinutes.value = nextMinutes
                    if (nextMinutes <= 0) {
                        _isPlaying.value = false
                        mediaPlayer?.pause()
                        incomingMediaPlayer?.pause()
                        _sleepTimerMinutes.value = 0
                        break
                    }
                }
            }
        }
    }

    fun stopSleepTimer() {
        _sleepTimerMinutes.value = 0
    }

    fun stopAll() {
        isThreadRunning = false
        _isPlaying.value = false
        releaseAudioEffects()
        cancelCrossfadeSafely()
        releasePreloadedPlayer()

        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null

        incomingMediaPlayer?.let { inMp ->
            try {
                if (inMp.isPlaying) inMp.stop()
                inMp.release()
            } catch (_: Exception) {}
        }
        incomingMediaPlayer = null

        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        audioTrack = null
        synthesisThread?.interrupt()
    }
}
