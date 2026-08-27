package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.audio.RepeatMode
import com.example.data.MusicTrack
import com.example.data.PlaybackQueue
import com.example.data.Playlist
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TrackAlbumArt(
    track: MusicTrack?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val artUri = track?.albumArtUri ?: ""
    val contentUri = track?.contentUri ?: ""

    if (artUri.isNotBlank()) {
        AsyncImage(
            model = artUri,
            contentDescription = "Album Art",
            contentScale = contentScale,
            error = painterResource(id = R.drawable.img_default_album_art),
            placeholder = painterResource(id = R.drawable.img_default_album_art),
            modifier = modifier
        )
    } else if (contentUri.isNotBlank()) {
        AsyncImage(
            model = contentUri,
            contentDescription = "Album Art",
            contentScale = contentScale,
            error = painterResource(id = R.drawable.img_default_album_art),
            placeholder = painterResource(id = R.drawable.img_default_album_art),
            modifier = modifier
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.img_default_album_art),
            contentDescription = "Default Album Art",
            contentScale = contentScale,
            modifier = modifier
        )
    }
}


@Composable
fun EmptyMusicStateCard(
    onScanClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = "No music",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Phone Music Found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextLight,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Grant storage permission and scan your device to read local MP3, M4A, FLAC, and WAV audio files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onScanClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan Phone Storage",
                        style = TextStyle(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun MusicMainScreen(viewModel: MusicViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentTab by viewModel.currentTab.collectAsState()
    val tracks by viewModel.allTracks.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()
    val queues by viewModel.allQueues.collectAsState()
    val selectedQueueId by viewModel.selectedQueueId.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanLocalMusic(context)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanLocalMusic(context)
        } else {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    // Player States in sync with playback manager
    val currentPlayingTrack by viewModel.playbackManager.currentTrack.collectAsState()
    val isPlaying by viewModel.playbackManager.isPlaying.collectAsState()
    val playbackProgress by viewModel.playbackManager.playbackProgress.collectAsState()
    val currentSecs by viewModel.playbackManager.currentPositionSec.collectAsState()

    var showFullPlayer by remember { mutableStateOf(false) }
    var trackForOptions by remember { mutableStateOf<MusicTrack?>(null) }
    var trackForTagEdit by remember { mutableStateOf<MusicTrack?>(null) }
    var playlistForTracks by remember { mutableStateOf<Playlist?>(null) }
    
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistSelector by remember { mutableStateOf<MusicTrack?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .background(SpaceDarkBg)
                    .statusBarsPadding()
            ) {
                // Header Title with Musicolet style multiple queues banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = "Logo",
                                tint = OnTeal
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "MUSICPLAY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Offline Pro • Clean Experience",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.scanLocalMusic(context)
                                } else {
                                    permissionLauncher.launch(permissionToRequest)
                                }
                            }
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = "Scan Media",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Multi-Queue Selector pill
                        var showQueueSelectorDropdown by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { showQueueSelectorDropdown = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QueueMusic,
                                    contentDescription = "Active Queue",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val activeQName = queues.find { it.queueId == selectedQueueId }?.name ?: "Queue"
                                Text(
                                    text = activeQName,
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                        DropdownMenu(
                            expanded = showQueueSelectorDropdown,
                            onDismissRequest = { showQueueSelectorDropdown = false },
                            modifier = Modifier.background(SpaceCardBg)
                        ) {
                            Text(
                                text = "Switch Playback Queue",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = TextMuted
                            )
                            queues.forEach { q ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = q.name,
                                                color = if (q.queueId == selectedQueueId) TealPrimary else TextLight
                                            )
                                            val size = if (q.trackIdsString.isEmpty()) 0 else q.trackIdsString.split(",").size
                                            Text(
                                                text = "$size songs",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.loadQueue(q.queueId)
                                        showQueueSelectorDropdown = false
                                    }
                                )
                            }
                            HorizontalDivider(color = SpaceDarkBg)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Add, "add", tint = TealPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Create New Queue", color = TealPrimary)
                                    }
                                },
                                onClick = {
                                    showQueueSelectorDropdown = false
                                    coroutineScope.launch {
                                        viewModel.addQueue("Queue ${queues.size + 1}")
                                    }
                                }
                            )
                        }
                    }
                }
            }

                // Beautiful Musicolet top horizontal tabs
                ScrollableTabRow(
                    selectedTabIndex = getTabIndex(currentTab),
                    containerColor = SpaceDarkBg,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[getTabIndex(currentTab)]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    }
                ) {
                    val tabs = listOf("Queue", "Folders", "Songs", "Albums", "Artists", "Playlists")
                    tabs.forEach { tab ->
                        Tab(
                            selected = currentTab == tab,
                            onClick = { viewModel.setTab(tab) },
                            text = {
                                Text(
                                    text = tab,
                                    fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (currentTab == tab) MaterialTheme.colorScheme.primary else TextMuted
                                )
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Interactive Bottom mini player (Musicolet style!)
            if (currentPlayingTrack != null) {
                Surface(
                    onClick = { showFullPlayer = true },
                    tonalElevation = 8.dp,
                    color = SpacePlayerBg,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .height(68.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Progress bar running along the top of mini-player
                        LinearProgressIndicator(
                            progress = { playbackProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = TealPrimary,
                            trackColor = SpaceDarkBg
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackAlbumArt(
                                    track = currentPlayingTrack,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = currentPlayingTrack?.title ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextLight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentPlayingTrack?.artist ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.playbackManager.playPrev() }) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipPrevious,
                                        contentDescription = "Prev",
                                        tint = TextLight,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.playbackManager.togglePlayPause() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = OnTeal,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(onClick = { viewModel.playbackManager.playNext() }) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = "Next",
                                        tint = TextLight,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpaceDarkBg)
                .padding(paddingValues)
        ) {
            // Screen router
            if (tracks.isEmpty() && currentTab != "Playlists" && !isScanning) {
                EmptyMusicStateCard(
                    onScanClick = {
                        if (ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.scanLocalMusic(context)
                        } else {
                            permissionLauncher.launch(permissionToRequest)
                        }
                    }
                )
            } else {
                when (currentTab) {
                    "Queue" -> ActiveQueueScreen(
                        viewModel = viewModel,
                        idQueues = queues,
                        selectedQueueId = selectedQueueId,
                        onMoreClicked = { trackForOptions = it }
                    )
                    "Folders" -> FolderBrowsingScreen(
                        tracks = tracks,
                        onPlayTrack = { track, list ->
                            viewModel.playbackManager.playTrack(track, list, list.indexOf(track))
                        },
                        onMoreClicked = { trackForOptions = it }
                    )
                    "Songs" -> SongsListScreen(
                        tracks = tracks,
                        onTrackClicked = { track ->
                            viewModel.playbackManager.playTrack(track, tracks, tracks.indexOf(track))
                        },
                        onMoreClicked = { trackForOptions = it }
                    )
                    "Albums" -> AlbumGridScreen(
                        tracks = tracks,
                        onPlayTrack = { track, list ->
                            viewModel.playbackManager.playTrack(track, list, list.indexOf(track))
                        },
                        onMoreClicked = { trackForOptions = it }
                    )
                    "Artists" -> ArtistGridScreen(
                        tracks = tracks,
                        onPlayTrack = { track, list ->
                            viewModel.playbackManager.playTrack(track, list, list.indexOf(track))
                        },
                        onMoreClicked = { trackForOptions = it }
                    )
                    "Playlists" -> PlaylistsScreen(
                        playlists = playlists,
                        allTracks = tracks,
                        onCreateClick = { showAddPlaylistDialog = true },
                        onPlaylistSelect = { playlistForTracks = it },
                        onDeleteClick = { viewModel.deletePlaylist(it) },
                        onPlayTrack = { track, list ->
                            viewModel.playbackManager.playTrack(track, list, list.indexOf(track))
                        }
                    )
                }
            }
        }
    }

    // Modal sheet full player overlay
    if (showFullPlayer && currentPlayingTrack != null) {
        FullPlayerSheet(
            track = currentPlayingTrack!!,
            viewModel = viewModel,
            onDismiss = { showFullPlayer = false },
            onMoreClicked = { trackForOptions = it }
        )
    }

    // Song options contextual popup menu
    if (trackForOptions != null) {
        TrackOptionsDialog(
            track = trackForOptions!!,
            onDismiss = { trackForOptions = null },
            onEditTags = {
                trackForTagEdit = trackForOptions
                trackForOptions = null
            },
            onAddToPlaylist = {
                showAddToPlaylistSelector = trackForOptions
                trackForOptions = null
            },
            onToggleFavorite = {
                viewModel.toggleFavorite(trackForOptions!!)
                trackForOptions = null
            }
        )
    }

    // Playlist Selector Drawer
    if (showAddToPlaylistSelector != null) {
        AddToPlaylistDialog(
            playlists = playlists,
            track = showAddToPlaylistSelector!!,
            onDismiss = { showAddToPlaylistSelector = null },
            onSelect = { pId ->
                viewModel.addTrackToPlaylist(pId, showAddToPlaylistSelector!!.id)
                showAddToPlaylistSelector = null
            }
        )
    }

    // Easy playlist creator
    if (showAddPlaylistDialog) {
        var pName by remember { mutableStateOf("") }
        var pDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPlaylistDialog = false },
            containerColor = SpaceCardBg,
            title = { Text("Create Playlist", color = TextLight) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pName,
                        onValueChange = { pName = it },
                        label = { Text("Playlist Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pDesc,
                        onValueChange = { pDesc = it },
                        label = { Text("Description (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pName.isNotBlank()) {
                            viewModel.createPlaylist(pName, pDesc)
                        }
                        showAddPlaylistDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Create", color = OnTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPlaylistDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Musicolet Tag Editor (100% functional, real file metadata persists in SQLite!)
    if (trackForTagEdit != null) {
        TagEditorDialog(
            track = trackForTagEdit!!,
            onDismiss = { trackForTagEdit = null },
            onSave = { updatedTrackMap ->
                viewModel.updateTrackTags(
                    trackId = trackForTagEdit!!.id,
                    title = updatedTrackMap["title"] ?: "",
                    artist = updatedTrackMap["artist"] ?: "",
                    album = updatedTrackMap["album"] ?: "",
                    genre = updatedTrackMap["genre"] ?: "",
                    year = updatedTrackMap["year"] ?: "",
                    trackNumber = updatedTrackMap["trackNumber"] ?: "",
                    lyrics = updatedTrackMap["lyrics"] ?: ""
                )
                trackForTagEdit = null
            }
        )
    }

    // Playlist tracks listing dialog
    if (playlistForTracks != null) {
        PlaylistDetailDialog(
            playlist = playlistForTracks!!,
            allTracks = tracks,
            onDismiss = { playlistForTracks = null },
            onRemoveTrack = { tId ->
                viewModel.removeTrackFromPlaylist(playlistForTracks!!.playlistId, tId)
                // update local dialog state
                coroutineScope.launch {
                    val freshPlaylist = viewModel.getPlaylistById(playlistForTracks!!.playlistId)
                    playlistForTracks = freshPlaylist
                }
            },
            onPlayTrack = { track, list ->
                viewModel.playbackManager.playTrack(track, list, list.indexOf(track))
            }
        )
    }
}

// Map tabs to indices
private fun getTabIndex(tab: String): Int {
    return when (tab) {
        "Queue" -> 0
        "Folders" -> 1
        "Songs" -> 2
        "Albums" -> 3
        "Artists" -> 4
        "Playlists" -> 5
        else -> 0
    }
}

// Generate matching visually pleasing linear gradients based on Track IDs (Conforming to Lavender/Deep Violet theme)
fun getTrackGradient(id: Int): Brush {
    return when (id % 5) {
        0 -> Brush.linearGradient(listOf(Color(0xFF4F378B), Color(0xFF21005D)))
        1 -> Brush.linearGradient(listOf(Color(0xFF6750A4), Color(0xFF311180)))
        2 -> Brush.linearGradient(listOf(Color(0xFF9A82D6), Color(0xFF4A25A3)))
        3 -> Brush.linearGradient(listOf(Color(0xFFB09FFF), Color(0xFF451EA1)))
        else -> Brush.linearGradient(listOf(Color(0xFF8C52FF), Color(0xFF3F00B5)))
    }
}

// Formats seconds into standard audio clock view MM:SS
fun formatTime(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return String.format("%02d:%02d", m, s)
}

// ACTIVE QUEUE SCREEN (Supports multi-queues additions/removes/clear)
@Composable
fun ActiveQueueScreen(
    viewModel: MusicViewModel,
    idQueues: List<PlaybackQueue>,
    selectedQueueId: Int,
    onMoreClicked: (MusicTrack) -> Unit
) {
    val currentQueueTracks by viewModel.queueTracks.collectAsState()
    val activeTrack by viewModel.playbackManager.currentTrack.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val qName = idQueues.find { it.queueId == selectedQueueId }?.name ?: "Queue"
                    Text(
                        text = "Current: $qName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                    Text(
                        text = "${currentQueueTracks.size} songs connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Row {
                    IconButton(
                        onClick = { viewModel.clearActiveQueue() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Clear Queue")
                    }
                }
            }
        }

        if (currentQueueTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.QueueMusic,
                        contentDescription = "Empty",
                        tint = TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This queue is empty",
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Go to Songs or Albums to add elements!",
                        color = TextMuted.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(currentQueueTracks) { index, track ->
                    val isPlayingNow = activeTrack?.id == track.id
                    val cardBg = if (isPlayingNow) SpacePlayerBg else Color.Transparent
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cardBg)
                            .clickable {
                                viewModel.playbackManager.playTrack(track, currentQueueTracks, index)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                TrackAlbumArt(track = track, modifier = Modifier.fillMaxSize())
                                if (isPlayingNow) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VolumeUp,
                                            contentDescription = "Playing",
                                            tint = TealPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = track.title,
                                    color = if (isPlayingNow) TealPrimary else TextLight,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.album}",
                                    color = TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatTime(track.durationSec),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { viewModel.removeTrackFromActiveQueue(index) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { onMoreClicked(track) }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Options",
                                    tint = TextLight
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = SpaceCardBg.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

// SONGS LIST SCREEN (With query search and quick playback)
@Composable
fun SongsListScreen(
    tracks: List<MusicTrack>,
    onTrackClicked: (MusicTrack) -> Unit,
    onMoreClicked: (MusicTrack) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTracks = tracks.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.artist.contains(searchQuery, ignoreCase = true) ||
        it.album.contains(searchQuery, ignoreCase = true) ||
        it.genre.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search songs, artists, albums...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Filled.Search, "search", tint = TextMuted) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, "clear", tint = TextMuted)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = SpaceCardBg,
                focusedContainerColor = SpaceCardBg,
                unfocusedContainerColor = SpaceCardBg,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(filteredTracks) { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackClicked(track) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackAlbumArt(
                        track = track,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = TextLight,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${track.artist} • ${track.album}",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (track.isFavorite) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Favorited",
                                    tint = TealPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = formatTime(track.durationSec),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                    IconButton(onClick = { onMoreClicked(track) }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = TextLight
                        )
                    }
                }
                HorizontalDivider(color = SpaceCardBg.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

// REUSABLE CATEGORY SONGS DETAIL VIEW (Folder, Album, Artist drill-down)
@Composable
fun CategorySongsDetailView(
    title: String,
    subtitle: String,
    categoryType: String,
    headerArt: @Composable () -> Unit,
    tracks: List<MusicTrack>,
    onBack: () -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onMoreClicked: (MusicTrack) -> Unit
) {
    BackHandler(onBack = onBack)

    val totalDurationSec = tracks.sumOf { it.durationSec }
    val formattedTotalDuration = if (totalDurationSec >= 3600) {
        val hrs = totalDurationSec / 3600
        val mins = (totalDurationSec % 3600) / 60
        "${hrs}h ${mins}m"
    } else {
        "${totalDurationSec / 60}m"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceDarkBg)
    ) {
        // Top back bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextLight
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$categoryType / $title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TealPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Category Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headerArt()
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextLight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(
                            text = "${tracks.size} songs • $formattedTotalDuration",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (tracks.isNotEmpty()) {
                                onPlayTrack(tracks.first(), tracks)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play All",
                            tint = OnTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", color = OnTeal, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (tracks.isNotEmpty()) {
                                val shuffled = tracks.shuffled()
                                onPlayTrack(shuffled.first(), shuffled)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = TealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", color = TealPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List of songs
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No songs in this $categoryType.",
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tracks) { index, track ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlayTrack(track, tracks)
                            },
                        colors = CardDefaults.cardColors(containerColor = SpaceCardBg.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(getTrackGradient(track.id)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextLight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • ${formatTime(track.durationSec)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(onClick = { onMoreClicked(track) }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Options",
                                    tint = TextLight
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// STORAGE FOLDERS SCREEN (Browse folders & show song list on click)
@Composable
fun FolderBrowsingScreen(
    tracks: List<MusicTrack>,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onMoreClicked: (MusicTrack) -> Unit
) {
    val folders = remember(tracks) { tracks.groupBy { it.folder } }
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    if (selectedFolder != null) {
        val folderTracks = folders[selectedFolder] ?: emptyList()
        CategorySongsDetailView(
            title = selectedFolder!!,
            subtitle = "Storage folder",
            categoryType = "Folder",
            headerArt = {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AmberAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Folder",
                        tint = AmberAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            tracks = folderTracks,
            onBack = { selectedFolder = null },
            onPlayTrack = onPlayTrack,
            onMoreClicked = onMoreClicked
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Storage Folder Hierarchy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TealPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Musicolet lists music organized in original directory root trees. Tap a folder to browse its songs.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(folders.keys.toList()) { folder ->
                    val fTracks = folders[folder] ?: emptyList()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedFolder = folder
                            },
                        colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = "Folder",
                                    tint = AmberAccent,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = folder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextLight
                                    )
                                    Text(
                                        text = "${fTracks.size} songs • Tap to view list",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (fTracks.isNotEmpty()) {
                                        onPlayTrack(fTracks.first(), fTracks)
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = TealPrimary)
                            ) {
                                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Play Folder")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ALBUMS GRID (Browse albums & show song list on click)
@Composable
fun AlbumGridScreen(
    tracks: List<MusicTrack>,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onMoreClicked: (MusicTrack) -> Unit
) {
    val albums = remember(tracks) { tracks.groupBy { it.album } }
    val albumList = remember(albums) { albums.keys.toList() }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    if (selectedAlbum != null) {
        val albumTracks = albums[selectedAlbum] ?: emptyList()
        val representativeTrack = albumTracks.firstOrNull()
        CategorySongsDetailView(
            title = selectedAlbum!!,
            subtitle = representativeTrack?.artist ?: "Unknown Artist",
            categoryType = "Album",
            headerArt = {
                TrackAlbumArt(
                    track = representativeTrack,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            },
            tracks = albumTracks,
            onBack = { selectedAlbum = null },
            onPlayTrack = onPlayTrack,
            onMoreClicked = onMoreClicked
        )
    } else {
        // Requirement 10: Preload Native Ads in advance for Albums screen
        val adsState = rememberNativeAdsState(itemCount = albumList.size)

        // Requirement 7, 8, 9, 11: Construct grid list inserting 1 Native Ad after every 5 real items (max 3 per screen)
        val gridItems = buildGridItemsWithAds(realItems = albumList, failedAdIndices = adsState.failedAdIndices)

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Albums Catalog",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TealPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(gridItems) { wrapper ->
                    when (wrapper) {
                        is GridItemWrapper.RealItem -> {
                            val album = wrapper.item
                            val albumTracks = albums[album] ?: emptyList()
                            val representativeTrack = albumTracks.firstOrNull()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAlbum = album
                                    },
                                colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column {
                                    TrackAlbumArt(
                                        track = representativeTrack,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    )

                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = album,
                                            fontWeight = FontWeight.Bold,
                                            color = TextLight,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = representativeTrack?.artist ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${albumTracks.size} songs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TealPrimary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        is GridItemWrapper.NativeAdItem -> {
                            // Requirement 2, 4, 5, 6, 12: Render Native Ad card matching album card appearance
                            val nativeAd = adsState.loadedAds[wrapper.adIndex]
                            AlbumNativeAdCard(nativeAd = nativeAd)
                        }
                    }
                }
            }
        }
    }
}

// ARTISTS GRID (Browse artists & show song list on click)
@Composable
fun ArtistGridScreen(
    tracks: List<MusicTrack>,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onMoreClicked: (MusicTrack) -> Unit
) {
    val artists = remember(tracks) { tracks.groupBy { it.artist } }
    val artistList = remember(artists) { artists.keys.toList() }
    var selectedArtist by remember { mutableStateOf<String?>(null) }

    if (selectedArtist != null) {
        val artistTracks = artists[selectedArtist] ?: emptyList()
        val representativeTrack = artistTracks.firstOrNull()
        val albumCount = artistTracks.map { it.album }.distinct().size
        CategorySongsDetailView(
            title = selectedArtist!!,
            subtitle = "$albumCount albums",
            categoryType = "Artist",
            headerArt = {
                TrackAlbumArt(
                    track = representativeTrack,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                )
            },
            tracks = artistTracks,
            onBack = { selectedArtist = null },
            onPlayTrack = onPlayTrack,
            onMoreClicked = onMoreClicked
        )
    } else {
        // Requirement 10: Preload Native Ads in advance for Artists screen
        val adsState = rememberNativeAdsState(itemCount = artistList.size)

        // Requirement 7, 8, 9, 11: Construct grid list inserting 1 Native Ad after every 5 real items (max 3 per screen)
        val gridItems = buildGridItemsWithAds(realItems = artistList, failedAdIndices = adsState.failedAdIndices)

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Artists Catalog",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TealPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(gridItems) { wrapper ->
                    when (wrapper) {
                        is GridItemWrapper.RealItem -> {
                            val artist = wrapper.item
                            val artistTracks = artists[artist] ?: emptyList()
                            val representativeTrack = artistTracks.firstOrNull()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedArtist = artist
                                    },
                                colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    TrackAlbumArt(
                                        track = representativeTrack,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = artist,
                                        fontWeight = FontWeight.Bold,
                                        color = TextLight,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${artistTracks.size} songs connected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                        is GridItemWrapper.NativeAdItem -> {
                            // Requirement 2, 4, 5, 6, 12: Render Native Ad card matching artist card appearance
                            val nativeAd = adsState.loadedAds[wrapper.adIndex]
                            ArtistNativeAdCard(nativeAd = nativeAd)
                        }
                    }
                }
            }
        }
    }
}

// USER PLAYLISTS LISTING
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    allTracks: List<MusicTrack>,
    onCreateClick: () -> Unit,
    onPlaylistSelect: (Playlist) -> Unit,
    onDeleteClick: (Playlist) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Custom Playlists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TealPrimary
            )
            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Icon(Icons.Filled.Add, "doc", tint = OnTeal)
                Spacer(modifier = Modifier.width(4.dp))
                Text("New", color = OnTeal)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No custom playlists. Create one now!", color = TextMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(playlists) { playlist ->
                    val ids = playlist.trackIdsString.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SpaceCardBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaylistSelect(playlist) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Filled.PlaylistPlay,
                                    contentDescription = "Playlist",
                                    tint = TealPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextLight
                                    )
                                    if (playlist.description.isNotEmpty()) {
                                        Text(
                                            text = playlist.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${ids.size} songs connected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealSecondary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            Row {
                                if (ids.isNotEmpty()) {
                                    val playlistTracks = allTracks.filter { ids.contains(it.id) }
                                    IconButton(onClick = {
                                        if (playlistTracks.isNotEmpty()) {
                                            onPlayTrack(playlistTracks.first(), playlistTracks)
                                        }
                                    }) {
                                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Play", tint = TealPrimary)
                                    }
                                }
                                IconButton(onClick = { onDeleteClick(playlist) }) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// FULL SCREEN EXPANDED PLAYER (Beautiful dark player with EQ, Audio Effects, Pitch, Speed, Lyrics, and Tag Editor!)
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FullPlayerSheet(
    track: MusicTrack,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onMoreClicked: (MusicTrack) -> Unit
) {
    val isPlaying by viewModel.playbackManager.isPlaying.collectAsState()
    val progress by viewModel.playbackManager.playbackProgress.collectAsState()
    val currentSecs by viewModel.playbackManager.currentPositionSec.collectAsState()
    val speedFactor by viewModel.playbackManager.speed.collectAsState()
    val pitchFactor by viewModel.playbackManager.pitch.collectAsState()
    val repeatMode by viewModel.playbackManager.repeatMode.collectAsState()
    val isShuffle by viewModel.playbackManager.isShuffle.collectAsState()
    val crossfadeSec by viewModel.playbackManager.crossfadeSec.collectAsState()
    val isGapless by viewModel.playbackManager.gaplessEnabled.collectAsState()
    
    val eqGains by viewModel.playbackManager.eqGains.collectAsState()
    val bassBoost by viewModel.playbackManager.bassBoostStrength.collectAsState()
    val virtualizer by viewModel.playbackManager.virtualizerStrength.collectAsState()
    val reverbPreset by viewModel.playbackManager.reverbPreset.collectAsState()
    val balance by viewModel.playbackManager.balance.collectAsState()
    val sleepTimerRemaining by viewModel.playbackManager.sleepTimerMinutes.collectAsState()

    var showEqPanel by remember { mutableStateOf(false) }
    var showLyricsPanel by remember { mutableStateOf(false) }
    var showTimerPanel by remember { mutableStateOf(false) }
    var showDetailsPanel by remember { mutableStateOf(false) }

    // Rotating album art animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rotation"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SpaceDarkBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Top controls bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Hide",
                            tint = TextLight,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )

                    IconButton(onClick = { onDismiss(); onMoreClicked(track) }) {
                        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More", tint = TextLight)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Beautiful Real Album Cover art centered with optional spinning/elevation frame
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(SpaceCardBg),
                    contentAlignment = Alignment.Center
                ) {
                    TrackAlbumArt(
                        track = track,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isPlaying) Modifier.rotate(rotationAngle * 0.1f) else Modifier)
                    )
                    
                    if (track.genre.isNotBlank()) {
                        Text(
                            text = track.genre.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Track title & metadata
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextLight,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive icons panel: Equalizer, Lyrics, Sleep Timer, Favorites, Track Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite icon
                    IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (track.isFavorite) TealPrimary else TextMuted
                        )
                    }

                    // Equalizer & Audio Effects Trigger
                    IconButton(onClick = { showEqPanel = !showEqPanel }) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Equalizer & DSP",
                            tint = if (showEqPanel) TealPrimary else TextLight
                        )
                    }

                    // Lyrics Trigger
                    IconButton(onClick = { showLyricsPanel = !showLyricsPanel }) {
                        Icon(
                            imageVector = Icons.Filled.LibraryBooks,
                            contentDescription = "Lyrics",
                            tint = if (showLyricsPanel) TealPrimary else TextLight
                        )
                    }

                    // Sleep Timer Trigger
                    IconButton(onClick = { showTimerPanel = !showTimerPanel }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Snooze,
                                contentDescription = "Sleep timer",
                                tint = if (sleepTimerRemaining > 0) TealPrimary else TextLight
                            )
                            if (sleepTimerRemaining > 0) {
                                Text(
                                    text = sleepTimerRemaining.toString(),
                                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = AmberAccent,
                                    modifier = Modifier.offset(x = 10.dp, y = (-10).dp)
                                )
                            }
                        }
                    }

                    // Track Metadata Specs Trigger
                    IconButton(onClick = { showDetailsPanel = !showDetailsPanel }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Audio Info",
                            tint = if (showDetailsPanel) TealPrimary else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Audio Info/Specs Panel
                AnimatedVisibility(visible = showDetailsPanel) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SpaceCardBg)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Audio Format & File Info",
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bitrate:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                Text("${track.bitrate} kbps", color = TextLight, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Sample Rate:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                Text("${track.sampleRate} Hz", color = TextLight, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Year / Track:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                Text("${track.year} (Track #${track.trackNumber})", color = TextLight, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Folder Path:", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                            Text(track.folder, color = TextLight, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Live Equalizer, BassBoost, Virtualizer & Crossfade panel
                AnimatedVisibility(visible = showEqPanel) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SpaceCardBg)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "5-Band Equalizer & Audio FX",
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary
                                )
                                var showPresetDropdown by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { showPresetDropdown = true }) {
                                        Text("Presets", color = TealPrimary)
                                    }
                                    DropdownMenu(
                                        expanded = showPresetDropdown,
                                        onDismissRequest = { showPresetDropdown = false },
                                        modifier = Modifier.background(SpaceCardBg)
                                    ) {
                                        val presets = listOf("Flat", "Bass Booster", "Vocal Booster", "Pop", "Rock", "Classical")
                                        presets.forEach { preset ->
                                            DropdownMenuItem(
                                                text = { Text(preset, color = TextLight) },
                                                onClick = {
                                                    viewModel.playbackManager.setEqualizerPreset(preset)
                                                    showPresetDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 5 EQ Frequency band sliders
                            val bandLabels = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
                            for (b in 0 until 5) {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = bandLabels[b], style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                        val dbVal = eqGains.getOrElse(b) { 0f }
                                        Text(
                                            text = "${if (dbVal >= 0) "+" else ""}${dbVal.toInt()} dB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TealPrimary
                                        )
                                    }
                                    Slider(
                                        value = eqGains.getOrElse(b) { 0f },
                                        onValueChange = { newVal ->
                                            val currentGains = eqGains.copyOf()
                                            if (b in currentGains.indices) {
                                                currentGains[b] = newVal
                                                viewModel.playbackManager.updateEqualizerGains(currentGains)
                                            }
                                        },
                                        valueRange = -12f..12f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = TealPrimary,
                                            activeTrackColor = TealPrimary,
                                            inactiveTrackColor = SpaceDarkBg
                                        )
                                    )
                                }
                            }

                            HorizontalDivider(color = SpaceDarkBg, modifier = Modifier.padding(vertical = 12.dp))

                            // Bass Boost & 3D Virtualizer
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bass Boost", style = MaterialTheme.typography.bodySmall, color = TextLight, fontWeight = FontWeight.Bold)
                                Text("${(bassBoost / 10).toInt()}%", style = MaterialTheme.typography.bodySmall, color = TealPrimary)
                            }
                            Slider(
                                value = bassBoost.toFloat(),
                                onValueChange = { viewModel.playbackManager.setBassBoost(it.toInt()) },
                                valueRange = 0f..1000f,
                                colors = SliderDefaults.colors(thumbColor = TealPrimary, activeTrackColor = TealPrimary, inactiveTrackColor = SpaceDarkBg)
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("3D Virtualizer", style = MaterialTheme.typography.bodySmall, color = TextLight, fontWeight = FontWeight.Bold)
                                Text("${(virtualizer / 10).toInt()}%", style = MaterialTheme.typography.bodySmall, color = TealPrimary)
                            }
                            Slider(
                                value = virtualizer.toFloat(),
                                onValueChange = { viewModel.playbackManager.setVirtualizer(it.toInt()) },
                                valueRange = 0f..1000f,
                                colors = SliderDefaults.colors(thumbColor = TealPrimary, activeTrackColor = TealPrimary, inactiveTrackColor = SpaceDarkBg)
                            )

                            // Crossfade & Gapless
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Crossfade Duration", style = MaterialTheme.typography.bodySmall, color = TextLight, fontWeight = FontWeight.Bold)
                                Text("${crossfadeSec}s", style = MaterialTheme.typography.bodySmall, color = TealPrimary)
                            }
                            Slider(
                                value = crossfadeSec.toFloat(),
                                onValueChange = { viewModel.playbackManager.setCrossfade(it.toInt()) },
                                valueRange = 0f..5f,
                                steps = 4,
                                colors = SliderDefaults.colors(thumbColor = TealPrimary, activeTrackColor = TealPrimary, inactiveTrackColor = SpaceDarkBg)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Gapless Track Transition", style = MaterialTheme.typography.bodySmall, color = TextLight, fontWeight = FontWeight.Bold)
                                Switch(
                                    checked = isGapless,
                                    onCheckedChange = { viewModel.playbackManager.setGapless(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = OnTeal, checkedTrackColor = TealPrimary)
                                )
                            }
                        }
                    }
                }

                // Interactive Sleep Timer setup panel
                AnimatedVisibility(visible = showTimerPanel) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SpaceCardBg)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Setup Sleep Timer",
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                val times = listOf(0, 5, 15, 30, 45, 60)
                                times.forEach { t ->
                                    Button(
                                        onClick = {
                                            if (t == 0) {
                                                viewModel.playbackManager.stopSleepTimer()
                                            } else {
                                                viewModel.playbackManager.startSleepTimer(t)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (sleepTimerRemaining == t) TealPrimary else SpaceDarkBg
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = if (t == 0) "OFF" else "${t}m",
                                            color = if (sleepTimerRemaining == t) OnTeal else TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive Lyric Panel dialog overlay
                AnimatedVisibility(visible = showLyricsPanel) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SpaceCardBg)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Song Lyrics",
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            val cleanLyrics = track.lyrics.ifBlank { "No lyrics saved. Tap the 3-dot menu on top right to edit tags & lyrics." }
                            Text(
                                text = cleanLyrics,
                                style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = TextLight),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Seek bar & Timers
                Column {
                    Slider(
                        value = progress,
                        onValueChange = { newVal -> viewModel.playbackManager.seekToProgress(newVal) },
                        colors = SliderDefaults.colors(
                            thumbColor = TealPrimary,
                            activeTrackColor = TealPrimary,
                            inactiveTrackColor = SpaceCardBg
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = formatTime(currentSecs), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Text(text = formatTime(track.durationSec), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Complete Physical controls: Shuffle, Previous, Play/Pause, Next, Repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle toggle button
                    IconButton(onClick = { viewModel.playbackManager.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) TealPrimary else TextMuted,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Previous button
                    IconButton(onClick = { viewModel.playbackManager.playPrev() }) {
                        Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = "Prev", tint = TextLight, modifier = Modifier.size(44.dp))
                    }

                    // Main Play/Pause button
                    IconButton(
                        onClick = { viewModel.playbackManager.togglePlayPause() },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = OnTeal,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next button
                    IconButton(onClick = { viewModel.playbackManager.playNext() }) {
                        Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "Next", tint = TextLight, modifier = Modifier.size(44.dp))
                    }

                    // Repeat toggle button (OFF, ALL, ONE)
                    IconButton(onClick = { viewModel.playbackManager.toggleRepeatMode() }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    RepeatMode.ONE -> Icons.Filled.RepeatOne
                                    else -> Icons.Filled.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (repeatMode != RepeatMode.NONE) TealPrimary else TextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Speed / Pitch tuning slider card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SpaceCardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Speed, "speed", tint = TealPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tempo Speed Tuning", style = MaterialTheme.typography.bodyMedium, color = TextLight, fontWeight = FontWeight.Bold)
                            }
                            Text(text = String.format("%.2fx", speedFactor), style = MaterialTheme.typography.bodySmall, color = TealPrimary)
                        }
                        Slider(
                            value = speedFactor,
                            onValueChange = { viewModel.playbackManager.setSpeed(it) },
                            valueRange = 0.25f..3.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = TealPrimary,
                                activeTrackColor = TealPrimary,
                                inactiveTrackColor = SpaceDarkBg
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Acoustic Pitch Tuning", style = MaterialTheme.typography.bodyMedium, color = TextLight, fontWeight = FontWeight.Bold)
                            Text(text = String.format("%.2fx", pitchFactor), style = MaterialTheme.typography.bodySmall, color = TealPrimary)
                        }
                        Slider(
                            value = pitchFactor,
                            onValueChange = { viewModel.playbackManager.setPitch(it) },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = TealPrimary,
                                activeTrackColor = TealPrimary,
                                inactiveTrackColor = SpaceDarkBg
                            )
                        )
                    }
                }
            }
        }
    }
}

// CONTEXT MENU FOR TRACK OPTIONS
@Composable
fun TrackOptionsDialog(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onEditTags: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpaceCardBg,
        title = {
            Column {
                Text(text = track.title, color = TextLight, fontWeight = FontWeight.Bold)
                Text(text = "${track.artist} • ${track.album}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {},
        dismissButton = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Favorite, contentDescription = "Fav", tint = TealPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (track.isFavorite) "Remove from Favorites" else "Add to Favorites", color = TextLight)
                    }
                }
                TextButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.PlaylistAdd, contentDescription = "Add Playlist", tint = TealPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Add to Custom Playlist", color = TextLight)
                    }
                }
                TextButton(
                    onClick = onEditTags,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit Tags", tint = TealPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Edit ID3 Metadata & Lyrics", color = TextLight)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(36.dp))
                        Text("Close Options", color = TextMuted)
                    }
                }
            }
        }
    )
}

// CONTEXT PLAYLIST SELECTOR
@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    track: MusicTrack,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpaceCardBg,
        title = { Text("Select Target Playlist", color = TextLight) },
        text = {
            if (playlists.isEmpty()) {
                Text("You don't have any playlists yet. Return to Playlists tab and create one first!", color = TextMuted)
            } else {
                LazyColumn {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(playlist.playlistId) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlaylistPlay, "p", tint = TealPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(playlist.name, color = TextLight, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

// REAL METADATA TAG EDITOR (SQLite saveable!)
@Composable
fun TagEditorDialog(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    var genre by remember { mutableStateOf(track.genre) }
    var year by remember { mutableStateOf(track.year) }
    var trackNumber by remember { mutableStateOf(track.trackNumber) }
    var lyrics by remember { mutableStateOf(track.lyrics) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
            shape = RoundedCornerShape(12.dp),
            color = SpaceCardBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "ID3 Editor (Musicolet style)",
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Track Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary, unfocusedBorderColor = TextMuted, focusedTextColor = TextLight, unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Lead Artist") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary, unfocusedBorderColor = TextMuted, focusedTextColor = TextLight, unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary, unfocusedBorderColor = TextMuted, focusedTextColor = TextLight, unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Genre") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary, unfocusedBorderColor = TextMuted, focusedTextColor = TextLight, unfocusedTextColor = TextLight
                        ),
                        modifier = Modifier.weight(1f).padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Year") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary, unfocusedBorderColor = TextMuted, focusedTextColor = TextLight, unfocusedTextColor = TextLight
                        ),
                        modifier = Modifier.weight(1f).padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                    )
                }

                OutlinedTextField(
                    value = trackNumber,
                    onValueChange = { trackNumber = it },
                    label = { Text("Track Index Number") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary, unfocusedBorderColor = TextMuted, focusedTextColor = TextLight, unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text("Lyrics Text Block") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary, unfocusedBorderColor = TextMuted, focusedTextColor = TextLight, unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val map = mapOf(
                                "title" to title,
                                "artist" to artist,
                                "album" to album,
                                "genre" to genre,
                                "year" to year,
                                "trackNumber" to trackNumber,
                                "lyrics" to lyrics
                            )
                            onSave(map)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("Save Tags", color = OnTeal)
                    }
                }
            }
        }
    }
}

// PLAYLIST DETAIL DISPLAYER
@Composable
fun PlaylistDetailDialog(
    playlist: Playlist,
    allTracks: List<MusicTrack>,
    onDismiss: () -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit
) {
    val ids = playlist.trackIdsString.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
    val trackMap = allTracks.associateBy { it.id }
    val playlistTracks = ids.mapNotNull { trackMap[it] }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
            shape = RoundedCornerShape(12.dp),
            color = SpaceCardBg
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(playlist.name, fontWeight = FontWeight.Bold, color = TealPrimary, style = MaterialTheme.typography.titleMedium)
                        Text("${playlistTracks.size} songs connected", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "close", tint = TextLight)
                    }
                }

                HorizontalDivider(color = SpaceDarkBg, modifier = Modifier.padding(vertical = 12.dp))

                if (playlistTracks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No tracks added. Go to Songs list and pick 'Add to Custom Playlist' from options!", color = TextMuted, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(playlistTracks) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayTrack(track, playlistTracks) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(getTrackGradient(track.id)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.MusicNote, "m", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(track.title, color = TextLight, fontWeight = FontWeight.Bold)
                                        Text(track.artist, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                IconButton(onClick = { onRemoveTrack(track.id) }) {
                                    Icon(Icons.Filled.Delete, "remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider(color = SpaceDarkBg.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
