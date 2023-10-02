package org.elm.workspace.commandLineTools

import com.google.gson.JsonSyntaxException
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.elm.ide.notifications.showBalloon
import org.elm.openapiext.GeneralCommandLine
import org.elm.openapiext.Result
import org.elm.openapiext.execute
import org.elm.openapiext.pathRelative
import org.elm.workspace.*
import org.elm.workspace.elmreview.ElmReviewError
import org.elm.workspace.elmreview.readErrorReportLine
import java.nio.file.Path
import kotlin.streams.toList

private val log = logger<ElmReviewCLI>()


/**
 * Interact with external `elm-review` process.
 */
class ElmReviewCLI(val elmReviewExecutablePath: Path) {

    fun runReviewForInspection(project: Project, elmProject: ElmProject, elmCompiler: ElmCLI?, currentFile: VirtualFile? = null): List<ElmReviewError> {

        // This option makes the CLI output non-JSON output, but can be useful to debug what is happening
        // "--debug",

        val arguments = listOf("--report=ndjson", "--namespace=intellij-elm") +
                if (elmProject is ElmApplicationProject) "--config=./review" else "" +
                        if (elmCompiler == null) "" else "--compiler=${elmCompiler.elmExecutablePath}"

        val generalCommandLine = GeneralCommandLine(elmReviewExecutablePath).withWorkDirectory(elmProject.projectDirPath.toString()).withParameters(arguments)


//            indicator.text = "reviewing ${elmProject.projectDirPath}"
            val handler = CapturingProcessHandler(generalCommandLine)
            val processKiller = Disposable { handler.destroyProcess() }

            Disposer.register(project, processKiller)
            try {
                val alreadyDisposed = runReadAction { project.isDisposed }
                if (alreadyDisposed) {
                    throw ExecutionException("External command failed to start")
                }

                var line = 0
                val msgs = handler.process.inputStream.bufferedReader().lines().map {
                    if (project.isDisposed) {
                        null
                    } else {
                        line += 1
                        try {
                            readErrorReportLine(it)
                        } catch (e: JsonSyntaxException) {
                            project.showBalloon("elm-review truncated JSON on line $line.", NotificationType.ERROR)
                            null
                        }
                    }
                    }.toList().filterNotNull().sortedWith(
                        compareBy(
                            { it.path },
                            { it.region!!.start!!.line },
                            { it.region!!.start!!.column }
                        ))
                    return if (currentFile != null) {
                        val predicate: (ElmReviewError) -> Boolean = { it.path == currentFile.pathRelative(project).toString() }
                        val sortedMessages = msgs.filter(predicate) + msgs.filterNot(predicate)
                        sortedMessages
                    } else msgs
//                if (!isUnitTestMode) {
////                    indicator.text = "review finished"
////                    ApplicationManager.getApplication().invokeLater {
////                        project.messageBus.syncPublisher(ELM_REVIEW_ERRORS_TOPIC).update(elmProject.projectDirPath, messages, null, 0)
////                    }
//                }
//                return null!!
            }
            catch (e: Exception) {
                return emptyList()
            }
            finally {
                Disposer.dispose(processKiller)
//
////                return null!!
            }
    }


    fun queryVersion(project: Project): Result<Version> {
        val firstLine = try {
            val arguments: List<String> = listOf("--version")
            GeneralCommandLine(elmReviewExecutablePath)
                .withParameters(arguments)
                .execute(elmReviewTool, project)
                .stdoutLines
                .firstOrNull()
        } catch (e: ExecutionException) {
            return Result.Err("failed to run elm-review: ${e.message}")
        } ?: return Result.Err("no output from elm-review")

        return try {
            Result.Ok(Version.parse(firstLine))
        } catch (e: ParseException) {
            Result.Err("invalid elm-review version: ${e.message}")
        }
    }
}

