package com.example.echo

import android.media.MediaMetadataRetriever
import java.io.File

actual class MetadataReader {
    actual fun getMetadata(file: File): MediaMetadata {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        return MediaMetadata(
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Unknown Title",
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        )
    }
}
