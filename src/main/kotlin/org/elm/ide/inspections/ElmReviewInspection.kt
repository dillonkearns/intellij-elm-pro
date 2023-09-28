package org.elm.ide.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.GlobalInspectionContextUtil
import com.intellij.codeInspection.reference.RefElement
import com.intellij.codeInspection.ui.InspectionToolPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.ancestorOrSelf
import org.elm.workspace.elmToolchain


class ElmReviewInspection : GlobalSimpleInspectionTool() {

    override fun inspectionStarted(
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        globalContext.putUserData(ANALYZED_FILES, ContainerUtil.newConcurrentSet())
    }

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        problemsHolder: ProblemsHolder,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        if (file !is ElmFile) return
        val analyzedFiles = globalContext.getUserData(ANALYZED_FILES) ?: return
        analyzedFiles.add(file)
    }

    override fun inspectionFinished(
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        if (globalContext !is GlobalInspectionContextImpl) return
        val analyzedFiles = globalContext.getUserData(ANALYZED_FILES) ?: return

        val project = manager.project
        val currentProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val toolWrapper = currentProfile.getInspectionTool(SHORT_NAME, project) ?: return

        while (true) {
            val disposable = project.messageBus.createDisposableOnAnyPsiChange()
                .also { Disposer.register(project, it) }
//            val cargoProjects = run {
//                val allProjects = project.cargoProjects.allProjects
//                if (allProjects.size == 1) {
//                    setOf(allProjects.first())
//                } else {
//                    analyzedFiles.mapNotNull { it.cargoProject }.toSet()
//                }
//            }
            val futures = listOf(project).map {
                ApplicationManager.getApplication().executeOnPooledThread<RsExternalLinterResult?> {
                    checkProjectLazily(it, disposable)?.value
                }
            }
            val annotationResults = futures.mapNotNull { it.get() }

            val exit = runReadAction {
                ProgressManager.checkCanceled()
                if (Disposer.isDisposed(disposable)) return@runReadAction false
//                if (annotationResults.size < cargoProjects.size) return@runReadAction true
                for (annotationResult in annotationResults) {
                    val problemDescriptors = getProblemDescriptors(project, analyzedFiles, annotationResult)
                    val presentation = globalContext.getPresentation(toolWrapper)
                    presentation.addProblemDescriptors(problemDescriptors, globalContext)
                }
                true
            }

            if (exit) break
        }
    }

    override fun getDisplayName(): String = "elm-review inspection"

    override fun getShortName(): String = SHORT_NAME

    companion object {
        const val SHORT_NAME: String = "ElmReviewInspection"

        private val ANALYZED_FILES: Key<MutableSet<ElmFile>> = Key.create("ANALYZED_FILES")

        private fun checkProjectLazily(
            cargoProject: Project,
            disposable: Disposable
        ): Lazy<RsExternalLinterResult?> = runReadAction {
            ElmReviewUtils.checkLazily(
                cargoProject.elmToolchain,
                cargoProject,
                disposable,
            )
        }

        private fun getProblemDescriptors(
            project: Project,
            analyzedFiles: Set<ElmFile>,
            annotationResult: RsExternalLinterResult
        ): List<ProblemDescriptor> = highlightsForFile(project, annotationResult).mapNotNull { (file,info) -> ProblemDescriptorUtil.toProblemDescriptor(file, info) }


        private fun InspectionToolPresentation.addProblemDescriptors(
            descriptors: List<ProblemDescriptor>,
            context: GlobalInspectionContext
        ) {
            if (descriptors.isEmpty()) return
            val problems = hashMapOf<RefElement, MutableList<ProblemDescriptor>>()

            for (descriptor in descriptors) {
                val element = descriptor.psiElement ?: continue
                val refElement = getProblemElement(element, context) ?: continue
                val elementProblems = problems.getOrPut(refElement) { mutableListOf() }
                elementProblems.add(descriptor)
            }

            for ((refElement, problemDescriptors) in problems) {
                val descriptions = problemDescriptors.toTypedArray<CommonProblemDescriptor>()
                addProblemElement(refElement, false, *descriptions)
            }
        }

        private fun getProblemElement(element: PsiElement, context: GlobalInspectionContext): RefElement? {
            val problemElement = element.ancestorOrSelf<ElmFile>()
            val refElement = context.refManager.getReference(problemElement)
            return if (refElement == null && problemElement != null) {
                GlobalInspectionContextUtil.retrieveRefElement(element, context)
            } else {
                refElement
            }
        }
    }
}