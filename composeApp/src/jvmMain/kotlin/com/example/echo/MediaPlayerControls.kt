package com.example.echo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import uk.co.caprica.vlcj.player.base.MediaPlayer as VlcjMediaPlayer
import java.util.Locale

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
}

@Composable
fun MediaPlayerControls(
    mediaPlayer: VlcjMediaPlayer?,
    isPlaying: Boolean,
    title: String,
    artist: String,
    duration: Long,
    position: Long,
    artwork: ByteArray?,
    codecInfo: String,
    isShuffle: Boolean,
    loopMode: Int,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onLoopToggle: () -> Unit,
    onExpandToggle: () -> Unit,
    onQualityClick: () -> Unit,
    onArtworkClick: () -> Unit,
    onArtistClick: (String) -> Unit, // New callback
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp)
            .widthIn(max = 1000.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2833).copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            artwork?.let {
                val imageBitmap = remember(it) { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onArtworkClick)
                )
            }
            
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(
                    artist, 
                    color = Color.Gray, 
                    maxLines = 1, 
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { onArtistClick(artist) } // Clickable artist name
                )
                
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable(onClick = onQualityClick).padding(top = 4.dp)
                ) {
                    Text(
                        codecInfo, 
                        color = Color.White.copy(alpha = 0.7f), 
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material.Slider(
                        value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { mediaPlayer?.controls()?.setPosition(it) },
                        modifier = Modifier.weight(1f).height(4.dp),
                        colors = androidx.compose.material.SliderDefaults.colors(
                            thumbColor = Color.Transparent, // Removed circle scrubber
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        "${formatDuration(position / 1000)} / ${formatDuration(duration / 1000)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                IconButton(onClick = onShuffleToggle) {
                    Icon(Icons.Default.Shuffle, null, tint = if (isShuffle) Color.Cyan else Color.White)
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White)
                }
                
                IconButton(onClick = {
                    if (isPlaying) mediaPlayer?.controls()?.pause() else mediaPlayer?.controls()?.play()
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, tint = Color.White, modifier = Modifier.size(36.dp)
                    )
                }
                
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White)
                }
                IconButton(onClick = onLoopToggle) {
                    Icon(if (loopMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if (loopMode > 0) Color.Cyan else Color.White)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Volume controls REMOVED
                
                IconButton(onClick = onExpandToggle) {
                    Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                }
            }
        }
    }
}
