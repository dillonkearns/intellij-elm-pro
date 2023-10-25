package org.elm.workspace

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import org.elm.ide.notifications.showBalloon
import org.elm.workspace.elmreview.ElmReviewError
import org.elm.workspace.elmreview.readErrorReport
import java.nio.file.Path
import kotlin.io.path.absolutePathString


private val log = logger<ElmReviewService>()

@State(name = "ElmReview")
@Service(Service.Level.PROJECT)
class ElmReviewService(val project: Project) {
    init {
        start()
    }

    var activeWatchmodeProcess: Process? = null
    var messages = listOf<ElmReviewError>()

    interface ElmReviewWatchListener {
        fun update(
            baseDirPath: Path, messages: List<ElmReviewError>
        )
    }

    companion object {
        val ELM_REVIEW_WATCH_TOPIC = Topic("elm-review errors", ElmReviewWatchListener::class.java)
    }

    fun start() {
        if (activeWatchmodeProcess == null) {
            // TODO handle multiple projects in one workspace (maybe through UI configuration?)
            val elmProject = project.elmWorkspace.allProjects.firstOrNull()
                ?: return showError(project, "Could not determine active Elm project")

            val elmCompiler = project.elmToolchain.elmCLI
            val elmReviewExecutablePath = project.elmToolchain.elmReviewPath!!
            val arguments = listOf("--watch", "--report=json", "--namespace=intellij-elm") +
                    if (project is ElmProject) "--config=./review" else "" +
                            if (elmCompiler == null) "" else "--compiler=${elmCompiler.elmExecutablePath}"

            val command: List<String> = listOf(elmReviewExecutablePath.absolutePathString(), *arguments.toTypedArray())
            ApplicationManager.getApplication().executeOnPooledThread {
                activeWatchmodeProcess = startProcess(command, elmProject, project)
                val disposable = Disposer.newDisposable("New elm-review input")

                activeWatchmodeProcess!!.inputStream.bufferedReader().forEachLine { line ->
                    // if we're still parsing output from a previous run, cancel it
                    disposable.dispose()
                    BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposable) {
                        val reviewErrors = readErrorReport(line, disposable)
                        this.messages = reviewErrors
                        project.messageBus.syncPublisher(ELM_REVIEW_WATCH_TOPIC)
                            .update(elmProject.projectDirPath, reviewErrors)
                    }
                }

            }
        }
    }

    private fun startProcess(cmd: List<String>, elmProject: ElmProject, project: Project): Process =
        ProcessBuilder(cmd)
            .directory(elmProject.projectDirPath.toFile())
            .start()

    private fun showError(project: Project, message: String, includeFixAction: Boolean = false) {
        val actions = if (includeFixAction)
            arrayOf("Fix" to { project.elmWorkspace.showConfigureToolchainUI() })
        else
            emptyArray()
        project.showBalloon(message, NotificationType.ERROR, *actions)
    }
}
