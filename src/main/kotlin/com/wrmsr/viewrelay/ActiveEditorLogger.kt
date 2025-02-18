// com.intellij.openapi.diagnostic.Logger.getInstance("ActiveEditorLogger").info("Current file: $filePath")
package com.wrmsr.viewrelay

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ActiveEditorLogger : ProjectActivity {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val logger = Logger.getInstance("ActiveEditorLogger")
    private val isRunning = AtomicBoolean(false)

    override suspend fun execute(project: Project) {
        startLogging()
    }

    private fun startLogging() {
        scheduler.scheduleAtFixedRate({
            if (isRunning.compareAndSet(false, true)) { // Check if another execution is running
                try {
                    logActiveFilePath()
                } finally {
                    isRunning.set(false) // Release the lock after execution
                }
            }
        }, 0, 1, TimeUnit.SECONDS) // Run every 1 second
    }

    private fun logActiveFilePath() {
        val editor = EditorFactory.getInstance().allEditors.firstOrNull()
        val filePath = editor?.virtualFile?.path

        filePath?.let {
            val msg = "Active file: $filePath"

            println(msg)

            logger.info(msg)

            Logger.getInstance("ActiveEditorLogger").info(msg)

            // // Also show it in the Event Log
            // val notification = Notification(
            //     "Active Editor Logger",
            //     "Active File Changed",
            //     msg,
            //     NotificationType.INFORMATION
            // )
            // Notifications.Bus.notify(notification)
        }
    }
}