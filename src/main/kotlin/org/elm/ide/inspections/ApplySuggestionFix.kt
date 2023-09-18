/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.inspections

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.elm.ElmBundle

class ApplySuggestionFix(
    private val message: String,
    private val replacement: String,
    // read-only PSI-related properties need to be marked as safe: https://plugins.jetbrains.com/docs/intellij/code-intentions-preview.html#preparation-for-the-default-diff-preview
    @FileModifier.SafeFieldForPreview
    val textRange: TextRange
) : IntentionAndQuickFixAction() {
    override fun getFamilyName(): String = ElmBundle.message("intention.family.name.apply.suggested.replacement.made.by.external.linter")
    override fun getName(): String {
        return message
    }

    override fun applyFix(project: Project, file: PsiFile?, editor: Editor?) {
        val document = editor?.document ?: file?.viewProvider?.document ?: return
        document.replaceString(textRange.startOffset, textRange.endOffset, replacement)
    }

    override fun getText(): String = ElmBundle.message("intention.name.external.linter", message)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApplySuggestionFix

        if (message != other.message) return false
        if (replacement != other.replacement) return false
        if (textRange != other.textRange) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + replacement.hashCode()
        result = 31 * result + (textRange.hashCode())
        return result
    }
}
