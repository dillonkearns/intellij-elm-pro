package org.elm.json.inspections

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonElementVisitor
import com.intellij.json.psi.JsonFile
import com.intellij.util.io.HttpRequests
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.elm.ide.notifications.showBalloon
import org.elm.openapiext.runWithCheckCanceled

import java.io.IOException

class NewPackageVersionAvailableInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JsonElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                when (element) {
                    is JsonFile -> {
                        val versions = packageVersions(holder.project)
                        val root = element.topLevelValue as JsonObject
                        val projectType = (root.findProperty("type")?.value as JsonStringLiteral).value
                        if (projectType == "application") {
                            (((root.findProperty("dependencies")?.value as JsonObject)).findProperty(
                                "direct"
                            )?.value as JsonObject).propertyList.map {
                                val packageName = it.name
                                val jsonStringLiteral = it.value as JsonStringLiteral
                                val latestVersion = versions?.get(packageName)?.last()
                                if (latestVersion != (it.value as JsonStringLiteral).value) {
                                    holder.registerProblem(
                                        jsonStringLiteral.originalElement,
                                        "The package '${it.name}' has version $latestVersion available."
                                    )
                                }
                            }
                        } else if (projectType == "package") {
                            (root.findProperty("dependencies")?.value as JsonObject).propertyList.map {
                                val packageName = it.name
                                val versionRange: String = (it.value as JsonStringLiteral).value
                                val (lower, upper) = versionRange.split(Regex("\\s*<=\\s*v\\s*<\\s*")).let { matches ->
                                    Pair(matches[0], matches[1])
                                }

                                val latestVersion = versions?.get(packageName)?.last()
                                val isUpToDate = upper.split(".")[0].toInt() == latestVersion?.split(".")?.get(0)?.toInt()!! + 1
                                if (!isUpToDate) {
                                    holder.registerProblem(
                                        it.value?.originalElement!!,
                                        "The package '${it.name}' has version $latestVersion available."
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun packageVersions(project: Project): HashMap<String, List<String>>? {
    return try {
        runWithCheckCanceled {
            val response = HttpRequests.request("https://package.elm-lang.org/all-packages")
                .readString(ProgressManager.getInstance().progressIndicator)
            val typeToken = object : TypeToken<HashMap<String, List<String>>>() {}.type
            Gson().fromJson(response, typeToken)
        }
    } catch (e: IOException) {
        project.showBalloon("Could not reach Elm package server.", NotificationType.WARNING)
        null
    } catch (e: JsonSyntaxException) {
        project.showBalloon("Could not parser response from Elm package server.", NotificationType.WARNING)
        null
    }
}
