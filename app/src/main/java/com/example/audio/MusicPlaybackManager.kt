package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import kotlin.math.sin
import kotlin.random.Random

enum class RepeatMode {
    NONE, ALL, ONE
}

class MusicPlaybackManager {

    companion object {
        const val SAMPLE_RATE = 22050
        private const val BUFFER_SIZE = 1024

        @Volatile
        private var instance: MusicPlaybackManager? = null

        fun getInstance(): MusicPlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlaybackManager().also { instance = it }
            }
        }
    }

    private var appContext: Context? = null
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var nextMediaPlayer: MediaPlayer? = null
    private var synthesisThread: Thread? = null
    private var isThreadRunning = false

    // Audio effects hardware handles
    private var equalizerFx: Equalizer? = null
    private var bassBoostFx: BassBoost? = null
    private var presetReverbFx: PresetReverb? = null
    private var virtualizerFx: Virtualizer? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())

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

    private val _crossfadeSec = MutableStateFlow(0) // 0, 1, 2, 3, 5 seconds
    val crossfadeSec = _crossfadeSec.asStateFlow()

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
    private val _bassBoostStrength = MutableStateFlow(0) // 0 - 100
    val bassBoostStrength = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0) // 0 - 100
    val virtualizerStrength = _virtualizerStrength.asStateFlow()

    private val _reverbPreset = MutableStateFlow("None")
    val reverbPreset = _reverbPreset.asStateFlow()

    private val _balance = MutableStateFlow(0f) // -1.0 (Left) to +1.0 (Right)
    val balance = _balance.asStateFlow()

    // Sleep timer in minutes remaining (0 means disabled)
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes = _sleepTimerMinutes.asStateFlow()

    // Current queue tracking
    var currentIndex: Int = 0
    private val _activeQueueList = MutableStateFlow<List<MusicTrack>>(emptyList())
    val activeQueueList = _activeQueueList.asStateFlow()

    private var phase = 0.0
    private var beatPosition = 0.0

    init {
        initAudioTrack()
        startSynthesisLoop()
        startMediaPlayerProgressLoop()
    }

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun startMediaPlayerProgressLoop() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(200)
                val mp = mediaPlayer
                val track = _currentTrack.value
                if (mp != null && _isPlaying.value && track != null && track.contentUri.isNotBlank()) {
                    try {
                        if (mp.isPlaying && mp.duration > 0) {
                            val posMs = mp.currentPosition
                            val durMs = mp.duration
                            _playbackProgress.value = (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
                            _currentPositionSec.value = (posMs / 1000)

                            // Handle crossfade near end of song if configured
                            val remMs = durMs - posMs
                            val cfSec = _crossfadeSec.value
                            if (cfSec > 0 && remMs <= (cfSec * 1000) && remMs > 500) {
                                val fadeRatio = (remMs.toFloat() / (cfSec * 1000f)).coerceIn(0f, 1f)
                                mp.setVolume(fadeRatio, fadeRatio)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore transient state errors
                    }
                }
            }
        }
    }

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
        } catch (e: Exception) {
            Log.e("MusicPlaybackManager", "Error initializing AudioTrack: ${e.message}")
        }
    }

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
        } catch (e: Exception) {}
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

    private fun startSynthesisLoop() {
        isThreadRunning = true
        synthesisThread = Thread {
            val buffer = ShortArray(BUFFER_SIZE)
            while (isThreadRunning) {
                val track = _currentTrack.value
                if (_isPlaying.value && track != null && track.contentUri.isBlank()) {
                    val pitchFactor = _pitch.value
                    val speedFactor = _speed.value
                    val eqMultipliers = _eqGains.value.map { db -> Math.pow(10.0, db / 20.0).toFloat() }.toFloatArray()

                    for (i in 0 until BUFFER_SIZE) {
                        var sample = 0.0

                        val tempoBpm = when (track.id % 5) {
                            0 -> 120.0
                            1 -> 80.0
                            2 -> 65.0
                            3 -> 95.0
                            else -> 110.0
                        } * speedFactor

                        beatPosition += (1.0 / SAMPLE_RATE) * (tempoBpm / 60.0)
                        if (beatPosition >= 16.0) {
                            beatPosition -= 16.0
                        }

                        val keyRootFreq = when (track.id % 4) {
                            0 -> 130.81
                            1 -> 146.83
                            2 -> 110.00
                            else -> 116.54
                        } * pitchFactor

                        val barStep = (beatPosition * 4.0).toInt() % 16
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

                        when {
                            track.genre.contains("Ambient", ignoreCase = true) -> {
                                val carrier = sin(phase * keyRootFreq * 2.0 * Math.PI / SAMPLE_RATE)
                                val subOsc = sin(phase * (keyRootFreq * 0.5) * 2.0 * Math.PI / SAMPLE_RATE) * 0.6
                                var lead = 0.0
                                if (melodyIntervalMultiplier > 0.0) {
                                    val leadFreq = keyRootFreq * 2.0 * melodyIntervalMultiplier
                                    lead = sin(phase * leadFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.4
                                }
                                sample = (carrier + subOsc + lead) * 0.35
                            }
                            track.genre.contains("Synthwave", ignoreCase = true) -> {
                                val bassFreq = if ((beatPosition.toInt() % 2) == 0) keyRootFreq else keyRootFreq * 1.12
                                val bass = if (sin(phase * bassFreq * 2.0 * Math.PI / SAMPLE_RATE) > 0.0) 0.35 else -0.35
                                var lead = 0.0
                                if (melodyIntervalMultiplier > 0.0 && (barStep % 2 == 0)) {
                                    val leadFreq = keyRootFreq * 3.0 * melodyIntervalMultiplier
                                    val leadPhase = (phase * leadFreq / SAMPLE_RATE) % 1.0
                                    lead = (leadPhase - 0.5) * 0.3
                                }
                                sample = (bass + lead) * 0.3
                            }
                            else -> {
                                val fundamental = sin(phase * keyRootFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.4
                                val overtone = sin(phase * keyRootFreq * 3.0 * 2.0 * Math.PI / SAMPLE_RATE) * 0.15
                                var lead = 0.0
                                if (melodyIntervalMultiplier > 0.0) {
                                    val leadFreq = keyRootFreq * 2.0 * melodyIntervalMultiplier
                                    lead = sin(phase * leadFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.25
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
                        if (sample > 1.0) sample = 1.0
                        if (sample < -1.0) sample = -1.0

                        buffer[i] = (sample * 32767.0).toInt().toShort()

                        phase += 1.0
                        if (phase > SAMPLE_RATE * 1000) {
                            phase = 0.0
                        }
                    }

                    try {
                        audioTrack?.write(buffer, 0, BUFFER_SIZE)
                    } catch (e: Exception) {
                        Log.e("MusicPlaybackManager", "PCM write error: ${e.message}")
                    }

                    scope.launch {
                        val currentProgress = _playbackProgress.value
                        val newProgress = currentProgress + (BUFFER_SIZE.toFloat() / SAMPLE_RATE) / track.durationSec
                        if (newProgress >= 1f) {
                            _playbackProgress.value = 0f
                            _currentPositionSec.value = 0
                            onTrackCompletion()
                        } else {
                            _playbackProgress.value = newProgress
                            _currentPositionSec.value = (newProgress * track.durationSec).toInt()
                        }
                    }
                } else {
                    try {
                        Thread.sleep(60)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun setQueue(queueList: List<MusicTrack>, index: Int = 0) {
        _activeQueueList.value = queueList
        currentIndex = if (queueList.isEmpty()) 0 else index.coerceIn(0, queueList.size - 1)
    }

    fun clearQueue() {
        _activeQueueList.value = emptyList()
        currentIndex = 0
    }

    fun playTrack(track: MusicTrack, queueList: List<MusicTrack>, index: Int) {
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
                        setupNextGaplessTrack()
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
            _isPlaying.value = true
        }
    }

    private fun setupNextGaplessTrack() {
        val mp = mediaPlayer ?: return
        val list = _activeQueueList.value
        if (!_gaplessEnabled.value || list.isEmpty()) return

        val nextIndex = getNextTrackIndex()
        if (nextIndex >= 0 && nextIndex < list.size) {
            val nextTrack = list[nextIndex]
            if (nextTrack.contentUri.isNotBlank() && appContext != null) {
                try {
                    nextMediaPlayer?.release()
                    nextMediaPlayer = MediaPlayer().apply {
                        setDataSource(appContext!!, Uri.parse(nextTrack.contentUri))
                        prepareAsync()
                        setOnPreparedListener { nmp ->
                            try {
                                mp.setNextMediaPlayer(nmp)
                            } catch (e: Exception) {
                                Log.e("MusicPlaybackManager", "Gapless setup failed: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlaybackManager", "Next player error: ${e.message}")
                }
            }
        }
    }

    private fun onTrackCompletion() {
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
            mediaPlayer?.let { mp ->
                try {
                    if (playing) {
                        if (!mp.isPlaying) mp.start()
                    } else {
                        if (mp.isPlaying) mp.pause()
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlaybackManager", "Toggle error: ${e.message}")
                }
            }
        }
    }

    private fun getNextTrackIndex(): Int {
        val list = _activeQueueList.value
        if (list.isEmpty()) return 0
        if (_isShuffle.value && list.size > 1) {
            var nextIdx = Random.nextInt(list.size)
            if (nextIdx == currentIndex) nextIdx = (currentIndex + 1) % list.size
            return nextIdx
        }
        return (currentIndex + 1) % list.size
    }

    fun playNext() {
        val list = _activeQueueList.value
        if (list.isNotEmpty()) {
            val nextIdx = getNextTrackIndex()
            currentIndex = nextIdx
            val nextTrack = list[currentIndex]
            playTrack(nextTrack, list, currentIndex)
        }
    }

    fun playPrev() {
        val list = _activeQueueList.value
        if (list.isNotEmpty()) {
            val prevIdx = if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
            currentIndex = prevIdx
            val prevTrack = list[currentIndex]
            playTrack(prevTrack, list, currentIndex)
        }
    }

    // Queue Management
    fun playNextInQueue(track: MusicTrack) {
        val currentList = _activeQueueList.value.toMutableList()
        if (currentList.isEmpty()) {
            playTrack(track, listOf(track), 0)
            return
        }
        currentList.add(currentIndex + 1, track)
        _activeQueueList.value = currentList
    }

    fun addToQueue(track: MusicTrack) {
        val currentList = _activeQueueList.value.toMutableList()
        currentList.add(track)
        _activeQueueList.value = currentList
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
        }
    }

    // Playback Modes Toggles
    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun setCrossfadeSec(seconds: Int) {
        _crossfadeSec.value = seconds.coerceIn(0, 5)
    }

    fun setCrossfade(seconds: Int) {
        setCrossfadeSec(seconds)
    }

    fun toggleGapless() {
        _gaplessEnabled.value = !_gaplessEnabled.value
    }

    fun setGapless(enabled: Boolean) {
        _gaplessEnabled.value = enabled
    }

    // Audio Effects Setters
    fun setBassBoost(strength: Int) {
        _bassBoostStrength.value = strength.coerceIn(0, 100)
        try {
            bassBoostFx?.setStrength((_bassBoostStrength.value * 10).toShort())
        } catch (e: Exception) {}
    }

    fun setVirtualizer(strength: Int) {
        _virtualizerStrength.value = strength.coerceIn(0, 100)
        try {
            virtualizerFx?.setStrength((_virtualizerStrength.value * 10).toShort())
        } catch (e: Exception) {}
    }

    fun setReverbPreset(preset: String) {
        _reverbPreset.value = preset
        try {
            presetReverbFx?.preset = getReverbPresetShort(preset)
        } catch (e: Exception) {}
    }

    fun setBalance(bal: Float) {
        _balance.value = bal.coerceIn(-1.0f, 1.0f)
        mediaPlayer?.let { applyVolumeAndBalance(it) }
    }

    private fun applyVolumeAndBalance(mp: MediaPlayer) {
        val bal = _balance.value
        val left = if (bal > 0) 1.0f - bal else 1.0f
        val right = if (bal < 0) 1.0f + bal else 1.0f
        try {
            mp.setVolume(left, right)
        } catch (e: Exception) {}
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

    fun seekToProgress(progress: Float) {
        val currentTrackDuration = _currentTrack.value?.durationSec ?: 100
        val targetProgress = progress.coerceIn(0f, 1f)
        _playbackProgress.value = targetProgress
        _currentPositionSec.value = (targetProgress * currentTrackDuration).toInt()

        mediaPlayer?.let { mp ->
            try {
                if (mp.duration > 0) {
                    val targetMs = (targetProgress * mp.duration).toInt()
                    mp.seekTo(targetMs)
                }
            } catch (e: Exception) {
                // silent catch
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        if (minutes > 0) {
            scope.launch {
                while (_sleepTimerMinutes.value > 0 && _isPlaying.value) {
                    kotlinx.coroutines.delay(60000)
                    val nextMinutes = _sleepTimerMinutes.value - 1
                    _sleepTimerMinutes.value = nextMinutes
                    if (nextMinutes <= 0) {
                        _isPlaying.value = false
                        mediaPlayer?.pause()
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
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (e: Exception) {}
        }
        mediaPlayer = null
        nextMediaPlayer?.let { nmp ->
            try { nmp.release() } catch (e: Exception) {}
        }
        nextMediaPlayer = null
        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {}
        audioTrack = null
        synthesisThread?.interrupt()
    }
}

