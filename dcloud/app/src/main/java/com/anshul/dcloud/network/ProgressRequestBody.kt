package com.anshul.dcloud.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

class ProgressRequestBody(
    private val contentType: MediaType?,
    private val contentBytes: ByteArray,
    private val onProgressUpdate: (percentage: Int, bytesUploaded: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = contentBytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedSink = countingSink.buffer()
        bufferedSink.write(contentBytes)
        bufferedSink.flush()
    }

    private inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        private var bytesWritten = 0L

        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            val total = contentLength()
            val percentage = if (total > 0) ((bytesWritten * 100) / total).toInt() else 0
            onProgressUpdate(percentage, bytesWritten, total)
        }
    }
}
