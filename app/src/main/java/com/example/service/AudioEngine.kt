package com.example.service

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.abs
import kotlin.math.sqrt

class AudioInputManager(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioInputManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    var isMuted: Boolean = false

    @SuppressLint("MissingPermission")
    fun startRecording(
        onAudioChunk: (ByteArray) -> Unit,
        onAmplitudeChanged: (Float) -> Unit
    ) {
        stopRecording()

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufSize, 3200)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_COMMUNICATION fails
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            audioRecord?.startRecording()

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(1600) // 100ms chunk @ 16kHz
                val byteBuffer = ByteArray(3200)

                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSamples > 0) {
                        // Calculate RMS amplitude
                        var sum = 0.0
                        for (i in 0 until readSamples) {
                            val sample = buffer[i]
                            sum += sample * sample

                            // Convert short to little-endian bytes
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }

                        val rms = sqrt(sum / readSamples)
                        // Normalize 0 to 1 with noise floor threshold
                        val normalized = (rms / 12000.0).toFloat().coerceIn(0f, 1f)

                        launch(Dispatchers.Main) {
                            onAmplitudeChanged(if (isMuted) 0f else normalized)
                        }

                        if (!isMuted) {
                            val chunkToSend = byteBuffer.copyOf(readSamples * 2)
                            onAudioChunk(chunkToSend)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }
}

class AudioOutputManager(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioOutputManager"
        const val SAMPLE_RATE = 24000 // Gemini Multimodal Live uses 24kHz PCM
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufSize, 9600)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    fun startPlayback(onAmplitudeChanged: (Float) -> Unit) {
        if (playbackJob != null) return

        playbackJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val chunk = audioQueue.poll()
                    if (chunk != null && chunk.isNotEmpty()) {
                        if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack?.play()
                        }

                        // Calculate RMS amplitude for Gemini speaking animation
                        val sampleCount = chunk.size / 2
                        var sum = 0.0
                        for (i in 0 until sampleCount) {
                            val low = chunk[i * 2].toInt() and 0xFF
                            val high = chunk[i * 2 + 1].toInt()
                            val sample = ((high shl 8) or low).toShort()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / sampleCount.coerceAtLeast(1))
                        val normalized = (rms / 14000.0).toFloat().coerceIn(0f, 1f)

                        launch(Dispatchers.Main) {
                            onAmplitudeChanged(normalized)
                        }

                        audioTrack?.write(chunk, 0, chunk.size)
                    } else {
                        // Decay amplitude smoothly when idle
                        launch(Dispatchers.Main) {
                            onAmplitudeChanged(0f)
                        }
                        kotlinx.coroutines.delay(15)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "AudioTrack write error", e)
                }
            }
        }
    }

    fun queueAudio(pcmData: ByteArray) {
        audioQueue.offer(pcmData)
    }

    fun interrupt() {
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error interrupting audio", e)
        }
    }

    fun release() {
        playbackJob?.cancel()
        playbackJob = null
        audioQueue.clear()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }
}
