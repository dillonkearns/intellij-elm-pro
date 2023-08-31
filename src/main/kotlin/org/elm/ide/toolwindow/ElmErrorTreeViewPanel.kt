package org.elm.ide.toolwindow

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.event.TreeSelectionListener

class ElmErrorTreeViewPanel(project: Project, helpId: String?, createExitAction: Boolean, createToolbar: Boolean) : NewErrorTreeViewPanel(project, helpId, createExitAction, createToolbar) {

    val messages = mutableListOf<String>()

    init {
        connectFriendlyMessages(project)
    }

    override fun addMessage(type: Int, text: Array<String>, file: VirtualFile?, line: Int, column: Int, data: Any?) {
        super.addMessage(type, text, file, line, column, null)
        messages.add(data as String)
    }

    private fun addSelectionListener(tsl: TreeSelectionListener) {
        myTree.addTreeSelectionListener(tsl)
    }

    private fun connectFriendlyMessages(project: Project) {
        ToolWindowManager.getInstance(project).getToolWindow("Friendly Messages")?.let {
            val reportUI = (it.contentManager.contents[0].component as ReportPanel).reportUI
            val selectionListener = ErrorTreeSelectionListener(messages, reportUI, it)
            addSelectionListener(selectionListener)
            reportUI.background = background
            reportUI.text = ""
        }
    }
}
