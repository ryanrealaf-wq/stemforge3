package com.example.audiomidi.api.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Audio playback controller for original input audio tracks.
 */
class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    @Volatile
    var isPlaying: Boolean = false
        private set

    var volume: Float = 0.8f
        set(value) {
            field = value.coerceIn(0f, 1f)
            mediaPlayer?.setVolume(field, field)
        }

    fun loadUri(uri: Uri, onPrepared: ((Long) -> Unit)? = null) {
        release()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                setOnPreparedListener { mp ->
                    setVolume(volume, volume)
                    onPrepared?.invoke(mp.duration.toLong())
                }
                prepareAsync()
            }
        } catch (_: Exception) {}
    }

    fun loadFile(file: File, onPrepared: ((Long) -> Unit)? = null) {
        loadUri(Uri.fromFile(file), onPrepared)
    }

    fun play(
        onProgress: ((Long) -> Unit)? = null,
        onCompletion: (() -> Unit)? = null
    ) {
        val player = mediaPlayer ?: return
        if (player.isPlaying) return

        player.setOnCompletionListener {
            isPlaying = false
            progressJob?.cancel()
            onCompletion?.invoke()
        }

        player.start()
        isPlaying = true

        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && isPlaying) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        onProgress?.invoke(mp.currentPosition.toLong())
                    }
                }
                delay(40)
            }
        }
    }

    fun pause() {
        isPlaying = false
        progressJob?.cancel()
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
        } catch (_: Exception) {}
    }

    fun getDuration(): Long {
        return try {
            mediaPlayer?.duration?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun getCurrentPosition(): Long {
        return try {
            mediaPlayer?.currentPosition?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun release() {
        isPlaying = false
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }
}
