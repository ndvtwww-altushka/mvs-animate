package com.mvs.animate.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.mvs.animate.model.AudioAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Decodes audio locally. No audio samples are uploaded to OpenCode or any model. */
object AudioAnalyzer {
    suspend fun analyze(context: Context, uri: Uri): AudioAnalysis = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) { audioTrack = i; format = candidate; break }
            }
            require(audioTrack >= 0 && format != null) { "No decodable audio track found" }
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio MIME type")
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            extractor.selectTrack(audioTrack)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val energy = ArrayList<Float>()
            var windowSquares = 0.0
            var windowSamples = 0
            var totalSamples = 0L
            val samplesPerWindow = max(1, sampleRate / 10)
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: error("Decoder input buffer unavailable")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = decoder.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            output.order(ByteOrder.LITTLE_ENDIAN)
                            while (output.remaining() >= 2) {
                                val sample = output.short.toInt() / 32768.0
                                windowSquares += sample * sample
                                windowSamples++
                                totalSamples++
                                if (windowSamples >= samplesPerWindow * channels) {
                                    energy += sqrt(windowSquares / windowSamples).toFloat()
                                    windowSquares = 0.0
                                    windowSamples = 0
                                    if (energy.size >= 60 * 60 * 10) break
                                }
                            }
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
                if (energy.size >= 60 * 60 * 10) break // 60 minutes hard safety cap
            }
            if (windowSamples > 0) energy += sqrt(windowSquares / windowSamples).toFloat()
            val durationMs = (totalSamples / channels * 1000L / sampleRate).coerceAtLeast(1L)
            analyzeEnvelope(energy, durationMs)
        } finally {
            try { decoder?.stop() } catch (_: Exception) { }
            try { decoder?.release() } catch (_: Exception) { }
            extractor.release()
        }
    }

    fun analyzeEnvelope(energy: List<Float>, durationMs: Long): AudioAnalysis {
        if (energy.isEmpty()) return AudioAnalysis(durationMs, null, 0f, 0f, 0, durationMs, "No decoded samples")
        val average = energy.average().toFloat()
        val maxValue = energy.maxOrNull() ?: 0f
        val minValue = energy.minOrNull() ?: 0f
        val dynamic = (maxValue - minValue).coerceAtLeast(0f)
        val onset = energy.indices.map { i -> if (i == 0) 0f else (energy[i] - energy[i - 1]).coerceAtLeast(0f) }
        val bpm = estimateBpm(onset)
        val preferredLength = minOf(12_000L, durationMs).coerceAtLeast(1_000L)
        val windowCount = (preferredLength / 100L).toInt().coerceAtLeast(1)
        var bestStart = 0
        var bestScore = Float.NEGATIVE_INFINITY
        val stride = max(1, windowCount / 4)
        var i = 0
        while (i + windowCount <= energy.size) {
            val score = energy.subList(i, i + windowCount).average().toFloat() * 0.55f +
                onset.subList(i, i + windowCount).average().toFloat() * 0.45f
            if (score > bestScore) { bestScore = score; bestStart = i }
            i += stride
        }
        val startMs = (bestStart * 100L).coerceIn(0L, durationMs)
        val endMs = (startMs + preferredLength).coerceAtMost(durationMs)
        val beatMarkers = if (bpm != null) {
            val interval = 60_000.0 / bpm
            val first = ((startMs / interval).toLong() * interval).toLong()
            generateSequence(first) { it + interval.toLong() }.takeWhile { it <= endMs }.toList()
        } else emptyList()
        val dynamicLabel = when {
            dynamic < 0.035f -> "compressed / even dynamics"
            dynamic > 0.28f -> "strong dynamic contrast"
            else -> "moderate dynamics"
        }
        val energyLabel = when {
            average < 0.035f -> "quiet"
            average < 0.13f -> "medium energy"
            else -> "high energy"
        }
        val description = buildString {
            append(energyLabel); append(", "); append(dynamicLabel)
            append("; 100 ms RMS envelope")
            if (bpm != null) append("; estimated tempo $bpm BPM") else append("; tempo uncertain")
        }
        return AudioAnalysis(durationMs, bpm, average, dynamic, startMs, endMs, description, beatMarkers)
    }

    private fun estimateBpm(onset: List<Float>): Int? {
        if (onset.size < 40) return null
        val mean = onset.average().toFloat()
        val centered = onset.map { it - mean }
        var bestLag = 0
        var best = 0.0
        // Envelope sampled at 10 Hz; 3..10 samples correspond to 180..60 BPM.
        for (lag in 3..10) {
            var correlation = 0.0
            var count = 0
            for (i in lag until centered.size) {
                correlation += centered[i].toDouble() * centered[i - lag].toDouble()
                count++
            }
            if (count > 0) correlation /= count
            if (correlation > best) { best = correlation; bestLag = lag }
        }
        if (bestLag == 0 || best <= 0.000001) return null
        var bpm = (600.0 / bestLag).toInt()
        while (bpm < 70) bpm *= 2
        while (bpm > 180) bpm /= 2
        return bpm.takeIf { it in 60..190 }
    }
}
