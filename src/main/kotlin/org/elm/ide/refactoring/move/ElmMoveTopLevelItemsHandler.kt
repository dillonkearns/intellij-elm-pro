package org.elm.ide.refactoring.move

//import org.elm.ide.utils.collectElements
//import org.elm.ide.utils.getTopmostParentInside
//import org.elm.lang.core.psi.ext.*
//import org.elm.lang.core.types.rawType
//import org.elm.lang.core.types.ty.TyAdt
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.elm.ide.utils.collectElements
import org.elm.ide.utils.getElementRange
import org.elm.ide.utils.getTopmostParentInside
import org.elm.lang.core.ElmLanguage
import org.elm.lang.core.psi.*
import org.elm.lang.core.psi.elements.ElmValueDeclaration
import org.elm.openapiext.isUnitTestMode
import org.elm.openapiext.toPsiFile

class ElmMoveTopLevelItemsHandler : MoveHandlerDelegate() {

    override fun supportsLanguage(language: Language): Boolean = language.`is`(ElmLanguage)

    override fun canMove(
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        reference: PsiReference?
    ): Boolean {
        val containingMod = (elements.firstOrNull() as? ElmPsiElement)?.elmFile ?: return false
        return elements.all { canMoveElement(it) && it.parent == containingMod }
    }

    // Invoked in non-editor context (e.g file structure view)
    override fun doMove(
        project: Project,
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        moveCallback: MoveCallback?
    ) {
        doMove(project, elements.toList(), null)
    }

    // Invoked in editor context
    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext?,
        reference: PsiReference?,
        editor: Editor?
    ): Boolean {
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        if (hasSelection || element !is PsiFile) {
            return doMove(project, listOf(element), editor)
        }
        return false
    }

    private fun doMove(project: Project, elements: List<PsiElement>, editor: Editor?): Boolean {
        val (itemsToMove, containingMod) = editor?.let { collectInitialItems(project, editor) }
            ?: run {
                val containingMod = (elements.first() as? ElmPsiElement)?.elmFile ?: return false
                val items = elements.filterIsInstance<ElmValueDeclaration>().toSet()
                items to containingMod
            }

        if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, itemsToMove, true)) return false

//        val relatedImplItems = collectRelatedImplItems(containingMod, itemsToMove)
        val itemsToMoveAll = itemsToMove as Set<ElmExposableTag>// + relatedImplItems
        if (isUnitTestMode) {
            // TODO is this cast safe/correct?
            doMoveInUnitTestMode(project, itemsToMoveAll, containingMod)
        } else {
            val dialog = ElmMoveTopLevelItemsDialog(project, itemsToMoveAll, containingMod)
            dialog.show()
        }
        return true
    }

    private fun doMoveInUnitTestMode(project: Project, itemsToMove: Set<ElmExposableTag>, sourceMod: ElmFile) {
        val sourceFile = sourceMod.containingFile
        val targetMod = sourceFile.getUserData(MOVE_TARGET_MOD_KEY)
            ?: ElmMoveTopLevelItemsDialog.getOrCreateTargetMod(
                sourceFile.getUserData(MOVE_TARGET_FILE_PATH_KEY)!!,
                project,
//                sourceMod.crateRoot
                sourceMod
            )!!

        // TODO
        val processor = ElmMoveTopLevelItemsProcessor(project, itemsToMove, targetMod, searchForReferences = true)
        processor.run()
    }

    private fun collectInitialItems(project: Project, editor: Editor): Pair<Set<ElmExposableTag>, ElmFile>? {
        val file = editor.toPsiFile(project) ?: return null
        val selection = editor.selectionModel
        return if (selection.hasSelection()) {
            collectItemsInsideSelection(file, selection)
        } else {
            collectedItemsUnderCaret(file, editor.caretModel)
        }
    }

    private fun collectItemsInsideSelection(
        file: PsiFile,
        selection: SelectionModel
    ): Pair<Set<ElmExposableTag>, ElmFile>? {
        val (leafElement1, leafElement2) = file.getElementRange(selection.selectionStart, selection.selectionEnd)
            ?: return null
        val element1 = leafElement1.ancestorOrSelf<ElmPsiElement>() ?: leafElement1
        val element2 = leafElement2.ancestorOrSelf<ElmPsiElement>() ?: leafElement2
        val containingMod = element1.containingFile as ElmFile
        val item1 = element1.getTopmostParentInside(containingMod)
        val item2 = element2.getTopmostParentInside(containingMod)
        val items = collectElements(item1, item2.nextSibling) { it is ElmValueDeclaration }
            .mapTo(mutableSetOf()) { (it as ElmValueDeclaration).functionDeclarationLeft as ElmExposableTag }
        return items to containingMod
    }

    private fun collectedItemsUnderCaret(file: PsiFile, caretModel: CaretModel): Pair<Set<ElmExposableTag>, ElmFile>? {
        val elements = caretModel.allCarets.mapNotNull {
            val offset = it.offset
            val leafElement = file.findElementAt(offset)
            val element = if (offset > 0 && leafElement is PsiWhiteSpace) {
                // Workaround for situation when cursor is after item
                file.findElementAt(offset - 1)
            } else {
                leafElement
            }
            // TODO ensure that it is top-level
            element?.ancestorOrSelf<ElmValueDeclaration>()?.functionDeclarationLeft
        }
        val containingMod = elements.first().elmFile ?: return null
        return elements.toSet() to containingMod
    }

    companion object {
        fun canMoveElement(element: PsiElement): Boolean {
            return true
//            return element is ElmItemElement
//                    && element !is ElmModDeclItem
//                    && element !is ElmUseItem
//                    && element !is ElmExternCrateItem
//                    && element !is ElmForeignModItem
        }
    }
}

/** Mostly needed for case when [elements] contains only one [ElmFile] */
private inline fun <reified T : ElmPsiElement> findCommonAncestorStrictOfType(elements: List<PsiElement>): T? {
    val parent = PsiTreeUtil.findCommonParent(elements) ?: return null
    return if (elements.contains(parent)) {
        parent.ancestorStrict()
    } else {
        parent.ancestorOrSelf()
    }
}

//private fun collectRelatedImplItems(containingMod: ElmFile, items: Set<ElmItemElement>): List<ElmImplItem> {
//    if (isUnitTestMode) return emptyList()
//    // For struct `Foo` we should collect:
//    // * impl Foo { ... }
//    // * impl ... for Foo { ... }
//    // * Maybe also `impl From<Foo> for Bar { ... }`?
//    //   if `Bar` belongs to same crate (but to different module from `Foo`)
//    //
//    // For trait `Foo` we should collect:
//    // * impl Foo for ... { ... }
//    return groupImplsByStructOrTrait(containingMod, items).values.flatten()
//}

//fun groupImplsByStructOrTrait(
//    containingMod: ElmFile,
//    items: Set<ElmItemElement>
//): Map<ElmItemElement, List<ElmImplItem>> {
//    return containingMod
//        .childrenOfType<ElmImplItem>()
//        .mapNotNull { impl ->
//            val struct: ElmItemElement? = (impl.typeReference?.rawType as? TyAdt)?.item
//            val trait = impl.traitRef?.path?.reference?.resolve() as? ElmTraitItem
//            val relatedStruct = struct?.takeIf { items.contains(it) }
//            val relatedTrait = trait?.takeIf { items.contains(it) }
//            val relatedItem = relatedStruct ?: relatedTrait ?: return@mapNotNull null
//            relatedItem to impl
//        }
//        .groupBy({ it.first }, { it.second })
//}
