/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.inspections

//import org.rust.RsBundle
//import org.rust.cargo.project.workspace.PackageOrigin
//import org.rust.cargo.toolchain.RsToolchainBase
//import org.rust.cargo.toolchain.impl.*
//import org.rust.cargo.toolchain.tools.CargoCheckArgs
//import org.rust.cargo.toolchain.tools.cargoOrWrapper
//import org.rust.ide.annotator.RsExternalLinterFilteredMessage.Companion.filterMessage
//import org.rust.ide.annotator.RsExternalLinterUtils.TEST_MESSAGE
//import org.rust.ide.inspections.lints.RsLint
//import org.rust.ide.inspections.lints.RsSuppressQuickFix.Companion.createSuppressFixes
//import org.rust.ide.status.ElmExternalFormatAction
//import org.rust.lang.RsConstants
//import org.rust.lang.core.psi.RsFile
//import org.rust.lang.core.psi.ext.containingCargoPackage
//import org.rust.openapiext.JsonUtils.tryParseJsonObject
//import org.rust.stdext.capitalized
//import org.rust.stdext.unwrapOrElse
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.impl.AnyPsiChangeListener
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.DocumentUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.messages.MessageBus
import org.elm.ElmBundle
import org.elm.ide.status.ElmReviewWidget
import org.elm.lang.core.psi.ElmFile
import org.elm.openapiext.ProjectCache
import org.elm.openapiext.checkReadAccessAllowed
import org.elm.workspace.ElmProject
import org.elm.workspace.ElmToolchain
import org.elm.workspace.elmWorkspace
import org.elm.workspace.elmreview.ElmReviewError
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

object ElmReviewUtils {
    private val LOG: Logger = logger<ElmReviewUtils>()
    const val TEST_MESSAGE: String = "ElmReview"

    /**
     * Returns (and caches if absent) lazily computed messages from external linter.
     *
     * Note: before applying this result you need to check that the PSI modification stamp of current project has not
     * changed after calling this method.
     *
     * @see PsiModificationTracker.MODIFICATION_COUNT
     */
    fun checkLazily(
        toolchain: ElmToolchain,
        project: Project,
        owner: Disposable,
//        workingDirectory: Path,
//        args: CargoCheckArgs
    ): Lazy<RsExternalLinterResult?> {
        checkReadAccessAllowed()
        val workingDirectory = Path.of(project.projectFilePath)
        return externalLinterLazyResultCache.getOrPut(project, Key(workingDirectory)) {
            // We want to run external linter in background thread and *without* read action.
            // And also we want to cache result of external linter because it is cargo package-global,
            // but annotator can be invoked separately for each file.
            // With `CachedValuesManager` our cached value should be invalidated on any PSI change.
            // Important note about this cache is that modification count will be stored AFTER computation
            // of a value. If we aren't in read action, PSI can be modified during computation of the value
            // and so an outdated value will be cached. So we can't use the cache without read action.
            // What we really want:
            // 1. Store current PSI modification count;
            // 2. Run external linter and retrieve results (in background thread and without read action);
            // 3. Try to cache result use modification count stored in (1). Result can be already outdated here.
            // We get such behavior by storing `Lazy` computation to the cache. Cache result is created in read
            // action, so it will be stored within correct PSI modification count. Then, we will retrieve the value
            // from `Lazy` in a background thread. The value will be computed or retrieved from the already computed
            // `Lazy` value.
            lazy {
                // This code will be executed out of read action in background thread
                // TODO restore unit test mode
//                if (!isUnitTestMode) checkReadAccessNotAllowed()
                checkWrapped(toolchain, project, owner, workingDirectory)
            }
        }
    }
    private fun saveAllDocuments() = FileDocumentManager.getInstance().saveAllDocuments()

    private fun checkWrapped(
//        toolchain: RsToolchainBase,
        toolchain: ElmToolchain,
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
//        args: CargoCheckArgs
    ): RsExternalLinterResult? {
        val widget = WriteAction.computeAndWait<ElmReviewWidget?, Throwable> {
//            saveAllDocumentsAsTheyAre()
            saveAllDocuments()
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            statusBar?.getWidget(ElmReviewWidget.ID) as? ElmReviewWidget
        }

        val future = CompletableFuture<RsExternalLinterResult?>()
        val task = object : Task.Backgroundable(project, ElmBundle.message("progress.title.analyzing.project.with"), true) {

            override fun run(indicator: ProgressIndicator) {
                widget?.inProgress = true
                future.complete(check(toolchain, project, owner, workingDirectory ))
            }

            override fun onFinished() {
                widget?.inProgress = false
            }
        }
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, EmptyProgressIndicator())
        return future.get()
    }

    private fun check(
        toolchain: ElmToolchain,
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
//        args: CargoCheckArgs
    ): RsExternalLinterResult? {
        ProgressManager.checkCanceled()
        val started = Instant.now()
//        val output = toolchain
//            .cargoOrWrapper(workingDirectory)
//            .checkProject(project, owner, args)
//            .unwrapOrElse { e ->
//                LOG.error(e)
//                return null
//            }

        val elmProject: ElmProject = project.elmWorkspace.allProjects.first() // TODO which project should be chosen?
        val output: List<ElmReviewError> = toolchain
            .elmReviewCLI!!.runReviewForInspection(project, elmProject, toolchain.elmCLI)
//            .checkProject(project, owner, args)
//            .unwrapOrElse { e ->
//                LOG.error(e)
//                return null
//            }
        val finish = Instant.now()
        ProgressManager.checkCanceled()
//        if (output.isCancelled) return null
        return RsExternalLinterResult(output, Duration.between(started, finish).toMillis())
    }

    private data class Key(
//        val toolchain: RsToolchainBase,
        val workingDirectory: Path,
//        val args: CargoCheckArgs
    )

    private val externalLinterLazyResultCache =
        ProjectCache<Key, Lazy<RsExternalLinterResult?>>("externalLinterLazyResultCache") {
            PsiModificationTracker.MODIFICATION_COUNT
        }
}

fun MessageBus.createDisposableOnAnyPsiChange(): Disposable {
    val disposable = Disposer.newDisposable("Dispose on PSI change")
    connect(disposable).subscribe(
        PsiManagerImpl.ANY_PSI_CHANGE_TOPIC,
        object : AnyPsiChangeListener {
            override fun beforePsiChanged(isPhysical: Boolean) {
                if (isPhysical) {
                    Disposer.dispose(disposable)
                }
            }
        }
    )
    return disposable
}

fun highlightsForFile(
    file: ElmFile,
    annotationResult: RsExternalLinterResult,
//    minApplicability: Applicability
): List<HighlightInfo?> {
//    val cargoPackageOrigin = file.containingCargoPackage?.origin
//    if (cargoPackageOrigin != PackageOrigin.WORKSPACE) return

    val doc = file.viewProvider.document
        ?: error("Can't find document for $file in external linter")

    return annotationResult.messages.mapNotNull { message ->
        if (!file.originalFile.virtualFile.canonicalPath?.contains(message.path.toString())!!) return@mapNotNull null
        // We can't control what messages cargo generates, so we can't test them well.
        // Let's use the special message for tests to distinguish annotation from external linter
        val highlightBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
//            .severity(message.severity)
//            .description(if (isUnitTestMode) TEST_MESSAGE else message.message)
            .description(message.message!!)
            .escapedToolTip(message.html!!)
            .range(message.region.let {
                TextRange(DocumentUtil.calculateOffset(doc, it!!.start!!.line, it.start!!.column, 4)
                    ,DocumentUtil.calculateOffset(doc, it.end!!.line, it.end!!.column, 4)
                )
            })

            .needsUpdateOnTyping(true)

        // TODO add quick fixes
//        message.quickFixes
////            .singleOrNull { it.applicability <= minApplicability }
//            ?.let { fix ->
//                val element = fix.startElement ?: fix.endElement
//                val lint = message.lint
//                val actions =  if (element != null && lint != null) createSuppressFixes(element, lint) else emptyArray()
//                val options = convertBatchToSuppressIntentionActions(actions).toList()
//                val displayName = "elm-review"
//                val key = HighlightDisplayKey.findOrRegister(RUST_EXTERNAL_LINTER_ID, displayName)
//                highlightBuilder.registerFix(fix, options, displayName, fix.textRange, key)
//            }

        highlightBuilder.create()
    }
}

private const val RUST_EXTERNAL_LINTER_ID: String = "ElmReviewOptions"

class RsExternalLinterResult(val messages: List<ElmReviewError>, val executionTime: Long) {
    companion object {
    }
}

//private data class RsExternalLinterFilteredMessage(
//    val severity: HighlightSeverity,
//    val textRange: TextRange,
//    @Nls val message: String,
//    @Nls val htmlTooltip: String,
////    val lint: RsLint.ExternalLinterLint?,
//    val quickFixes: List<ApplySuggestionFix>
//) {
//    companion object {
//        fun filterMessage(file: PsiFile, document: Document, message: RustcMessage): RsExternalLinterFilteredMessage? {
//            if (message.message.startsWith("aborting due to") || message.message.startsWith("cannot continue")) {
//                return null
//            }
//
//            val severity = when (message.level) {
//                "error" -> HighlightSeverity.ERROR
//                "warning" -> HighlightSeverity.WEAK_WARNING
//                else -> HighlightSeverity.INFORMATION
//            }
//
//            // Some error messages are global, and we *could* show then atop of the editor,
//            // but they look rather ugly, so just skip them.
//            val span = message.mainSpan ?: return null
//
//            val syntaxErrors = listOf("expected pattern", "unexpected token")
//            if (syntaxErrors.any { it in span.label.orEmpty() || it in message.message }) {
//                return null
//            }
//
//            val spanFilePath = PathUtil.toSystemIndependentName(span.file_name)
//            if (!file.virtualFile.path.endsWith(spanFilePath)) return null
//
//            val textRange = span.toTextRange(document) ?: return null
//
//            @NlsSafe val tooltip = buildString {
//                append(formatMessage(StringEscapeUtils.escapeHtml(message.message)).escapeUrls())
//                val code = message.code.formatAsLink()
//                if (code != null) {
//                    append(" [$code]")
//                }
//
//                with(mutableListOf<String>()) {
//                    if (span.label != null && !message.message.startsWith(span.label)) {
//                        add(StringEscapeUtils.escapeHtml(span.label))
//                    }
//
//                    message.children
//                        .filter { it.message.isNotBlank() }
//                        .map { "${it.level.capitalized()}: ${StringEscapeUtils.escapeHtml(it.message)}" }
//                        .forEach { add(it) }
//
//                    append(joinToString(prefix = "<br>", separator = "<br>") { formatMessage(it) }.escapeUrls())
//                }
//            }
//
//            return RsExternalLinterFilteredMessage(
//                severity,
//                textRange,
//                message.message.capitalized(),
//                tooltip,
//                message.code?.code?.let { RsLint.ExternalLinterLint(it) },
//                message.collectQuickFixes(file, document)
//            )
//        }
//    }
//}

private fun String.escapeUrls(): String = replace(URL_REGEX) { url -> "<a href='${url.value}'>${url.value}</a>" }

//fun RustcSpan.isValid(): Boolean =
//    line_end > line_start || (line_end == line_start && column_end >= column_start)
//
private val ERROR_REGEX: Regex = """E\d{4}""".toRegex()
private val URL_REGEX: Regex = URLUtil.URL_PATTERN.toRegex()
//
//private fun ErrorCode?.formatAsLink(): String? {
//    if (this?.code?.matches(ERROR_REGEX) != true) return null
//    return "<a href=\"${RsConstants.ERROR_INDEX_URL}#$code\">$code</a>"
//}
//
//private fun RustcMessage.collectQuickFixes(file: PsiFile, document: Document): List<ApplySuggestionFix> {
//    val quickFixes = mutableListOf<ApplySuggestionFix>()
//
//    fun go(message: RustcMessage) {
//        val span = message.spans.singleOrNull { it.is_primary && it.isValid() }
//        createQuickFix(file, document, span, message.message)?.let { quickFixes.add(it) }
//        message.children.forEach(::go)
//    }
//
//    go(this)
//    return quickFixes
//}
//
//private fun createQuickFix(file: PsiFile, document: Document, span: RustcSpan?, message: String): ApplySuggestionFix? {
//    if (span?.suggested_replacement == null || span.suggestion_applicability == null) return null
//    val textRange = span.toTextRange(document) ?: return null
//    val endElement = file.findElementAt(textRange.endOffset - 1) ?: return null
//    val startElement = file.findElementAt(textRange.startOffset) ?: endElement
//    return ApplySuggestionFix(
//        message,
//        span.suggested_replacement,
//        span.suggestion_applicability,
//        startElement,
//        endElement,
//        textRange
//    )
//}

private fun formatMessage(message: String): String {
    data class Group(val isList: Boolean, val lines: ArrayList<String>)

    val (lastGroup, groups) =
        message.split("\n").fold(
            Pair(null as Group?, ArrayList<Group>())
        ) { (group: Group?, acc: ArrayList<Group>), lineWithPrefix ->
            val (isListItem, line) = if (lineWithPrefix.startsWith("-")) {
                true to lineWithPrefix.substring(2)
            } else {
                false to lineWithPrefix
            }

            when {
                group == null -> Pair(Group(isListItem, arrayListOf(line)), acc)
                group.isList == isListItem -> {
                    group.lines.add(line)
                    Pair(group, acc)
                }
                else -> {
                    acc.add(group)
                    Pair(Group(isListItem, arrayListOf(line)), acc)
                }
            }
        }
    if (lastGroup != null && lastGroup.lines.isNotEmpty()) groups.add(lastGroup)

    return groups.joinToString {
        if (it.isList) "<ul>${it.lines.joinToString("<li>", "<li>")}</ul>"
        else it.lines.joinToString("<br>")
    }
}
