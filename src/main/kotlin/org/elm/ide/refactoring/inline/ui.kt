package org.elm.ide.refactoring.inline

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.inline.InlineOptionsWithSearchSettingsDialog
import org.elm.lang.core.psi.elements.ElmFunctionDeclarationLeft
import org.elm.lang.core.resolve.reference.ElmReference

class ElmInlineFunctionDialog(
        private val function: ElmFunctionDeclarationLeft,
        private val refElement: ElmReference?,
        private val allowInlineThisOnly: Boolean,
        project: Project = function.project,
        private val occurrencesNumber: Int =
                initOccurrencesNumber(function)

) : InlineOptionsWithSearchSettingsDialog(project, true, function) {

    public override fun doAction() {
        invokeRefactoring(ElmInlineFunctionProcessor(
                project,
                function,
                refElement,
                isInlineThisOnly,
                !isInlineThisOnly && !isKeepTheDeclaration
        ))
    }

    fun shouldBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

    override fun getBorderTitle(): String =
            RefactoringBundle.message("inline.method.border.title")

    override fun getNameLabelText(): String {
        val occurrencesString =
                if (occurrencesNumber < 0) {
                    ""
                } else {
                    buildString {
                        append("has $occurrencesNumber occurrence")
                        if (occurrencesNumber != 1) append("s")
                    }
                }
        return RefactoringBundle.message(
                "inline.method.method.label",
                function.name,
                occurrencesString)
    }

    override fun getInlineAllText(): String {
        val text =
                if (function.isWritable && !allowInlineThisOnly) {
                    "all.invocations.and.remove.the.method"
                } else {
                    "all.invocations.in.project"
                }
        return RefactoringBundle.message(text)
    }

    override fun isInlineThis(): Boolean = false

    override fun getInlineThisText(): String =
            RefactoringBundle.message("this.invocation.only.and.keep.the.method")

    override fun getKeepTheDeclarationText(): String =
            if (function.isWritable) {
                "Inline all references and keep the function"
            } else {
                super.getKeepTheDeclarationText()
            }

    override fun getHelpId(): String =
            "refactoring.inlineMethod"


    private var searchInCommentsAndStrings = true
    private var searchInTextOccurrences = true

    override fun isSearchInCommentsAndStrings() =
            searchInCommentsAndStrings

    override fun saveSearchInCommentsAndStrings(searchInComments: Boolean) {
        searchInCommentsAndStrings = searchInComments
    }

    override fun isSearchForTextOccurrences(): Boolean =
            searchInTextOccurrences

    override fun saveSearchInTextOccurrences(searchInTextOccurrences: Boolean) {
        this.searchInTextOccurrences = searchInTextOccurrences
    }

    init {
        title = borderTitle
        myInvokedOnReference = refElement != null

        setPreviewResults(true)
        setDoNotAskOption(object : DoNotAskOption {
            override fun isToBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

            override fun setToBeShown(value: Boolean, exitCode: Int) {
                EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog = value
            }

            override fun canBeHidden() = true

            override fun shouldSaveOptionsOnCancel() = false

            override fun getDoNotShowMessage() = "Do not show in future"
        })

        init()
    }
}
