package org.elm.ide.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.stubs.index.ElmModulesIndex
import org.elm.lang.core.types.findInference
import org.elm.lang.core.types.renderParam
import org.elm.lang.core.types.renderedText

class MakeDeclarationFromUsageFix : NamedQuickFix("Create") {
    override fun applyFix(element: PsiElement, project: Project) {
        if (element !is ElmValueExpr) {
           return
        }

        val psiFactory = ElmPsiFactory(project)
        val s = element.referenceName


        // TODO handle pipe expressions
        // TODO handle plain values (not functions)
        val arity = (element.parent as? ElmFunctionCallExpr)?.arguments?.count() ?: 0

        val argList: String = argListFromArity(arity)

        val inference = element.findInference() ?: return
        val callExpr = element.parent as? ElmFunctionCallExpr ?: return
        val argsTys = callExpr.arguments.map { inference.elementType(it) }
        val parameters = argsTys.joinToString(" ") { it.renderParam() }

        // TODO need to wrap each in parens if it's a function type - could just wrap all in parens
        val typeAnnotation = argsTys.toList().joinToString(" -> ") { it.renderedText() } + " -> " + inference.elementType(element).renderedText()
        val elements =
           psiFactory.createTopLevelFunctionWithAnnotation("$s : $typeAnnotation", "$s $argList= Debug.todo \"NOT IMPLEMENTED\"")

        when (element.flavor) {
            Flavor.QualifiedValue -> {
                val elmModule = ElmModulesIndex.get(element.qid.qualifierPrefix, element.elmFile)
                val targetFile = elmModule?.elmFile
                if (targetFile != null) {
                    element.elmFile.addAll(elements)
                    val elmExposingList = elmModule.exposingList
                    elmExposingList?.addItem(element.referenceName)
                }
            }
            Flavor.BareValue -> {
                element.elmFile.addAll(elements)
            }
            Flavor.QualifiedConstructor -> {
                // TODO - handle Constructors
            }
            Flavor.BareConstructor -> {
                // TODO - handle Constructors
            }
        }
    }

    private fun argListFromArity(arity: Int): String {
        return when (arity) {
            0 -> ""
            else -> {
                (1..arity).map { "arg$it"}.joinToString(separator = " ", postfix = " ")
            }
        }
    }


}
