/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.rust.ide.refactoring.introduceParameter

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import org.elm.lang.core.psi.ElmFile

class ElmIntroduceParameterHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        if (file !is ElmFile) return

//        val exprs = findCandidateExpressionsToExtract(editor, file)
//            .filter { it.type !is TyUnit && it.type !is TyNever }
//
//        when (exprs.size) {
//            0 -> {
//                val message = RefactoringBundle.message(if (editor.selectionModel.hasSelection())
//                    "selected.block.should.represent.an.expression"
//                else
//                    "refactoring.introduce.selection.error"
//                )
//                showErrorMessageForExtractParameter(project, editor, message)
//            }
//            1 -> extractExpression(editor, exprs.single())
//            else -> showExpressionChooser(editor, exprs) {
//                extractExpression(editor, it)
//            }
//        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //this doesn't get called from the editor.
    }
}
