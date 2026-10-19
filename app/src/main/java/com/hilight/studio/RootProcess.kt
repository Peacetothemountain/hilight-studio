package com.hilight.studio

import java.util.concurrent.TimeUnit

/** Drain available output while waiting, without a reader that descendants can keep blocked. */
internal object RootProcess {
    private const val MAX_OUTPUT_BYTES = 4_096

    class CommandTimeout(message: String) : IllegalStateException(message)

    data class Result(val code: Int, val output: String)

    fun run(args: List<String>, timeoutSeconds: Long): Result {
        require(timeoutSeconds > 0)
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        val tail = ByteArray(MAX_OUTPUT_BYTES)
        var tailLen = 0
        val stream = process.inputStream
        val buffer = ByteArray(MAX_OUTPUT_BYTES)
        val started = System.nanoTime()
        val timeoutNanos = TimeUnit.SECONDS.toNanos(timeoutSeconds)

        fun appendTail(bytes: ByteArray, count: Int) {
            if (count <= 0) return
            if (count >= MAX_OUTPUT_BYTES) {
                System.arraycopy(bytes, count - MAX_OUTPUT_BYTES, tail, 0, MAX_OUTPUT_BYTES)
                tailLen = MAX_OUTPUT_BYTES
            } else if (tailLen + count <= MAX_OUTPUT_BYTES) {
                System.arraycopy(bytes, 0, tail, tailLen, count)
                tailLen += count
            } else {
                val drop = (tailLen + count) - MAX_OUTPUT_BYTES
                val keepExisting = tailLen - drop
                System.arraycopy(tail, drop, tail, 0, keepExisting)
                System.arraycopy(bytes, 0, tail, keepExisting, count)
                tailLen = MAX_OUTPUT_BYTES
            }
        }

        fun drainAvailable() {
            // Limit each pass so a continuously writing child cannot starve the timeout check.
            repeat(16) {
                val available = stream.available()
                if (available <= 0) return
                // This is the only reader. Reading at most available bytes never waits for EOF.
                val count = stream.read(buffer, 0, minOf(buffer.size, available))
                if (count < 0) return
                appendTail(buffer, count)
            }
        }

        fun decodedOutput(): String = String(tail, 0, tailLen, Charsets.UTF_8)

        try {
            // su -c never consumes interactive input. A detached renderer must not inherit it.
            process.outputStream.close()
            while (true) {
                drainAvailable()
                if (process.waitFor(5, TimeUnit.MILLISECONDS)) {
                    drainAvailable()
                    return Result(process.exitValue(), decodedOutput())
                }
                if (System.nanoTime() - started >= timeoutNanos) {
                    // Report only our fixed progress markers, never arbitrary command output.
                    val phase = decodedOutput().lineSequence().lastOrNull {
                        it == "HiLight cleanup: checking source identity" ||
                            it == "HiLight cleanup: waiting for renderer exit" ||
                            it == "HiLight cleanup: scanning remaining renderers"
                    }
                    throw CommandTimeout("command timed out after ${timeoutSeconds}s" +
                        (phase?.let { " ($it)" } ?: ""))
                }
            }
        } finally {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            }
            // No concurrent blocking read owns the stream lock, even if a descendant holds stdout.
            stream.close()
            process.errorStream.close()
        }
    }
}
