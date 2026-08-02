package com.anshul.dcloud.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

class ProgressRequestBody(
    private val contentType: MediaType?,
    private val contentBytes: ByteArray,
    private val onProgressUpdate: (percentage: Int, bytesUploaded: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = contentBytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val chunkSize = 16 * 1024
        var offset = 0
        var lastPercentage = -1

        while (offset < contentBytes.size) {
            val bytesToWrite = minOf(chunkSize, contentBytes.size - offset)
            sink.write(contentBytes, offset, bytesToWrite)
            sink.flush()
            offset += bytesToWrite

            val percentage = if (total > 0) ((offset.toLong() * 100) / total).toInt() else 0
            if (percentage != lastPercentage) {
                lastPercentage = percentage
                onProgressUpdate(percentage, offset.toLong(), total)
            }
        }
    }
}
