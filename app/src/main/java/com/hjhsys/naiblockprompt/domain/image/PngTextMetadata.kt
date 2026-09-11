package com.hjhsys.naiblockprompt.domain.image

import java.io.ByteArrayOutputStream

/** Retains NovelAI Source/Comment chunks after pixel compositing re-encodes a PNG. */
object PngTextMetadata {
    private val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private val textTypes = setOf("tEXt", "zTXt", "iTXt")

    fun transfer(sourcePng: ByteArray, targetPng: ByteArray): ByteArray {
        val metadata = chunks(sourcePng).filter { it.type in textTypes }.map { it.bytes }
        if (metadata.isEmpty()) return targetPng
        val output = ByteArrayOutputStream(targetPng.size + metadata.sumOf { it.size })
        output.write(signature)
        chunks(targetPng).forEach { chunk ->
            if (chunk.type == "IEND") metadata.forEach { output.write(it) }
            if (chunk.type !in textTypes) output.write(chunk.bytes)
        }
        return output.toByteArray()
    }

    private fun chunks(png: ByteArray): List<Chunk> {
        require(png.size >= signature.size && png.copyOfRange(0, signature.size).contentEquals(signature))
        val chunks = mutableListOf<Chunk>()
        var offset = signature.size
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            require(length >= 0 && offset.toLong() + length + 12 <= png.size)
            val end = offset + length + 12
            val type = png.copyOfRange(offset + 4, offset + 8).toString(Charsets.US_ASCII)
            chunks += Chunk(type, png.copyOfRange(offset, end))
            offset = end
            if (type == "IEND") break
        }
        return chunks
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private data class Chunk(val type: String, val bytes: ByteArray)
}
