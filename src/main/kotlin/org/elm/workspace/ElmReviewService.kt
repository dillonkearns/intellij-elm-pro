package org.elm.workspace

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import org.elm.ide.notifications.showBalloon
import org.elm.workspace.elmreview.ElmReviewError
import org.elm.workspace.elmreview.readErrorReport
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists


private val log = logger<ElmReviewService>()

@State(name = "ElmReview")
@Service(Service.Level.PROJECT)
class ElmReviewService(val project: Project) {
    private var watchers: HashMap<Path, Process> = hashMapOf()
    var messages: HashMap<Path, List<ElmReviewError>> = hashMapOf()

    fun messagesForCurrentProject(path: Path): List<ElmReviewError> {
        return messages[path] ?: emptyList()
    }

    interface ElmReviewWatchListener {
        fun update(
            baseDirPath: Path, messages: List<ElmReviewError>
        )
    }

    companion object {
        val ELM_REVIEW_WATCH_TOPIC = Topic("elm-review errors", ElmReviewWatchListener::class.java)
    }

    private fun reviewDirExists(): Boolean {
        return currentBasePath()?.let { Path.of(it, "review") }?.exists() ?: defaultWithError()
    }

    private fun defaultWithError(): Boolean {
        showError(project, "Could not determine whether a review/ folder is present in this project.")
        return false
    }

    private fun currentBasePath(): @SystemIndependent @NonNls String? = currentOrDefaultProject(project).basePath

    fun isElmProject(projectBasePath: Path) = projectBasePath.resolve("elm.json").exists()

    fun start(projectBasePath: Path) {
        if (this.watchers == null) {
            this.watchers = hashMapOf()
        }
        val currentWatcher: Process? = this.watchers[projectBasePath]

        if ((currentWatcher == null || !currentWatcher.isAlive) && reviewDirExists() && isElmProject(projectBasePath)) {
            val elmCompiler = project.elmToolchain.elmCLI
            val elmReviewExecutablePath = project.elmToolchain.elmReviewPath ?: return showError(
                project,
                "Could not find elm-review executable",
                true
            )
            val arguments = listOf("--watch", "--report=json", "--namespace=intellij-elm") +
                    if (project is ElmProject) "--config=./review" else "" +
                            if (elmCompiler == null) "" else "--compiler=${elmCompiler.elmExecutablePath}"

            val command: List<String> = listOf(elmReviewExecutablePath.absolutePathString(), *arguments.toTypedArray())
            ApplicationManager.getApplication().executeOnPooledThread {
                val newWatcher = startWatcher(command, projectBasePath, project)
                watchers[projectBasePath] = newWatcher
                val disposable = Disposer.newDisposable("New elm-review input")

                newWatcher.inputStream.bufferedReader().forEachLine { line ->
                    // if we're still parsing output from a previous run, cancel it
                    disposable.dispose()
                    if (!newWatcher.isAlive && newWatcher.exitValue() != 0) {
                        // if the process has exited unsuccessfully, print any output as errors
                        showError(project, line)
                    }
                    else {
                        BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposable) {
                            val reviewErrors = readErrorReport(line, disposable)
                            this.messages[projectBasePath] = reviewErrors
                            if (!Disposer.isDisposed(disposable)) {
                                project.messageBus.syncPublisher(ELM_REVIEW_WATCH_TOPIC)
                                    .update(projectBasePath, reviewErrors)
                            }
                        }
                    }
                }

                newWatcher.errorStream.reader().forEachLine {
                    showError(project, it)
                }
            }
        }
    }

    private fun startWatcher(cmd: List<String>, basePath: Path, project: Project): Process {
        return ProcessBuilder(cmd)
            .directory(basePath.toFile())
            .start()
    }

    private fun showError(project: Project, message: String, includeFixAction: Boolean = false) {
        val actions = if (includeFixAction)
            arrayOf("Fix" to { project.elmWorkspace.showConfigureToolchainUI() })
        else
            emptyArray()
        project.showBalloon(message, NotificationType.ERROR, "elm-review Error", *actions)
    }
}
