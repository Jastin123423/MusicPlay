package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MusicRepository(private val musicDao: MusicDao) {

    private val scanMutex = Mutex()

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

    suspend fun cleanFakeAndDuplicateTracks() {
        try {
            musicDao.deleteFakeTracks()
            val allDbTracks = musicDao.getAllTracksList()
            val seenUris = mutableSetOf<String>()
            val seenSignatures = mutableSetOf<String>()
            val duplicates = mutableListOf<MusicTrack>()

            for (track in allDbTracks) {
                val uri = track.contentUri.trim()
                val signature = "${track.title.trim().lowercase()}|${track.artist.trim().lowercase()}|${track.durationSec}|${track.album.trim().lowercase()}"
                val isDuplicate = (uri.isNotBlank() && !seenUris.add(uri)) || !seenSignatures.add(signature)
                if (isDuplicate) {
                    duplicates.add(track)
                }
            }
            for (dup in duplicates) {
                musicDao.deleteTrack(dup)
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error cleaning fake and duplicate tracks: ${e.message}")
        }
    }

    suspend fun scanDeviceAudioFiles(context: Context): Int = scanMutex.withLock {
        // Clean any residual fake songs or database duplicates first
        cleanFakeAndDuplicateTracks()

        val audioList = mutableListOf<MusicTrack>()
        val seenUris = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()
        val seenSignatures = mutableSetOf<String>()

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
                    val durationSec = (durationMs / 1000).toInt()
                    val filePath = cursor.getString(dataColumn) ?: ""

                    // Ignore tiny audio files (less than 3 seconds) which are typically notification pings/chimes
                    if (durationSec < 3) continue

                    // Validate file path on disk if available to eliminate ghost / stale MediaStore rows
                    if (filePath.isNotBlank()) {
                        val file = java.io.File(filePath)
                        if (!file.exists()) continue
                        val canonical = try { file.canonicalPath } catch (_: Exception) { filePath }
                        if (!seenPaths.add(canonical)) continue
                    }

                    val mediaUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString()
                    if (!seenUris.add(mediaUri)) continue

                    val signature = "${rawTitle.trim().lowercase()}|${rawArtist.trim().lowercase()}|${durationSec}|${rawAlbum.trim().lowercase()}"
                    if (!seenSignatures.add(signature)) continue

                    val albumId = if (albumIdColumn >= 0) cursor.getLong(albumIdColumn) else -1L
                    val albumArtUri = if (albumId > 0) ContentUris.withAppendedId(albumArtUriBase, albumId).toString() else ""

                    val folderName = if (filePath.isNotEmpty()) {
                        java.io.File(filePath).parentFile?.name ?: "Music"
                    } else "Music"

                    val year = if (yearColumn >= 0) (cursor.getString(yearColumn) ?: "") else ""
                    val trackNum = if (trackColumn >= 0) (cursor.getString(trackColumn) ?: "") else ""

                    val track = MusicTrack(
                        title = if (rawTitle.isBlank() || rawTitle == "<unknown>") "Track $mediaId" else rawTitle,
                        artist = if (rawArtist.isBlank() || rawArtist == "<unknown>") "Unknown Artist" else rawArtist,
                        album = if (rawAlbum.isBlank() || rawAlbum == "<unknown>") "Unknown Album" else rawAlbum,
                        genre = "Local Audio",
                        year = year,
                        trackNumber = trackNum,
                        durationSec = durationSec.coerceAtLeast(1),
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

        // Match with current database tracks to preserve stable IDs, favorites, and lyrics
        val currentDbTracks = musicDao.getAllTracksList()
        val dbByUri = currentDbTracks.filter { it.contentUri.isNotBlank() }.associateBy { it.contentUri }
        val dbBySignature = currentDbTracks.associateBy {
            "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}|${it.durationSec}|${it.album.trim().lowercase()}"
        }

        val finalTracksToSave = audioList.map { scannedTrack ->
            val existing = dbByUri[scannedTrack.contentUri]
                ?: dbBySignature["${scannedTrack.title.trim().lowercase()}|${scannedTrack.artist.trim().lowercase()}|${scannedTrack.durationSec}|${scannedTrack.album.trim().lowercase()}"]
            if (existing != null) {
                scannedTrack.copy(
                    id = existing.id,
                    isFavorite = existing.isFavorite,
                    lyrics = if (scannedTrack.lyrics.isNotBlank()) scannedTrack.lyrics else existing.lyrics
                )
            } else {
                scannedTrack
            }
        }

        // Delete obsolete tracks that were removed from the device
        val scannedUris = audioList.map { it.contentUri }.toSet()
        val obsoleteTracks = currentDbTracks.filter { it.contentUri.isNotBlank() && it.contentUri !in scannedUris }
        for (obsolete in obsoleteTracks) {
            musicDao.deleteTrack(obsolete)
        }

        if (finalTracksToSave.isNotEmpty()) {
            musicDao.insertTracks(finalTracksToSave)
        } else {
            // Keep library clean without fake songs if no local songs exist
            musicDao.clearAllTracks()
        }

        // Synchronize queues with only existing valid track IDs
        val savedTracks = musicDao.getAllTracksList()
        val savedTrackIds = savedTracks.map { it.id }
        val validIdSet = savedTrackIds.toSet()

        val currentQueues = allQueues.first()
        if (currentQueues.isEmpty()) {
            val q1Ids = savedTrackIds.joinToString(",")
            musicDao.insertQueue(PlaybackQueue(queueId = 1, name = "Primary Queue", trackIdsString = q1Ids, currentIndex = 0, currentPositionMs = 0L))
            musicDao.insertQueue(PlaybackQueue(queueId = 2, name = "Favorites & Quick Queue", trackIdsString = "", currentIndex = 0, currentPositionMs = 0L))
        } else {
            val q1 = currentQueues.firstOrNull { it.queueId == 1 } ?: PlaybackQueue(queueId = 1, name = "Primary Queue", trackIdsString = "")
            val currentQueueIds = q1.trackIdsString.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            val filteredQueueIds = currentQueueIds.filter { it in validIdSet }
            val finalQueueIds = if (filteredQueueIds.isNotEmpty()) filteredQueueIds else savedTrackIds
            musicDao.insertQueue(q1.copy(trackIdsString = finalQueueIds.joinToString(",")))
        }

        audioList.size
    }
}
