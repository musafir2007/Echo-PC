package com.example.echo

import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class TrackCache(
    val filePath: String?,
    val streamUrl: String?,
    val title: String,
    val number: String,
    val duration: Long,
    val albumName: String,
    val artist: String,
    val quality: String,
    val techDetails: String,
    val isFavorite: Boolean,
    val artworkPath: String? = null
)

@Serializable
data class LibraryCache(
    val tracks: List<TrackCache>,
    val musicFolders: List<String> = emptyList()
)

object LibraryManager {
    private val rootDir = File(System.getProperty("user.home"), ".echo_player").apply { mkdirs() }
    private val cacheFile = File(rootDir, "library_cache.json")
    private val artworkDir = File(rootDir, "artworks").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private fun getArtworkFilename(track: Track): String {
        val input = "${track.albumName}${track.artist}"
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) } + ".jpg"
    }

    fun saveCache(tracks: List<Track>, musicFolders: List<String>) {
        try {
            val trackCaches = tracks.map { track ->
                var savedArtworkPath: String? = null
                if (track.artwork != null) {
                    val filename = getArtworkFilename(track)
                    val artFile = File(artworkDir, filename)
                    if (!artFile.exists()) {
                        artFile.writeBytes(track.artwork!!)
                    }
                    savedArtworkPath = artFile.absolutePath
                }

                TrackCache(
                    filePath = track.file?.absolutePath,
                    streamUrl = track.streamUrl,
                    title = track.title,
                    number = track.number,
                    duration = track.duration,
                    albumName = track.albumName,
                    artist = track.artist,
                    quality = track.quality,
                    techDetails = track.techDetails,
                    isFavorite = track.isFavorite.value,
                    artworkPath = savedArtworkPath
                )
            }
            val cache = LibraryCache(trackCaches, musicFolders)
            cacheFile.writeText(json.encodeToString(cache))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadCache(): Pair<List<Track>, List<String>> {
        if (!cacheFile.exists()) return Pair(emptyList(), emptyList())
        return try {
            val cache = json.decodeFromString<LibraryCache>(cacheFile.readText())
            val tracks = cache.tracks.mapNotNull { trackData ->
                val filePath = trackData.filePath
                val file = filePath?.let { path -> File(path) }

                if (trackData.streamUrl != null || (file != null && file.exists())) {
                    val artworkBytes = trackData.artworkPath?.let { path ->
                        val artFile = File(path)
                        if (artFile.exists()) artFile.readBytes() else null
                    }
                    
                    Track(
                        file = file,
                        streamUrl = trackData.streamUrl,
                        title = trackData.title,
                        number = trackData.number,
                        duration = trackData.duration,
                        albumName = trackData.albumName,
                        artist = trackData.artist,
                        quality = trackData.quality,
                        techDetails = trackData.techDetails,
                        isFavorite = mutableStateOf(trackData.isFavorite),
                        artwork = artworkBytes
                    )
                } else null
            }
            Pair(tracks, cache.musicFolders)
        } catch (_: Exception) {
            Pair(emptyList(), emptyList())
        }
    }
}
