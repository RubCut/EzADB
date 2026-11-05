package dev.ezadb.data

import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.ForwardingSink
import okio.ForwardingSource
import okio.Sink
import okio.Source
import okio.buffer

/** Live byte counter for a running transfer. */
class TransferHandle(
    val onProgress: (Long) -> Unit = {},
    val ensureActive: () -> Unit = {},
) {
    @Volatile var transferred: Long = 0
        private set

    private var lastReport = 0L
    private var lastTime = 0L

    fun advance(bytes: Long) {
        ensureActive()
        transferred += bytes
        val now = System.currentTimeMillis()
        if (transferred - lastReport >= 64 * 1024 || now - lastTime >= 120) {
            lastReport = transferred
            lastTime = now
            onProgress(transferred)
        }
    }

    fun finish() {
        onProgress(transferred)
    }
}

fun Source.counted(handle: TransferHandle?): Source {
    if (handle == null) return this
    return object : ForwardingSource(this) {
        override fun read(sink: Buffer, byteCount: Long): Long {
            handle.ensureActive()
            val read = super.read(sink, byteCount)
            if (read > 0) handle.advance(read)
            return read
        }
    }
}

fun Sink.countedSink(handle: TransferHandle?): Sink {
    if (handle == null) return this
    return object : ForwardingSink(this) {
        override fun write(source: Buffer, byteCount: Long) {
            handle.ensureActive()
            super.write(source, byteCount)
            handle.advance(byteCount)
        }
    }
}

/** Buffers a counted sink; flush is forwarded so progress stays accurate. */
fun Sink.countedBuffered(handle: TransferHandle?) = countedSink(handle).buffer()
