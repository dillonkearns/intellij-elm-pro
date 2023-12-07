/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.elm.ide.refactoring.introduceParameter

//import org.rust.ide.presentation.renderInsertionSafe
//import org.elm.lang.core.psi.ext.*
//import org.elm.lang.core.types.type
import com.intellij.openapi.editor.Editor

fun extractExpression(editor: Editor
//                      , expr: RsExpr
) {
//    val project = expr.project
//    val enclosingFunctions = findEnclosingFunctions(expr)
//    when (enclosingFunctions.size) {
//        0 -> {
//            val message = ElmBundle.message("dialog.message.no.suitable.function.to.extract.parameter.found")
//            val title = RefactoringBundle.message("introduce.parameter.title")
//            val helpId = "refactoring.extractParameter"
//            CommonRefactoringUtil.showErrorHint(project, editor, message, title, helpId)
//        }
//        1 -> replaceExpressionOccurrences(editor, expr, enclosingFunctions.first())
//        else -> showEnclosingFunctionsChooser(editor, enclosingFunctions) { chosenFunction ->
//            replaceExpressionOccurrences(editor, expr, chosenFunction)
//        }
//    }
}
//
//private fun replaceExpressionOccurrences(editor: Editor, expr: RsExpr, function: RsFunction) {
//    val occurrences = findOccurrences(function, expr)
//    showOccurrencesChooser(editor, expr, occurrences) { occurrencesToReplace ->
//        replaceExpression(expr.project, editor, function, occurrencesToReplace)
//    }
//}
//
//private fun replaceExpression(project: Project, editor: Editor, function: RsFunction, exprs: List<RsExpr>) {
//    if (exprs.isEmpty()) return
//    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, function)) return
//
//    val replaceForTrait = function.owner is RsAbstractableOwner.Trait || function.owner.isTraitImpl
//    val paramIntroducer = ParamIntroducer(project, editor)
//    paramIntroducer.replaceExpressions(function, exprs, replaceForTrait)
//}
//
//private fun findEnclosingFunctions(expr: RsExpr): List<RsFunction> =
//    expr.ancestors.filterIsInstance<RsFunction>().toList()
//
//private class ParamIntroducer(
//    private val project: Project,
//    private val editor: Editor
//) {
//    private val psiFactory = RsPsiFactory(project)
//    /**
//     * Introduces a new parameter to the chosen function and replaces chosen expression occurrences with a newly introduced
//     * param.
//     * @param function function to append a new param
//     * @param exprs expressions to be replaced with a new param
//     * @param replaceForTrait if true then the new param will be introduced for all trait function implementations
//     */
//    fun replaceExpressions(function: RsFunction, exprs: List<RsExpr>, replaceForTrait: Boolean = true) {
//        if (exprs.isEmpty()) return
//        val expr = exprs.first()
//        val typeRef = psiFactory.tryCreateType(expr.type.renderInsertionSafe()) ?: return
//
//        val suggestedNames = expr.suggestedNames()
//
//        val traitFunction = function.superItem as? RsFunction ?: function
//        val functionUsages = if (replaceForTrait) {
//            findFunctionUsagesWithImpl(traitFunction)
//        } else {
//            findFunctionUsages(function)
//        }
//        project.runWriteCommandAction(RefactoringBundle.message("introduce.parameter.title")) {
//            appendNewArgument(functionUsages, expr)
//            if (replaceForTrait) {
//                getTraitAndImpls(traitFunction)
//                    .filter { it != function }
//                    .forEach { introduceParam(it, suggestedNames.default, typeRef) }
//            }
//            val newParam = introduceParam(function, suggestedNames.default, typeRef)
//            val name = psiFactory.createExpression(suggestedNames.default)
//            exprs.forEach { it.replace(name) }
//            val newParameter = moveEditorToNameElement(editor, newParam)
//
//            if (newParameter != null) {
//                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
//                RsInPlaceVariableIntroducer(newParameter, editor, project, ElmBundle.message("command.name.choose.parameter"))
//                    .performInplaceRefactoring(suggestedNames.all)
//            }
//        }
//    }
//
//    private fun appendNewArgument(usages: Sequence<PsiElement>, value: RsExpr) {
//        usages.forEach {
//            if (it is RsPath) {
//                val expr = it.ancestorOrSelf<RsCallExpr>() ?: return
//                introduceValueArgument(value, expr.valueArgumentList)
//            } else if (it is RsMethodCall) {
//                introduceValueArgument(value, it.valueArgumentList)
//            }
//        }
//    }
//
//    private fun findFunctionUsages(chosenFunction: RsFunction): Sequence<PsiElement> {
//        val projectScope = GlobalSearchScope.projectScope(chosenFunction.project)
//        val functionUsages = ReferencesSearch.search(chosenFunction, projectScope, false).findAll()
//        return functionUsages.map { it.element }.asSequence()
//    }
//
//    private fun getTraitAndImpls(traitFunction: RsFunction): Sequence<RsFunction> {
//        val trait = (traitFunction.owner as? RsAbstractableOwner.Trait)?.trait ?: return emptySequence()
//        val projectScope = GlobalSearchScope.projectScope(traitFunction.project)
//        val traitUsages = ReferencesSearch.search(trait, projectScope, false).findAll()
//        val refs = traitUsages.mapNotNull { findDescendantFunction(it, traitFunction) }
//        return refs.asSequence().plus(traitFunction)
//    }
//
//    private fun findFunctionUsagesWithImpl(traitFunction: RsFunction): Sequence<PsiElement> {
//        return getTraitAndImpls(traitFunction)
//            .map { findFunctionUsages(it) }
//            .flatten()
//    }
//
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
//
//    private fun introduceValueArgument(value: RsExpr, argumentList: RsValueArgumentList) {
//        val args = argumentList.exprList
//        if (args.isEmpty()) {
//            argumentList.addAfter(value, argumentList.firstChild)
//        } else {
//            argumentList.addAfter(value, args.last())
//            val comma = psiFactory.createComma()
//            argumentList.addAfter(comma, args.last())
//        }
//    }
//
//    private fun introduceParam(func: RsFunction, name: String, typeRef: RsTypeReference): PsiElement? {
//        val params = func.rawValueParameters
//        val parent = func.valueParameterList ?: return null
//        val newParam = createParam(name, typeRef)
//        return if (params.isEmpty()) {
//            if (parent.selfParameter != null) {
//                val newElem = parent.addAfter(newParam, parent.selfParameter)
//                val comma = psiFactory.createComma()
//                parent.addAfter(comma, parent.selfParameter)
//                newElem
//            } else {
//                parent.addAfter(newParam, parent.firstChild)
//            }
//        } else {
//            val newElem = parent.addAfter(newParam, params.last())
//            val comma = psiFactory.createComma()
//            parent.addAfter(comma, params.last())
//            newElem
//        }
//    }
//}
