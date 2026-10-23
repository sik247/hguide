package com.hguide.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16 kHz mono PCM16 WAV helpers. All app audio uses this format. */
object Wav {
    const val SAMPLE_RATE = 16_000
    const val BYTES_PER_MS = SAMPLE_RATE * 2 / 1000 // 32
    private const val HEADER = 44

    fun msToBytes(ms: Long): Int = (ms * BYTES_PER_MS).toInt().let { it - it % 2 }
    fun bytesToMs(bytes: Int): Long = bytes / BYTES_PER_MS.toLong()

    fun write(file: File, pcm: ByteArray) {
        file.parentFile?.mkdirs()
        val bb = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(pcm.size)
        file.outputStream().use { it.write(bb.array()); it.write(pcm) }
    }

    /** Reads the PCM payload of a WAV written by [write] (or any canonical 44-byte-header PCM16 file). */
    fun readPcm(file: File): ByteArray = readPcmBytes(file.readBytes())

    fun readPcmBytes(all: ByteArray): ByteArray {
        require(all.size >= HEADER) { "not a wav" }
        // Walk chunks so extra header chunks (LIST etc.) are skipped.
        var pos = 12
        while (pos + 8 <= all.size) {
            val id = String(all, pos, 4)
            val size = ByteBuffer.wrap(all, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (id == "data") {
                val end = minOf(all.size, pos + 8 + size)
                return all.copyOfRange(pos + 8, end)
            }
            pos += 8 + size + (size and 1)
        }
        error("no data chunk")
    }

    fun silence(ms: Long) = ByteArray(msToBytes(ms))

    fun tone(ms: Long, hz: Double, amp: Double = 0.2): ByteArray {
        val n = msToBytes(ms) / 2
        val out = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            out.putShort((Math.sin(2 * Math.PI * hz * i / SAMPLE_RATE) * amp * Short.MAX_VALUE).toInt().toShort())
        }
        return out.array()
    }

    fun concat(parts: List<ByteArray>): ByteArray {
        val o = ByteArrayOutputStream()
        parts.forEach { o.write(it) }
        return o.toByteArray()
    }
}
