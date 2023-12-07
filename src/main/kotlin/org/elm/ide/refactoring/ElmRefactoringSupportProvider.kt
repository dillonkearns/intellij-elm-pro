package org.elm.ide.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import org.elm.ide.refactoring.extractFunction.ElmExtractFunctionHandler
import org.rust.ide.refactoring.introduceParameter.ElmIntroduceParameterHandler


class ElmRefactoringSupportProvider : RefactoringSupportProvider() {

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        // TODO [kl] eventually we will want to be more selective
        return true
    }

    override fun getIntroduceVariableHandler(): RefactoringActionHandler? {
        return ElmIntroduceVariableHandler()
    }

    override fun getIntroduceParameterHandler(): RefactoringActionHandler = ElmIntroduceParameterHandler()


    override fun getExtractMethodHandler(): RefactoringActionHandler = ElmExtractFunctionHandler()
}