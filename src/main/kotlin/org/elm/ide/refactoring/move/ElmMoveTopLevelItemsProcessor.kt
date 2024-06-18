package org.elm.ide.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.elm.ElmBundle
import org.elm.ide.refactoring.move.common.ElementToMove
import org.elm.ide.refactoring.move.common.ElmMoveCommonProcessor
import org.elm.lang.core.psi.ElmExposableTag
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.elements.findMatchingItemFor
import org.elm.lang.core.psi.elements.removeItem
import org.elm.lang.core.psi.startOffset
import org.elm.lang.core.stubs.index.ElmNamedElementIndex

/** See overview of move refactoring in comment for [ElmMoveCommonProcessor] */
class ElmMoveTopLevelItemsProcessor(
    private val project: Project,
    private val itemsToMove: Set<ElmExposableTag>,
    private val targetMod: ElmFile,
    private val searchForReferences: Boolean
) : BaseRefactoringProcessor(project) {

    private val commonProcessor: ElmMoveCommonProcessor = run {
        val elementsToMove = itemsToMove.map { ElementToMove.fromItem(it) }
        ElmMoveCommonProcessor(project, elementsToMove, targetMod)
    }

    override fun findUsages(): Array<out UsageInfo> {
        if (!searchForReferences) return UsageInfo.EMPTY_ARRAY
        // TODO
        return commonProcessor.findUsages()
//        return emptyArray()
    }

    private fun checkNoItemsWithSameName(@Suppress("UnstableApiUsage") conflicts: MultiMap<PsiElement, @NlsContexts.DialogMessage String>) {
//        if (!searchForReferences) return
        itemsToMove.forEach {
            val conflict = ElmNamedElementIndex.find(it.name, project, GlobalSearchScope.fileScope(targetMod)).firstOrNull()
            if (conflict != null) {
                conflicts.putValue(it, "Target file already contains item with name ${it.name}")
            }
        }

//        val targetModItems = targetMod.expandedItemsExceptImplsAndUses
//            .filterIsInstance<ElmExposableTag>()
//            .groupBy { it.name }
//        for (item in itemsToMove) {
//            val name = (item as? ElmExposableTag)?.name ?: continue
//            val namespaces = item.namespaces
//            val itemsExisting = targetModItems[name] ?: continue
//            for (itemExisting in itemsExisting) {
//                val namespacesExisting = itemExisting.namespaces
//                if ((namespacesExisting intersect namespaces).isNotEmpty()) {
//                    conflicts.putValue(itemExisting, "Target file already contains item with name $name")
//                }
//            }
//        }
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        val conflicts = MultiMap<PsiElement, String>()
        checkNoItemsWithSameName(conflicts)
//        return commonProcessor.preprocessUsages(usages, conflicts) && showConflicts(conflicts, usages)
        return showConflicts(conflicts, usages)
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        commonProcessor.performRefactoring(usages, this::moveItems)
    }

    private fun moveItems(): List<ElementToMove> {
        val psiFactory = ElmPsiFactory(project)
        return itemsToMove
            .sortedBy { it.startOffset }
            .mapNotNull { item -> moveItem(item, psiFactory) }
    }

    private fun moveItem(item: ElmExposableTag, psiFactory: ElmPsiFactory): ElementToMove? {
//        commonProcessor.updateMovedItemVisibility(item)
//
//        if (targetMod.lastChildInner !is PsiWhiteSpace) {
//            targetMod.addInner(psiFactory.createNewline())
//        }
//        val targetModLastWhiteSpace = targetMod.lastChildInner as PsiWhiteSpace

//        val space = (item.prevSibling as? PsiWhiteSpace) ?: (item.nextSibling as? PsiWhiteSpace)
//        // have to call `copy` because of rare suspicious `PsiInvalidElementAccessException`
//        val itemNew = targetMod.addBefore(item.copy(), targetModLastWhiteSpace) as ElmExposableTag
//        targetMod.addBefore(space?.copy() ?: psiFactory.createNewline(), itemNew)
//
//        space?.delete()
        val exposingList = item.elmFile.getModuleDecl()?.exposingList ?: return null

        val exposedItem = exposingList.findMatchingItemFor(item)
        if (exposedItem != null) {
            exposingList.removeItem(exposedItem)
        }
        item.parent.delete()
//        return ElementToMove.fromItem(itemNew)
        return null
    }

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        MoveMultipleElementsViewDescriptor(itemsToMove.toTypedArray(), targetMod.name ?: "")

    override fun getCommandName(): String = ElmBundle.message("command.name.move.items")
}
