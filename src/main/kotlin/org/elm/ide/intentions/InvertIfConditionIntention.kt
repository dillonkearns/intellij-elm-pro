package org.elm.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.elm.lang.core.psi.ElmExpressionTag
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.ElmTypes
import org.elm.lang.core.psi.ancestorOrSelf
import org.elm.lang.core.psi.elements.ElmFunctionCallExpr
import org.elm.lang.core.psi.elements.ElmIfElseExpr

class InvertIfConditionIntention : ElmAtCaretIntentionActionBase<InvertIfConditionIntention.Context>() {
    data class Context(
        val condition: ElmExpressionTag,
        val thenBranch: ElmExpressionTag,
        val elseBranch: ElmExpressionTag
    )

    override fun getText() = "Invert if condition"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        return if (element.node.elementType == ElmTypes.IF) {
            element.ancestorOrSelf<ElmIfElseExpr>()?.expressionList?.let {
                if (it.size == 3) {
                    Context(it[0], it[1], it[2])
                } else {
                    null
                }
            }
        } else {
            null
        }
    }

    override fun invoke(project: Project, editor: Editor, context: Context) {
        val factory = ElmPsiFactory(project)
        val negatedExpression = negatedConditionExpression(context.condition)
        if (negatedExpression != null) {
            context.condition.replace(negatedExpression)
        } else {
            context.condition.replace(factory.createExpression("not (${context.condition.text})"))
        }
        val originalThen = factory.createExpression(context.thenBranch.text)
        val originalElse = factory.createExpression(context.elseBranch.text)
        context.thenBranch.replace(originalElse)
        context.elseBranch.replace(originalThen)
    }

    fun negatedConditionExpression(condition: ElmExpressionTag): ElmExpressionTag? {
        return if (condition is ElmFunctionCallExpr && condition.target.text == "not") {
            condition.arguments.first()
        } else {
            null
        }
    }
}
