package org.elm.ide.refactoring.introduceParameter

//import org.rust.ide.presentation.renderInsertionSafe
//import org.elm.lang.core.psi.ext.*
//import org.elm.lang.core.types.type
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.elm.ElmBundle
import org.elm.ide.refactoring.findOccurrences
import org.elm.ide.refactoring.showOccurrencesChooser
import org.elm.ide.refactoring.suggestedNames
import org.elm.lang.core.psi.ElmExpressionTag
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.ancestors
import org.elm.lang.core.psi.elements.ElmFunctionCallExpr
import org.elm.lang.core.psi.elements.ElmFunctionDeclarationLeft
import org.elm.lang.core.psi.elements.ElmValueDeclaration
import org.elm.lang.core.psi.elements.ElmValueExpr
import org.elm.openapiext.runWriteCommandAction

fun extractExpression(editor: Editor, expr: ElmExpressionTag) {
    val project = expr.project
    val enclosingFunctions = findEnclosingFunctions(expr)
    when (enclosingFunctions.size) {
        0 -> {
            val message = ElmBundle.message("dialog.message.no.suitable.function.to.extract.parameter.found")
            val title = RefactoringBundle.message("introduce.parameter.title")
            val helpId = "refactoring.extractParameter"
            CommonRefactoringUtil.showErrorHint(project, editor, message, title, helpId)
        }
        1 -> replaceExpressionOccurrences(editor, expr, enclosingFunctions.first())
//        else -> showEnclosingFunctionsChooser(editor, enclosingFunctions) { chosenFunction ->
//            replaceExpressionOccurrences(editor, expr, chosenFunction)
//        }
    }
}

private fun replaceExpressionOccurrences(editor: Editor, expr: ElmExpressionTag, function: ElmFunctionDeclarationLeft) {
    val occurrences = findOccurrences(function.body!!, expr)
    showOccurrencesChooser(editor, expr, occurrences) { occurrencesToReplace ->
        replaceExpression(expr.project, editor, function, occurrencesToReplace)
    }
}

private fun replaceExpression(project: Project, editor: Editor, function: ElmFunctionDeclarationLeft, exprs: List<ElmExpressionTag>) {
    if (exprs.isEmpty()) return
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, function)) return

    val paramIntroducer = ParamIntroducer(project, editor)
    paramIntroducer.replaceExpressions(function, exprs)
}

private fun findEnclosingFunctions(expr: ElmExpressionTag): List<ElmFunctionDeclarationLeft> =
    expr.ancestors.filterIsInstance<ElmValueDeclaration>().toList().mapNotNull { it.functionDeclarationLeft }

private class ParamIntroducer(
    private val project: Project,
    private val editor: Editor
) {
    private val psiFactory = ElmPsiFactory(project)

    /**
     * Introduces a new parameter to the chosen function and replaces chosen expression occurrences with a newly introduced
     * param.
     * @param function function to append a new param
     * @param exprs expressions to be replaced with a new param
     */
    fun replaceExpressions(function: ElmFunctionDeclarationLeft, exprs: List<ElmExpressionTag>) {
        if (exprs.isEmpty()) return
        val expr = exprs.first()
//        val typeRef = psiFactory.tryCreateType(expr.type.renderInsertionSafe()) ?: return
//
        val suggestedNames = expr.suggestedNames()
//
        val functionUsages = findFunctionUsages(function)
        project.runWriteCommandAction() {
//            RefactoringBundle.message("introduce.parameter.title")
            appendNewArgument(functionUsages, expr)
            val newParam = introduceParam(function, suggestedNames.default
//                , typeRef
            )
//            val name = psiFactory.createExpression(suggestedNames.default)
//            exprs.forEach { it.replace(name) }
//            val newParameter = moveEditorToNameElement(editor, newParam)
//
//            if (newParameter != null) {
//                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
//                RsInPlaceVariableIntroducer(newParameter, editor, project, ElmBundle.message("command.name.choose.parameter"))
//                    .performInplaceRefactoring(suggestedNames.all)
        }
    }

    private fun appendNewArgument(usages: Sequence<PsiElement>, value: ElmExpressionTag) {
        usages.forEach {
              if (it is ElmFunctionCallExpr) {
//                  introduceValueArgument(value, it.valueArgumentList)
                  TODO("Unhandled")
              } else if (it is ElmValueExpr) {
                    it.replace(psiFactory.createFunctionCallExpr("(${it.text} ${value.text})"))
              }
            // TODO what about curried function references?
            // TODO pipeline expressions
        }
    }

    private fun findFunctionUsages(chosenFunction: ElmFunctionDeclarationLeft): Sequence<PsiElement> {
        val projectScope = GlobalSearchScope.projectScope(chosenFunction.project)
        val functionUsages = ReferencesSearch.search(chosenFunction, projectScope, false).findAll()
        return functionUsages.map { it.element }.asSequence()
    }

//    private fun findDescendantFunction(traitImplRef: PsiReference, functionToSearch: RsFunction): RsFunction? {
//        val traitImpl = traitImplRef.element.parent?.parent as? RsImplItem ?: return null
//        val functions = traitImpl.descendantsOfType<RsFunction>()
//        return functions.first { it.name.equals(functionToSearch.name) }
//    }
//
//    private fun createParam(name: String, typeRef: RsTypeReference): RsValueParameter {
//        return createParamList(name, typeRef).valueParameterList.first()
//    }
//
//    private fun createParamList(name: String, typeRef: RsTypeReference): RsValueParameterList {
//        return psiFactory.createSimpleValueParameterList(name, typeRef)
//    }

//    private fun introduceValueArgument(value: ElmExpressionTag, argumentList: RsValueArgumentList) {
//        val args = argumentList.exprList
//        if (args.isEmpty()) {
//            argumentList.addAfter(value, argumentList.firstChild)
//        } else {
//            argumentList.addAfter(value, args.last())
//            val comma = psiFactory.createComma()
//            argumentList.addAfter(comma, args.last())
//        }
//    }

    private fun introduceParam(func: ElmFunctionDeclarationLeft, name: String
//                               , typeRef: RsTypeReference
    ): PsiElement? {
        val params = func.patterns
        val eq = (func.parent as ElmValueDeclaration).eqElement!!
        val valueDec = (func.parent as ElmValueDeclaration)
        val newDeclaration: ElmFunctionDeclarationLeft =
            psiFactory.createTopLevelFunction("${func.text} ${name} = ()").functionDeclarationLeft!!
        func.replace(newDeclaration)
//        val newParam = psiFactory.createTopLevelFunction("")
//        val eq = func
//        val parent = func.valueParameterList ?: return null
//        val parent = func.valueParameterList ?: return null
//        val newParam = createParam(name
////            , typeRef
//        )
//        return if (params.isEmpty()) {
//            parent.addAfter(newParam, parent.firstChild)
//        } else {
//            val newElem = parent.addAfter(newParam, params.last())
//            val comma = psiFactory.createComma()
//            parent.addAfter(comma, params.last())
//            newElem
//        }
        return null
    }
}
