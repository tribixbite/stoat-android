package com.tribixbite.stoatally.api.routes.microservices.autumn

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Self-contained animated GIF89a encoder. Accepts Bitmap frames with per-frame
 * delay and encodes as a looping animated GIF. Includes median-cut color
 * quantization (256 colors max) and standard GIF LZW compression.
 *
 * Usage:
 * ```
 * val out = FileOutputStream("output.gif")
 * val encoder = GifEncoder(out, width, height)
 * encoder.addFrame(bitmap1, delayMs = 100)
 * encoder.addFrame(bitmap2, delayMs = 100)
 * encoder.finish()
 * ```
 */
class GifEncoder(
    private val output: OutputStream,
    private val width: Int,
    private val height: Int,
    private val repeat: Int = 0 // 0 = infinite loop, -1 = no loop
) {
    private var started = false
    private var frameCount = 0

    /**
     * Add a frame to the animated GIF. The bitmap is scaled to match the
     * encoder's width/height if needed. Transparency is supported.
     *
     * @param bitmap Source frame (ARGB_8888)
     * @param delayMs Frame delay in milliseconds (min 20ms for GIF spec)
     * @param dispose Disposal method: 0=none, 1=keep, 2=restore background, 3=restore previous
     */
    fun addFrame(bitmap: Bitmap, delayMs: Int = 100, dispose: Int = 2) {
        // Scale bitmap to encoder dimensions if needed
        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }

        // Extract ARGB pixels
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()

        // Quantize to 256 colors
        val (palette, indexed, transparentIndex) = quantize(pixels)

        if (!started) {
            writeHeader()
            writeLogicalScreenDescriptor(palette.size / 3)
            writeGlobalColorTable(palette)
            if (repeat >= 0) writeNetscapeExtension()
            started = true
        }

        writeGraphicControlExtension(delayMs, dispose, transparentIndex)
        writeImageDescriptor()
        writeLzwImageData(indexed, palette.size / 3)
        frameCount++
    }

    /** Finalize the GIF file. Must be called after all frames are added. */
    fun finish() {
        if (started) {
            output.write(0x3B) // GIF trailer
        }
        output.flush()
    }

    // -- Header & Descriptors --

    private fun writeHeader() {
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
    }

    private fun writeLogicalScreenDescriptor(colorCount: Int) {
        writeShort(width)
        writeShort(height)
        // Packed field: GCT flag=1, color resolution=7 (8 bits), sort=0, GCT size
        val gctSizeBits = colorTableSizeBits(colorCount)
        val packed = 0x80 or // GCT flag
            (7 shl 4) or    // Color resolution (8 bits per channel)
            gctSizeBits      // GCT size
        output.write(packed)
        output.write(0) // Background color index
        output.write(0) // Pixel aspect ratio
    }

    private fun writeGlobalColorTable(palette: ByteArray) {
        // Pad to next power-of-two size (GIF requirement)
        val colorCount = palette.size / 3
        val paddedCount = nextPowerOfTwo(colorCount)
        output.write(palette)
        // Pad remaining entries with black
        repeat((paddedCount - colorCount) * 3) {
            output.write(0)
        }
    }

    private fun writeNetscapeExtension() {
        output.write(0x21) // Extension introducer
        output.write(0xFF) // Application extension
        output.write(11)   // Block size
        output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        output.write(3)    // Sub-block size
        output.write(1)    // Sub-block ID
        writeShort(repeat) // Loop count (0 = infinite)
        output.write(0)    // Block terminator
    }

    private fun writeGraphicControlExtension(delayMs: Int, dispose: Int, transparentIndex: Int) {
        output.write(0x21) // Extension introducer
        output.write(0xF9) // GCE label
        output.write(4)    // Block size
        // Packed: disposal method, user input=0, transparent color flag
        val hasTransparency = transparentIndex >= 0
        val packed = ((dispose and 0x7) shl 2) or (if (hasTransparency) 1 else 0)
        output.write(packed)
        writeShort(delayMs / 10) // Delay in centiseconds
        output.write(if (hasTransparency) transparentIndex else 0) // Transparent color index
        output.write(0) // Block terminator
    }

    private fun writeImageDescriptor() {
        output.write(0x2C) // Image separator
        writeShort(0)      // Left
        writeShort(0)      // Top
        writeShort(width)  // Width
        writeShort(height) // Height
        output.write(0)    // Packed: no LCT, not interlaced
    }

    // -- LZW Image Data --

    private fun writeLzwImageData(indexed: ByteArray, colorCount: Int) {
        val minCodeSize = when {
            colorCount <= 2 -> 2 // GIF minimum
            else -> colorTableSizeBits(colorCount) + 1
        }
        output.write(minCodeSize)

        val compressed = lzwCompress(indexed, minCodeSize)

        // Write in sub-blocks of max 255 bytes
        var offset = 0
        while (offset < compressed.size) {
            val blockSize = min(255, compressed.size - offset)
            output.write(blockSize)
            output.write(compressed, offset, blockSize)
            offset += blockSize
        }
        output.write(0) // Block terminator
    }

    /**
     * GIF-variant LZW compression. Uses variable-length codes with clear
     * and EOI markers.
     */
    private fun lzwCompress(data: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var nextCode = eoiCode + 1

        // String table: maps byte sequences to codes
        // Key = hashable representation of byte sequence
        val table = HashMap<Long, Int>(4096)

        val bitStream = BitOutputStream()
        bitStream.write(clearCode, codeSize)

        // Initialize table with single-byte entries
        fun resetTable() {
            table.clear()
            for (i in 0 until clearCode) {
                table[i.toLong()] = i
            }
            codeSize = minCodeSize + 1
            nextCode = eoiCode + 1
        }
        resetTable()

        if (data.isEmpty()) {
            bitStream.write(eoiCode, codeSize)
            return bitStream.toByteArray()
        }

        var current = data[0].toInt() and 0xFF

        for (i in 1 until data.size) {
            val next = data[i].toInt() and 0xFF
            // Combine current string + next byte into a lookup key
            val key = (current.toLong() shl 20) or next.toLong()

            val existing = table[key]
            if (existing != null) {
                current = existing
            } else {
                // Output code for current string
                bitStream.write(current, codeSize)

                if (nextCode < 4096) {
                    table[key] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    // Table full — emit clear code and reset
                    bitStream.write(clearCode, codeSize)
                    resetTable()
                }

                current = next
            }
        }

        // Output final code
        bitStream.write(current, codeSize)
        bitStream.write(eoiCode, codeSize)

        return bitStream.toByteArray()
    }

    // -- Color Quantization --

    /**
     * Quantize ARGB pixels to at most 256 colors using median-cut algorithm.
     * Returns (palette bytes [R,G,B,...], indexed pixels, transparent index or -1).
     */
    private fun quantize(pixels: IntArray): QuantizeResult {
        // Separate transparent and opaque pixels
        val opaqueColors = mutableListOf<Int>()
        var hasTransparency = false

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 128) {
                hasTransparency = true
            } else {
                opaqueColors.add(pixel or (0xFF shl 24)) // Force fully opaque
            }
        }

        // If few enough unique colors, use exact palette
        val uniqueColors = opaqueColors.toSet()
        val maxColors = if (hasTransparency) 255 else 256

        val palette: List<Int> = if (uniqueColors.size <= maxColors) {
            uniqueColors.toList()
        } else {
            medianCut(opaqueColors.toIntArray(), maxColors)
        }

        // Build palette bytes and index lookup
        val transparentIndex = if (hasTransparency) palette.size else -1
        val totalColors = palette.size + (if (hasTransparency) 1 else 0)
        val paletteBytes = ByteArray(totalColors * 3)

        val colorToIndex = HashMap<Int, Int>(palette.size * 2)
        for (i in palette.indices) {
            val c = palette[i]
            paletteBytes[i * 3] = Color.red(c).toByte()
            paletteBytes[i * 3 + 1] = Color.green(c).toByte()
            paletteBytes[i * 3 + 2] = Color.blue(c).toByte()
            colorToIndex[c] = i
        }
        // Transparent entry (black, hidden by transparency)
        if (hasTransparency) {
            val ti = transparentIndex * 3
            paletteBytes[ti] = 0
            paletteBytes[ti + 1] = 0
            paletteBytes[ti + 2] = 0
        }

        // Map each pixel to its closest palette index
        val indexed = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val alpha = (pixels[i] ushr 24) and 0xFF
            if (alpha < 128) {
                indexed[i] = transparentIndex.toByte()
            } else {
                val opaque = pixels[i] or (0xFF shl 24)
                val exact = colorToIndex[opaque]
                if (exact != null) {
                    indexed[i] = exact.toByte()
                } else {
                    indexed[i] = findNearest(opaque, palette).toByte()
                }
            }
        }

        return QuantizeResult(paletteBytes, indexed, transparentIndex)
    }

    private data class QuantizeResult(
        val palette: ByteArray,
        val indexed: ByteArray,
        val transparentIndex: Int
    )

    /**
     * Median-cut color quantization. Recursively splits the color cube
     * along the axis with the widest range until we have [maxColors] buckets.
     */
    private fun medianCut(colors: IntArray, maxColors: Int): List<Int> {
        if (colors.isEmpty()) return listOf(Color.BLACK)

        data class Bucket(val pixels: IntArray) {
            fun range(channel: Int): Int {
                var lo = 255; var hi = 0
                for (c in pixels) {
                    val v = when (channel) {
                        0 -> Color.red(c)
                        1 -> Color.green(c)
                        else -> Color.blue(c)
                    }
                    if (v < lo) lo = v
                    if (v > hi) hi = v
                }
                return hi - lo
            }

            fun widestChannel(): Int {
                val rr = range(0); val gr = range(1); val br = range(2)
                return when {
                    rr >= gr && rr >= br -> 0
                    gr >= br -> 1
                    else -> 2
                }
            }

            fun average(): Int {
                var rSum = 0L; var gSum = 0L; var bSum = 0L
                for (c in pixels) {
                    rSum += Color.red(c)
                    gSum += Color.green(c)
                    bSum += Color.blue(c)
                }
                val n = pixels.size.coerceAtLeast(1)
                return Color.rgb(
                    (rSum / n).toInt().coerceIn(0, 255),
                    (gSum / n).toInt().coerceIn(0, 255),
                    (bSum / n).toInt().coerceIn(0, 255)
                )
            }

            /** Split along widest channel at the median */
            fun split(): Pair<Bucket, Bucket> {
                val ch = widestChannel()
                val sorted = pixels.sortedBy { when (ch) {
                    0 -> Color.red(it)
                    1 -> Color.green(it)
                    else -> Color.blue(it)
                } }.toIntArray()
                val mid = sorted.size / 2
                return Bucket(sorted.sliceArray(0 until mid)) to
                    Bucket(sorted.sliceArray(mid until sorted.size))
            }
        }

        val buckets = mutableListOf(Bucket(colors))

        while (buckets.size < maxColors) {
            // Find the bucket with the widest range to split
            val best = buckets.maxByOrNull { b ->
                if (b.pixels.size <= 1) -1 else b.range(b.widestChannel())
            } ?: break
            if (best.pixels.size <= 1) break

            buckets.remove(best)
            val (a, b) = best.split()
            if (a.pixels.isNotEmpty()) buckets.add(a)
            if (b.pixels.isNotEmpty()) buckets.add(b)
        }

        return buckets.map { it.average() }
    }

    /** Find index of nearest color in palette using squared Euclidean distance */
    private fun findNearest(color: Int, palette: List<Int>): Int {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val pr = Color.red(palette[i])
            val pg = Color.green(palette[i])
            val pb = Color.blue(palette[i])
            val dist = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    // -- Utilities --

    private fun writeShort(value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }

    private fun colorTableSizeBits(colorCount: Int): Int {
        var bits = 0
        var n = nextPowerOfTwo(colorCount)
        while (n > 2) { n = n shr 1; bits++ }
        return bits.coerceIn(0, 7)
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var v = n.coerceAtLeast(2)
        v--
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return v + 1
    }

    /**
     * Bit-level output stream for LZW. Packs variable-width codes into bytes
     * in LSB-first order (GIF spec).
     */
    private class BitOutputStream {
        private val buffer = ByteArrayOutputStream()
        private var currentByte = 0
        private var bitPos = 0

        fun write(code: Int, bits: Int) {
            var remaining = bits
            var value = code
            while (remaining > 0) {
                currentByte = currentByte or ((value and 1) shl bitPos)
                bitPos++
                value = value shr 1
                remaining--
                if (bitPos == 8) {
                    buffer.write(currentByte)
                    currentByte = 0
                    bitPos = 0
                }
            }
        }

        fun toByteArray(): ByteArray {
            // Flush remaining bits
            if (bitPos > 0) {
                buffer.write(currentByte)
            }
            return buffer.toByteArray()
        }
    }
}
