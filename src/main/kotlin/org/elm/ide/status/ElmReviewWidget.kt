package org.elm.ide.status

import com.intellij.AppTopics
import com.intellij.icons.AllIcons.Actions.OfflineMode
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.ClickListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.elm.ElmBundle
import org.elm.ide.icons.ElmIcons
import org.elm.ide.settings.ElmExternalLinterConfigurable
import org.elm.ide.settings.ElmExternalLinterProjectSettingsService
import org.elm.ide.settings.ElmProjectSettingsServiceBase
import org.elm.ide.settings.ElmProjectSettingsServiceBase.Companion.ELM_SETTINGS_TOPIC
import org.elm.ide.settings.experimentalFlags
import org.elm.workspace.ElmReviewService
import org.elm.workspace.elmreview.ElmReviewError
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.JComponent

class ElmReviewWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ElmReviewWidget.ID
    override fun getDisplayName(): String = "elm-review"
//    override fun isAvailable(project: Project): Boolean = project.hasCargoProject
    override fun createWidget(project: Project): StatusBarWidget = ElmReviewWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class ElmReviewWidgetUpdater(private val project: Project) : ElmReviewService.ElmReviewWatchListener {
    override fun update(baseDirPath: Path, messages: List<ElmReviewError>) {
        val manager = project.service<StatusBarWidgetsManager>()
        manager.updateWidget(ElmReviewWidgetFactory::class.java)
    }
}

class ElmReviewWidget(private val project: Project) : TextPanel.WithIconAndArrows(), CustomStatusBarWidget {
    private var statusBar: StatusBar? = null

    private val turnedOn: Boolean get() = project.experimentalFlags.elmReviewOnTheFlyEnabled

    var inProgress: Boolean = false
        set(value) {
            field = value
            update()
        }

    init {
        setTextAlignment(CENTER_ALIGNMENT)
        border = JBUI.CurrentTheme.StatusBar.Widget.border()
    }

    override fun ID(): String = ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar

        if (!project.isDisposed) {
            object : ClickListener() {
                override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                    if (!project.isDisposed) {
                        project.showSettingsDialog<ElmExternalLinterConfigurable>()
                    }
                    return true
                }
            }.installOn(this, true)

            project.messageBus.connect(this).subscribe(ELM_SETTINGS_TOPIC, object :
                ElmProjectSettingsServiceBase.ElmSettingsListener {
                override fun <T : ElmProjectSettingsServiceBase.ElmProjectSettingsBase<T>> settingsChanged(e: ElmProjectSettingsServiceBase.SettingsChangedEventBase<T>) {
                    if (e !is ElmExternalLinterProjectSettingsService.SettingsChangedEvent) return
                    if (e.isChanged(ElmExternalLinterProjectSettingsService.ElmExternalLinterProjectSettings::enableElmReviewOnTheFly)) {
                        update()
                    }
                }
            })
            with(project.messageBus.connect()) {
                subscribe(
                    AppTopics.FILE_DOCUMENT_SYNC,
                    object : FileDocumentManagerListener {
                        override fun beforeDocumentSaving(document: Document) {
                            inProgress = true
                            update()
                        }
                    }
                )
            }
            project.messageBus.connect().apply {
                subscribe(ElmReviewService.ELM_REVIEW_WATCH_TOPIC, object : ElmReviewService.ElmReviewWatchListener {
                    override fun update(baseDirPath: Path, messages: List<ElmReviewError>) {
                        inProgress = false
                        update()
                    }
                }
                )}
        }

        update()
        statusBar.updateWidget(ID())
    }

    override fun dispose() {
        statusBar = null
        UIUtil.dispose(this)
    }

    override fun getComponent(): JComponent = this

    private fun update() {
        if (project.isDisposed) return
        UIUtil.invokeLaterIfNeeded {
            if (project.isDisposed) return@invokeLaterIfNeeded
            text = "elm-review"
            val status = if (turnedOn) ElmBundle.message("on") else ElmBundle.message("off")
            toolTipText = ElmBundle.message("is.in.progress.1.on.the.fly.analysis.is.turned.1", status, if (inProgress) 0 else 1)
            toolTipText = "Running..."
            icon = when {
                !turnedOn -> OfflineMode
                inProgress -> ElmIcons.ELM_ANIMATED
                else -> ElmIcons.TOOL_WINDOW
            }
            repaint()
        }
    }

    companion object {
        const val ID: String = "elmReviewWidget"
    }
}
inline fun <reified T: Configurable> Project.showSettingsDialog() {
    ShowSettingsUtil.getInstance().showSettingsDialog(this, T::class.java)
}
