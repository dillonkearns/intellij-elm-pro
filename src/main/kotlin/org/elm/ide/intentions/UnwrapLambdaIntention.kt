package org.elm.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.ancestorOrSelf
import org.elm.lang.core.psi.elementType
import org.elm.lang.core.psi.elements.ElmAnonymousFunctionExpr
import org.elm.lang.core.psi.elements.ElmFunctionCallExpr

class UnwrapLambdaIntention : ElmAtCaretIntentionActionBase<UnwrapLambdaIntention.Context>() {
    data class Context(
        val expression: ElmAnonymousFunctionExpr,
    )

    override fun getText() = "Unwrap lambda parameter"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        return if (element.elementType.toString() == "BACKSLASH") {
            // TODO handle case when there are multiple parameters in the lambda (don't show intention?)
            // TODO don't show intention if form is not just of a single function invocation within lambda (like a let form example?)
             element.ancestorOrSelf<ElmAnonymousFunctionExpr>()?.let { Context(it) }
        } else {
            null
        }
    }

    override fun invoke(project: Project, editor: Editor, context: Context) {
        val factory = ElmPsiFactory(project)
        val inner = context.expression.expression as ElmFunctionCallExpr
        // TODO handle case where it is a pipeline
        context.expression.replace(factory.createExpression(listOf(inner.target).plus(inner.arguments.toList().dropLast(1)).joinToString(" ") { it.text }))
    }
}
