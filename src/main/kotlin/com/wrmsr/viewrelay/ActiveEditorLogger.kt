// com.intellij.openapi.diagnostic.Logger.getInstance("ActiveEditorLogger").info("Current file: $filePath")
package com.wrmsr.viewrelay

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.startup.StartupActivity
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ActiveEditorLogger : ProjectActivity {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val logger = Logger.getInstance("ActiveEditorLogger")

    override suspend fun execute(project: Project) {
        startLogging()
    }

    private fun startLogging() {
        scheduler.scheduleAtFixedRate({
            logActiveFilePath()
        }, 0, 1, TimeUnit.SECONDS) // Run every 1 second
    }

    private fun logActiveFilePath() {
        val editor = EditorFactory.getInstance().allEditors.firstOrNull()
        val filePath = editor?.virtualFile?.path

        filePath?.let {
            // Log to IntelliJ Event Log & Debug Console
            logger.info("Active file: $filePath")
        }
    }
}