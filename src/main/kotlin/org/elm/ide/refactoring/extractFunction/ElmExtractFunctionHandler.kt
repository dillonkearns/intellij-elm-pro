package org.elm.ide.refactoring.extractFunction

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import org.elm.ide.utils.findExpressionInRange
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.elements.ElmValueDeclaration
import org.elm.lang.core.types.Ty
import org.elm.lang.core.types.findTy
import org.elm.lang.core.types.renderedText
import org.elm.openapiext.runWriteCommandAction

class ElmExtractFunctionHandler : RefactoringActionHandler {
    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //this doesn't get called from the editor.
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        if (file !is ElmFile) return
        val start = editor?.selectionModel?.selectionStart
        val end = editor?.selectionModel?.selectionEnd
        if (start === null || end === null) return
        val config = ElmExtractFunctionConfig.createConfig(file, start, end) ?: return

        extractFunctionDialog(project, config) {
            extractFunction(project, file, config)
        }
    }

    private fun extractFunction(project: Project, file: PsiFile, config: ElmExtractFunctionConfig) {
        project.runWriteCommandAction(
        ) {
            if (file !is ElmFile) return@runWriteCommandAction
            val psiFactory = ElmPsiFactory(project)
            val (start, end) = config.selection
            val expressionToExtract = findExpressionInRange(file, start, end)
            val fnBody = expressionToExtract?.text
            val signatureTypes: List<Ty?> = (config.parameters.map { it.type }) + listOf(expressionToExtract?.findTy())
            val signature = if (signatureTypes.all { it != null }) {
                signatureTypes.joinToString(" -> ") { it!!.renderedText().replace("→", "->") }
            } else { null }
            
            val parameterString = (listOf(config.name) + config.parameters.map { it.name } + listOf("=")).joinToString(" ")
            val newTopLevel = if (signature != null) {
                psiFactory.createTopLevelFunctionWithAnnotation("${config.name} : $signature", "$parameterString\n    $fnBody")
            } else {
                listOf(psiFactory.createTopLevelFunction("$parameterString\n    $fnBody"))
            }
            file.addAll(newTopLevel)
            expressionToExtract?.replace(psiFactory.createExpression((listOf(config.name) + config.parameters.map { it.name }).joinToString(" ")))
//            val extractedFunction = addExtractedFunction(project, config, psiFactory) ?: return@runWriteCommandAction
//            replaceOldStatementsWithCallExpr(config, psiFactory)
//            val parameters = config.valueParameters.filter { it.isSelected }
//            renameFunctionParameters(extractedFunction, parameters.map { it.name })
//            importTypeReferencesFromTys(extractedFunction, config.parametersAndReturnTypes)

        }
    }

    private fun addExtractedFunction(
        project: Project,
        config: ElmExtractFunctionConfig,
        psiFactory: ElmPsiFactory
    ): ElmValueDeclaration? {
//        val owner = config.function.owner

//        val function = psiFactory.createTopLevelFunction(config.functionText)
//        val psiParserFacade = PsiParserFacade.getInstance(project)
//        return when {
//            owner is ElmAbstractableOwner.Impl && !owner.isInherent -> {
//                val impl = findExistingInherentImpl(owner.impl) ?: createNewInherentImpl(owner.impl) ?: return null
//                val members = impl.members ?: return null
//                members.addBefore(psiParserFacade.createWhiteSpaceFromText("\n\n"), members.rbrace)
//                members.addBefore(function, members.rbrace) as? ElmFunction
//            }
//            else -> {
//                val newline = psiParserFacade.createWhiteSpaceFromText("\n\n")
//                val end = config.function.block?.rbrace ?: return null
//                config.function.addAfter(function, config.function.addAfter(newline, end)) as? ElmFunction
//            }
//        }
        return null
    }

    private fun List<PsiElement>.replaceEachWithReturn(factory: ElmPsiFactory, getValue: (String?) -> String) {
        for (element in this) {
//            if (element is ElmTryExpr) continue
//            val oldValue = element.getControlFlowValue()?.text
//            val newValue = getValue(oldValue)
//            element.replace(factory.createExpression("return $newValue"))
        }
    }

}
