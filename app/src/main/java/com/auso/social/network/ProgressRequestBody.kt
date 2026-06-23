package com.auso.social.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * A RequestBody that reports upload progress via [onProgress].
 *
 * Wraps raw bytes + media type and emits the percentage of bytes already written to the
 * sink. Useful for showing a real upload progress bar when uploading videos/images
 * via multipart.
 *
 * @param onProgress receives a Float in 0f..1f representing (bytesWritten / contentLength)
 */
class ProgressRequestBody(
    private val bytes: ByteArray,
    private val mediaType: MediaType?,
    private val onProgress: (Float) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val bufferSize = 8 * 1024 // 8KB chunks
        val total = contentLength()
        var offset = 0
        var totalWritten = 0L

        while (offset < bytes.size) {
            val length = minOf(bufferSize, bytes.size - offset)
            sink.write(bytes, offset, length)
            sink.flush()
            offset += length
            totalWritten += length.toLong()
            if (total > 0L) {
                onProgress((totalWritten.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
        }
        // Always emit 1f at the end
        onProgress(1f)
    }
}
