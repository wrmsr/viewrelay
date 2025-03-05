/*
TODO:
 - multiple carets
 - honor columns in visible
 - server

==

https://plugins.jetbrains.com/plugin/17669-flora-beta-
https://github.com/dkandalov/live-plugin

com.intellij.openapi.diagnostic.Logger.getInstance("ActiveEditorLogger").info("Current file: $filePath")
*/
package com.wrmsr.viewrelay

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.TextRange
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.awt.Rectangle

//

@Serializable
data class FilePosition(
    val line: Int,
    val column: Int,
    val offset: Int,
) {
    companion object {
        fun fromOffset(editor: Editor, offset: Int): FilePosition {
            val lp = editor.offsetToLogicalPosition(offset)
            return FilePosition(
                lp.line,
                lp.column,
                offset
            )
        }

        fun fromLogicalPosition(editor: Editor, lp: LogicalPosition): FilePosition {
            return FilePosition(
                lp.line,
                lp.column,
                editor.logicalPositionToOffset(lp),
            )
        }

        fun fromCaret(caret: Caret): FilePosition {
            return FilePosition(
                caret.logicalPosition.line,
                caret.logicalPosition.column,
                caret.offset,
            )
        }
    }
}

@Serializable
data class FileRange(
    val start: FilePosition,
    val end: FilePosition,
) {
    companion object {
        fun fromTextRange(editor: Editor, textRange: TextRange): FileRange {
            return FileRange(
                FilePosition.fromOffset(editor, textRange.startOffset),
                FilePosition.fromOffset(editor, textRange.endOffset),
            )
        }

        fun fromRectangle(editor: Editor, rectangle: Rectangle): FileRange {
            val document = editor.document

            // FIXME: honor columns
            val startLine = editor.yToVisualLine(rectangle.y)
            val endLine = min(editor.yToVisualLine(rectangle.y + rectangle.height), document.lineCount - 1)

            return fromTextRange(
                editor, TextRange(
                    document.getLineStartOffset(startLine),
                    document.getLineEndOffset(endLine),
                )
            )
        }
    }
}

@Serializable
data class FileVisibilityState(
    val filePath: String,
    val visible: FileRange,
    val caret: FilePosition,
    val selection: FileRange,
)

//

@Service
class ViewRelayService : Disposable {
    override fun dispose() {
        shutdown()
    }

    //

    private var hasSetup = false

    @Synchronized
    fun setup() {
        if (hasSetup) {
            return
        }
        hasSetup = true

        startBackgroundThread()

        installEditorListeners()
    }

    @Synchronized
    fun shutdown() {
        stopBackgroundThread()
    }

    //

    private var backgroundThread: Thread? = null

    private fun startBackgroundThread() {
        backgroundThread = Thread { backgroundThreadMain() }
        backgroundThread?.start()
    }
    
    private fun stopBackgroundThread() {
        val thread = backgroundThread ?: return
        thread.interrupt()
        thread.join()
        backgroundThread = null
    }

    private fun backgroundThreadMain() {
        try {
            while (!Thread.currentThread().isInterrupted) {
                backgroundThreadTick()
                Thread.sleep(1000)
            }
        } catch (e: InterruptedException) {}
    }
    
    private fun backgroundThreadTick() {
        println("Current time: ${java.time.LocalDateTime.now()}")
    }

    //

    private fun installEditorListeners() {
        val em = EditorFactory.getInstance().eventMulticaster

        em.addVisibleAreaListener(object : VisibleAreaListener {
            override fun visibleAreaChanged(event: VisibleAreaEvent) {
                tryUpdateEditor(event.editor)
            }
        }, this)

        em.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                tryUpdateEditor(event.editor)
            }

            override fun caretAdded(event: CaretEvent) {
                tryUpdateEditor(event.editor)
            }

            override fun caretRemoved(event: CaretEvent) {
                tryUpdateEditor(event.editor)
            }
        }, this)

        em.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) {
                tryUpdateEditor(event.editor)
            }
        }, this)

    }

    private fun tryUpdateEditor(editor: Editor) {
        try {
            updateState(editor)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //

    private val lastState = AtomicReference<FileVisibilityState?>(null)

    private fun updateState(editor: Editor) {
        val filePath = editor.virtualFile?.path ?: return

        val visible = FileRange.fromRectangle(editor, editor.scrollingModel.visibleArea)
        val caret = FilePosition.fromCaret(editor.caretModel.currentCaret)
        val selection = FileRange.fromTextRange(editor, editor.caretModel.currentCaret.selectionRange)

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
}

//

class ViewRelayProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            service<ViewRelayService>().setup()
        }
    }
}
