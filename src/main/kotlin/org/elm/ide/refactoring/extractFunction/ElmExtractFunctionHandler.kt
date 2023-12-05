package org.elm.ide.refactoring.extractFunction

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.usageView.UsageInfo
import org.elm.ide.refactoring.ElmRenamePsiElementProcessor
import org.elm.ide.utils.findExpressionInRange
import org.elm.lang.core.psi.*
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
                signatureTypes.joinToString(" -> ") { it!!.renderedText(withModule = true).replace("→", "->") }
            } else { null }
            
            val parameterString = (listOf(config.name) + config.parameters.map { it.originalName } + listOf("=")).joinToString(" ")
            val newTopLevel = if (signature != null) {
                psiFactory.createTopLevelFunctionWithAnnotation("${config.name} : $signature", "$parameterString\n    $fnBody")
            } else {
                listOf(psiFactory.createTopLevelFunction("$parameterString\n    $fnBody"))
            }
            val declaredNames = file.directChildrenOfType<ElmValueDeclaration>()
            val declarationStartOffsets = declaredNames.map { Pair(it.endOffset, it) }
            val nearestDeclaration = declarationStartOffsets.filter { it.first >= expressionToExtract!!.endOffset }.first().second
            nearestDeclaration.addAllAfter(
                listOf(
                psiFactory.createWhitespace("\n\n\n"),
                ).plus(newTopLevel)
            )
            expressionToExtract?.replace(psiFactory.createExpression((listOf(config.name) + config.parameters.map { it.originalName }).joinToString(" ")))
            val extractedFunction = file.directChildrenOfType<ElmValueDeclaration>().find { it.functionDeclarationLeft?.name == config.name }!!
            extractedFunction.functionDeclarationLeft?.namedParameters?.withIndex()
                ?.forEach { (parameterIndex, parameter) ->
                config.parameters.find { it.originalName == parameter.text }?.let {
                    val newName = it.name
                        if (newName != parameter.name) {
                            val parameterUsages = ReferencesSearch.search(parameter, LocalSearchScope(extractedFunction)).findAll()
                            val usageInfo = parameterUsages.map { UsageInfo(it) }.toTypedArray()
                            ElmRenamePsiElementProcessor().renameElement(parameter, newName, usageInfo, null)
                        }
                    }
                }
//            val parameters = config.valueParameters.filter { it.isSelected }
//            renameFunctionParameters(extractedFunction, parameters.map { it.name })
//            importTypeReferencesFromTys(extractedFunction, config.parametersAndReturnTypes)

        }
    }

}
