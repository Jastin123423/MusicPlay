package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_tracks")
data class MusicTrack(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String,
    val trackNumber: String,
    val durationSec: Int,
    val lyrics: String,
    val folder: String,
    val isFavorite: Boolean = false,
    val contentUri: String = "",
    val albumArtUri: String = "",
    val bitrate: String = "",
    val sampleRate: String = ""
)

@Entity(tableName = "playback_queues")
data class PlaybackQueue(
    @PrimaryKey val queueId: Int, // E.g. 1 to 20
    val name: String,
    val trackIdsString: String, // Comma separated list of Int IDs, e.g. "1,2,3"
    val currentIndex: Int = 0,
    val currentPositionMs: Long = 0L
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val playlistId: Int = 0,
    val name: String,
    val description: String = "",
    val trackIdsString: String = "" // Comma separated list of Int Track IDs
)
