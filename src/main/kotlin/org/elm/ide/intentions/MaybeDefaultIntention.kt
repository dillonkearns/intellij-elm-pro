package org.elm.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.ancestors
import org.elm.lang.core.psi.elements.ElmCaseOfExpr
import org.elm.lang.core.psi.elements.ElmFunctionCallExpr
import org.elm.lang.core.psi.elements.ElmLowerPattern
import org.elm.lang.core.psi.elements.ElmUnionPattern
import org.elm.lang.core.psi.prevSiblings

class MaybeDefaultIntention : ElmAtCaretIntentionActionBase<MaybeDefaultIntention.Context>() {
    data class Context(val maybeCaseExpression: ElmCaseOfExpr)

    override fun getText() = "Convert Maybe case to withDefault"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        return (element.ancestors.filterIsInstance<ElmUnionPattern>().firstOrNull())?.let { unionPattern ->
            if (unionPattern.upperCaseQID.refName == "Just" || unionPattern.upperCaseQID.refName == "Nothing") {
                unionPattern.ancestors.filterIsInstance<ElmCaseOfExpr>().firstOrNull()?.let { caseOfExpr ->
                    Context(caseOfExpr)
                }
            } else {
                null
            }
        }
    }

    override fun invoke(project: Project, editor: Editor, context: Context) {
        val justBranch = context.maybeCaseExpression.branches.first()
        val justPattern = (justBranch.pattern.child as ElmUnionPattern).argumentPatterns.first()
        val factory = ElmPsiFactory(project)
        // TODO get correct branch order
        // TODO handle case where there is a missing Just or Nothing branch (_ -> or similar)
        val maybeValue = context.maybeCaseExpression.expression!!.text
        // TODO handle case where it isn't ElmFunctionCallExpr
        val justFunction = (justBranch.expression as ElmFunctionCallExpr).target.text
        val nothingBranch = context.maybeCaseExpression.branches.last()
        val defaultValue = nothingBranch.expression!!.text
        if (justPattern is ElmLowerPattern) {
            context.maybeCaseExpression.replace(
                factory.createPipeChain(
                    context.maybeCaseExpression.prevSiblings.firstNotNullOf { it as? PsiWhiteSpace }.text,
                    "    ",
                    listOf(
                        maybeValue,
                        "Maybe.map $justFunction",
                        "Maybe.withDefault $defaultValue"
                    )
                )
            )
        } else {
            context.maybeCaseExpression.replace(
                factory.createPipeChain(
                    context.maybeCaseExpression.prevSiblings.firstNotNullOf { it as? PsiWhiteSpace }.text,
                    "    ",
                    listOf(
                        maybeValue,
                        "Maybe.map (\\(${justPattern.text}) -> ${justBranch.expression!!.text})",
                        "Maybe.withDefault $defaultValue"
                    )
                )
            )
        }

//        val mapCallExpr = context.mapInvocation
//        val itemVarName = uniqueValueName(mapCallExpr, "item")
//        val accVarName = uniqueValueName(mapCallExpr, "result")
//        val mapFuncText = mapCallExpr.arguments.toList().first().text
//        val itemsText = mapCallExpr.arguments.toList().getOrNull(1)?.text.orEmpty()
//
//        val newCallText = if (mapFuncText.contains("\n")) {
//            // multi-line case
//            buildIndentedText(mapCallExpr) {
//                appendLine("List.foldr")
//                level++
//                appendLine("""(\$itemVarName $accVarName ->""")
//                level++
//                for (line in mapCallExpr.arguments.first().textWithNormalizedIndents.lines()) {
//                    appendLine(line)
//                }
//                level++
//                appendLine(itemVarName)
//                appendLine(":: $accVarName")
//                level -= 2
//                appendLine(")")
//                appendLine("[]")
//                appendLine(itemsText)
//            }
//
//        } else {
//            // single-line case
//            "List.foldr (\\$itemVarName $accVarName -> $mapFuncText $itemVarName :: $accVarName) [] $itemsText"
//        }
//
//        mapCallExpr.replace(factory.createFunctionCallExpr(newCallText))
    }
}
