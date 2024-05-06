/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.inspections

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.elm.workspace.elmreview.ElmReviewError
import java.nio.file.Path

fun highlightsForFile(
//    file: ElmFile,
    project: Project,
    basePath: Path,
    annotationResult: ElmReviewResult,
//    minApplicability: Applicability
): List<Pair<PsiFile, HighlightInfo>> {
//    val cargoPackageOrigin = file.containingCargoPackage?.origin
//    if (cargoPackageOrigin != PackageOrigin.WORKSPACE) return

//    val project = file.project
//    project.
//    FileDocumentManager.getInstance().
//    val doc = file.viewProvider.document
//        ?: error("Can't find document for $file in external linter")

    return annotationResult.messages.mapNotNull { message ->
//        if (!file.originalFile.virtualFile.canonicalPath?.contains(message.path.toString())!!) return@mapNotNull null

//        val fileWithError: VirtualFile = FilenameIndex.getVirtualFilesByName(message.path!!, GlobalSearchScope.allScope(project)).first()
        // `path` is null for global messages, which usually indicate that elm-review is unable to run. Might make sense to short-circuit and return an empty list here instead
        if (message.path == null) return@mapNotNull null
        val fileWithError: VirtualFile = LocalFileSystem.getInstance().findFileByPath(Path.of(basePath.toString(), message.path).toString())!!
        val psiFile = PsiManager.getInstance(project).findFile(fileWithError)


//        ElmFile.fromVirtualFile
//        val doc = ( fileWithError as ElmFile ).viewProvider.document
//            ?: error("Can't find document for $file in external linter")
        val doc = fileWithError.findDocument()
            ?: error("Can't find document for $fileWithError in external linter")
        if (psiFile == null) { return emptyList() }

        // We can't control what messages cargo generates, so we can't test them well.
        // Let's use the special message for tests to distinguish annotation from external linter
        val severity = if (message.rule!!.startsWith("NoUnused.")) {
            HighlightInfoType.UNUSED_SYMBOL
        } else if (message.rule.equals("NoDeprecated")) {
            HighlightInfoType.DEPRECATED
        } else {
            HighlightInfoType.WARNING
        }
        val tooltipHtml = "<h2><a href=\"${message.ruleLink}\">elm-review ${message.rule}</a></h2><p>${message.message}</p><br /><p>${message.details?.joinToString("\n")}</p>"
        val highlightBuilder = HighlightInfo.newHighlightInfo(severity)
            .severity(HighlightSeverity.WARNING)
            .description(message.message!!)
            .escapedToolTip(tooltipHtml)
            .needsUpdateOnTyping(true)
        message.region?.toTextRange(doc)?.let { textRange ->
            if (textRange.startOffset < 0 || textRange.startOffset > textRange.endOffset) {
                return emptyList()
            }
            highlightBuilder.range(textRange)
        } ?: return emptyList()
        // if we get null from the TextRange, it means the document has changed since we got the elm-review results,
        // so we should skip this highlighting pass and wait for it to re-run upon receiving fresh elm-review --watch results


        message.fix.orEmpty()
            .let { fixes ->
                message.region?.toTextRange(doc)?.let { textRange ->
                    val fixPatches = fixes.map { fix ->
                        Pair(fix.string, fix.range)
                    }
                    val options = null
                    val displayName = "elm-review"
                    val key = HighlightDisplayKey.findOrRegister(RUST_EXTERNAL_LINTER_ID, displayName)

                    if (fixPatches.isNotEmpty()) {
                        val action = ApplySuggestionFix(
                            "Apply elm-review ${message.rule} fix",
                            fixPatches,
                            doc
                        )
                        highlightBuilder.registerFix(action, options, displayName, textRange, key)
                    }
                }
            }

        Pair(psiFile, highlightBuilder.create()!!)
    }
}


private const val RUST_EXTERNAL_LINTER_ID: String = "ElmReviewOptions"

class ElmReviewResult(val messages: List<ElmReviewError>, val basePath: Path, val executionTime: Long) {
    companion object {
    }
}
