package com.hjhsys.naiblockprompt.data.network.nai

/** Decodes NovelAI's length-prefixed MessagePack image stream without retaining intermediates. */
internal object NaiMsgpackImageStream {
    fun decode(
        bytes: ByteArray,
        fallbackSeed: Long?,
        captureDiagnostics: Boolean = false,
    ): NaiApiResult<GeneratedImagePayload> = runCatching {
        var offset = 0
        var finalImage: ByteArray? = null
        var selectedFrameIndex: Int? = null
        var errorMessage: String? = null
        val diagnostics = if (captureDiagnostics) mutableListOf<NaiImageStreamFrame>() else null
        var frameIndex = 0
        while (offset < bytes.size) {
            require(bytes.size - offset >= 4) { "truncated image stream frame header" }
            val length = ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)
            offset += 4
            require(length >= 0 && length <= bytes.size - offset) { "invalid image stream frame length" }
            val event = Reader(bytes, offset, offset + length).readValue() as? Map<*, *>
                ?: error("image stream frame is not a map")
            val eventType = event["event_type"] as? String
            val image = event["image"] as? ByteArray
            diagnostics?.add(
                NaiImageStreamFrame(
                    index = frameIndex,
                    eventType = eventType,
                    stepIndex = event.longValue("step_ix"),
                    sampleIndex = event.longValue("samp_ix"),
                    generationId = event.longValue("gen_id"),
                    keys = event.keys.filterIsInstance<String>().sorted(),
                    image = image,
                ),
            )
            when (eventType) {
                "final" -> {
                    finalImage = image ?: error("final image stream frame has no image")
                    selectedFrameIndex = frameIndex
                }
                "error" -> errorMessage = event["message"] as? String ?: "image stream error"
            }
            offset += length
            frameIndex++
        }
        finalImage?.let {
            NaiApiResult.Success(
                GeneratedImagePayload(
                    bytes = it,
                    seed = fallbackSeed,
                    streamDiagnostics = diagnostics?.let { frames ->
                        NaiImageStreamDiagnostics(bytes, frames, selectedFrameIndex)
                    },
                ),
            )
        }
            ?: NaiApiResult.Failure(NaiApiFailure.InvalidResponse(errorMessage ?: "final image frame is missing"))
    }.getOrElse { NaiApiResult.Failure(NaiApiFailure.InvalidResponse(it.message)) }

    private fun Map<*, *>.longValue(key: String): Long? = when (val value = this[key]) {
        is Number -> value.toLong()
        else -> null
    }

    private class Reader(
        private val bytes: ByteArray,
        start: Int,
        private val end: Int,
    ) {
        private var position = start

        fun readValue(): Any? {
            val marker = readU8()
            return when {
                marker <= 0x7f -> marker.toLong()
                marker in 0x80..0x8f -> readMap(marker and 0x0f)
                marker in 0x90..0x9f -> readArray(marker and 0x0f)
                marker in 0xa0..0xbf -> readString(marker and 0x1f)
                marker >= 0xe0 -> (marker - 256).toLong()
                else -> when (marker) {
                    0xc0 -> null
                    0xc2 -> false
                    0xc3 -> true
                    0xc4 -> readBinary(readU8())
                    0xc5 -> readBinary(readU16())
                    0xc6 -> readBinary(readLength32())
                    0xc7 -> readExtension(readU8())
                    0xc8 -> readExtension(readU16())
                    0xc9 -> readExtension(readLength32())
                    0xca -> Float.fromBits(readI32())
                    0xcb -> Double.fromBits(readI64())
                    0xcc -> readU8().toLong()
                    0xcd -> readU16().toLong()
                    0xce -> readI32().toLong() and 0xffffffffL
                    0xcf -> readI64()
                    0xd0 -> readI8().toLong()
                    0xd1 -> readI16().toLong()
                    0xd2 -> readI32().toLong()
                    0xd3 -> readI64()
                    0xd4 -> readExtension(1)
                    0xd5 -> readExtension(2)
                    0xd6 -> readExtension(4)
                    0xd7 -> readExtension(8)
                    0xd8 -> readExtension(16)
                    0xd9 -> readString(readU8())
                    0xda -> readString(readU16())
                    0xdb -> readString(readLength32())
                    0xdc -> readArray(readU16())
                    0xdd -> readArray(readLength32())
                    0xde -> readMap(readU16())
                    0xdf -> readMap(readLength32())
                    else -> error("unsupported MessagePack marker 0x${marker.toString(16)}")
                }
            }
        }

        private fun readMap(size: Int): Map<Any?, Any?> = buildMap(size) {
            repeat(size) { put(readValue(), readValue()) }
        }

        private fun readArray(size: Int): List<Any?> = List(size) { readValue() }
        private fun readString(size: Int): String = readBytes(size).toString(Charsets.UTF_8)
        private fun readBinary(size: Int): ByteArray = readBytes(size)

        private fun readExtension(size: Int): ByteArray {
            readU8()
            return readBytes(size)
        }

        private fun readLength32(): Int {
            val value = readI32().toLong() and 0xffffffffL
            require(value <= Int.MAX_VALUE) { "MessagePack value is too large" }
            return value.toInt()
        }

        private fun readBytes(size: Int): ByteArray {
            require(size >= 0 && position + size <= end) { "truncated MessagePack value" }
            return bytes.copyOfRange(position, position + size).also { position += size }
        }

        private fun readU8(): Int {
            require(position < end) { "truncated MessagePack value" }
            return bytes[position++].toInt() and 0xff
        }

        private fun readI8(): Byte = readU8().toByte()
        private fun readU16(): Int = (readU8() shl 8) or readU8()
        private fun readI16(): Short = readU16().toShort()
        private fun readI32(): Int = (readU8() shl 24) or (readU8() shl 16) or (readU8() shl 8) or readU8()
        private fun readI64(): Long = (readI32().toLong() shl 32) or (readI32().toLong() and 0xffffffffL)
    }
}
