package com.example.echo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Canvas
import java.io.File

@Composable
fun VideoPlayer(file: File, modifier: Modifier = Modifier) {
    val canvas = remember { Canvas() }
    val mediaPlayerFactory = remember { MediaPlayerFactory() }
    val mediaPlayer = remember { mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer() }

    DisposableEffect(Unit) {
        mediaPlayer.videoSurface().set(mediaPlayerFactory.videoSurfaces().newVideoSurface(canvas))
        onDispose {
            mediaPlayer.release()
            mediaPlayerFactory.release()
        }
    }

    SwingPanel(
        factory = { canvas },
        modifier = modifier
    )

    DisposableEffect(file) {
        mediaPlayer.media().play(file.absolutePath)
        onDispose {
            mediaPlayer.controls().stop()
        }
    }
}
