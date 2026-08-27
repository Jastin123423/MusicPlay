package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.data.MusicTrack
import com.example.ui.MusicMainScreen
import com.example.ui.MusicViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Requirement 1: Initialize Google Mobile Ads SDK using App ID
        MobileAds.initialize(this) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        handleIncomingAudioIntent(intent)

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MusicMainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAudioIntent(intent)
    }

    private fun handleIncomingAudioIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val action = incomingIntent.action
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND || action == "android.intent.action.MUSIC_PLAYER") {
            val uri: Uri? = incomingIntent.data ?: incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM) ?: incomingIntent.clipData?.let {
                if (it.itemCount > 0) it.getItemAt(0).uri else null
            }

            if (uri != null) {
                try {
                    // Check if track is already in the library
                    val allTracks = viewModel.allTracks.value
                    val matchingTrack = allTracks.find {
                        it.contentUri == uri.toString() || (it.contentUri.isNotBlank() && uri.toString().endsWith(it.contentUri))
                    }

                    if (matchingTrack != null) {
                        viewModel.playbackManager.playTrack(matchingTrack, allTracks, allTracks.indexOf(matchingTrack))
                        return
                    }

                    var title = "External Audio"
                    var artist = "Unknown Artist"
                    var album = "Audio File"
                    var genre = "Audio"
                    var durationSec = 180

                    // Read Display Name from ContentResolver
                    try {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                val name = cursor.getString(nameIndex)
                                if (!name.isNullOrBlank()) {
                                    title = name
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error reading display name: ${e.message}")
                    }

                    // Extract ID3 Metadata with MediaMetadataRetriever
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(this, uri)
                        val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        val metaGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                        if (!metaTitle.isNullOrBlank()) title = metaTitle
                        if (!metaArtist.isNullOrBlank()) artist = metaArtist
                        if (!metaAlbum.isNullOrBlank()) album = metaAlbum
                        if (!metaGenre.isNullOrBlank()) genre = metaGenre
                        if (!metaDuration.isNullOrBlank()) {
                            val durMs = metaDuration.toLongOrNull() ?: 0L
                            if (durMs > 0) durationSec = (durMs / 1000).toInt()
                        }
                        retriever.release()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error reading metadata: ${e.message}")
                    }

                    if (title.contains(".") && !title.startsWith(".")) {
                        title = title.substringBeforeLast(".")
                    }

                    val externalTrack = MusicTrack(
                        id = (System.currentTimeMillis() % 100000).toInt() + 100000,
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre,
                        year = "",
                        trackNumber = "1",
                        durationSec = durationSec,
                        lyrics = "",
                        folder = "External",
                        contentUri = uri.toString()
                    )

                    viewModel.playbackManager.playTrack(externalTrack, listOf(externalTrack), 0)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to handle incoming audio intent: ${e.message}")
                }
            }
        }
    }
}

