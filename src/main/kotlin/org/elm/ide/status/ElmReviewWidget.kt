package org.elm.ide.status

//import org.rust.cargo.project.configurable.ElmReviewLinterConfigurable
//import org.rust.cargo.project.model.CargoProject
//import org.rust.cargo.project.model.CargoProjectsService
//import org.rust.cargo.project.settings.ElmReviewLinterProjectSettingsService
//import org.rust.cargo.project.settings.RsProjectSettingsServiceBase.*
//import org.rust.cargo.project.settings.RsProjectSettingsServiceBase.Companion.RUST_SETTINGS_TOPIC
//import org.rust.cargo.project.settings.externalLinterSettings
//import org.rust.cargo.runconfig.hasCargoProject
//import org.rust.cargo.toolchain.ExternalLinter
//import org.rust.ide.icons.RsIcons
//import org.rust.ide.notifications.ElmReviewLinterTooltipService
//import org.rust.openapiext.showSettingsDialog
import com.intellij.AppTopics
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
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

class ElmReviewLinterWidgetUpdater(private val project: Project) : ElmReviewService.ElmReviewWatchListener {
    override fun update(baseDirPath: Path, messages: List<ElmReviewError>) {
        val manager = project.service<StatusBarWidgetsManager>()
        manager.updateWidget(ElmReviewWidgetFactory::class.java)
    }
}

class ElmReviewWidget(private val project: Project) : TextPanel.WithIconAndArrows(), CustomStatusBarWidget {
    private var statusBar: StatusBar? = null

//    private val linter: ExternalLinter get() = project.externalLinterSettings.tool
//    private val turnedOn: Boolean get() = project.externalLinterSettings.runOnTheFly

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
//                        project.showSettingsDialog<ElmReviewLinterConfigurable>()
                    }
                    return true
                }
            }.installOn(this, true)

//            project.messageBus.connect(this).subscribe(RUST_SETTINGS_TOPIC, object : RsSettingsListener {
//                override fun <T : RsProjectSettingsBase<T>> settingsChanged(e: SettingsChangedEventBase<T>) {
//                    if (e !is ElmReviewLinterProjectSettingsService.SettingsChangedEvent) return
//                    if (e.isChanged(ElmReviewLinterProjectSettingsService.ElmReviewLinterProjectSettings::tool) ||
//                        e.isChanged(ElmReviewLinterProjectSettingsService.ElmReviewLinterProjectSettings::runOnTheFly)) {
//                        update()
//                    }
//                }
//            })
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


//            project.service<ElmReviewTool>().showTooltip(this)
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
            val turnedOn = true
            val status = if (turnedOn) ElmBundle.message("on") else ElmBundle.message("off")
            toolTipText = ElmBundle.message("is.in.progress.1.on.the.fly.analysis.is.turned.1", status, if (inProgress) 0 else 1)
            toolTipText = "Running..."
            icon = when {
//                !turnedOn -> ElmIcons.GEAR_OFF
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
