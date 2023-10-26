package org.elm.ide.toolwindow

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.ui.MessageCategory
import org.elm.openapiext.findFileByPath
import org.elm.workspace.ElmReviewService
import org.elm.workspace.elmreview.ElmReviewError
import java.nio.file.Path

class ElmReviewToolWindowFactory : ToolWindowFactory {
    private var newlyOpened = true
    private fun paintMessages(
        project: Project,
        toolWindow: ToolWindow,
        messages: List<ElmReviewError>,
        baseDirPath: Path

    ) {
        invokeLater {
            val errorTreeViewPanel =
                ElmErrorTreeViewPanel(
                    project,
                    "elm-review",
                    createExitAction = false,
                    createToolbar = true
                )

            messages.forEachIndexed { index, elmReviewError ->
                val sourceLocation = elmReviewError.path!!
                val virtualFile = baseDirPath.resolve(sourceLocation).let {
                    LocalFileSystem.getInstance().findFileByPath(it)
                }
                val encodedIndex = "\u200B".repeat(index)
                updateErrorTree(errorTreeViewPanel, encodedIndex, elmReviewError, virtualFile)
            }

            toolWindow.contentManager.removeAllContents(true)
            toolWindow.contentManager.addContent(
                ContentImpl(
                    errorTreeViewPanel,
                    "Elm-Review watchmode result",
                    true
                )
            )
            if (toolWindow.isVisible || this.newlyOpened) {
                toolWindow.show(null)
                errorTreeViewPanel.expandAll()
                errorTreeViewPanel.requestFocus()
                focusEditor(project)
            }
            this.newlyOpened = false
        }
    }
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.getService(ElmReviewService::class.java)
        service?.start()
        paintMessages(project, toolWindow, service.messages, project.basePath!!.toNioPath())
        with(project.messageBus.connect()) {
            subscribe(ElmReviewService.ELM_REVIEW_WATCH_TOPIC, object : ElmReviewService.ElmReviewWatchListener {
                override fun update(baseDirPath: Path, messages: List<ElmReviewError>) {
                    paintMessages(project, toolWindow, messages, baseDirPath)
                }

            })
        }
    }

    private fun updateErrorTree(
        errorTreeViewPanel: ElmErrorTreeViewPanel,
        encodedIndex: String,
        elmReviewError: ElmReviewError,
        virtualFile: VirtualFile?
    ) {
        if (elmReviewError.region == null) {
            errorTreeViewPanel.addMessage(
                MessageCategory.SIMPLE, arrayOf("$encodedIndex${elmReviewError.rule ?: ""}:", elmReviewError.message ?: ""),
                virtualFile,
                0,
                0,
                elmReviewError.html ?: "General Error !"
            )
        } else {
            errorTreeViewPanel.addMessage(
                MessageCategory.SIMPLE, arrayOf("$encodedIndex${elmReviewError.rule}:", "${elmReviewError.message}"),
                virtualFile,
                elmReviewError.region!!.start.let { it!!.line - 1 },
                elmReviewError.region!!.start.let { it!!.column - 1 },
                elmReviewError.html!!
            )
        }
    }
}

fun focusEditor(project: Project) {
    DataManager.getInstance().dataContextFromFocusAsync.then {
        val editor = it.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
            IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true)
        }
    }
}
