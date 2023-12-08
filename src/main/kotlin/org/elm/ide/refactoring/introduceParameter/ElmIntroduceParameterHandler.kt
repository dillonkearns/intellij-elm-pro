package org.elm.ide.refactoring.introduceParameter

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.elm.ide.refactoring.findCandidateExpressions
import org.elm.ide.refactoring.showExpressionChooser
import org.elm.lang.core.psi.ElmFile


class ElmIntroduceParameterHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        if (file !is ElmFile) return

//        val exprs = findCandidateExpressionsToExtract(editor, file)
//            .filter { it.type !is TyUnit && it.type !is TyNever }
        val exprs = findCandidateExpressions(editor, file)

        when (exprs.size) {
            0 -> {
                val message = RefactoringBundle.message(if (editor.selectionModel.hasSelection())
                    "selected.block.should.represent.an.expression"
                else
                    "refactoring.introduce.selection.error"
                )
                showErrorMessageForExtractParameter(project, editor, message)
            }
            1 -> extractExpression(editor, exprs.single())
            else -> showExpressionChooser(editor, exprs) {
                extractExpression(editor, it)
            }
        }
    }

    private fun showErrorMessageForExtractParameter(project: Project, editor: Editor, message: String) {
        val title = RefactoringBundle.message("introduce.parameter.title")
        val helpId = "refactoring.extractParameter"
        CommonRefactoringUtil.showErrorHint(project, editor, message, title, helpId)


    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //this doesn't get called from the editor.
    }
}
