package com.example.echo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer as VlcjMediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Canvas
import java.io.File

@Composable
fun MediaPlayer(
    file: File?,
    streamUrl: String? = null,
    isDolbyAtmosEnabled: Boolean = false,
    isGaplessEnabled: Boolean = false,
    speed: Float = 1.0f,
    pitchWithSpeed: Boolean = false,
    bassBoost: Int = 0,
    modifier: Modifier = Modifier,
    onReady: (mediaPlayer: VlcjMediaPlayer, title: String, artist: String, duration: Long, artwork: ByteArray?, quality: String, techDetails: String) -> Unit,
    onPositionChanged: (Long) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onFinished: () -> Unit
) {
    val isVideo = file?.extension?.lowercase() in listOf("mp4", "avi", "mov", "mkv")
    
    val mediaPlayerFactory = remember { 
        NativeDiscovery().discover()
        try {
            val args = mutableListOf(
                "--no-audio-time-stretch", 
                "--audio-replay-gain-mode=none", 
                "--no-volume-save", 
                "--audio-filter=none", 
                "--audio-resampler=none",
                "--input-repeat=0",
                "--no-video-title-show",
                "--network-caching=3000" // Buffer for streaming
            )
            if (isDolbyAtmosEnabled) {
                args.add("--wasapi-exclusive")
                args.add("--wasapi-passthrough=1")
                args.add("--spdif")
            }
            MediaPlayerFactory(*args.toTypedArray())
        } catch (e: Exception) {
            MediaPlayerFactory()
        }
    }
    
    val mediaPlayer = remember(mediaPlayerFactory) {
        if (isVideo) mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()
        else mediaPlayerFactory.mediaPlayers().newMediaPlayer()
    }

    LaunchedEffect(speed) { mediaPlayer.controls().setRate(speed) }

    LaunchedEffect(bassBoost) {
        try {
            val equalizer = mediaPlayerFactory.equalizer().newEqualizer()
            val boostAmount = bassBoost.toFloat() * 2.5f 
            equalizer.setAmp(0, boostAmount)
            equalizer.setAmp(1, boostAmount * 0.8f)
            equalizer.setAmp(2, boostAmount * 0.4f)
            mediaPlayer.audio().setEqualizer(equalizer)
        } catch (e: Exception) {}
    }

    DisposableEffect(mediaPlayer, mediaPlayerFactory) {
        onDispose {
            mediaPlayer.release()
            mediaPlayerFactory.release()
        }
    }

    if (isVideo) {
        val canvas = remember { Canvas() }
        (mediaPlayer as EmbeddedMediaPlayer).videoSurface().set(mediaPlayerFactory.videoSurfaces().newVideoSurface(canvas))
        SwingPanel(factory = { canvas }, modifier = modifier)
    }

    DisposableEffect(file, streamUrl, mediaPlayer) {
        val listener = object : MediaPlayerEventAdapter() {
            override fun mediaPlayerReady(mp: VlcjMediaPlayer?) {
                mp?.let {
                    val duration = it.status().length()
                    var title = file?.nameWithoutExtension ?: "Streaming Track"
                    var artist = "Unknown"
                    var artwork: ByteArray? = null
                    var quality = "Standard Quality"
                    var techDetails = "Stream"

                    if (file != null) {
                        try {
                            val audioFile = AudioFileIO.read(file)
                            audioFile.tag?.let { tag ->
                                title = tag.getFirst(FieldKey.TITLE).ifEmpty { file.nameWithoutExtension }
                                artist = tag.getFirst(FieldKey.ARTIST).ifEmpty { "Unknown" }
                                artwork = tag.firstArtwork?.binaryData
                            }
                            audioFile.audioHeader?.let { header ->
                                val bitsPerSample = header.bitsPerSample
                                val sampleRate = header.sampleRateAsNumber
                                quality = if (bitsPerSample >= 16) "Lossless" else "Standard"
                                techDetails = "${header.format} · ${bitsPerSample}Bit"
                            }
                        } catch (e: Exception) {}
                    }
                    onReady(it, title, artist, duration, artwork, quality, techDetails)
                }
            }
            override fun positionChanged(mp: VlcjMediaPlayer?, newPosition: Float) {
                mp?.let {
                    val duration = it.status().length()
                    if (duration > 0) onPositionChanged((newPosition * duration).toLong())
                }
            }
            override fun playing(mp: VlcjMediaPlayer?) { onIsPlayingChanged(true) }
            override fun paused(mp: VlcjMediaPlayer?) { onIsPlayingChanged(false) }
            override fun stopped(mp: VlcjMediaPlayer?) { onIsPlayingChanged(false) }
            override fun finished(mp: VlcjMediaPlayer?) { onIsPlayingChanged(false); onFinished() }
        }
        mediaPlayer.events().addMediaPlayerEventListener(listener)
        
        if (file != null) mediaPlayer.media().play(file.absolutePath)
        else if (streamUrl != null) mediaPlayer.media().play(streamUrl)

        onDispose {
            mediaPlayer.controls().stop()
            mediaPlayer.events().removeMediaPlayerEventListener(listener)
        }
    }
}
