package org.elm.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.elm.ide.refactoring.uniqueValueName
import org.elm.lang.core.psi.ElmExpressionTag
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.ancestorOrSelf
import org.elm.lang.core.psi.elements.ElmValueQID
import org.elm.lang.core.types.TyFunction
import org.elm.lang.core.types.findInference

class WrapWithLambdaIntention : ElmAtCaretIntentionActionBase<WrapWithLambdaIntention.Context>() {
    data class Context(
        val expression: ElmValueQID,
    )

    override fun getText() = "Wrap a function with a lambda"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? =
        (element.findInference()?.ty as? TyFunction)?.let {
            element.ancestorOrSelf<ElmValueQID>()?.let { qid -> Context(qid) }
        }

    override fun invoke(project: Project, editor: Editor, context: Context) {
        val factory = ElmPsiFactory(project)

        val paramName = uniqueValueName(context.expression.ancestorOrSelf<ElmExpressionTag>()!!, "a")
        context.expression.parent.replace(factory.createLambda("\\$paramName -> ${context.expression.text} $paramName"))
    }
}
