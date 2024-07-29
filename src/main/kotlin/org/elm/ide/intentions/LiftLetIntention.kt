package org.elm.ide.intentions

import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.IntroduceTargetChooser
import org.elm.ide.refactoring.asPass
import org.elm.ide.refactoring.extractFunction.Parameter
import org.elm.ide.refactoring.extractFunction.parametersToExtract
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.elements.ElmFunctionDeclarationLeft
import org.elm.lang.core.psi.elements.ElmLetInExpr
import org.elm.lang.core.psi.elements.ElmValueDeclaration
import org.elm.openapiext.toPsiFile

class LiftLetIntention : ElmAtCaretIntentionActionBase<LiftLetIntention.Context>() {
    data class Context(val letExpr: ElmLetInExpr, val letBinding: ElmValueDeclaration)

    override fun getText() = "Move let to top-level"
    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val binding = ((element.context as? ElmFunctionDeclarationLeft)?.parent as? ElmValueDeclaration)
        val letExpr = (binding?.context as? ElmLetInExpr)
        val params: List<Parameter> = binding?.expression?.let { parametersToExtract(it, forLetExpression = true) }.orEmpty()
        return if (binding != null && letExpr != null && params.isEmpty()) {
            Context(letExpr, binding)
        } else {
            null
        }
    }

    override fun invoke(project: Project, editor: Editor, context: Context) {

        chooser(editor, gatherContext(context.letExpr)) { expression ->
            runWriteCommandAction(project) {
                val factory = ElmPsiFactory(project)
                // TODO handle moving type annotation and doc comment
                if (context.letExpr.valueDeclarationList.size == 1) {
                    context.letExpr.deleteChildRange(context.letExpr.letKeyword, context.letExpr.inKeyword)
                } else {
                    context.letBinding.delete()
                }
                context.letBinding.delete()
                val newTopLevel = factory.createTopLevelFunction(context.letBinding.text)
                context.letExpr.containingFile.add(newTopLevel)
            }
        }
    }

    private fun chooser(editor: Editor, emptyList: List<PsiElement>, function: (PsiElement) -> Unit) {
        if (false) {
            IntroduceTargetChooser.showChooser(editor, emptyList, function.asPass) { it.text }
        } else {
            function(editor.toPsiFile(editor.project!!)!!)
        }
    }

    private fun gatherContext(letExpr: ElmLetInExpr): List<PsiElement> {
        var context = letExpr.context
        val expressions = mutableListOf<PsiElement>()
        while (context != null) {
            when (context) {
                is ElmLetInExpr -> {
                    expressions.add(context)
                }

                is ElmValueDeclaration -> {
                    expressions.add(context)
                }

                is ElmFile -> {
                    expressions.add(context)
                }
            }
            context = context.context
        }
        return expressions
    }

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement {
        return currentFile
    }
}
