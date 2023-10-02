/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.inspections

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.elm.ElmBundle
import org.elm.workspace.elmreview.Region

class ApplySuggestionFix(
    private val message: String,
    // read-only PSI-related properties need to be marked as safe: https://plugins.jetbrains.com/docs/intellij/code-intentions-preview.html#preparation-for-the-default-diff-preview
    @FileModifier.SafeFieldForPreview
    val patches: List<Pair<String, Region>>,
    @FileModifier.SafeFieldForPreview
    val doc: Document
) : IntentionAndQuickFixAction(), HighPriorityAction {
    override fun getFamilyName(): String = ElmBundle.message("intention.family.name.apply.suggested.replacement.made.by.external.linter")
    override fun availableInBatchMode(): Boolean {
        return false
    }

    override fun getName(): String {
        return message
    }

sealed class PatchLocation : Comparable<PatchLocation> {
    abstract fun startOffset(): Int
    override fun compareTo(other: PatchLocation): Int {
        return startOffset().compareTo((other as PatchLocation).startOffset())
    }
    class Range(val value: TextRange) : PatchLocation() {
        override fun startOffset(): Int {
            return value.startOffset
        }
    }
        class Point(val value: Int) : PatchLocation() {
            override fun startOffset(): Int {
                return value
            }
        }

        companion object {
            fun fromRegion(document: Document, region: Region): PatchLocation {
            return if (region.start?.line == region.end?.line && region.start?.column == region.end?.column) {
                Point(Region.toOffset(document, region.start?.line!!, region.start?.column!!)!!)
            } else {
                val textRange = region.toTextRange(document)
                if (textRange == null) {
                    error("region is null")
                } else {
                    Range(textRange)
                }
            }
        }
        }
    }


    override fun applyFix(project: Project, file: PsiFile?, editor: Editor?) {
        val document = editor?.document ?: file?.viewProvider?.document ?: return
        patches.map { (replacement, region) -> Pair(replacement, PatchLocation.fromRegion(document, region)) }
        .sortedByDescending { (replacement, patchRegion) ->
            patchRegion
        }.forEach { (replacement, patchLocation) ->
            when (patchLocation) {
                is PatchLocation.Range -> {
                    document.replaceString(patchLocation.value.startOffset, patchLocation.value.endOffset, replacement)
                }
                is PatchLocation.Point -> {
                    document.insertString(patchLocation.value, replacement)
                }
            }
        }
        if (ApplicationManager.getApplication().isWriteAccessAllowed && editor != null) {
            FileDocumentManager.getInstance().saveDocument(editor.document)
        }
    }

    override fun getText(): String = ElmBundle.message("intention.name.external.linter", message)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApplySuggestionFix

        if (message != other.message) return false
        if (patches.size != other.patches.size) return false
        for (i in patches.indices) {
            if (patches[i] != other.patches[i]) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        patches.forEach { (replacement, textRange) ->
            result = 31 * result + replacement.hashCode()
            result = 31 * result + textRange.hashCode()
        }
        return result
    }
}
