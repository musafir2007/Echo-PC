package com.example.echo

import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
    val isFavorite: Boolean
)

@Serializable
data class LibraryCache(
    val tracks: List<TrackCache>,
    val musicFolders: List<String> = emptyList()
)

object LibraryManager {
    private val cacheFile = File(System.getProperty("user.home"), ".echo_library_cache.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun saveCache(tracks: List<Track>, musicFolders: List<String>) {
        try {
            val cache = LibraryCache(
                tracks = tracks.map { 
                    TrackCache(
                        filePath = it.file?.absolutePath,
                        streamUrl = it.streamUrl,
                        title = it.title,
                        number = it.number,
                        duration = it.duration,
                        albumName = it.albumName,
                        artist = it.artist,
                        quality = it.quality,
                        techDetails = it.techDetails,
                        isFavorite = it.isFavorite.value
                    ) 
                },
                musicFolders = musicFolders
            )
            cacheFile.writeText(json.encodeToString(cache))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadCache(): Pair<List<Track>, List<String>> {
        if (!cacheFile.exists()) return Pair(emptyList(), emptyList())
        return try {
            val cache = json.decodeFromString<LibraryCache>(cacheFile.readText())
            val tracks = cache.tracks.mapNotNull { 
                val file = it.filePath?.let { path -> File(path) }
                // Only load if it's a stream OR the local file exists
                if (it.streamUrl != null || (file != null && file.exists())) {
                    Track(
                        file = file,
                        streamUrl = it.streamUrl,
                        title = it.title,
                        number = it.number,
                        duration = it.duration,
                        albumName = it.albumName,
                        artist = it.artist,
                        quality = it.quality,
                        techDetails = it.techDetails,
                        isFavorite = mutableStateOf(it.isFavorite)
                    )
                } else null
            }
            Pair(tracks, cache.musicFolders)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }
}
