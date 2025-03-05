package com.wrmsr.viewrelay

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.util.TextRange
import kotlinx.serialization.Serializable
import java.awt.Rectangle
import kotlin.math.min

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
data class ViewRelayState(
    val filePath: String,
    val visible: FileRange,
    val caret: FilePosition,
    val selection: FileRange,
) {
    companion object {
        fun fromEditor(editor: Editor): ViewRelayState? {
            val filePath = editor.virtualFile?.path?: return null

            val visible = FileRange.fromRectangle(editor, editor.scrollingModel.visibleArea)
            val caret = FilePosition.fromCaret(editor.caretModel.currentCaret)
            val selection = FileRange.fromTextRange(editor, editor.caretModel.currentCaret.selectionRange)

            return ViewRelayState(
                filePath,
                visible,
                caret,
                selection,
            )
        }
    }
}
