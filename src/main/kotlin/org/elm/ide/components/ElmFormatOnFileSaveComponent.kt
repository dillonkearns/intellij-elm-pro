package org.elm.ide.components

import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import org.elm.ide.notifications.showBalloon
import org.elm.lang.core.psi.isElmFile
import org.elm.workspace.commandLineTools.ElmFormatCLI
import org.elm.workspace.commandLineTools.ElmFormatCLI.ElmFormatResult
import org.elm.workspace.elmSettings
import org.elm.workspace.elmToolchain
import org.elm.workspace.elmWorkspace

class ElmFormatOnFileSaveComponent(val project: Project) {

    private var isSuppressed: Boolean = false
    fun withoutReformatting(action: () -> Unit) {
        val oldStatus = isSuppressed
        try {
            isSuppressed = true
            action()
        } finally {
            isSuppressed = oldStatus
        }
    }
    companion object {
        fun getInstance(): ElmFormatOnFileSaveComponent = service()
        fun getInstanceIfCreated(): ElmFormatOnFileSaveComponent? = serviceIfCreated()
    }


        init {
        with(project.messageBus.connect()) {
            subscribe(
                FileDocumentManagerListener.TOPIC,
                object : FileDocumentManagerListener {
                    override fun beforeDocumentSaving(document: Document) {
                        val isSuppressed = getInstanceIfCreated()?.isSuppressed == true
                        if (isSuppressed) return
                        if (!project.elmSettings.toolchain.isElmFormatOnSaveEnabled) return
                        val vFile = FileDocumentManager.getInstance().getFile(document) ?: return
                        if (!vFile.isElmFile) return
                        val elmVersion = ElmFormatCLI.getElmVersion(project, vFile) ?: return
                        val elmFormat = project.elmToolchain.elmFormatCLI ?: return

                        val result = elmFormat.formatDocumentAndSetText(project, document, elmVersion, addToUndoStack = false)
                        when (result) {
                            is ElmFormatResult.BadSyntax ->
                                project.showBalloon(result.msg, NotificationType.WARNING)

                            is ElmFormatResult.FailedToStart ->
                                project.showBalloon(result.msg, NotificationType.ERROR,
                                    "Configure" to { project.elmWorkspace.showConfigureToolchainUI() }
                                )

                            is ElmFormatResult.UnknownFailure ->
                                project.showBalloon(result.msg, NotificationType.ERROR)

                            is ElmFormatResult.Success ->
                                return
                        }
                    }
                }
            )
        }
    }
}