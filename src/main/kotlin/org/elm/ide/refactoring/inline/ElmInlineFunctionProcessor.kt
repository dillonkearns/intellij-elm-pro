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
import org.elm.ide.refactoring.ElmImportOptimizer
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.ancestors
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.psi.prevSiblings
import org.elm.lang.core.psi.withoutWsOrComments
import org.elm.lang.core.resolve.reference.ElmReference
import org.elm.openapiext.isUnitTestMode
import org.elm.workspace.commandLineTools.ElmFormatCLI
import org.elm.workspace.elmToolchain
import kotlin.math.absoluteValue

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
        usages.addAll(ReferencesSearch.search(function, projectScope).findAll().filter { it.element is ElmValueExpr })

        usages.removeAll(usagesAsReference)
        return usages.map(::UsageInfo).toTypedArray()
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val conflicts = MultiMap<PsiElement, String>()

        return showConflicts(conflicts, refUsages.get())
    }


    override fun performRefactoring(usages: Array<out UsageInfo>) {
        usages.asIterable().forEach loop@{
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
                                val arguments = realCaller.arguments.plus(prev2).toList()
                                val realBody =
                                    ElmPsiFactory(project).createDeclaration(function.originalElement.parent.text)

                                val bodyExpression = realBody.expression?.originalElement
                                realBody.functionDeclarationLeft?.namedParameters?.withIndex()
                                    ?.forEach { (parameterIndex, namedParameter) ->
                                        ReferencesSearch.search(namedParameter, LocalSearchScope(realBody)).findAll()
                                            .forEach { parameterReference ->
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

                                replaceCallerWithRetExpr(function, reference.element)
                            }

                        } else {
                            replaceCallerWithRetExpr(function, reference.element)
                        }
                    }
                }
            }

        }
        if (removeDefinition) {
            deleteDeclaration(function.originalElement)
        }
        val filesToOptimize = usages.mapNotNull { it.file }.toSet()
        filesToOptimize.forEach {
            ElmImportOptimizer().processFile(it).run()
        }
    }

    override fun getCommandName(): String = "Inline function ${function.name}"

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return object : UsageViewDescriptor {
            override fun getCommentReferencesText(usagesCount: Int, filesCount: Int) =
                RefactoringBundle.message(
                    "comments.elements.header",
                    UsageViewBundle.getOccurencesString(usagesCount, filesCount)
                )

            override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) =
                RefactoringBundle.message(
                    "invocations.to.be.inlined",
                    UsageViewBundle.getReferencesString(usagesCount, filesCount)
                )

            override fun getElements() = arrayOf(function)

            override fun getProcessedElementsHeader() = "Function to inline"
        }
    }

    private fun deleteDeclaration(element: PsiElement) {
        val declaration = element.ancestors.takeWhile { it !is ElmValueDeclaration }
            .last()
            .parent
        val fdl = (declaration as? ElmValueDeclaration)?.functionDeclarationLeft
        val moduleDecl = fdl?.elmFile?.getModuleDecl()
        val exposedItem = moduleDecl?.exposingList?.findMatchingItemFor(fdl)
        exposedItem?.let { moduleDecl.exposingList?.removeItem(it) }
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
        return if ((caller.parent as? ElmFunctionCallExpr)?.target == caller) {
            caller.parent
        } else if (caller is ElmFunctionCallExpr || caller is ElmValueExpr) {
            caller
        } else {
            containingFunctionCall(caller.parent)
        }
    }


    private fun replaceCallerWithRetExpr(functionLeft: ElmFunctionDeclarationLeft, caller: PsiElement) {
        // Covering a case in which ; isn't included in the expression, and parent is too wide.
        // e.g. if RsExpr is surrounded by RsBlock going to the RsBlock won't help.
        val bodyExpression = functionLeft.body!!
        when (val realCaller = containingFunctionCall(caller)) {
            is ElmFunctionCallExpr -> {
                val needsLambda = needsLambdaReplacement(functionLeft)

                if (!needsLambda) {
                    val copied = factory.createDeclaration(functionLeft.parent.text)
                    val curriedParams = copied.functionDeclarationLeft!!.patterns.drop(realCaller.arguments.count())
                    copied.functionDeclarationLeft?.patterns?.withIndex()
                        ?.forEach { (parameterIndex, namedParameter) ->
                            when (namedParameter) {
                                is ElmLowerPattern -> {
                                    ReferencesSearch.search(namedParameter, LocalSearchScope(copied)).findAll()
                                        .forEach { parameterReference ->
                                            if (parameterReference.canonicalText.equals(namedParameter.name)) {
                                                realCaller.arguments.toList().getOrNull(parameterIndex)?.let {
                                                    parameterReference.element.replace(it)
                                                }
                                            }
                                        }
                                }
                                is ElmRecordPattern -> {
                                    namedParameter.lowerPatternList.forEach {
                                        ReferencesSearch.search(it, LocalSearchScope(copied)).findAll().forEach { reference ->
                                            reference.element.replace(
                                                factory.createExpression("${realCaller.arguments.first().text}.${reference.canonicalText}")
                                            )

                                        }
                                    }
                                }
                            }
                        }
                    val rawPointFreeArgumentCount =
                        ((copied.functionDeclarationLeft?.patterns?.toList()?.size ?: 0)) - (realCaller.arguments.toList().size) - curriedParams.count()
                    val pointFreeArgumentCount =
                        rawPointFreeArgumentCount.absoluteValue

                    val copied2 = if (curriedParams.count() > 0) {
                        factory.createLambda("\\${curriedParams.joinToString(" ") { it.text }} -> ${copied.expression?.text!!}")
                    } else if (pointFreeArgumentCount == 0) {
                        copied.expression!!
                    } else {
                        factory.createParens(copied.expression?.text!!, "")
                    }
                    realCaller.arguments.toList().takeLast(pointFreeArgumentCount).forEach {
                        copied2.addAfter(factory.createWhitespace(" "), copied2)
                        copied2.addAfter(it, copied2)
                    }
                    realCaller.replace(copied2)
                } else {
                    val lambda = factory.createParens(
                        "(\\" + functionLeft.patterns.joinToString(" ") {
                            "(${it.text})"
                        }
                                + " -> " + functionLeft.body!!.text
                                + ") ${realCaller.arguments.joinToString(" ") { it.text }}"
                    )
                    realCaller.replace(lambda)
                }
            }
            is ElmValueExpr -> {
                if (bodyExpression != null) {
                    realCaller.replace(bodyExpression)
                }
            }


        }
    }

    private fun needsLambdaReplacement(functionLeft: ElmFunctionDeclarationLeft): Boolean {
        return functionLeft.patterns.any {
            (it is ElmPattern) && (it.child is ElmUnionPattern)
        }
    }


    private fun PsiElement.addLeftSibling(element: PsiElement) {
        this.parent.addBefore(element, this)
    }
}
