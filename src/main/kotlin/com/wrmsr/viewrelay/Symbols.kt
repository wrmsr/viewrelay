package com.wrmsr.viewrelay

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore

class GoToDefinitionAction(
    private val logger: Logger? = null,
) : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return

        val caretOffset = editor.caretModel.offset
        val elementAtCaret = file.findElementAt(caretOffset) ?: return

        val targetDefinition = resolveDefinition(elementAtCaret)
        if (targetDefinition != null) {
            navigateToElement(project, targetDefinition)
        } else {
            logger?.warn("No definition found for: ${elementAtCaret.text}")
        }
    }

    private fun resolveDefinition(element: PsiElement): PsiElement? {
        val reference = element.reference ?: return null
        val resolved = reference.resolve() ?: return null
        return resolved
    }

    private fun navigateToElement(project: Project, element: PsiElement) {
        val virtualFile: VirtualFile = PsiUtilCore.getVirtualFile(element) ?: return
        val navigable = OpenFileDescriptor(project, virtualFile, element.textOffset)
        FileEditorManager.getInstance(project).openTextEditor(navigable, true)
    }
}

