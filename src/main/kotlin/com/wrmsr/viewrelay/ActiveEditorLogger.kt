// https://plugins.jetbrains.com/plugin/17669-flora-beta-
// https://github.com/dkandalov/live-plugin
// com.intellij.openapi.diagnostic.Logger.getInstance("ActiveEditorLogger").info("Current file: $filePath")
package com.wrmsr.viewrelay

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlinx.serialization.*
import kotlinx.serialization.json.*

class ActiveEditorVisibilityTracker : ProjectActivity {
    @Serializable
    data class FilePosition(
        val line: Int,
        val column: Int,
        val offset: Int,
    )

    @Serializable
    data class FileRange(
        val start: FilePosition,
        val end: FilePosition,
    )

    @Serializable
    data class FileVisibilityState(
        val filePath: String,
        val visible: FileRange,
        val caret: FilePosition,
        val selection: FileRange,
    )

    //

    private val logger = Logger.getInstance("ActiveEditorLogger")

    private val lastState = AtomicReference<FileVisibilityState?>(null)

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val em = EditorFactory.getInstance().eventMulticaster
            val pd: Disposable = project

            em.addVisibleAreaListener(object : VisibleAreaListener {
                override fun visibleAreaChanged(event: VisibleAreaEvent) {
                    tryLogVisibleFileAndLines(event.editor)
                }
            }, pd)

            em.addCaretListener(object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    tryLogVisibleFileAndLines(event.editor)
                }

                override fun caretAdded(event: CaretEvent) {
                    tryLogVisibleFileAndLines(event.editor)
                }

                override fun caretRemoved(event: CaretEvent) {
                    tryLogVisibleFileAndLines(event.editor)
                }
            }, pd)

            em.addSelectionListener(object : SelectionListener {
                override fun selectionChanged(event: SelectionEvent) {
                    tryLogVisibleFileAndLines(event.editor)
                }
            }, pd)
        }
    }

    //

    private fun filePositionFromOffset(editor: Editor, offset: Int): FilePosition {
        val lp = editor.offsetToLogicalPosition(offset)
        return FilePosition(
            lp.line,
            lp.column,
            offset
        )
    }

    private fun filePositionFromLogicalPosition(editor: Editor, lp: LogicalPosition): FilePosition {
        return FilePosition(
            lp.line,
            lp.column,
            editor.logicalPositionToOffset(lp),
        )
    }

    //

    private fun tryLogVisibleFileAndLines(editor: Editor) {
        try {
            logVisibleFileAndLines(editor)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun logVisibleFileAndLines(editor: Editor) {
        val filePath = editor.virtualFile?.path ?: return
        val visible = getVisibleLineRange(editor)

        val currentCaret = editor.caretModel.currentCaret

        val caret = FilePosition(
            currentCaret.logicalPosition.line,
            currentCaret.logicalPosition.column,
            currentCaret.offset,
        )

        val caretSelectionRange = currentCaret.selectionRange
        val selection = FileRange(
            filePositionFromOffset(editor, caretSelectionRange.startOffset),
            filePositionFromOffset(editor, caretSelectionRange.endOffset),
        )

        val newState = FileVisibilityState(
            filePath,
            visible,
            caret,
            selection,
        )

        val prevState = lastState.getAndSet(newState)

        if (newState != prevState) {
            val json = Json { prettyPrint = true }
            val js = json.encodeToString(newState)
            println(js)
        }
    }

    private fun getVisibleLineRange(editor: Editor): FileRange {
        val document = editor.document
        val scrollingModel = editor.scrollingModel
        val visibleArea = scrollingModel.visibleArea

        val startLine = editor.yToVisualLine(visibleArea.y)
        val endLine = min(editor.yToVisualLine(visibleArea.y + visibleArea.height), document.lineCount - 1)

        val startLogicalPosition =
            editor.visualToLogicalPosition(editor.offsetToVisualPosition(document.getLineStartOffset(startLine)))
        val endLogicalPosition =
            editor.visualToLogicalPosition(editor.offsetToVisualPosition(document.getLineEndOffset(endLine)))

        return FileRange(
            filePositionFromLogicalPosition(editor, startLogicalPosition),
            filePositionFromLogicalPosition(editor, endLogicalPosition),
        )
    }
}
