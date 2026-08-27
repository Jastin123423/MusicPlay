package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.example.MainActivity
import com.example.audio.MusicPlaybackManager
import com.example.data.MusicTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class MusicPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "musicolet_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.example.musicolet.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicolet.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.musicolet.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.musicolet.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val playbackManager = MusicPlaybackManager.getInstance()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        observePlaybackState()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active music playback controls on lockscreen and notification panel"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlaybackService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    playbackManager.togglePlayPause()
                }

                override fun onPause() {
                    playbackManager.togglePlayPause()
                }

                override fun onSkipToNext() {
                    playbackManager.playNext()
                }

                override fun onSkipToPrevious() {
                    playbackManager.playPrev()
                }

                override fun onSeekTo(pos: Long) {
                    val track = playbackManager.currentTrack.value
                    if (track != null && track.durationSec > 0) {
                        val progress = (pos / 1000f) / track.durationSec
                        playbackManager.seekToProgress(progress)
                    }
                }

                override fun onStop() {
                    playbackManager.stopAll()
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            launch {
                playbackManager.currentTrack.collectLatest { track ->
                    updateMediaSessionAndNotification(track, playbackManager.isPlaying.value)
                }
            }
            launch {
                playbackManager.isPlaying.collectLatest { isPlaying ->
                    updateMediaSessionAndNotification(playbackManager.currentTrack.value, isPlaying)
                }
            }
            launch {
                playbackManager.currentPositionSec.collectLatest { posSec ->
                    updatePlaybackState(playbackManager.isPlaying.value, posSec)
                }
            }
        }
    }

    private fun loadAlbumArtBitmap(track: MusicTrack): Bitmap? {
        if (track.albumArtUri.isNotBlank()) {
            try {
                contentResolver.openInputStream(Uri.parse(track.albumArtUri))?.use { inputStream ->
                    return BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {}
        }
        if (track.contentUri.isNotBlank()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, Uri.parse(track.contentUri))
                val artBytes = retriever.embeddedPicture
                retriever.release()
                if (artBytes != null) {
                    return BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }
            } catch (e: Exception) {}
        }
        return try {
            BitmapFactory.decodeResource(resources, com.example.R.drawable.img_default_album_art)
        } catch (e: Exception) { null }
    }

    private fun updateMediaSessionAndNotification(track: MusicTrack?, isPlaying: Boolean) {
        if (track == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            val artBitmap = loadAlbumArtBitmap(track)

            withContext(Dispatchers.Main) {
                val metadataBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationSec * 1000L)

                if (artBitmap != null) {
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
                }

                mediaSession.setMetadata(metadataBuilder.build())
                updatePlaybackState(isPlaying, playbackManager.currentPositionSec.value)

                val notification = buildNotification(track, isPlaying, artBitmap)
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun updatePlaybackState(isPlaying: Boolean, posSec: Int) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY_PAUSE

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, posSec * 1000L, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun buildNotification(track: MusicTrack, isPlaying: Boolean, artBitmap: Bitmap?): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREVIOUS }
        val pendingPrev = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val pendingPlayPause = PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }
        val pendingNext = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText("${track.artist} • ${track.album}")
            .setSubText(track.folder)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingOpenApp)
            .setDeleteIntent(pendingStop)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Previous", pendingPrev)
            .addAction(playPauseIcon, playPauseTitle, pendingPlayPause)
            .addAction(android.R.drawable.ic_media_next, "Next", pendingNext)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )

        if (artBitmap != null) {
            builder.setLargeIcon(artBitmap)
        }

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playbackManager.togglePlayPause()
            ACTION_NEXT -> playbackManager.playNext()
            ACTION_PREVIOUS -> playbackManager.playPrev()
            ACTION_STOP -> {
                playbackManager.stopAll()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession.isActive = false
        mediaSession.release()
    }
}
