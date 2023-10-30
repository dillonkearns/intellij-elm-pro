package org.elm.workspace.commandLineTools

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import org.elm.openapiext.GeneralCommandLine
import org.elm.openapiext.Result
import org.elm.openapiext.execute
import org.elm.workspace.ParseException
import org.elm.workspace.Version
import org.elm.workspace.elmReviewTool
import java.nio.file.Path


/**
 * Interact with external `elm-review` process.
 */
class ElmReviewCLI(val elmReviewExecutablePath: Path) {
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
