package com.wrmsr.viewrelay

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener

class ViewRelayListeners(
    val update: (Editor) -> Unit,
    val disposable: Disposable,

    private val logger: Logger? = null,
) {
    fun install() {
        val em = EditorFactory.getInstance().eventMulticaster

        em.addVisibleAreaListener(object : VisibleAreaListener {
            override fun visibleAreaChanged(event: VisibleAreaEvent) {
                tryUpdateEditor(event.editor)
            }
        }, disposable)

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
        }, disposable)

        em.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) {
                tryUpdateEditor(event.editor)
            }
        }, disposable)
    }

    private fun tryUpdateEditor(editor: Editor) {
        try {
            update(editor)
        } catch (e: Exception) {
            logger?.exception(e)
        }
    }
}