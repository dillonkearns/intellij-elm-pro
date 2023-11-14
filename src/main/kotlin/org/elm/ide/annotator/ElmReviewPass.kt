/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.annotator

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.elm.ide.inspections.ElmReviewResult
import org.elm.ide.inspections.highlightsForFile
import org.elm.ide.settings.experimentalFlags
import org.elm.lang.core.psi.ElmFile
import org.elm.workspace.ElmReviewService
import org.elm.workspace.elmreview.ElmReviewError
import java.nio.file.Path


class ElmReviewPass(
    private val factory: ElmReviewPassFactory,
    private val file: PsiFile,
    private val editor: Editor
) : TextEditorHighlightingPass(file.project, editor.document), DumbAware {
    private var highlights: List<Pair<PsiFile, HighlightInfo>> = emptyList()
    @Volatile
    private var annotationInfo: ElmReviewResult? = null
    private val annotationResult: ElmReviewResult? get() = annotationInfo
    @Volatile
    private var disposable: Disposable = myProject

    override fun doCollectInformation(progress: ProgressIndicator) {
        if (file !is ElmFile || !isAnnotationPassEnabled) return
        val pathToListenFor: Path = file.elmProject?.projectDirPath ?: return

        val service = editor.project?.getService(ElmReviewService::class.java)
        PsiManager.getInstance(editor.project!!).addPsiTreeChangeListener(object : PsiTreeChangeListener {
            override fun beforeChildAddition(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun beforeChildMovement(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun beforePropertyChange(event: PsiTreeChangeEvent) {

                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun childAdded(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun childRemoved(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun childReplaced(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun childrenChanged(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun childMoved(event: PsiTreeChangeEvent) {
                if (event.file?.virtualFile?.path == file.virtualFile?.path) {
                    updateHighlighting(emptyList(), editor.project!!, pathToListenFor)
                }
            }

            override fun propertyChanged(event: PsiTreeChangeEvent) {
            }
        }, disposable)

        service?.start(pathToListenFor)
        if (service != null) {
            updateHighlighting(service.messagesForCurrentProject(pathToListenFor), editor.project!!, pathToListenFor)
        }

        editor.project?.messageBus?.connect()?.apply {
            subscribe(ElmReviewService.ELM_REVIEW_WATCH_TOPIC, object : ElmReviewService.ElmReviewWatchListener {
                override fun update(baseDirPath: Path, messages: List<ElmReviewError>) {
                    if (pathToListenFor == baseDirPath) {
                        updateHighlighting(messages, editor.project!!, pathToListenFor)
                    }
                }
            }
            )

        }
    }

    private fun updateHighlighting(
        messages: List<ElmReviewError>,
        project: Project,
        basePath: Path
    ) {
        annotationInfo = ElmReviewResult(messages, basePath, 0)
        ApplicationManager.getApplication().runReadAction {
            highlights = highlightsForFile(project, basePath, annotationInfo!!)
            doFinish(highlights)
        }
    }

    override fun doApplyInformationToEditor() {
        if (file !is ElmFile) return

        if (annotationInfo == null || !isAnnotationPassEnabled) {
            disposable = myProject
            doFinish(emptyList())
            return
        }
        class WatchModeUpdate(messages: List<ElmReviewError>) : Update(messages) {
            override fun setRejected() {
                super.setRejected()
                doFinish(highlights)
            }

            override fun run() {
                BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposable, Runnable {
                    val annotationResult = annotationResult ?: return@Runnable
                    runReadAction {
                        doApply(annotationResult, annotationResult.basePath)
                        doFinish(highlights)
                    }
                })
            }
        }


        editor.project!!.messageBus.connect().apply {
            subscribe(ElmReviewService.ELM_REVIEW_WATCH_TOPIC, object : ElmReviewService.ElmReviewWatchListener {
                override fun update(baseDirPath: Path, messages: List<ElmReviewError>) {
                    factory.scheduleExternalActivity(WatchModeUpdate(messages))
                }
            }
            )
        }
    }

    private fun doApply(annotationResult: ElmReviewResult, basePath: Path) {
        if (file !is ElmFile || !file.isValid) return
        try {
            ApplicationManager.getApplication().runReadAction {
                highlights = highlightsForFile(file.project, basePath, annotationResult)
            }
        } catch (t: Throwable) {
            if (t is ProcessCanceledException) throw t
            LOG.error(t)
        }
    }

    private fun doFinish(highlights: List<Pair<PsiFile, HighlightInfo>>) {
        invokeLater(ModalityState.stateForComponent(editor.component)) {
            val groupedHighlights: List<HighlightInfo> =
                highlights.groupBy { it.first.virtualFile.path }[file.virtualFile.path]?.map { it.second }.orEmpty()
            UpdateHighlightersUtil.setHighlightersToEditor(
                myProject,
                document,
                0,
                file.textLength,
                groupedHighlights,
                colorsScheme,
                id
            )
            DaemonCodeAnalyzerEx.getInstanceEx(myProject).fileStatusMap.markFileUpToDate(document, id)
        }
    }

    private val isAnnotationPassEnabled: Boolean
        get() = myProject.experimentalFlags.elmReviewOnTheFlyEnabled

    companion object {
        private val LOG: Logger = logger<ElmReviewPass>()
    }
}

class ElmReviewPassFactory(
    project: Project,
    registrar: TextEditorHighlightingPassRegistrar
) : DirtyScopeTrackingHighlightingPassFactory {
    private val myPassId: Int = registrar.registerTextEditorHighlightingPass(
        this,
        null,
        null,
        false,
        -1
    )

    private val elmReviewQueue = MergingUpdateQueue(
        "ElmReviewQueue",
        TIME_SPAN,
        true,
        MergingUpdateQueue.ANY_COMPONENT,
        project,
        null,
        false
    )

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        FileStatusMap.getDirtyTextRange(editor.document, file, passId) ?: return null
        return ElmReviewPass(this, file, editor)
    }

    override fun getPassId(): Int = myPassId

    fun scheduleExternalActivity(update: Update) = elmReviewQueue.queue(update)

    companion object {
        private const val TIME_SPAN: Int = 300
    }
}
