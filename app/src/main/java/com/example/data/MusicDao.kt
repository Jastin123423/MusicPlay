package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Tracks
    @Query("SELECT * FROM music_tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks WHERE id = :id")
    suspend fun getTrackById(id: Int): MusicTrack?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: MusicTrack): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<MusicTrack>)

    @Update
    suspend fun updateTrack(track: MusicTrack)

    @Delete
    suspend fun deleteTrack(track: MusicTrack)

    @Query("DELETE FROM music_tracks")
    suspend fun clearAllTracks()

    // Playback Queues (Musicolet Multi-Queues)
    @Query("SELECT * FROM playback_queues ORDER BY queueId ASC")
    fun getAllQueues(): Flow<List<PlaybackQueue>>

    @Query("SELECT * FROM playback_queues WHERE queueId = :queueId")
    suspend fun getQueueById(queueId: Int): PlaybackQueue?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(queue: PlaybackQueue)

    @Update
    suspend fun updateQueue(queue: PlaybackQueue)

    @Query("DELETE FROM playback_queues WHERE queueId = :queueId")
    suspend fun deleteQueue(queueId: Int)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylistById(playlistId: Int): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)
}
