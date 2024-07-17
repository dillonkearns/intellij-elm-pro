package org.elm.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.elm.lang.core.psi.*
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.resolve.reference.QualifiedValueReference

class MaybeDefaultToCaseIntention : ElmAtCaretIntentionActionBase<MaybeDefaultToCaseIntention.Context>() {
    data class Context(
        val expression: ElmValueExpr,
        val mapFunction: ElmAtomTag,
        val defaultValue: ElmAtomTag,
        val pipeline: PsiElement
    )

    override fun getText() = "Convert Maybe.withDefault to case of"
    override fun getFamilyName() = text

    private fun isMaybeMap(element: PsiElement): Pipeline? {
        val qid = (element as? ElmValueQID ?: element.parent as? ElmValueQID)
        return if (qid?.text == "Maybe.map") {
            qid.parentOfType<ElmBinOpExpr>()?.asPipeline()
        } else {
            null
        }
    }

    fun extractFromFunction(element: ElmPsiElement): ImmutableList<Pair<ElmAtomTag, List<ElmAtomTag>>> {
        val innermost = element.parent.parent.parent
        var current = innermost
        val collected = mutableListOf<Pair<ElmAtomTag, List<ElmAtomTag>>>()
        while (true) {
            when (current) {
                is ElmValueExpr -> {
                    collected.add(Pair(current, emptyList()))
                }
                is ElmParenthesizedExpr -> {
                    current = current.expression
                }
                is ElmFunctionCallExpr -> {
                    collected.add(Pair(current.target, current.arguments.toList()))
                    current = current.parentOfType<ElmFunctionCallExpr>() ?: break
                }
                else -> return collected.toImmutableList()
            }
        }
        return collected.toImmutableList()
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        return isMaybeMap(element)?.let {
            val segments = it.segments()
            if (segments.size == 3) {
                val segment1 = segments[0].expressionParts.first()
                val segment2 = segments[1].expressionParts.first()
                val segment3 = segments[2].expressionParts.first()
                if (segment1 is ElmValueExpr
                    && segment2 is ElmFunctionCallExpr && (segment2.target.reference as? QualifiedValueReference)?.canonicalName == "Maybe.map"
                    && segment3 is ElmFunctionCallExpr && (segment3.target.reference as? QualifiedValueReference)?.canonicalName == "Maybe.withDefault"
                ) {
                    Context(segment1, segment2.arguments.first(), segment3.arguments.first(), it.pipeline)
                } else {
                    null
                }
            } else {
                null
            }

        } ?: extractContextFromNested(element)
    }

    private fun extractContextFromNested(element: PsiElement) : Context? {
        val qid = (element.parent as ElmValueQID)
        val extractFromFunction = extractFromFunction(qid)
        return Context(extractFromFunction.first().second.last() as ElmValueExpr, extractFromFunction.first().second.first(), extractFromFunction.last().second.first(), qid.parent.parent.parent.parent)
    }

    override fun invoke(project: Project, editor: Editor, context: Context) {
        val factory = ElmPsiFactory(project)
        val caseExpr = factory.createCaseExpression(
            existingIndent = context.pipeline.prevSiblings.firstNotNullOf { it as? PsiWhiteSpace }.text,
            expression = context.expression,
            branches = listOf(
                Pair("Just something", "${context.mapFunction.text} something"),
                Pair("Nothing", context.defaultValue.text)
            )
        )
        context.pipeline.replace(caseExpr)
    }
}
