package org.elm.ide.refactoring.inline


import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.ancestors
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.psi.prevSiblings
import org.elm.lang.core.psi.withoutWsOrComments
import org.elm.lang.core.resolve.reference.ElmReference

class ElmInlineFunctionProcessor(
        private val project: Project,
        private val function: ElmFunctionDeclarationLeft,
        private val ref: ElmReference?,
        private val inlineThisOnly: Boolean,
        private val removeDefinition: Boolean,
        private val factory: ElmPsiFactory = ElmPsiFactory(project),
        private val logger: Logger = Logger.getInstance(ElmInlineFunctionProcessor::class.java),
        private var usagesAsReference: List<PsiReference> = emptyList()
) : BaseRefactoringProcessor(project) {

    override fun findUsages(): Array<UsageInfo> {
        if (inlineThisOnly && ref != null) {
            return arrayOf(UsageInfo(ref))
        }

        val projectScope = GlobalSearchScope.projectScope(project)
        val usages = mutableListOf<PsiReference>()
        usages.addAll(ReferencesSearch.search(function, projectScope).findAll())

        usages.removeAll(usagesAsReference)
        return usages.map(::UsageInfo).toTypedArray()
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val conflicts = MultiMap<PsiElement, String>()

        return showConflicts(conflicts, refUsages.get())
    }


    override fun performRefactoring(usages: Array<out UsageInfo>) {
        usages.asIterable().forEach loop@{ it ->
            when (val reference = it.reference) {
                is ElmReference -> {
                    if (reference.element is ElmTypeAnnotation) {

                    } else {
                        val prevSiblings = reference.element.parent.prevSiblings.withoutWsOrComments
                        if (prevSiblings.toList().size == 2) {
                            val prev = prevSiblings.toList()[0]
                            val prev2 = prevSiblings.toList()[1]
                            if (prev is ElmOperator && prev.referenceName.equals("|>")) {
                                val realCaller = containingFunctionCall(reference.element) as ElmFunctionCallExpr
                                val arguments =  realCaller.arguments.plus(prev2).toList()
                                val realBody = ElmPsiFactory(project).createDeclaration(function.originalElement.parent.text)

                                val bodyExpression = realBody.expression?.originalElement
                                realBody.functionDeclarationLeft?.namedParameters?.withIndex()?.forEach { ( parameterIndex, namedParameter ) ->
                                    ReferencesSearch.search(namedParameter, LocalSearchScope(realBody)).findAll().forEach { parameterReference ->
                                        if (parameterReference.canonicalText.equals(namedParameter.name)) {
                                            parameterReference.element.replace(arguments[parameterIndex])
                                        }
                                    }
                                }
                                if (bodyExpression != null) {
                                    realCaller.replace(bodyExpression)
                                }
                                prev.delete()
                                prev2.delete()
                            } else {

                                replaceCallerWithRetExpr(function.originalElement, reference.element)
                            }

                        } else {
                            replaceCallerWithRetExpr(function.originalElement, reference.element)
                        }
                    }
                }
            }

        }
        if (removeDefinition) {
            deleteDeclaration(function.originalElement)
        }
    }

    override fun getCommandName(): String = "Inline function ${function.name}"

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return object : UsageViewDescriptor {
            override fun getCommentReferencesText(usagesCount: Int, filesCount: Int) =
                    RefactoringBundle.message("comments.elements.header",
                            UsageViewBundle.getOccurencesString(usagesCount, filesCount))

            override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) =
                    RefactoringBundle.message("invocations.to.be.inlined",
                            UsageViewBundle.getReferencesString(usagesCount, filesCount))

            override fun getElements() = arrayOf(function)

            override fun getProcessedElementsHeader() = "Function to inline"
        }
    }

    private fun deleteDeclaration(element: PsiElement) {
        val declaration = element.ancestors.takeWhile { it !is ElmValueDeclaration }
                .last()
                .parent
        when (val parentThing = declaration.parent) {
            is ElmLetInExpr -> {
                if (parentThing.valueDeclarationList.size == 1) {
                    val thingy = parentThing.expression as PsiElement
                    parentThing.replace(thingy)
                }
            }
        }
        declaration
                .prevSiblings
                .withoutWsOrComments
                .takeWhile { it is ElmTypeAnnotation }
                .plus(declaration)
                .forEach { it.delete() }
    }

    private fun containingFunctionCall(caller: PsiElement): PsiElement {
        return if (caller.parent is ElmFunctionCallExpr) {
            caller.parent
        } else if (caller is ElmFunctionCallExpr || caller is ElmValueExpr) {
            caller
        } else {
            containingFunctionCall(caller.parent)
        }
    }


    private fun replaceCallerWithRetExpr(body: PsiElement, caller: PsiElement) {
        // Covering a case in which ; isn't included in the expression, and parent is too wide.
        // e.g. if RsExpr is surrounded by RsBlock going to the RsBlock won't help.
        val realBody = ElmPsiFactory(project).createDeclaration(body.parent.text)
        val bodyExpression = realBody.expression?.originalElement
        when (val realCaller = containingFunctionCall(caller)) {
            is ElmFunctionCallExpr -> {
                realBody.functionDeclarationLeft?.namedParameters?.withIndex()?.forEach { ( parameterIndex, namedParameter ) ->
                    ReferencesSearch.search(namedParameter, LocalSearchScope(realBody)).findAll().forEach { parameterReference ->
                        if (parameterReference.canonicalText.equals(namedParameter.name)) {
                            parameterReference.element.replace(realCaller.arguments.toList()[parameterIndex])
                        }
                    }
                }
                if (bodyExpression != null) {
                    realCaller.replace(bodyExpression)
                }
            }
            is ElmValueExpr -> {
                if (bodyExpression != null) {
                    realCaller.replace(bodyExpression)
                }
            }


        }
    }


    private fun PsiElement.addLeftSibling(element: PsiElement) {
        this.parent.addBefore(element, this)
    }
}
