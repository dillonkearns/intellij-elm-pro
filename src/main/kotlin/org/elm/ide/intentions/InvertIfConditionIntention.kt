package org.elm.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.elm.lang.core.psi.ElmExpressionTag
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.ElmTypes
import org.elm.lang.core.psi.ancestorOrSelf
import org.elm.lang.core.psi.elements.ElmBinOpExpr
import org.elm.lang.core.psi.elements.ElmFunctionCallExpr
import org.elm.lang.core.psi.elements.ElmIfElseExpr
import org.elm.lang.core.psi.elements.ElmOperator
import org.elm.lang.core.withoutParens

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
        val replaceWith = negated(factory, context.condition)
        context.condition.replace(replaceWith)
        val originalThen = factory.createExpression(context.thenBranch.text)
        val originalElse = factory.createExpression(context.elseBranch.text)
        context.thenBranch.replace(originalElse)
        context.elseBranch.replace(originalThen)
    }

    private fun negated(
        factory: ElmPsiFactory,
        condition: ElmExpressionTag
    ): ElmExpressionTag {
        return negatedConditionExpression(condition)
            ?: (negateCondition(factory, condition) ?: factory.createExpression("not (${condition.text})"))
    }

    private fun negateCondition(factory: ElmPsiFactory, condition: ElmExpressionTag): ElmExpressionTag? {
        return if (condition is ElmBinOpExpr) {
            val binOpExpr = condition.parts.toList()
            if (binOpExpr.size == 3) {
                val maybeOperator = binOpExpr[1] as? ElmOperator
                val newOperator = maybeOperator?.let { invertedOperator(it) }
                if (newOperator != null) {
                    val newBinOpExpr = binOpExpr.map { it.text }.toMutableList()
                    newBinOpExpr[1] = newOperator
                    return factory.createExpression(newBinOpExpr.joinToString(" "))
                }
                null
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun invertedOperator(operator: ElmOperator): String? {
        return when (operator.text) {
            "==" -> "/="
            "/=" -> "=="
            "<" -> ">="
            ">" -> "<="
            "<=" -> ">"
            ">=" -> "<"
            else -> null
        }
    }

    private fun negatedConditionExpression(condition: ElmExpressionTag): ElmExpressionTag? {
        return if (condition is ElmFunctionCallExpr && condition.target.text == "not") {
            val inner = condition.arguments.firstOrNull() ?: return null
            inner.withoutParens
        } else {
            null
        }
    }

}
