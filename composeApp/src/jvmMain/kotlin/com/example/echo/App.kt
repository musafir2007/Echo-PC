package com.example.echo

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import uk.co.caprica.vlcj.player.base.MediaPlayer as VlcjMediaPlayer
import java.io.File
import java.util.Locale
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.Port
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Track(
    val file: File? = null,
    val streamUrl: String? = null,
    val title: String,
    val number: String = "0",
    val duration: Long = 0,
    val albumName: String = "Unknown Album",
    val artist: String = "Unknown Artist",
    var quality: String = "",
    var techDetails: String = "",
    val isFavorite: MutableState<Boolean> = mutableStateOf(false),
    var artwork: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Track) return false
        if (file != other.file) return false
        if (streamUrl != other.streamUrl) return false
        return true
    }
    override fun hashCode(): Int = file?.hashCode() ?: streamUrl.hashCode()
}

data class Album(val name: String, val artist: String, val artwork: ByteArray?, val tracks: List<Track>, val folder: File?, var genre: String = "Unknown", var year: String = "Unknown") {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Album) return false
        if (name != other.name || artist != other.artist) return false
        return true
    }
    override fun hashCode(): Int = 31 * name.hashCode() + artist.hashCode()
}

data class Artist(val name: String, val artwork: ByteArray?, val albums: List<Album>) {
    override fun equals(other: Any?): Boolean { if (this === other) return true; if (other !is Artist) return false; return name == other.name }
    override fun hashCode(): Int = name.hashCode()
}

data class Playlist(val name: String, val tracks: MutableList<Track>, var artwork: ByteArray? = null) {
    override fun equals(other: Any?): Boolean { if (this === other) return true; if (other !is Playlist) return false; return name == other.name }
    override fun hashCode(): Int = name.hashCode()
}

data class LyricLine(val time: Long, val text: String)

enum class AppPage(val title: String, val icon: ImageVector, val isTopLevel: Boolean = true) {
    Home("Home", Icons.Default.Home),
    Search("Search", Icons.Default.Search),
    Playlists("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
    Settings("Settings", Icons.Default.Settings),
    AllAlbums("Albums", Icons.Default.Album, false),
    AllArtists("Artists", Icons.Default.Person, false),
    AllTracks("Tracks", Icons.Default.MusicNote, false)
}

enum class FullscreenPopout { Queue, Lyrics }

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var isOledMode by remember { mutableStateOf(false) }
    var isDolbyAtmosEnabled by remember { mutableStateOf(false) }
    var isGaplessEnabled by remember { mutableStateOf(false) }
    var isViewingExtensions by remember { mutableStateOf(false) }
    var selectedExtension by remember { mutableStateOf<Extension?>(null) }
    
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var pitchWithSpeed by remember { mutableStateOf(false) }
    var bassBoostLevel by remember { mutableStateOf(0) }

    val baseBackgroundColor = Color(0xFF151219)
    val currentAppBackgroundColor = if (isOledMode) Color.Black else baseBackgroundColor
    
    val extensions = remember { mutableStateListOf<Extension>(OfflineExtension()) }
    
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var allTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var recentlyPlayed by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var currentPage by remember { mutableStateOf(AppPage.Home) }

    val offlineExtension = remember(extensions) {
        extensions.find { it is OfflineExtension } as? OfflineExtension
    }
    
    val isOfflineEnabled = remember(offlineExtension) {
        derivedStateOf { offlineExtension?.isEnabled ?: true }
    }

    val handleBack = {
        if (selectedAlbum != null) selectedAlbum = null
        else if (selectedArtist != null) selectedArtist = null
        else if (selectedExtension != null) selectedExtension = null
        else if (isViewingExtensions) isViewingExtensions = false
        else if (!currentPage.isTopLevel) currentPage = AppPage.Home
    }

    val isBackEnabled = selectedAlbum != null || selectedArtist != null || selectedExtension != null || isViewingExtensions || !currentPage.isTopLevel

    MaterialTheme {
        Surface(
            color = currentAppBackgroundColor, 
            modifier = Modifier.fillMaxSize()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace) {
                        if (isBackEnabled) { handleBack(); true } else false
                    } else false
                }
        ) {
            var mediaFile by remember { mutableStateOf<File?>(null) }
            var streamUrl by remember { mutableStateOf<String?>(null) }
            var mediaPlayer by remember { mutableStateOf<VlcjMediaPlayer?>(null) }
            var isPlayingState by remember { mutableStateOf(false) }
            var title by remember { mutableStateOf("") }
            var artistName by remember { mutableStateOf("") }
            var duration by remember { mutableStateOf(0L) }
            var position by remember { mutableStateOf(0L) }
            var artwork by remember { mutableStateOf<ByteArray?>(null) }
            var qualityText by remember { mutableStateOf("") }
            var techDetails by remember { mutableStateOf("") }
            var showQualityPopup by remember { mutableStateOf(false) }
            
            var currentPlaylist by remember { mutableStateOf<List<Track>>(emptyList()) }
            var currentTrackIndex by remember { mutableStateOf(0) }
            
            var isShuffle by remember { mutableStateOf(false) }
            var loopMode by remember { mutableStateOf(0) }
            
            var isPlayerExpanded by remember { mutableStateOf(false) }
            var isSidebarExpanded by remember { mutableStateOf(false) }
            var activePopout by remember { mutableStateOf<FullscreenPopout?>(null) }
            var showArtworkMenu by remember { mutableStateOf(false) }
            var searchQuery by remember { mutableStateOf("") }
            
            var volume by remember { mutableStateOf(getSystemVolume()) }

            val navigateToArtist: (String) -> Unit = { name ->
                artists.find { it.name == name }?.let {
                    selectedArtist = it; selectedAlbum = null; currentPage = AppPage.Home; isPlayerExpanded = false
                }
            }

            LaunchedEffect(Unit) {
                ExtensionManager.loadAll()
                extensions.addAll(ExtensionManager.installedExtensions)
                val (cachedTracks, cachedFolders) = LibraryManager.loadCache()
                if (cachedFolders.isNotEmpty()) { offlineExtension?.musicFolders?.clear(); offlineExtension?.musicFolders?.addAll(cachedFolders) }
                if (cachedTracks.isNotEmpty()) {
                    allTracks = cachedTracks
                    val groupedAlbums = allTracks.groupBy { it.albumName }.map { (name, tracks) ->
                        val firstTrack = tracks.first()
                        Album(name = name, artist = firstTrack.artist, artwork = firstTrack.artwork, tracks = tracks.sortedBy { it.number.toIntOrNull() ?: 0 }, folder = firstTrack.file?.parentFile, genre = "Various", year = "Various")
                    }
                    albums = groupedAlbums.sortedBy { it.name }
                    artists = albums.groupBy { it.artist }.map { (name, artistAlbums) -> Artist(name, artistAlbums.firstOrNull { it.artwork != null }?.artwork, artistAlbums) }.sortedBy { it.name }
                }
            }

            LaunchedEffect(offlineExtension, isOfflineEnabled.value, offlineExtension?.musicFolders?.size) {
                if (isOfflineEnabled.value && offlineExtension != null && offlineExtension.musicFolders.isNotEmpty()) {
                    offlineExtension.isScanning = true
                    val musicDirs = offlineExtension.musicFolders.map { File(it) }
                    var allMediaFiles = mutableListOf<File>()
                    musicDirs.distinct().forEach { dir -> if (dir.exists() && dir.isDirectory) allMediaFiles.addAll(scanForMediaFiles(dir) { _, _ -> }) }
                    if (allMediaFiles.isNotEmpty()) {
                        val resultAlbums = processFilesToAlbums(allMediaFiles.distinctBy { it.absolutePath }) { offlineExtension.scanProgress = it }
                        albums = resultAlbums; allTracks = resultAlbums.flatMap { it.tracks }
                        artists = resultAlbums.groupBy { it.artist }.map { (name, artistAlbums) -> Artist(name, artistAlbums.firstOrNull { it.artwork != null }?.artwork, artistAlbums) }.sortedBy { it.name }
                        LibraryManager.saveCache(allTracks, offlineExtension.musicFolders.toList())
                    }
                    offlineExtension.isScanning = false
                }
            }

            LaunchedEffect(volume) { setSystemVolume(volume); mediaPlayer?.audio()?.setVolume(100) }

            val playTrack: (Track, List<Track>) -> Unit = { track, list ->
                title = track.title; artistName = track.artist; qualityText = track.quality; techDetails = track.techDetails; mediaFile = track.file; streamUrl = track.streamUrl
                currentPlaylist = if (isShuffle) list.shuffled() else list
                currentTrackIndex = currentPlaylist.indexOfFirst { it.title == track.title }.coerceAtLeast(0)
                if (recentlyPlayed.none { it.title == track.title }) recentlyPlayed = (listOf(track) + recentlyPlayed).take(10)
            }

            if (mediaFile != null || streamUrl != null) {
                MediaPlayer(file = mediaFile, streamUrl = streamUrl, isDolbyAtmosEnabled = isDolbyAtmosEnabled, isGaplessEnabled = isGaplessEnabled, speed = playbackSpeed, pitchWithSpeed = pitchWithSpeed, bassBoost = bassBoostLevel, modifier = Modifier.size(0.dp),
                    onReady = { player, _, _, newDuration, newArtwork, quality, tech -> mediaPlayer = player; duration = newDuration; artwork = newArtwork; qualityText = quality; techDetails = tech; player.audio().setVolume(100) },
                    onPositionChanged = { position = it }, onIsPlayingChanged = { isPlayingState = it },
                    onFinished = {
                        if (currentPlaylist.isNotEmpty()) {
                            if (loopMode == 2) mediaPlayer?.controls()?.play()
                            else {
                                val nextIndex = (currentTrackIndex + 1) % currentPlaylist.size
                                if (nextIndex != 0 || loopMode != 0) {
                                    val nextTrack = currentPlaylist[nextIndex]
                                    playTrack(nextTrack, currentPlaylist)
                                }
                            }
                        }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(start = 72.dp)) {
                    if (offlineExtension?.isScanning == true) LinearProgressIndicator(progress = offlineExtension.scanProgress, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Color.Cyan, backgroundColor = Color.White.copy(alpha = 0.2f))
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentPage) {
                            AppPage.Home -> {
                                if (isOfflineEnabled.value) {
                                    if (allTracks.isEmpty() && offlineExtension?.isScanning == false) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.LibraryMusic, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                                                Spacer(Modifier.height(16.dp)); Text("Your library is empty", color = Color.White, style = MaterialTheme.typography.titleLarge); Text("Add a music folder in Settings to start listening", color = Color.Gray); Spacer(Modifier.height(24.dp))
                                                Button(onClick = { isViewingExtensions = true; currentPage = AppPage.Settings }) { Text("Go to Settings") }
                                            }
                                        }
                                    } else {
                                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                            item {
                                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                    Button(onClick = { if (allTracks.isNotEmpty()) playTrack(allTracks.first(), allTracks) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play All") }
                                                    Button(onClick = { if (allTracks.isNotEmpty()) { isShuffle = true; playTrack(allTracks.random(), allTracks) } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("Shuffle") }
                                                }
                                                HomeSectionHeader("Albums") { currentPage = AppPage.AllAlbums }
                                                LazyRow { items(albums.take(10), key = { "home_" + it.name + it.artist }) { album -> AlbumGridItem(album) { selectedAlbum = album } } }
                                                Spacer(modifier = Modifier.height(32.dp))
                                                HomeSectionHeader("Artists") { currentPage = AppPage.AllArtists }
                                                LazyRow { items(artists.take(10), key = { "home_" + it.name }) { artist -> ArtistGridItem(artist, size = 140.dp) { selectedArtist = artist } } }
                                                Spacer(modifier = Modifier.height(32.dp))
                                                HomeSectionHeader("Tracks") { currentPage = AppPage.AllTracks }
                                                Column { allTracks.take(10).forEach { track -> key(track.title) { TrackItem(track, showArtwork = true) { playTrack(track, allTracks) }; HorizontalDivider(color = Color.White.copy(alpha = 0.05f)) } } }
                                            }
                                        }
                                    }
                                } else { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Offline mode disabled", color = Color.Gray) } }
                            }
                            AppPage.AllAlbums -> { Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Albums", color = Color.White, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(16.dp)); LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), modifier = Modifier.fillMaxSize()) { items(albums, key = { it.name + it.artist }) { album -> AlbumGridItem(album) { selectedAlbum = album } } } } }
                            AppPage.AllArtists -> { Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Artists", color = Color.White, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(16.dp)); LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), modifier = Modifier.fillMaxSize()) { items(artists, key = { it.name }) { artist -> ArtistGridItem(artist, size = 140.dp) { selectedArtist = artist } } } } }
                            AppPage.AllTracks -> { Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Tracks", color = Color.White, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(16.dp)); LazyColumn(modifier = Modifier.fillMaxSize()) { items(allTracks, key = { it.title + it.artist }) { track -> TrackItem(track, showArtwork = true) { playTrack(track, allTracks) }; HorizontalDivider(color = Color.White.copy(alpha = 0.05f)) } } } }
                            AppPage.Search -> {
                                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                    TextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search everywhere...") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF2D2833), unfocusedContainerColor = Color(0xFF2D2833), focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (searchQuery.isNotEmpty()) {
                                        var extensionResults by remember { mutableStateOf<List<Track>>(emptyList()) }
                                        LaunchedEffect(searchQuery) {
                                            val results = mutableListOf<Track>()
                                            extensions.forEach { if (it.isEnabled) results.addAll(it.search(searchQuery)) }
                                            extensionResults = results
                                        }
                                        LazyColumn {
                                            val localTracks = allTracks.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
                                            if (localTracks.isNotEmpty()) { item { Text("Local Library", color = Color.Gray, style = MaterialTheme.typography.labelLarge) }; items(localTracks) { track -> TrackItem(track, showArtwork = true) { playTrack(track, localTracks) }; HorizontalDivider(color = Color.White.copy(alpha = 0.05f)) } }
                                            if (extensionResults.isNotEmpty()) { item { Text("Extensions", color = Color.Gray, style = MaterialTheme.typography.labelLarge) }; items(extensionResults) { track -> TrackItem(track, showArtwork = true) { playTrack(track, extensionResults) }; HorizontalDivider(color = Color.White.copy(alpha = 0.05f)) } }
                                        }
                                    }
                                }
                            }
                            AppPage.Playlists -> { PlaylistsPage(playlists = playlists, onPlaylistsUpdated = { playlists = it }) }
                            AppPage.Settings -> {
                                if (isViewingExtensions) { Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text(if (selectedExtension != null) selectedExtension!!.name else "Extensions", color = Color.White, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(24.dp)); if (selectedExtension == null) ExtensionsPage(extensions, onExtensionClick = { selectedExtension = it }) else ExtensionSettingsPage(selectedExtension!!) } }
                                else {
                                    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                                        Text("Settings", color = Color.White, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(24.dp))
                                        Text("Appearance", color = Color.Cyan, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Column { Text("OLED Mode", color = Color.White, style = MaterialTheme.typography.titleMedium); Text("Pure black background for the main pages", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Switch(checked = isOledMode, onCheckedChange = { isOledMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFB39DDB))) }
                                        Spacer(Modifier.height(24.dp)); Text("Audio Effects", color = Color.Cyan, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(16.dp)); Text("Playback Speed: ${String.format("%.2fx", playbackSpeed)}", color = Color.White, style = MaterialTheme.typography.titleMedium); Slider(value = playbackSpeed, onValueChange = { playbackSpeed = it }, valueRange = 0.5f..2.0f, colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan)); Spacer(Modifier.height(16.dp)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.weight(1f)) { Text("Shift Pitch with Speed", color = Color.White, style = MaterialTheme.typography.titleMedium); Text("Classic vinyl/tape effect", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }; Switch(checked = pitchWithSpeed, onCheckedChange = { pitchWithSpeed = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan)) }; Spacer(Modifier.height(24.dp)); Text("Bass Boost", color = Color.White, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(12.dp)); Row(modifier = Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (i in 0 until 11) { val isActive = i <= bassBoostLevel; Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (isActive) Color.Cyan.copy(alpha = 0.3f + (i * 0.07f)) else Color.White.copy(alpha = 0.1f)).clickable { bassBoostLevel = i }) } }; Text("Level ${bassBoostLevel + 1}", color = Color.Cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
                                        Spacer(Modifier.height(32.dp)); Text("Hardware & Playback", color = Color.Cyan, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Column { Text("Dolby Atmos", color = Color.White, style = MaterialTheme.typography.titleMedium); Text("Enable Dolby Atmos passthrough (Requires hardware support)", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Switch(checked = isDolbyAtmosEnabled, onCheckedChange = { isDolbyAtmosEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFB39DDB))) } ; Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Column { Text("Gapless Playback", color = Color.White, style = MaterialTheme.typography.titleMedium); Text("Minimize silence between tracks", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Switch(checked = isGaplessEnabled, onCheckedChange = { isGaplessEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFB39DDB))) }
                                        Spacer(Modifier.height(32.dp)); Surface(modifier = Modifier.fillMaxWidth().clickable { isViewingExtensions = true }, color = Color.Transparent) { Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Extension, null, tint = Color.White); Spacer(Modifier.width(16.dp)); Column { Text("Extensions", color = Color.White, style = MaterialTheme.typography.titleMedium); Text("Manage music providers and trackers", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = Color.Gray, modifier = Modifier.size(16.dp)) } }
                                    }
                                }
                            }
                        }
                        if (selectedAlbum != null) Surface(color = currentAppBackgroundColor, modifier = Modifier.fillMaxSize()) { AlbumDetailView(album = selectedAlbum!!, onPlay = { playTrack(it, selectedAlbum!!.tracks) }, onShuffle = { val shuffled = selectedAlbum!!.tracks.shuffled(); playTrack(shuffled.first(), shuffled) }, onArtistClick = navigateToArtist) }
                        else if (selectedArtist != null) Surface(color = currentAppBackgroundColor, modifier = Modifier.fillMaxSize()) { ArtistDetailView(selectedArtist!!, onAlbumClick = { album -> selectedAlbum = album }) }
                    }
                    if (mediaFile != null || streamUrl != null && !isPlayerExpanded) { Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) { MediaPlayerControls(mediaPlayer = mediaPlayer, isPlaying = isPlayingState, title = title, artist = artistName, duration = duration, position = position, artwork = artwork, codecInfo = qualityText, isShuffle = isShuffle, loopMode = loopMode, volume = volume, onVolumeChange = { volume = it }, onPrevious = { if (currentPlaylist.isNotEmpty()) { currentTrackIndex = (currentTrackIndex - 1 + currentPlaylist.size) % currentPlaylist.size; playTrack(currentPlaylist[currentTrackIndex], currentPlaylist) } }, onNext = { if (currentPlaylist.isNotEmpty()) { currentTrackIndex = (currentTrackIndex + 1) % currentPlaylist.size; playTrack(currentPlaylist[currentTrackIndex], currentPlaylist) } }, onShuffleToggle = { isShuffle = !isShuffle }, onLoopToggle = { loopMode = (loopMode + 1) % 3 }, onExpandToggle = { isPlayerExpanded = true }, onQualityClick = { showQualityPopup = true }, onArtworkClick = { showArtworkMenu = true }, onArtistClick = navigateToArtist) } }
                }
                val sidebarWidth by animateDpAsState(targetValue = if (isSidebarExpanded) 200.dp else 72.dp, animationSpec = tween(300, easing = FastOutSlowInEasing))
                Column(modifier = Modifier.width(sidebarWidth).fillMaxHeight().background(currentAppBackgroundColor).padding(vertical = 16.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (isSidebarExpanded) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = handleBack, enabled = isBackEnabled) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (isBackEnabled) Color.White else Color.Gray) }; Spacer(modifier = Modifier.width(8.dp)); Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { val iconFile = File("composeApp/src/jvmMain/composeResources/drawable/Nightly.png"); if (iconFile.exists()) { val imageBitmap = org.jetbrains.skia.Image.makeFromEncoded(iconFile.readBytes()).toComposeImageBitmap(); Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Spacer(modifier = Modifier.width(8.dp)); Text("Echo", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Clip) } }
                        else { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { IconButton(onClick = handleBack, enabled = isBackEnabled) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (isBackEnabled) Color.White else Color.Gray) } } }
                    }
                    Spacer(modifier = Modifier.height(8.dp)); IconButton(onClick = { isSidebarExpanded = !isSidebarExpanded }, modifier = Modifier.padding(start = 12.dp).align(Alignment.Start)) { Icon(Icons.Default.Menu, null, tint = Color.White) }; Spacer(modifier = Modifier.height(8.dp))
                    AppPage.entries.filter { it.isTopLevel }.forEach { page -> NavigationDrawerItem(label = { if (isSidebarExpanded) Text(page.title, color = Color.White, maxLines = 1) }, selected = currentPage == page, onClick = { currentPage = page; selectedAlbum = null; selectedArtist = null; isViewingExtensions = false; selectedExtension = null }, icon = { Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { Icon(page.icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) } }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent, selectedContainerColor = Color(0xFF2D2833)), modifier = Modifier.padding(horizontal = 8.dp)) }
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (isPlayerExpanded && (mediaFile != null || streamUrl != null)) {
                    Box(modifier = Modifier.fillMaxSize().background(currentAppBackgroundColor).pointerInput(Unit) { }) {
                        artwork?.let { val infiniteTransition = rememberInfiniteTransition(); val driftX by infiniteTransition.animateFloat(initialValue = -100f, targetValue = 100f, animationSpec = infiniteRepeatable(animation = tween(15000, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse)); val driftY by infiniteTransition.animateFloat(initialValue = -50f, targetValue = 50f, animationSpec = infiniteRepeatable(animation = tween(20000, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse)); val scale by infiniteTransition.animateFloat(initialValue = 2.0f, targetValue = 2.3f, animationSpec = infiniteRepeatable(animation = tween(25000, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse)); val imageBitmap = org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap(); Box(modifier = Modifier.fillMaxSize()) { Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer { translationX = driftX; translationY = driftY; scaleX = scale; scaleY = scale }.blur(250.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded), contentScale = ContentScale.Crop); Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer { translationX = -driftX * 0.7f; translationY = -driftY * 0.7f; scaleX = scale * 1.1f; scaleY = scale * 1.1f; alpha = 0.4f }.blur(300.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded), contentScale = ContentScale.Crop) } }
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))); IconButton(onClick = { isPlayerExpanded = false; activePopout = null }, modifier = Modifier.align(Alignment.TopStart).padding(32.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Minimize", tint = Color.White, modifier = Modifier.size(48.dp)) }
                        val popoutWidth = 450.dp; val playerOffset by animateDpAsState(targetValue = if (activePopout != null) (-popoutWidth / 2) else 0.dp, animationSpec = tween(300))
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(modifier = Modifier.width(600.dp).offset(x = playerOffset).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                val currentImageSize = 450.dp; artwork?.let { Image(org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap(), null, modifier = Modifier.size(currentImageSize).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray).pointerInput(Unit) { awaitPointerEventScope { while (true) { val event = awaitPointerEvent(); if (event.buttons.isSecondaryPressed) { showArtworkMenu = true } } } }) }
                                Spacer(modifier = Modifier.height(24.dp)); Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center); Text(artistName, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { navigateToArtist(artistName) }); Spacer(modifier = Modifier.height(12.dp)); Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp), modifier = Modifier.clickable { showQualityPopup = true }) { Text(qualityText, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }; Spacer(modifier = Modifier.height(32.dp)); val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f; Box(modifier = Modifier.width(currentImageSize * 2).height(12.dp).pointerInput(duration) { detectTapGestures { offset -> if (duration > 0) { val newProgress = (offset.x / size.width).coerceIn(0f, 1f); mediaPlayer?.controls()?.setPosition(newProgress) } } }, contentAlignment = Alignment.Center) { LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Color.White, backgroundColor = Color.White.copy(alpha = 0.2f)) }; Spacer(modifier = Modifier.height(12.dp)); Row(modifier = Modifier.width(currentImageSize * 2), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatDuration(position / 1000), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall); Text(formatDuration(duration / 1000), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall) }
                                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { activePopout = if (activePopout == FullscreenPopout.Queue) null else FullscreenPopout.Queue }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = if (activePopout == FullscreenPopout.Queue) Color.Cyan else Color.White) }; Spacer(modifier = Modifier.width(16.dp)); IconButton(onClick = { isShuffle = !isShuffle }) { Icon(Icons.Default.Shuffle, null, tint = if (isShuffle) Color.Cyan else Color.White) }; IconButton(onClick = { if (currentPlaylist.isNotEmpty()) { currentTrackIndex = (currentTrackIndex - 1 + currentPlaylist.size) % currentPlaylist.size; val nextTrack = currentPlaylist[currentTrackIndex]; playTrack(nextTrack, currentPlaylist) } }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp)) }; IconButton(onClick = { if (isPlayingState) mediaPlayer?.controls()?.pause() else mediaPlayer?.controls()?.play() }) { Icon(if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(64.dp)) }; IconButton(onClick = { if (currentPlaylist.isNotEmpty()) { currentTrackIndex = (currentTrackIndex + 1) % currentPlaylist.size; val nextTrack = currentPlaylist[currentTrackIndex]; playTrack(nextTrack, currentPlaylist) } }) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp)) }; IconButton(onClick = { loopMode = (loopMode + 1) % 3 }) { Icon(if (loopMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if (loopMode > 0) Color.Cyan else Color.White) }; Spacer(modifier = Modifier.width(16.dp)); IconButton(onClick = { activePopout = if (activePopout == FullscreenPopout.Lyrics) null else FullscreenPopout.Lyrics }) { Icon(Icons.Default.Lyrics, null, tint = if (activePopout == FullscreenPopout.Lyrics) Color.Cyan else Color.White) } }
                            }
                        }
                        Row(modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd)) { AnimatedVisibility(visible = activePopout != null, enter = expandHorizontally(), exit = shrinkHorizontally()) { Column(modifier = Modifier.width(popoutWidth).fillMaxHeight().padding(vertical = 16.dp)) { when (activePopout) { FullscreenPopout.Queue -> { Text("Queue", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)); LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) { items(currentPlaylist) { track -> TrackItem(track, isCurrent = track.title == title, showArtwork = true) { playTrack(track, currentPlaylist) }; HorizontalDivider(color = Color.White.copy(alpha = 0.05f)) } } } FullscreenPopout.Lyrics -> { val dummyLyrics = listOf(LyricLine(0, "I see a red door"), LyricLine(3000, "And I want it painted black"), LyricLine(6000, "No colors anymore"), LyricLine(9000, "I want them to turn black"), LyricLine(12000, "I see the girls walk by"), LyricLine(15000, "Dressed in their summer clothes"), LyricLine(18000, "I have to turn my head"), LyricLine(21000, "Until my darkness goes")); Box(modifier = Modifier.padding(16.dp)) { LyricsView(lyrics = dummyLyrics, currentPosition = position) } } else -> {} } } } }
                    }
                }
            }
            if (showQualityPopup) { Dialog(onDismissRequest = { showQualityPopup = false }) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2833)), shape = RoundedCornerShape(12.dp), modifier = Modifier.width(300.dp)) { Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(qualityText, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center); Spacer(modifier = Modifier.height(8.dp)); Text(techDetails, color = Color.Gray, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center); Spacer(modifier = Modifier.height(24.dp)); TextButton(onClick = { showQualityPopup = false }) { Text("Close", color = Color.Cyan) } } } } }
            if (showArtworkMenu && (mediaFile != null || streamUrl != null)) { Dialog(onDismissRequest = { showArtworkMenu = false }) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2833)), shape = RoundedCornerShape(12.dp), modifier = Modifier.width(250.dp)) { Column(modifier = Modifier.padding(16.dp)) { Text("Navigation", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)); TextButton(onClick = { artists.find { it.name == artistName }?.let { selectedArtist = it; currentPage = AppPage.Home }; showArtworkMenu = false; isPlayerExpanded = false }) { Text("Go to Artist: $artistName", color = Color.White) }; TextButton(onClick = { val track = currentPlaylist.find { it.title == title }; albums.find { it.name == track?.albumName }?.let { selectedAlbum = it; currentPage = AppPage.Home }; showArtworkMenu = false; isPlayerExpanded = false }) { Text("Go to Album", color = Color.White) } } } } }
        }
    }
}

@Composable
fun ExtensionInstallDialog(onDismiss: () -> Unit, onLinkInstall: (String) -> Unit, onFileInstall: () -> Unit) {
    var showLinkInput by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2833)), shape = RoundedCornerShape(12.dp)) { Column(modifier = Modifier.padding(24.dp).width(300.dp)) { Text("Install Extension", color = Color.White, style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(24.dp)); if (!showLinkInput) { Button(onClick = { showLinkInput = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text("From Link") }; Spacer(Modifier.height(12.dp)); Button(onClick = onFileInstall, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("From File (.eapk)") } } else { TextField(value = linkText, onValueChange = { linkText = it }, placeholder = { Text("https://example.com/plugin.eapk") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(16.dp)); Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { TextButton(onClick = { showLinkInput = false }) { Text("Back") }; Button(onClick = { onLinkInstall(linkText) }, enabled = linkText.isNotEmpty()) { Text("Install") } } } } } }
}

@Composable
fun HomeSectionHeader(title: String, onClick: () -> Unit) { Row(modifier = Modifier.clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall); Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = Color.White, modifier = Modifier.size(16.dp)) } }
@Composable
fun PlaylistsPage(playlists: List<Playlist>, onPlaylistsUpdated: (List<Playlist>) -> Unit) { var isCreating by remember { mutableStateOf(false) } ; if (isCreating) { CreatePlaylistView(onCancel = { isCreating = false }, onConfirm = { name, artwork -> onPlaylistsUpdated(playlists + Playlist(name, mutableListOf(), artwork)); isCreating = false }) } else { Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Playlists", color = Color.White, style = MaterialTheme.typography.headlineMedium); Spacer(modifier = Modifier.height(24.dp)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isCreating = true }.padding(8.dp)) { Surface(modifier = Modifier.size(60.dp), color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(32.dp)) } } ; Spacer(modifier = Modifier.width(16.dp)); Text("New Playlist...", color = Color.White, style = MaterialTheme.typography.titleMedium) } ; Spacer(modifier = Modifier.height(24.dp)); LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(playlists) { playlist -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(160.dp).clickable { }) { playlist.artwork?.let { Image(bitmap = org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap(), contentDescription = null, modifier = Modifier.size(160.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) } ?: Box(modifier = Modifier.size(160.dp).background(Color.DarkGray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color.Gray, modifier = Modifier.size(64.dp)) } ; Text(playlist.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)) } } } } } }
@Composable
fun CreatePlaylistView(onCancel: () -> Unit, onConfirm: (String, ByteArray?) -> Unit) { var name by remember { mutableStateOf("") } ; var artwork by remember { mutableStateOf<ByteArray?>(null) } ; Box(modifier = Modifier.fillMaxSize()) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel", tint = Color.White) } ; IconButton(onClick = { if (name.isNotEmpty()) onConfirm(name, artwork) }, enabled = name.isNotEmpty()) { Icon(Icons.Default.Check, "Confirm", tint = if (name.isNotEmpty()) Color.Cyan else Color.Gray) } } ; Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Box(modifier = Modifier.size(250.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).clickable { val fileChooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Images", "jpg", "png", "jpeg"); dialogTitle = "Select Playlist Cover" } ; if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) { artwork = fileChooser.selectedFile.readBytes() } }, contentAlignment = Alignment.Center) { artwork?.let { Image(bitmap = org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) } ?: Icon(Icons.Default.PhotoCamera, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp)) } ; Spacer(modifier = Modifier.height(32.dp)); TextField(value = name, onValueChange = { name = it }, placeholder = { Text("Playlist Name", color = Color.Gray) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.White, unfocusedIndicatorColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.width(300.dp), singleLine = true, textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center)) } } }
@Composable
fun AlbumGridItem(album: Album, onClick: () -> Unit) { Column(modifier = Modifier.width(160.dp).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { album.artwork?.let { Image(bitmap = org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap(), contentDescription = null, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp))) } ; Text(album.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium); Text(album.artist, color = Color.Gray, maxLines = 1, style = MaterialTheme.typography.bodySmall) } }
@Composable
fun ArtistGridItem(artist: Artist, size: androidx.compose.ui.unit.Dp = 100.dp, onClick: () -> Unit) { Column(modifier = Modifier.width(size + 20.dp).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { artist.artwork?.let { Image(bitmap = org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap(), contentDescription = null, modifier = Modifier.size(size).clip(CircleShape)) } ?: Box(modifier = Modifier.size(size).clip(CircleShape).background(Color.DarkGray)); Text(artist.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) } }
@Composable
fun AlbumDetailView(album: Album, onPlay: (Track) -> Unit, onShuffle: () -> Unit, onArtistClick: (String) -> Unit) { Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { album.artwork?.let { Image(bitmap = org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap(), contentDescription = null, modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))) } ; Column(modifier = Modifier.padding(start = 24.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Album, null, tint = Color.White, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(album.name, color = Color.White, style = MaterialTheme.typography.headlineMedium) } ; Spacer(Modifier.height(8.dp)); Text(album.artist, color = Color.Cyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onArtistClick(album.artist) }); Text("${album.genre} · ${album.year}", color = Color.Gray); Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) { Button(onClick = { onPlay(album.tracks.first()) }) { Text("Play") } ; Spacer(Modifier.width(16.dp)); IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, "Shuffle", tint = Color.White, modifier = Modifier.size(32.dp)) } } } } ; Spacer(modifier = Modifier.height(24.dp)); LazyColumn { items(album.tracks) { track -> TrackItem(track, showArtwork = true, showFeatures = true) { onPlay(track) }; HorizontalDivider(color = Color.White.copy(alpha = 0.05f)) } } } }
@Composable
fun ArtistDetailView(artist: Artist, onAlbumClick: (Album) -> Unit) { Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text(artist.name, color = Color.White, style = MaterialTheme.typography.headlineLarge); Spacer(modifier = Modifier.height(16.dp)); LazyVerticalGrid(columns = GridCells.Adaptive(160.dp)) { items(artist.albums) { album -> AlbumGridItem(album) { onAlbumClick(album) } } } } }
@Composable
fun TrackItem(track: Track, isCurrent: Boolean = false, showArtwork: Boolean = false, showFeatures: Boolean = false, onClick: () -> Unit) { Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).background(if (isCurrent) Color.White.copy(alpha = 0.1f) else Color.Transparent).padding(vertical = 8.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { val textColor = if (isCurrent) Color.Cyan else Color.White; if (showArtwork) { val artwork = track.artwork ?: (if (track.albumName != "Unknown Album") null else null) ; artwork?.let { Image(bitmap = org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop) } ?: Box(modifier = Modifier.size(40.dp).background(Color.DarkGray, RoundedCornerShape(4.dp))) ; Spacer(Modifier.width(16.dp)) } ; Column(modifier = Modifier.weight(1f)) { Text(track.title, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 1) } ; if (showFeatures) { Text(formatDuration(track.duration), color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp)); IconButton(onClick = { track.isFavorite.value = !track.isFavorite.value }) { Icon(Icons.Default.Favorite, null, tint = if (track.isFavorite.value) Color.Red else Color.Gray) } ; IconButton(onClick = { }) { Icon(Icons.Default.MoreHoriz, null, tint = Color.Gray) } } } }
@Composable
fun LyricsView(lyrics: List<LyricLine>, currentPosition: Long) { val listState = rememberLazyListState() ; val activeIndex = lyrics.indexOfLast { it.time <= currentPosition }.coerceAtLeast(0) ; LaunchedEffect(activeIndex) { if (lyrics.isNotEmpty()) listState.animateScrollToItem(activeIndex, scrollOffset = -200) } ; LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(vertical = 100.dp) ) { items(lyrics.size) { index -> val lyric = lyrics[index] ; val opacity by animateFloatAsState(if (index == activeIndex) 1f else 0.4f) ; val scale by animateFloatAsState(if (index == activeIndex) 1.1f else 1f) ; Text(text = lyric.text, color = Color.White.copy(alpha = opacity), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp), modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clickable { }, textAlign = TextAlign.Start) } } }
@Composable
fun ExtensionsPage(extensions: MutableList<Extension>, onExtensionClick: (Extension) -> Unit) { var selectedCategory by remember { mutableStateOf(ExtensionCategory.Music) }; var showInstallDialog by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope(); Column(modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Available Extensions", color = Color.Gray, style = MaterialTheme.typography.labelLarge); Button(onClick = { showInstallDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB39DDB))) { Icon(Icons.Default.AddCircle, null); Spacer(Modifier.width(8.dp)); Text("Install") } }; if (showInstallDialog) { ExtensionInstallDialog(onDismiss = { showInstallDialog = false }, onLinkInstall = { url -> scope.launch { val success = withContext(Dispatchers.IO) { ExtensionManager.installFromLink(url) }; if (success) { extensions.clear(); extensions.add(OfflineExtension()); extensions.addAll(ExtensionManager.installedExtensions) }; showInstallDialog = false } }, onFileInstall = { val fileChooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Echo Extension (.eapk)", "eapk"); dialogTitle = "Select Extension File" }; if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) { val success = ExtensionManager.installFromFile(fileChooser.selectedFile); if (success) { extensions.clear(); extensions.add(OfflineExtension()); extensions.addAll(ExtensionManager.installedExtensions) } }; showInstallDialog = false }) }; Spacer(modifier = Modifier.height(16.dp)); ScrollableTabRow(selectedTabIndex = selectedCategory.ordinal, containerColor = Color.Transparent, contentColor = Color.White, edgePadding = 0.dp, divider = {}) { ExtensionCategory.entries.forEach { category -> Tab(selected = selectedCategory == category, onClick = { selectedCategory = category }, text = { Text(category.name) }) } }; Spacer(modifier = Modifier.height(16.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 600.dp)) { items(extensions.filter { it.category == selectedCategory }) { extension -> ExtensionItem(extension, onClick = { onExtensionClick(extension) }) } } } }
@Composable
fun ExtensionItem(extension: Extension, onClick: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2833)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(modifier = Modifier.size(48.dp), color = Color.Black.copy(alpha = 0.3f), shape = CircleShape) { Box(contentAlignment = Alignment.Center) { Icon(extension.icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) } } ; Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) { Text(extension.name, color = Color.White, style = MaterialTheme.typography.titleMedium); Text(extension.description, color = Color.Gray, style = MaterialTheme.typography.bodySmall); Text("${extension.version} • ${extension.type}", color = Color.Gray, style = MaterialTheme.typography.labelSmall); if (extension is OfflineExtension && extension.isScanning) { Spacer(Modifier.height(4.dp)); LinearProgressIndicator(progress = extension.scanProgress, modifier = Modifier.fillMaxWidth().height(2.dp), color = Color.Cyan, backgroundColor = Color.White.copy(alpha = 0.1f)) } } ; Switch(checked = extension.isEnabled, onCheckedChange = { extension.isEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFB39DDB), checkedTrackColor = Color(0xFFB39DDB).copy(alpha = 0.5f))) } } }
@Composable
fun ExtensionSettingsPage(extension: Extension) { Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("General Settings", color = Color.Gray, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(16.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable Extension", color = Color.White, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.weight(1f)); Switch(checked = extension.isEnabled, onCheckedChange = { extension.isEnabled = it }) } ; if (extension is OfflineExtension) { Spacer(Modifier.height(32.dp)); Text("Music Folders", color = Color.Gray, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); Text("Select directories to scan for music files.", color = Color.Gray, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(16.dp)); Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { extension.musicFolders.forEach { folder -> Surface(color = Color(0xFF2D2833), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = Color.Cyan, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(folder, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); IconButton(onClick = { extension.musicFolders.remove(folder) }) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) } } } } ; Button(onClick = { val fileChooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Select Music Folder" } ; if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) { val path = fileChooser.selectedFile.absolutePath; if (!extension.musicFolders.contains(path)) extension.musicFolders.add(path) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))) { Icon(Icons.Default.Add, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Add Music Folder", color = Color.White) } } } } }
private fun getSystemVolume(): Float { return try { val port = AudioSystem.getLine(Port.Info.SPEAKER) as Port; port.open(); (port.getControl(FloatControl.Type.VOLUME) as FloatControl).value * 100f } catch (e: Exception) { 100f } }
private fun setSystemVolume(volume: Float) { try { val port = AudioSystem.getLine(Port.Info.SPEAKER) as Port; port.open(); (port.getControl(FloatControl.Type.VOLUME) as FloatControl).value = volume / 100f } catch (e: Exception) { } }
