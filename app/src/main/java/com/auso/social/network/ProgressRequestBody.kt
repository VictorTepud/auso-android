package com.auso.social.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source

/**
 * A RequestBody that reports upload progress via [onProgress].
 *
 * Wraps an existing [RequestBody] (or raw bytes + media type) and emits the percentage
 * of bytes already written to the sink. Useful for showing a real upload progress bar
 * when uploading videos/images via multipart.
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
        val source = bytes.source().buffer()
        val bufferSize = 8 * 1024 // 8KB chunks
        var totalWritten = 0L
        val total = contentLength()

        while (true) {
            val read = source.read(sink.buffer, bufferSize.toLong())
            if (read == -1L) break
            sink.flush()
            totalWritten += read
            if (total > 0) {
                onProgress((totalWritten.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
        }
        // Ensure we always emit 1f at the end
        onProgress(1f)
    }
}
