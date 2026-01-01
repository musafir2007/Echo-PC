package com.example.echo

import java.io.File

expect class MetadataReader {
    fun getMetadata(file: File): MediaMetadata
}

data class MediaMetadata(
    val title: String,
    val artist: String
)
