package com.example.echo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

enum class ExtensionCategory {
    Music, Tracker, Lyrics, Misc
}

abstract class Extension(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val category: ExtensionCategory,
    val version: String = "v1.0.0",
    val type: String = "BuiltIn",
    initialEnabled: Boolean = false
) {
    var isEnabled by mutableStateOf(initialEnabled)

    /**
     * Search for tracks provided by this extension.
     * Returns a list of Track objects with either a file or a stream URL.
     */
    open suspend fun search(query: String): List<Track> = emptyList()

    /**
     * Get featured or trending tracks.
     */
    open suspend fun getDiscovery(): List<Track> = emptyList()
}

class OfflineExtension : Extension(
    "Offline",
    "Play locally stored music files.",
    Icons.Default.Save,
    ExtensionCategory.Music,
    version = "v1.0.0",
    type = "BuiltIn",
    initialEnabled = true
) {
    var isScanning by mutableStateOf(false)
    var scanProgress by mutableStateOf(0f)
    val musicFolders = mutableStateListOf<String>()
}
