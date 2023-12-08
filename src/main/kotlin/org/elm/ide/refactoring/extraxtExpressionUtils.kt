package org.elm.ide.refactoring

//import org.elm.lang.core.psi.ext.ElmElement
//import org.elm.lang.core.psi.ext.ElmItemElement
//import org.elm.lang.core.psi.ext.ancestorOrSelf
//import org.elm.lang.core.psi.ext.ancestors
import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import org.elm.ide.utils.findExpressionAtCaret
import org.elm.ide.utils.findExpressionInRange
import org.elm.lang.core.psi.ElmExpressionTag
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.ElmPsiElement
import org.elm.lang.core.psi.ancestors
import org.elm.lang.core.psi.elements.ElmLetInExpr
import org.elm.lang.core.psi.elements.ElmValueDeclaration

//fun findCandidateExpressionsToExtract(editor: Editor, file: ElmFile): List<ElmExpressionTag> {
//    val selection = editor.selectionModel
//    return if (selection.hasSelection()) {
//        // If there's an explicit selection, suggest only one expression
//        listOfNotNull(findExpressionInRange(file, selection.selectionStart, selection.selectionEnd))
//    } else {
//        val expr = findExpressionAtCaret(file, editor.caretModel.offset)
//            ?: return emptyList()
//        // Finds possible expressions that might want to be bound to a local variable.
//        // We don't go further than the current block scope,
//        // further more path expressions don't make sense to bind to a local variable so we exclude them.
//        expr.ancestors
//            .takeWhile { it !is ElmBlock }
//            .filterIsInstance<ElmExpressionTag>()
//            .filter { it !is ElmPathExpr }
//            .toList()
//    }
//}


fun findCandidateExpressions(editor: Editor, file: ElmFile): List<ElmExpressionTag> {
    val selection = editor.selectionModel
    return if (selection.hasSelection()) {
        // If the user has some text selected, make a single suggestion based on the selection
        listOfNotNull(findExpressionInRange(file, selection.selectionStart, selection.selectionEnd))
    } else {
        // Suggest nested expressions at caret position
        val expr = findExpressionAtCaret(file, editor.caretModel.offset) ?: return emptyList()
        expr.ancestors
            .takeWhile { it !is ElmValueDeclaration && it !is ElmLetInExpr }
            .filterIsInstance<ElmExpressionTag>()
            .toList()
    }
}

/**
 * Finds occurrences in the sub scope of expr, so that all will be replaced if replace all is selected.
 */
fun findOccurrences(expr: ElmExpressionTag): List<ElmExpressionTag> {
//    val parent = expr.ancestorOrSelf<ElmBlock>()
//        ?: expr.ancestorOrSelf<ElmItemElement>() // outside a function, try to find a parent
//        ?: return emptyList()
    val parent = expr.parent.parent as ElmPsiElement
    return findOccurrences(parent, expr)
}

fun findOccurrences(parent: ElmPsiElement, expr: ElmExpressionTag): List<ElmExpressionTag> {
    val visitor = object : PsiRecursiveElementVisitor() {
        val foundOccurrences = ArrayList<ElmExpressionTag>()
        override fun visitElement(element: PsiElement) {
            if (element is ElmExpressionTag && PsiEquivalenceUtil.areElementsEquivalent(expr, element)) {
                foundOccurrences.add(element)
            } else {
                super.visitElement(element)
            }
        }
    }
    parent.acceptChildren(visitor)
    return visitor.foundOccurrences
}

//fun moveEditorToNameElement(editor: Editor, element: PsiElement?): ElmPatBinding? {
//    val newName = element?.findBinding()
//    editor.caretModel.moveToOffset(newName?.identifier?.textRange?.startOffset ?: 0)
//    return newName
//}
//
//fun PsiElement.findBinding() = PsiTreeUtil.findChildOfType(this, ElmPatBinding::class.java)
