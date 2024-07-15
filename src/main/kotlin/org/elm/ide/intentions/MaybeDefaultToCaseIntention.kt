package org.elm.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.elm.lang.core.psi.ElmAtomTag
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.psi.parentOfType
import org.elm.lang.core.psi.prevSiblings
import org.elm.lang.core.resolve.reference.QualifiedValueReference

class MaybeDefaultToCaseIntention : ElmAtCaretIntentionActionBase<MaybeDefaultToCaseIntention.Context>() {
    data class Context(
        val expression: ElmValueExpr,
        val mapFunction: ElmAtomTag,
        val defaultValue: ElmAtomTag,
        val pipeline: Pipeline
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

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? =
        isMaybeMap(element)?.let {
            val segments = it.segments()
            if (segments.size == 3) {
                val segment1 = segments[0].expressionParts.first()
                val segment2 = segments[1].expressionParts.first()
                val segment3 = segments[2].expressionParts.first()
                if (segment1 is ElmValueExpr
                    && segment2 is ElmFunctionCallExpr && (segment2.target.reference as? QualifiedValueReference)?.canonicalName == "Maybe.map"
                    && segment3 is ElmFunctionCallExpr && (segment3.target.reference as? QualifiedValueReference)?.canonicalName == "Maybe.withDefault"
                ) {
                    Context(segment1, segment2.arguments.first(), segment3.arguments.first(), it)
                } else {
                    null
                }
            } else {
                null
            }

        }

    override fun invoke(project: Project, editor: Editor, context: Context) {
        val factory = ElmPsiFactory(project)
        val caseExpr = factory.createCaseExpression(
            existingIndent = context.pipeline.pipeline.prevSiblings.firstNotNullOf { it as? PsiWhiteSpace }.text,
            expression = context.expression,
            branches = listOf(
                Pair("Just something", "${context.mapFunction.text} something"),
                Pair("Nothing", context.defaultValue.text)
            )
        )
        context.pipeline.pipeline.replace(caseExpr)
    }
}
