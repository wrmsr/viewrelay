package com.wrmsr.viewrelay

import kotlin.concurrent.thread

class ViewRelayThinker {
    private var backgroundThread: Thread? = null

    fun start() {
        backgroundThread = thread(start = false) { threadMain() }
        backgroundThread?.start()
    }

    fun stop() {
        val thread = backgroundThread ?: return
        thread.interrupt()
        thread.join()
        backgroundThread = null
    }

    private fun threadMain() {
        try {
            while (!Thread.currentThread().isInterrupted) {
                tick()
                Thread.sleep(1000)
            }
        } catch (e: InterruptedException) {}
    }

    private fun tick() {
        println("Current time: ${java.time.LocalDateTime.now()}")
    }
}
