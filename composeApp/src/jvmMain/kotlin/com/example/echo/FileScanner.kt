package com.example.echo

import kotlinx.coroutines.*
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

suspend fun scanForMediaFiles(directory: File, onProgress: (Int, Int) -> Unit): List<File> {
    return withContext(Dispatchers.IO) {
        val audioExtensions = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "aiff")
        val allFiles = directory.walkTopDown()
            .onEnter { !it.name.startsWith(".") }
            .filter { it.isFile && it.extension.lowercase() in audioExtensions }
            .toList()
        
        val total = allFiles.size
        if (total > 0) onProgress(total, total)
        allFiles
    }
}

suspend fun processFilesToAlbums(mediaFiles: List<File>, onProgress: (Float) -> Unit): List<Album> {
    return withContext(Dispatchers.Default) {
        val total = mediaFiles.size
        if (total == 0) return@withContext emptyList()
        
        val processedCount = AtomicInteger(0)
        val groups = mediaFiles.groupBy { it.parentFile }
        
        val albumResults = groups.entries.chunked(maxOf(1, groups.size / Runtime.getRuntime().availableProcessors())).map { chunk ->
            async {
                chunk.mapNotNull { (folder, files) ->
                    if (folder == null) return@mapNotNull null
                    
                    val artworkFile = try { AudioFileIO.read(files.first()) } catch (_: Exception) { null }
                    val albumArtwork = artworkFile?.tag?.firstArtwork?.binaryData
                    
                    val albumTracks = files.map { file ->
                        val audioFile = try { AudioFileIO.read(file) } catch (_: Exception) { null }
                        val tag = audioFile?.tag
                        val header = audioFile?.audioHeader
                        
                        val currentProcessed = processedCount.incrementAndGet()
                        if (currentProcessed % 10 == 0 || currentProcessed == total) {
                            onProgress(currentProcessed.toFloat() / total.toFloat())
                        }
                        
                        val bitsPerSample = header?.bitsPerSample ?: 16
                        val sampleRate = header?.sampleRateAsNumber ?: 44100
                        val bitrate = header?.bitRateAsNumber ?: -1
                        val format = header?.format ?: "Unknown"
                        
                        val quality = when {
                            bitsPerSample >= 24 && sampleRate > 48000 -> "Hi-Res Lossless"
                            bitsPerSample >= 16 && sampleRate >= 44100 -> "Lossless"
                            else -> "Standard Quality"
                        }
                        
                        val khz = if (sampleRate >= 1000) "${sampleRate / 1000}khz" else "${sampleRate}Hz"
                        val bitrateText = if (bitrate > 0) " · $bitrate kbps" else ""
                        val techDetails = "$format · ${bitsPerSample}Bit-$khz$bitrateText"
                        
                        Track(
                            file = file,
                            title = cleanTitle(tag?.getFirst(FieldKey.TITLE)?.ifEmpty { file.name } ?: file.name),
                            number = tag?.getFirst(FieldKey.TRACK) ?: "0",
                            duration = header?.trackLength?.toLong() ?: 0L,
                            albumName = tag?.getFirst(FieldKey.ALBUM) ?: folder.name,
                            artist = tag?.getFirst(FieldKey.ALBUM_ARTIST)?.ifEmpty { tag.getFirst(FieldKey.ARTIST) } ?: "Unknown Artist",
                            quality = quality,
                            techDetails = techDetails,
                            artwork = albumArtwork // Set artwork for each track
                        )
                    }
                    
                    val firstTrack = albumTracks.firstOrNull() ?: return@mapNotNull null
                    val genre = try { artworkFile?.tag?.getFirst(FieldKey.GENRE) ?: "Unknown" } catch(_: Exception) { "Unknown" }
                    val year = try { artworkFile?.tag?.getFirst(FieldKey.YEAR) ?: "Unknown" } catch(_: Exception) { "Unknown" }
                    
                    Album(
                        name = firstTrack.albumName,
                        artist = firstTrack.artist,
                        artwork = albumArtwork,
                        tracks = albumTracks.sortedBy { it.number.toIntOrNull() ?: 0 },
                        folder = folder,
                        genre = genre,
                        year = year
                    )
                }
            }
        }.awaitAll().flatten().sortedBy { it.name }
        
        onProgress(1f)
        albumResults
    }
}

private fun cleanTitle(rawTitle: String): String {
    return rawTitle.replaceFirst(Regex("^\\d+[\\s.\\-_]+"), "").substringBeforeLast(".")
}
