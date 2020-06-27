package org.elm.ide.refactoring.inline


import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.ancestors
import org.elm.lang.core.psi.elements.ElmFunctionDeclarationLeft
import org.elm.lang.core.psi.elements.ElmLetInExpr
import org.elm.lang.core.psi.elements.ElmTypeAnnotation
import org.elm.lang.core.psi.elements.ElmValueDeclaration
import org.elm.lang.core.psi.prevSiblings
import org.elm.lang.core.psi.withoutWsOrComments
import org.elm.lang.core.resolve.reference.ElmReference

//import org.rust.ide.surroundWith.addStatements
//import org.rust.lang.core.cfg.ExitPoint
//import org.rust.lang.core.psi.ext.*
//import org.rust.lang.core.resolve.ref.RsReference

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

//        usagesAsReference = usages.filter {
//            it.element.ancestorOrSelf<ElmFunctionCallExpr>() == null
//                    && it.element.ancestorOrSelf<RsMethodCall>() == null
//        }

        usages.removeAll(usagesAsReference)
        return usages.map(::UsageInfo).toTypedArray()
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val conflicts = MultiMap<PsiElement, String>()
//        refUsages.get().forEach { usage ->
//            val caller = usage.element?.ancestors?.filter { it is RsCallExpr || it is RsDotExpr }?.firstOrNull()
//            val exprAncestor = usage.element?.ancestorOrSelf<RsStmt>() ?: usage.element?.ancestorOrSelf<RsExpr>()
//            when {
//                exprAncestor == null -> {
//                    conflicts.putValue(usage.element, "Usage is not part of an expression")
//                    return@forEach
//                }
//                !exprAncestor.isWritable -> {
//                    conflicts.putValue(usage.element, "Usage is not writable")
//                    return@forEach
//                }
//                usagesAsReference.contains(usage.reference) && removeDefinition -> {
//                    conflicts.putValue(usage.element, "Usage with function pointer inline is't currently supported")
//                    return@forEach
//                }
//                caller == null -> {
//                    conflicts.putValue(usage.element, "Usage is not part of a caller expression")
//                    return@forEach
//                }
//                checkCallerConflicts(function, caller) != null -> {
//                    conflicts.putValue(caller, checkCallerConflicts(function, caller))
//                    return@forEach
//                }
//            }
//        }

        return showConflicts(conflicts, refUsages.get())
    }

//    private fun checkCallerConflicts(function: RsFunction, caller: PsiElement): String? {
//        val funcArguments = (function.copy() as RsFunction).valueParameters
//        val callArguments = when (caller) {
//            is ElmFunctionCallExpr -> caller.valueArgumentList.exprList
//            else -> return "Unknown caller expression type"
//        }
//
//        if (funcArguments.size != callArguments.size) {
//            return "Cannot inline function to references with mismatching arguments"
//        }
//        return null
//    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        usages.asIterable().forEach loop@{
            val reference = it.reference as? ElmReference ?: return@loop
//            inlineWithLetBindingsAdded(reference, function)

            replaceCallerWithRetExpr(function.originalElement, reference.element)
        }
        if (removeDefinition) {
            function.delete()
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

    companion object {
//        fun doesFunctionHaveMultipleReturns(fn: RsFunction): Boolean {
//            var entryCount = 0
//            val sink: (ExitPoint) -> Unit = {
//                if (it !is ExitPoint.TryExpr) {
//                    ++entryCount
//                }
//            }
//            ExitPoint.process(fn, sink)
//            return entryCount > 1
//        }
//
//        fun isFunctionRecursive(fn: RsFunction): Boolean {
//            return fn.descendantsOfType<RsPath>().any { it.reference?.resolve() == fn }
//        }
//
//        fun checkIfLoopCondition(fn: RsFunction, element: PsiElement): Boolean {
//            val block = fn.block!!
//            val statements = block.stmtList
//
//            val hasStatements = when (block.expr) {
//                null -> statements.size > 1 ||
//                        statements.size == 1 && statements[0].descendantsOfType<RsRetExpr>().isEmpty()
//                else -> statements.size > 0
//            }
//
//            return hasStatements && element.ancestorOrSelf<RsWhileExpr>() != null
//        }
    }

//    private fun inlineWithLetBindingsAdded(ref: RsReference, function: RsFunction) {
//        try {
//            val functionDup = function.copy() as RsFunction
//            val body = functionDup.block ?: throw IncorrectOperationException("Empty function bypassed preprocessing")
//            replaceLastExprToStatement(body)
//            val enclosingStatement = ref.element.ancestorOrSelf<RsStmt>()
//                    ?: ref.element.ancestorOrSelf<RsExpr>()
//                    ?: throw IncorrectOperationException(
//                            "Usage without expression parent bypassed preprocessing: ${ref.element.text}")
//            val blockParent = enclosingStatement.ancestorOrSelf<RsBlock>()
//            val childContainingEnclosingStatement = if (blockParent?.children?.isNotEmpty() == true) {
//                blockParent.children.first { it.isAncestorOf(enclosingStatement) }
//            } else {
//                null
//            }
//
//            val caller = ref.element.ancestors.filter { it is RsCallExpr || it is RsDotExpr }.firstOrNull()
//                    ?: throw IncorrectOperationException("Usage without caller expression parent bypassed preprocessing: ${ref.element.text}")
//
//            val funcScope = LocalSearchScope(body)
//
//            val selfParam = functionDup.selfParameter
//            replaceSelfParamWithExpr(selfParam, caller, funcScope)
//
//            val retExprInside = body.descendantsOfType<RsRetExpr>().firstOrNull()?.expr
//            if (retExprInside != null && body.stmtList.size == 1) {
//                caller.replace(retExprInside)
//                return
//            }
//            replaceCallerWithRetExpr(body, caller)
//
//            val leftSibling = enclosingStatement.leftSiblings.firstOrNull { it is RsStmt }
//            val rightSibling = enclosingStatement.rightSiblings.firstOrNull { it is RsStmt }
//            if (blockParent != null) {
//                if (enclosingStatement.isPhysical) {
//                    body.children.filter { it.text != ";" }.forEach {
//                        enclosingStatement.addLeftSibling(it)
//                    }
//                } else {
//                    addFunctionBodyToCallerWithCorrectSpacing(leftSibling, rightSibling, blockParent, body, childContainingEnclosingStatement)
//                }
//
//                blockParent.children.filter { it.text == ";" }.forEach { it.delete() }
//            }
//            enclosingStatement.addLeftSibling(factory.createNewline())
//
//            enclosingStatement.ancestorOrSelf<RsBlock>()?.let {
//                it.replace(CodeStyleManager.getInstance(project).reformat(it))
//            }
//
//            if (enclosingStatement.descendantsOfType<RsExpr>().isEmpty() || enclosingStatement.text == ";") {
//                enclosingStatement.delete()
//            }
//        } catch (e: IncorrectOperationException) {
//            logger.error(e)
//        }
//    }

//    private fun addFunctionBodyToCallerWithCorrectSpacing(leftSibling: PsiElement?, rightSibling: PsiElement?, blockParent: RsBlock, body: RsBlock, childContainingEnclosingStatement: PsiElement?) {
//        if (leftSibling != null) {
//            val isThereSpaceBeforeFunctionCall = leftSibling.nextSibling.text.startsWith("\n\n")
//            val isThereSpaceAfterFunctionCall = rightSibling?.prevSibling?.text?.startsWith("\n\n") == true
//            blockParent.addRangeAfter(body.children.first(), body.children.last(), leftSibling)
//            if (isThereSpaceBeforeFunctionCall || isThereSpaceAfterFunctionCall) {
//                blockParent.addAfter(factory.createWhitespace("\n\n    "), leftSibling)
//            }
//        } else if (rightSibling != null) {
//            val isThereSpaceAfterFunctionCall = rightSibling.prevSibling.text.startsWith("\n\n")
//            blockParent.addRangeBefore(body.children.first(), body.children.last(), rightSibling)
//            if (isThereSpaceAfterFunctionCall) {
//                blockParent.addBefore(factory.createWhitespace("\n\n    "), rightSibling)
//            }
//        } else if (childContainingEnclosingStatement?.isPhysical == true) {
//            blockParent.addBefore(factory.createWhitespace("\n    "), childContainingEnclosingStatement)
//            blockParent.addRangeBefore(body.children.first(), body.children.last(), childContainingEnclosingStatement)
//            blockParent.addBefore(factory.createNewline(), childContainingEnclosingStatement)
//        } else {
//            blockParent.addStatements(body.children)
//        }
//    }

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


    private fun replaceCallerWithRetExpr(body: PsiElement, caller: PsiElement) {
        // Covering a case in which ; isn't included in the expression, and parent is too wide.
        // e.g. if RsExpr is surrounded by RsBlock going to the RsBlock won't help.
        val realBody = (body.parent as ElmValueDeclaration)
        val bodyExpression = realBody.expression?.originalElement
        if (bodyExpression != null) {
            caller.replace(bodyExpression)
        }
        deleteDeclaration(body)
//        val actualRetExpr: PsiElement = if (body.parent.text == body.text + ";") {
//            body.parent
//        } else {
//            body
//        }
//        if (caller.parent !is RsBlock && caller.parent.text != caller.text + ";") {
//            caller.replace(body)
//            body.delete()
//        } else {
//            actualRetExpr.replace(body)
//            if (caller.parent.text == caller.text + ";") {
//                caller.parent.delete()
//            } else {
//                caller.delete()
//            }
//        }
    }

//    private fun replaceSelfParamWithExpr(selfParam: RsSelfParameter?, caller: PsiElement, funcScope: LocalSearchScope) {
//        if (selfParam != null) {
//            val selfExprText = buildString {
//                if (selfParam.mut != null) append("mut")
//                append(" ")
//                append((caller as RsDotExpr).expr.text)
//            }
//
//            val selfExpr = factory.tryCreateExpression(selfExprText)!!
//            ReferencesSearch.search(selfParam.navigationElement, funcScope).findAll().forEach {
//                it.element.ancestorOrSelf<RsExpr>()!!.replace(selfExpr)
//            }
//        }
//    }

//    private fun replaceLastExprToStatement(body: RsBlock) {
//        val expr = body.expr ?: return
//        val text = buildString {
//            if (expr !is RsRetExpr) append("return ")
//            append(expr.text)
//            append(";")
//        }
//
//        val stmt = factory.createStatement(text)
//        expr.replace(stmt)
//    }

    private fun PsiElement.addLeftSibling(element: PsiElement) {
        this.parent.addBefore(element, this)
    }
}
