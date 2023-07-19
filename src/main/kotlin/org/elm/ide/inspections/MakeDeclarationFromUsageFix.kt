package org.elm.ide.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.stubs.index.ElmModulesIndex

class MakeDeclarationFromUsageFix : NamedQuickFix("Create") {
    override fun applyFix(element: PsiElement, project: Project) {
        if (element !is ElmValueExpr) {
           return
        }

        val psiFactory = ElmPsiFactory(project)
        val s = element.referenceName


        val arity = (element.parent as? ElmFunctionCallExpr)?.arguments?.count() ?: 0

        val argList: String = argListFromArity(arity)
        val valueDeclaration =
           psiFactory.createTopLevelFunction("$s $argList= Debug.todo \"NOT IMPLEMENTED\"")

        when (element.flavor) {
            Flavor.QualifiedValue -> {
                val elmModule = ElmModulesIndex.get(element.qid.qualifierPrefix, element.elmFile)
                val targetFile = elmModule?.elmFile
                if (targetFile != null) {
                    targetFile.add(valueDeclaration)
                    val elmExposingList = elmModule.exposingList
                    elmExposingList?.addItem(element.referenceName)
                }
            }
            Flavor.BareValue -> {
                element.elmFile.add(valueDeclaration)
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
