package com.selxo.rougo

import android.media.MediaRecorder
import android.os.Build
import android.util.Size
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import java.io.File

internal class ShadowAudioRecorder(private val noiseCancellationEnabled: Boolean) {
    private var audioRecord: android.media.AudioRecord? = null
    private var encoder: android.media.MediaCodec? = null
    private var muxer: android.media.MediaMuxer? = null
    private var recordingThread: Thread? = null
    private var outputFile: File? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var wroteSamples = false

    private var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null
    private var echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
    private var gainControl: android.media.audiofx.AutomaticGainControl? = null

    @Volatile
    private var shouldRecord = false

    @Volatile
    private var threadFailed = false

    fun start(file: File): Boolean {
        release()
        val sources = buildList {
            if (noiseCancellationEnabled) {
                add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                add(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                add(MediaRecorder.AudioSource.MIC)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) add(MediaRecorder.AudioSource.UNPROCESSED)
                add(MediaRecorder.AudioSource.MIC)
                add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            }
        }.distinct()

        for (source in sources) {
            try { file.delete() } catch (e: Exception) {}
            if (startWithSource(file, source)) return true
            release()
        }

        try { file.delete() } catch (e: Exception) {}
        return false
    }

    fun stop(): Boolean {
        shouldRecord = false
        try { audioRecord?.stop() } catch (e: Exception) {}

        val thread = recordingThread
        if (thread != null && thread.isAlive) {
            try {
                thread.join(5000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                threadFailed = true
            }
            if (thread.isAlive) threadFailed = true
        }

        val success = !threadFailed && wroteSamples && (outputFile?.length() ?: 0L) > 0L
        release()
        return success
    }

    fun release() {
        shouldRecord = false
        releaseEffect(gainControl)
        releaseEffect(echoCanceler)
        releaseEffect(noiseSuppressor)
        gainControl = null
        echoCanceler = null
        noiseSuppressor = null

        try { audioRecord?.stop() } catch (e: Exception) {}
        try { audioRecord?.release() } catch (e: Exception) {}
        audioRecord = null

        try { encoder?.stop() } catch (e: Exception) {}
        try { encoder?.release() } catch (e: Exception) {}
        encoder = null

        try {
            if (muxerStarted && wroteSamples) muxer?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try { muxer?.release() } catch (e: Exception) {}
        muxer = null

        recordingThread = null
        outputFile = null
        trackIndex = -1
        muxerStarted = false
        wroteSamples = false
    }

    private fun startWithSource(file: File, source: Int): Boolean {
        val minBuffer = android.media.AudioRecord.getMinBufferSize(
            RECORD_SAMPLE_RATE,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false

        val pcmBufferSize = maxOf(minBuffer * 2, RECORD_SAMPLE_RATE / 5 * RECORD_BYTES_PER_FRAME)
        return try {
            val recorder = createAudioRecord(source, pcmBufferSize)
            if (recorder.state != android.media.AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return false
            }

            audioRecord = recorder
            if (noiseCancellationEnabled) attachVoiceEffects(recorder.audioSessionId)

            val format = android.media.MediaFormat.createAudioFormat(
                android.media.MediaFormat.MIMETYPE_AUDIO_AAC,
                RECORD_SAMPLE_RATE,
                RECORD_CHANNEL_COUNT
            ).apply {
                setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(android.media.MediaFormat.KEY_BIT_RATE, RECORD_BIT_RATE)
                setInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE, pcmBufferSize)
            }

            val nextEncoder = android.media.MediaCodec.createEncoderByType(android.media.MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder = nextEncoder
            nextEncoder.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
            val nextMuxer = android.media.MediaMuxer(file.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = nextMuxer

            outputFile = file
            trackIndex = -1
            muxerStarted = false
            wroteSamples = false
            threadFailed = false
            shouldRecord = true

            nextEncoder.start()
            recorder.startRecording()
            recordingThread = Thread({ encodeLoop() }, "RougoShadowAudioRecorder").also { it.start() }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Suppress("MissingPermission")
    private fun createAudioRecord(source: Int, bufferSize: Int): android.media.AudioRecord {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.media.AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setSampleRate(RECORD_SAMPLE_RATE)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.media.AudioRecord(
                source,
                RECORD_SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }
    }

    private fun attachVoiceEffects(audioSessionId: Int) {
        noiseSuppressor = try {
            if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                android.media.audiofx.NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        echoCanceler = try {
            if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                android.media.audiofx.AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        gainControl = try {
            if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                android.media.audiofx.AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun releaseEffect(effect: android.media.audiofx.AudioEffect?) {
        try { effect?.release() } catch (e: Exception) {}
    }

    private fun encodeLoop() {
        val recorder = audioRecord ?: return
        val nextEncoder = encoder ?: return
        val nextMuxer = muxer ?: return
        val info = android.media.MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var submittedFrames = 0L

        try {
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = nextEncoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = nextEncoder.getInputBuffer(inputIndex)
                        if (inputBuffer == null) {
                            nextEncoder.queueInputBuffer(inputIndex, 0, 0, 0L, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else if (shouldRecord) {
                            inputBuffer.clear()
                            val bytesRead = recorder.read(inputBuffer, inputBuffer.capacity(), android.media.AudioRecord.READ_BLOCKING)
                            if (bytesRead > 0) {
                                val presentationTimeUs = submittedFrames * 1_000_000L / RECORD_SAMPLE_RATE
                                nextEncoder.queueInputBuffer(inputIndex, 0, bytesRead, presentationTimeUs, 0)
                                submittedFrames += bytesRead / RECORD_BYTES_PER_FRAME
                            } else if (!shouldRecord) {
                                nextEncoder.queueInputBuffer(inputIndex, 0, 0, submittedFrames * 1_000_000L / RECORD_SAMPLE_RATE, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                threadFailed = true
                                nextEncoder.queueInputBuffer(inputIndex, 0, 0, submittedFrames * 1_000_000L / RECORD_SAMPLE_RATE, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            }
                        } else {
                            nextEncoder.queueInputBuffer(inputIndex, 0, 0, submittedFrames * 1_000_000L / RECORD_SAMPLE_RATE, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        }
                    }
                }

                var outputIndex = nextEncoder.dequeueOutputBuffer(info, 10000)
                while (outputIndex != android.media.MediaCodec.INFO_TRY_AGAIN_LATER) {
                    when {
                        outputIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = nextMuxer.addTrack(nextEncoder.outputFormat)
                            nextMuxer.start()
                            muxerStarted = true
                        }
                        outputIndex >= 0 -> {
                            val outputBuffer = nextEncoder.getOutputBuffer(outputIndex)
                            if (outputBuffer != null) {
                                if ((info.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    info.size = 0
                                }
                                if (info.size > 0 && muxerStarted) {
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    nextMuxer.writeSampleData(trackIndex, outputBuffer, info)
                                    wroteSamples = true
                                }
                            }

                            outputEnded = (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            nextEncoder.releaseOutputBuffer(outputIndex, false)
                            if (outputEnded) break
                        }
                    }
                    outputIndex = nextEncoder.dequeueOutputBuffer(info, 0)
                }
            }
        } catch (e: Exception) {
            threadFailed = true
            e.printStackTrace()
        }
    }

    private companion object {
        const val RECORD_SAMPLE_RATE = 48000
        const val RECORD_CHANNEL_COUNT = 1
        const val RECORD_BIT_RATE = 160000
        const val RECORD_BYTES_PER_FRAME = 2 * RECORD_CHANNEL_COUNT
    }
}
