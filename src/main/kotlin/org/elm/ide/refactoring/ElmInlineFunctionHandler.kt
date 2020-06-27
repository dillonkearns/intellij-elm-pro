package org.elm.ide.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.elm.ide.refactoring.inline.ElmInlineFunctionDialog
import org.elm.lang.core.ElmLanguage
import org.elm.lang.core.psi.elements.ElmFunctionDeclarationLeft
import org.elm.lang.core.resolve.reference.ElmReference

class ElmInlineFunctionHandler : InlineActionHandler() {
    override fun isEnabledOnElement(element: PsiElement): Boolean = canInlineElement(element)

    override fun isEnabledOnElement(element: PsiElement, editor: Editor?): Boolean =
            isEnabledOnElement(element)

    override fun inlineElement(project: Project, editor: Editor, element: PsiElement) {
        val function = element as ElmFunctionDeclarationLeft
//                ElmFunctionCallExpr


        val reference = TargetElementUtil.findReference(editor, editor.caretModel.offset)

//        if (ElmInlineFunctionProcessor.doesFunctionHaveMultipleReturns(function)) {
//            errorHint(project, editor, "cannot inline function with more than one exit points")
//            return
//        }
//
//        var allowInlineThisOnly = false
//        if (ElmInlineFunctionProcessor.isFunctionRecursive(function)) {
//            if (reference != null) {
//                allowInlineThisOnly = true
//            } else {
//                errorHint(project, editor, "cannot inline function with recursive calls")
//                return
//            }
//        }
//
//        if (reference != null && RsInlineFunctionProcessor.checkIfLoopCondition(function, reference.element)) {
//            errorHint(project, editor, "cannot inline multiline function into \"while\" loop condition")
//            return
//        }
//
//        if (function.block == null) {
//            errorHint(project, editor,"Cannot inline an empty function")
//            return
//        }

        val allowInlineThisOnly = true
        val dialog = ElmInlineFunctionDialog(function, reference as ElmReference?, allowInlineThisOnly)
        if (!ApplicationManager.getApplication().isUnitTestMode && dialog.shouldBeShown()) {
            dialog.show()
            if (!dialog.isOK) {
                val statusBar = WindowManager.getInstance().getStatusBar(function.project)
                statusBar?.info = RefactoringBundle.message("press.escape.to.remove.the.highlighting")
            }
        } else {
            dialog.doAction()
        }
    }

    override fun isEnabledForLanguage(l: Language?): Boolean = l == ElmLanguage

    override fun canInlineElementInEditor(element: PsiElement, editor: Editor?): Boolean = canInlineElement(element)

    override fun canInlineElement(element: PsiElement): Boolean =
            true
//            element is RsFunction && element.navigationElement is RsFunction

    private fun errorHint(project: Project, editor: Editor, message: String) {
        CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                message,
                "inline.method.title",
                "refactoring.inlineMethod")
    }
}
