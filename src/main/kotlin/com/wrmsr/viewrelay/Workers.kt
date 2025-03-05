package com.wrmsr.viewrelay

import kotlin.concurrent.thread

abstract class Worker {
    @Volatile
    private var _stopped = false

    val stopped: Boolean
        get() = _stopped

    @Volatile
    private var thread: Thread? = null

    @Synchronized
    fun start() {
        if (_stopped) {
            throw RuntimeException("Stopped")
        }

        if (thread != null) {
            return
        }

        thread = thread(start = false) { threadMain() }
        thread?.start()
    }

    @Synchronized
    fun stop() {
        _stopped = true

        close()

        thread?.interrupt()
        thread?.join()

        close()
    }

    protected abstract fun threadMain()

    open fun close() {}
}