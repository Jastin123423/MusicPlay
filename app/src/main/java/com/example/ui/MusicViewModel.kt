package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.MusicPlaybackManager
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.data.MusicTrack
import com.example.data.PlaybackQueue
import com.example.data.Playlist
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MusicDatabase.getDatabase(application)
    private val repository = MusicRepository(db.musicDao())

    val playbackManager = MusicPlaybackManager.getInstance()

    // Observable states from DB
    val allTracks: StateFlow<List<MusicTrack>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allQueues: StateFlow<List<PlaybackQueue>> = repository.allQueues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI State selectors
    private val _selectedQueueId = MutableStateFlow(1) // Default to primary queue
    val selectedQueueId = _selectedQueueId.asStateFlow()

    private val _currentTab = MutableStateFlow("Queue") // Queue, Folders, Songs, Albums, Artists, Playlists
    val currentTab = _currentTab.asStateFlow()

    // Active track list and details reflecting the Room db state for the active queue
    private val _queueTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val queueTracks = _queueTracks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    init {
        playbackManager.setContext(application)
        viewModelScope.launch {
            try {
                _isScanning.value = true
                repository.scanDeviceAudioFiles(application)
                loadQueue(selectedQueueId.value)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error initializing local music: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
        
        // Keep queueTracks in sync when DB tracks change
        viewModelScope.launch {
            combine(allTracks, selectedQueueId, allQueues) { tracks, qId, queues ->
                val activeQueue = queues.find { it.queueId == qId }
                if (activeQueue != null && tracks.isNotEmpty()) {
                    val idList = activeQueue.trackIdsString.split(",")
                        .filter { it.isNotEmpty() }
                        .mapNotNull { it.toIntOrNull() }
                    
                    val map = tracks.associateBy { it.id }
                    val orderedTracks = idList.mapNotNull { map[it] }
                    _queueTracks.value = orderedTracks
                    
                    // If the playback manager queue has not been loaded, initialize it!
                    if (playbackManager.activeQueueList.value.isEmpty() && orderedTracks.isNotEmpty()) {
                        playbackManager.setQueue(orderedTracks, activeQueue.currentIndex)
                    }
                }
            }.collect()
        }
    }

    // MULTI-QUEUE ACTIONS (Iconic Musicolet!)
    fun loadQueue(queueId: Int) {
        viewModelScope.launch {
            _selectedQueueId.value = queueId
            val queue = repository.getQueueById(queueId)
            val tracks = allTracks.first()
            if (queue != null && tracks.isNotEmpty()) {
                val idList = queue.trackIdsString.split(",")
                    .filter { it.isNotEmpty() }
                    .mapNotNull { it.toIntOrNull() }
                
                val map = tracks.associateBy { it.id }
                val orderedTracks = idList.mapNotNull { map[it] }
                
                _queueTracks.value = orderedTracks
                playbackManager.setQueue(orderedTracks, queue.currentIndex)
                if (orderedTracks.isNotEmpty()) {
                    playbackManager.seekToProgress(0f)
                }
            }
        }
    }

    fun addQueue(name: String) {
        viewModelScope.launch {
            val nextQueueId = (allQueues.value.maxOfOrNull { it.queueId } ?: 0) + 1
            if (nextQueueId <= 20) { // Musicolet limit approx
                val newQueue = PlaybackQueue(
                    queueId = nextQueueId,
                    name = name,
                    trackIdsString = "",
                    currentIndex = 0,
                    currentPositionMs = 0L
                )
                repository.insertQueue(newQueue)
                loadQueue(nextQueueId)
            }
        }
    }

    fun removeQueue(queueId: Int) {
        viewModelScope.launch {
            if (allQueues.value.size > 1) {
                repository.deleteQueue(queueId)
                // Select another queue
                val remaining = allQueues.value.filter { it.queueId != queueId }
                if (remaining.isNotEmpty()) {
                    loadQueue(remaining.first().queueId)
                }
            }
        }
    }

    fun addTrackToActiveQueue(trackId: Int) {
        viewModelScope.launch {
            val qId = selectedQueueId.value
            val queue = repository.getQueueById(qId) ?: return@launch
            val ids = queue.trackIdsString.split(",").filter { it.isNotEmpty() }.toMutableList()
            ids.add(trackId.toString())
            val updatedString = ids.joinToString(",")
            val updatedQueue = queue.copy(trackIdsString = updatedString)
            repository.updateQueue(updatedQueue)
            loadQueue(qId)
        }
    }

    fun removeTrackFromActiveQueue(index: Int) {
        viewModelScope.launch {
            val qId = selectedQueueId.value
            val queue = repository.getQueueById(qId) ?: return@launch
            val ids = queue.trackIdsString.split(",").filter { it.isNotEmpty() }.toMutableList()
            if (index in ids.indices) {
                ids.removeAt(index)
                val updatedString = ids.joinToString(",")
                // Adjust index if needed
                var activeIdx = playbackManager.currentIndex
                if (activeIdx >= ids.size) {
                    activeIdx = (ids.size - 1).coerceAtLeast(0)
                }
                val updatedQueue = queue.copy(trackIdsString = updatedString, currentIndex = activeIdx)
                repository.updateQueue(updatedQueue)
                loadQueue(qId)
            }
        }
    }

    fun clearActiveQueue() {
        viewModelScope.launch {
            val qId = selectedQueueId.value
            val queue = repository.getQueueById(qId) ?: return@launch
            val updatedQueue = queue.copy(trackIdsString = "", currentIndex = 0)
            repository.updateQueue(updatedQueue)
            _queueTracks.value = emptyList()
            playbackManager.clearQueue()
            playbackManager.seekToProgress(0f)
        }
    }

    // SONG TAG EDIT ACTIONS (Famous Musicolet!)
    fun updateTrackTags(
        trackId: Int,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        trackNumber: String,
        lyrics: String
    ) {
        viewModelScope.launch {
            val originalTrack = repository.getTrackById(trackId)
            if (originalTrack != null) {
                val updatedTrack = originalTrack.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    genre = genre,
                    year = year,
                    trackNumber = trackNumber,
                    lyrics = lyrics
                )
                repository.updateTrack(updatedTrack)
                
                // If it's the current track, refresh it in the player as well
                if (playbackManager.currentTrack.value?.id == trackId) {
                    playbackManager.playTrack(
                        updatedTrack,
                        playbackManager.activeQueueList.value.map { if (it.id == trackId) updatedTrack else it },
                        playbackManager.currentIndex
                    )
                }
            }
        }
    }

    suspend fun getPlaylistById(playlistId: Int): Playlist? {
        return repository.getPlaylistById(playlistId)
    }

    // PLAYLISTS ACTIONS (Musicolet!)
    fun createPlaylist(name: String, description: String) {
        viewModelScope.launch {
            val newPlaylist = Playlist(
                name = name,
                description = description,
                trackIdsString = ""
            )
            repository.insertPlaylist(newPlaylist)
        }
    }

    fun addTrackToPlaylist(playlistId: Int, trackId: Int) {
        viewModelScope.launch {
            val playlist = repository.getPlaylistById(playlistId) ?: return@launch
            val stringIds = playlist.trackIdsString.split(",").filter { it.isNotEmpty() }.toMutableList()
            if (!stringIds.contains(trackId.toString())) {
                stringIds.add(trackId.toString())
                val updatedPlaylist = playlist.copy(trackIdsString = stringIds.joinToString(","))
                repository.updatePlaylist(updatedPlaylist)
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Int, trackId: Int) {
        viewModelScope.launch {
            val playlist = repository.getPlaylistById(playlistId) ?: return@launch
            val stringIds = playlist.trackIdsString.split(",").filter { it.isNotEmpty() }.toMutableList()
            stringIds.remove(trackId.toString())
            val updatedPlaylist = playlist.copy(trackIdsString = stringIds.joinToString(","))
            repository.updatePlaylist(updatedPlaylist)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    // FAVORITES (Toggle status)
    fun toggleFavorite(track: MusicTrack) {
        viewModelScope.launch {
            val updated = track.copy(isFavorite = !track.isFavorite)
            repository.updateTrack(updated)
            // Refresh current playing state if modified
            if (playbackManager.currentTrack.value?.id == track.id) {
                playbackManager.playTrack(
                    updated,
                    playbackManager.activeQueueList.value.map { if (it.id == track.id) updated else it },
                    playbackManager.currentIndex
                )
            }
        }
    }

    fun setTab(tab: String) {
        _currentTab.value = tab
    }

    fun scanLocalMusic(context: android.content.Context) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.scanDeviceAudioFiles(context)
                loadQueue(selectedQueueId.value)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error scanning device music: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.stopAll()
    }
}
