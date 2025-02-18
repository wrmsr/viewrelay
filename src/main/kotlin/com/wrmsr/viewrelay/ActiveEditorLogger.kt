// https://plugins.jetbrains.com/plugin/17669-flora-beta-
// https://github.com/dkandalov/live-plugin
// com.intellij.openapi.diagnostic.Logger.getInstance("ActiveEditorLogger").info("Current file: $filePath")
package com.wrmsr.viewrelay

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

class ActiveEditorVisibilityTracker : ProjectActivity {
    private val logger = Logger.getInstance("ActiveEditorLogger")
    private val lastState = AtomicReference<Pair<String, IntRange>?>(null)

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(object : VisibleAreaListener {
                override fun visibleAreaChanged(event: VisibleAreaEvent) {
                    try {
                        val editor = event.editor
                        logVisibleFileAndLines(editor)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }, project)
        }
    }

    private fun logVisibleFileAndLines(editor: Editor) {
        val filePath = editor.virtualFile?.path ?: return
        val visibleLines = getVisibleLineRange(editor) ?: return

        val newState = filePath to visibleLines
        val prevState = lastState.getAndSet(newState)

        // Print only if the file or visible lines changed
        if (newState != prevState) {
            println("Visible file: $filePath, Lines: $visibleLines")
        }
    }

    private fun getVisibleLineRange(editor: Editor): IntRange? {
        val document = editor.document
        val scrollingModel = editor.scrollingModel
        val visibleArea = scrollingModel.visibleArea

        val startLine = editor.yToVisualLine(visibleArea.y)
        val endLine = min(editor.yToVisualLine(visibleArea.y + visibleArea.height), document.lineCount - 1)

        val startLogicalLine = editor.visualToLogicalPosition(editor.offsetToVisualPosition(document.getLineStartOffset(startLine))).line
        val endLogicalLine = editor.visualToLogicalPosition(editor.offsetToVisualPosition(document.getLineEndOffset(endLine))).line

        return startLogicalLine..endLogicalLine
    }
}
