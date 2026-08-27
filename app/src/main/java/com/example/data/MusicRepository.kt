package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MusicRepository(private val musicDao: MusicDao) {

    val allTracks: Flow<List<MusicTrack>> = musicDao.getAllTracks()
    val allQueues: Flow<List<PlaybackQueue>> = musicDao.getAllQueues()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()

    suspend fun getTrackById(id: Int): MusicTrack? = musicDao.getTrackById(id)

    suspend fun insertTrack(track: MusicTrack): Long = musicDao.insertTrack(track)

    suspend fun updateTrack(track: MusicTrack) = musicDao.updateTrack(track)

    suspend fun deleteTrack(track: MusicTrack) = musicDao.deleteTrack(track)

    suspend fun getQueueById(queueId: Int): PlaybackQueue? = musicDao.getQueueById(queueId)

    suspend fun insertQueue(queue: PlaybackQueue) = musicDao.insertQueue(queue)

    suspend fun updateQueue(queue: PlaybackQueue) = musicDao.updateQueue(queue)

    suspend fun deleteQueue(queueId: Int) = musicDao.deleteQueue(queueId)

    suspend fun getPlaylistById(playlistId: Int): Playlist? = musicDao.getPlaylistById(playlistId)

    suspend fun insertPlaylist(playlist: Playlist): Long = musicDao.insertPlaylist(playlist)

    suspend fun updatePlaylist(playlist: Playlist) = musicDao.updatePlaylist(playlist)

    suspend fun deletePlaylist(playlist: Playlist) = musicDao.deletePlaylist(playlist)

    suspend fun scanDeviceAudioFiles(context: Context): Int {
        val audioList = mutableListOf<MusicTrack>()
        val contentResolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)

                val albumArtUriBase = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idColumn)
                    val rawTitle = cursor.getString(titleColumn) ?: "Unknown Track"
                    val rawArtist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val rawAlbum = cursor.getString(albumColumn) ?: "Unknown Album"
                    val durationMs = cursor.getLong(durationColumn)
                    val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)
                    val filePath = cursor.getString(dataColumn) ?: ""

                    val albumId = if (albumIdColumn >= 0) cursor.getLong(albumIdColumn) else -1L
                    val albumArtUri = if (albumId > 0) ContentUris.withAppendedId(albumArtUriBase, albumId).toString() else ""

                    val folderName = if (filePath.isNotEmpty()) {
                        java.io.File(filePath).parentFile?.name ?: "Internal Storage"
                    } else "Internal Storage"

                    val year = if (yearColumn >= 0) (cursor.getString(yearColumn) ?: "") else ""
                    val trackNum = if (trackColumn >= 0) (cursor.getString(trackColumn) ?: "") else ""

                    val mediaUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString()

                    val track = MusicTrack(
                        title = if (rawTitle.isBlank() || rawTitle == "<unknown>") "Track $mediaId" else rawTitle,
                        artist = if (rawArtist.isBlank() || rawArtist == "<unknown>") "Unknown Artist" else rawArtist,
                        album = if (rawAlbum.isBlank() || rawAlbum == "<unknown>") "Unknown Album" else rawAlbum,
                        genre = "Local Audio",
                        year = year,
                        trackNumber = trackNum,
                        durationSec = durationSec,
                        lyrics = "",
                        folder = folderName,
                        contentUri = mediaUri,
                        albumArtUri = albumArtUri,
                        bitrate = "320 kbps",
                        sampleRate = "44.1 kHz"
                    )
                    audioList.add(track)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error scanning media store: ${e.message}")
        }

        // Wipe old tracks/demo data from DB and replace with real scanned phone music
        musicDao.clearAllTracks()
        if (audioList.isNotEmpty()) {
            musicDao.insertTracks(audioList)

            val newTracks = allTracks.first()
            val newTrackIds = newTracks.map { it.id }

            val currentQueues = allQueues.first()
            if (currentQueues.isEmpty()) {
                val q1Ids = newTrackIds.joinToString(",")
                musicDao.insertQueue(PlaybackQueue(queueId = 1, name = "Primary Queue", trackIdsString = q1Ids, currentIndex = 0, currentPositionMs = 0L))
                musicDao.insertQueue(PlaybackQueue(queueId = 2, name = "Favorites & Quick Queue", trackIdsString = "", currentIndex = 0, currentPositionMs = 0L))
            } else {
                val q1 = currentQueues.firstOrNull { it.queueId == 1 } ?: PlaybackQueue(queueId = 1, name = "Primary Queue", trackIdsString = "")
                musicDao.insertQueue(q1.copy(trackIdsString = newTrackIds.joinToString(",")))
            }
        } else {
            val currentQueues = allQueues.first()
            if (currentQueues.isEmpty()) {
                musicDao.insertQueue(PlaybackQueue(queueId = 1, name = "Primary Queue", trackIdsString = "", currentIndex = 0, currentPositionMs = 0L))
            }
        }

        return audioList.size
    }
}
