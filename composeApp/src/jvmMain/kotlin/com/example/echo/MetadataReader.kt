package com.example.echo

import java.io.File

actual class MetadataReader {
    actual fun getMetadata(file: File): MediaMetadata {
        return MediaMetadata("Unknown Title", "Unknown Artist")
    }
}
