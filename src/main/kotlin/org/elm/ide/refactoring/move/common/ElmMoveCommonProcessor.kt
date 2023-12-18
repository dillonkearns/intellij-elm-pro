/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.refactoring.move.common

//import org.elm.ide.fixes.MakePublicFix
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.LOG
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.containingModStrict
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.convertFromPathOriginal
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.isAbsolute
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.isInsideMovedElements
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.isSimplePath
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.resolvesToAndAccessible
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.startsWithSuper
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.textNormalized
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.toElmPath
//import org.elm.ide.refactoring.move.common.ElmMoveUtil.toElmPathInEmptyTmpMod
//import org.elm.ide.utils.import.ElmImportHelper
//import org.elm.lang.core.psi.ext.*
//import org.elm.openapiext.computeWithCancelableProgress
import com.intellij.openapi.project.Project
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import org.elm.lang.core.psi.*
import org.elm.lang.core.psi.elements.ElmFunctionDeclarationLeft
import org.elm.lang.core.psi.elements.addItem

//import org.elm.openapiext.runWithCancelableProgress

class ItemToMove(val item: ElmExposableTag) : ElementToMove()
//class ModToMove(val mod: ElmMod) : ElementToMove()

sealed class ElementToMove {
    val element: ElmExposableTag
        get() = when (this) {
            is ItemToMove -> item
//            is ModToMove -> mod
        }

    companion object {
        fun fromItem(item: ElmExposableTag): ElementToMove = when (item) {
//            is ElmModItem -> ModToMove(item)
            else -> ItemToMove(item)
        }
    }
}

/**
 * Move refactoring supports moving files (to other directory) or top level items (to other file)
 *
 * ## High-level description
 * 1. Check conflicts (if new mod already has item with same name)
 * 2. Check visibility conflicts (target of any reference should remain accessible after move).
 *    We should check: `ElmPath`, struct/enum field (struct literal, destructuring), struct/trait method call.
 *     - references from moved items (both to old mod and to other mods)
 *     - references to moved items (both from old mod and from other mods)
 * 3. Update `pub(in path)` visibility modifiers for moved items if necessary
 * 4. Move items to new mod
 *     - make moved items public if necessary
 *     - also make public items in old mod if necessary (TODO)
 * 5. Update outside references (from moved items - both to old mod and to other mods)
 *     - replace relative paths (which starts with `super::`)
 *     - add necessary imports (including trait imports - for trait methods)
 *         - usual imports (which already are in old mod)
 *         - imports to items in old mod (which are used by moved items)
 *     - replace paths which are still not resolved - e.g. previously path is absolute,
 *       but after move we should use path through reexports)
 * 6. Update inside references (to moved items - both from old mod and from other mods)
 *     - change existing imports
 *         - remove import if it is in new mod
 *     - fix unresolved paths (including trait methods)
 *
 * ## Implementation notes
 * Most important class is [ElmMoveReferenceInfo].
 * It is used both for inside and outside references and for each [ElmPath] provides new path to replace old path with.
 *
 * "Move file" and "Move items" has different processors (because of different UX).
 * So this class is created to be used by both processors.
 * It provides following methods:
 * 1. [findUsages] — just finds usages and convert them to our class [ElmMoveUsageInfo]
 * 2. [preprocessUsages]
 *     - creates [ElmMoveReferenceInfo] for all references
 *     - checks visibility conflicts
 * 3. [performRefactoring]
 *     - moves items/files
 *     - updates references using [ElmMoveRetargetReferencesProcessor]
 */
class ElmMoveCommonProcessor(
    private val project: Project,
    private var elementsToMove: List<ElementToMove>,
    private val targetMod: ElmFile
) {

    private val psiFactory: ElmPsiFactory = ElmPsiFactory(project)
//    private val codeFragmentFactory: ElmCodeFragmentFactory = ElmCodeFragmentFactory(project)

//    private val sourceMod: ElmFile = elementsToMove
//        .also { if (it.isEmpty()) throw IncorrectOperationException("No items to move") }
////        .map { it.element.containingModStrict }
//        .distinct()
//        .singleOrNull()
//        ?: error("Elements to move must belong to single parent mod")
    private val sourceMod: ElmFile = elementsToMove
        .also { if (it.isEmpty()) throw IncorrectOperationException("No items to move") }
        .singleOrNull()?.element?.elmFile
        ?: error("Elements to move must belong to single parent mod")


//    private val pathHelper: ElmMovePathHelper = ElmMovePathHelper(project, targetMod)
//    private lateinit var conflictsDetector: ElmMoveConflictsDetector
//    private val traitMethodsProcessor: ElmMoveTraitMethodsProcessor =
//        ElmMoveTraitMethodsProcessor(psiFactory, sourceMod, targetMod, pathHelper)
//
//    private lateinit var outsideReferences: List<ElmMoveReferenceInfo>

    init {
        if (targetMod == sourceMod) {
            throw IncorrectOperationException("Source and destination modules should be different")
        }
    }

//    /**
//     * - Direct reference is reference to one of [elementsToMove]:
//     * ```elm
//     * fn usages() {
//     *     item1();
//     *     ~~~~~ direct reference
//     *     item2::func();
//     *     ~~~~~ direct reference
//     * }
//     *
//     * fn item1/*caret*/() {}
//     * mod item2/*caret*/ { pub fn func() {} }
//     * ```
//     *
//     * - Indirect reference is reference to item inside moved mod:
//     *   (if we can't find direct reference using base/parent path)
//     * ```elm
//     * use inner::func;  // this import will remain in source mod!
//     * fn usages() {
//     *     func();
//     *     ~~~~ indirect reference
//     * }
//     *
//     * mod inner/*caret*/ { pub fn func() {} }
//     * ```
//     */
//    fun findUsages(): Array<ElmMoveUsageInfo> {
//        val referencesDirect = findDirectInsideReferences()
//        val referencesIndirect = findIndirectInsideReferences()
//        return (referencesDirect + referencesIndirect)
//            .mapNotNull { createMoveUsageInfo(it) }
//            // sorting is needed for stable results in tests
//            .sortedWith(compareBy({ it.element.containingMod.crateRelativePath }, { it.element.startOffset }))
//            .toTypedArray()
//    }
//
//    private fun findDirectInsideReferences(): List<PsiReference> = elementsToMove
//        .flatMap { ReferencesSearch.search(it.element, GlobalSearchScope.projectScope(project)) }
//
//    private fun findIndirectInsideReferences(): List<PsiReference> =
//        movedElementsShallowDescendantsOfType<ElmPath>(elementsToMove, processInlineModules = false)
//            .filter { path ->
//                if (path.ancestorStrict<ElmUseGroup>() != null || path.path != null) return@filter false
//                val target = path.reference?.resolve() ?: return@filter false
//                // these are direct references
//                if (elementsToMove.any { it is ItemToMove && it.item == target }) return@filter false
//                target.isInsideMovedElements(elementsToMove)
//            }
//            .mapNotNull { it.reference }
//
//    private fun createMoveUsageInfo(reference: PsiReference): ElmMoveUsageInfo? {
//        val element = reference.element
//        val target = reference.resolve() ?: return null
//
//        return when {
//            element is ElmModDeclItem && target is ElmFile -> ElmModDeclUsageInfo(element, target)
//            element is ElmPath && target is ElmQualifiedNamedElement -> ElmPathUsageInfo(element, reference, target)
//            else -> null
//        }
//    }

//    fun preprocessUsages(
//        usages: Array<UsageInfo>,
//        @Suppress("UnstableApiUsage") conflicts: MultiMap<PsiElement, @DialogMessage String>
//    ): Boolean {
//        val title = message("refactoring.preprocess.usages.progress")
//        return try {
//            /**
//             * We need to use [computeWithCancelableProgress] and not [runWithCancelableProgress],
//             * because otherwise any exceptions will be silently ignored.
//             * (exception will only be written to log, `runWithCancelableProgress` will return `true`)
//             */
//            project.computeWithCancelableProgress(title) {
//                runReadAction {
//                    // TODO: two threads
//                    outsideReferences = collectOutsideReferences()
//                    /** Also contains self-references */
//                    val insideReferences = preprocessInsideReferences(usages)
//                    traitMethodsProcessor.preprocessOutsideReferences(conflicts, elementsToMove)
//                    traitMethodsProcessor.preprocessInsideReferences(conflicts, elementsToMove)
//
//                    if (!isUnitTestMode) {
//                        @Suppress("DialogTitleCapitalization")
//                        ProgressManager.getInstance().progressIndicator.text = message("detecting.possible.conflicts")
//                    }
//
//                    conflictsDetector = ElmMoveConflictsDetector(conflicts, elementsToMove, sourceMod, targetMod)
//                    conflictsDetector.detectOutsideReferencesVisibilityProblems(outsideReferences)
//                    conflictsDetector.detectInsideReferencesVisibilityProblems(insideReferences)
//                    conflictsDetector.checkImpls()
//                }
//            }
//            true
//        } catch (e: ProcessCanceledException) {
//            false
//        }
//    }
//
//    private fun collectOutsideReferences(): List<ElmMoveReferenceInfo> {
//        // we should collect:
//        // - absolute references (starts with "::", "crate" or some crate name) to source or target crate
//        // - references which starts with "super"
//        // - references from old mod scope:
//        //     - to items in old mod
//        //     - to something which is imported in old mod
//
//        val references = mutableListOf<ElmMoveReferenceInfo>()
//        for (path in movedElementsDeepDescendantsOfType<ElmPath>(elementsToMove)) {
//            if (path.containingFile == sourceMod.containingFile) {
//                path.putCopyableUserData(RS_PATH_OLD_BEFORE_MOVE_KEY, path)
//            }
//
//            if (path.parent is ElmVisRestriction) continue
//            if (path.containingMod != sourceMod  // path inside nested mod of moved element
//                && !path.isAbsolute()
//                && !path.startsWithSuper()
//            ) continue
//            if (!isSimplePath(path)) continue
//            if (!checkMacroCallPath(path)) continue
//
//            // `use path1::{path2, path3}`
//            //              ~~~~~  ~~~~~ TODO: don't ignore such paths
//            if (path.ancestorStrict<ElmUseGroup>() != null) continue
//
//            val target = path.reference?.resolve() as? ElmQualifiedNamedElement ?: continue
//            // ignore relative references from child modules of moved file
//            // because we handle them as inside references (in `preprocessInsideReferences`)
//            val isSelfReference = target.isInsideMovedElements(elementsToMove)
//            if (isSelfReference) continue
//
//            val reference = createOutsideReferenceInfo(path, target) ?: continue
//            references += reference
//        }
//        for (patIdent in movedElementsShallowDescendantsOfType<ElmPatIdent>(elementsToMove)) {
//            patIdent.putCopyableUserData(RS_PATH_OLD_BEFORE_MOVE_KEY, patIdent)
//            val target = patIdent.patBinding.reference.resolve() as? ElmQualifiedNamedElement ?: continue
//            if (target !is ElmStructItem && target !is ElmEnumVariant && target !is ElmConstant) continue
//            val reference = createOutsideReferenceInfo(patIdent, target) ?: continue
//            references += reference
//        }
//        return references
//    }
//
//    private fun checkMacroCallPath(path: ElmPath): Boolean {
//        if (path.parent !is ElmMacroCall) return true
//        val target = path.reference?.resolve() as? ElmMacroDefinitionBase ?: return false
//        val targetCrate = target.containingCrate
//        // TODO: support references to macros in same crate
//        //  it is complicated: https://doc.elm-lang.org/reference/macros-by-example.html#scoping-exporting-and-importing
//        return targetCrate != sourceMod.containingCrate && target != targetMod.containingCrate
//    }
//
//    private fun createOutsideReferenceInfo(
//        pathOriginal: ElmElement,
//        target: ElmQualifiedNamedElement
//    ): ElmMoveReferenceInfo? {
//        val path = convertFromPathOriginal(pathOriginal, codeFragmentFactory)
//
//        // after move both `path` and its target will belong to `targetMod`
//        // so we can refer to item in `targetMod` just with its name
//        if (path.containingMod == sourceMod && target.containingModStrict == targetMod) {
//            val pathNew = target.name?.toElmPath(psiFactory)
//            if (pathNew != null) {
//                return ElmMoveReferenceInfo(path, pathOriginal, pathNew, pathNew, target, forceReplaceDirectly = true)
//            }
//        }
//
//        if (path.isAbsolute()) {
//            // when moving from binary to library crate, we should change path `library_crate::...` to `crate::...`
//            // when moving from one library crate to another, we should change path `crate::...` to `first_library::...`
//            val basePathTarget = path.basePath().reference?.resolve() as? ElmMod
//            if (basePathTarget != null
//                && basePathTarget.crateRoot != sourceMod.crateRoot
//                && basePathTarget.crateRoot != targetMod.crateRoot
//            ) return null  // not needed to change path
//
//            // ideally this check is enough and above check is not needed
//            // but for some paths (e.g. `base64::decode`) `pathNew.reference.resolve()` is null,
//            // though actually path will be resolved correctly after move
//            val pathNew = path.textNormalized.toElmPath(codeFragmentFactory, targetMod)
//            if (pathNew != null && pathNew.resolvesToAndAccessible(target)) return null  // not needed to change path
//        }
//
//        val pathNewFallback = if (path.containingMod == sourceMod) {
//            // after move `path` will belong to `targetMod`
//            target.qualifiedNameRelativeTo(targetMod)
//                ?.toElmPath(codeFragmentFactory, targetMod)
//        } else {
//            target.qualifiedNameInCrate(targetMod)
//                ?.toElmPathInEmptyTmpMod(codeFragmentFactory, psiFactory, targetMod)
//        }
//        val pathNewAccessible = ElmImportHelper.findPath(targetMod, target)
//            ?.toElmPathInEmptyTmpMod(codeFragmentFactory, psiFactory, targetMod)
//
//        return ElmMoveReferenceInfo(path, pathOriginal, pathNewAccessible, pathNewFallback, target)
//    }
//
//    /** Also processes self references */
//    private fun preprocessInsideReferences(usages: Array<UsageInfo>): List<ElmMoveReferenceInfo> {
//        val pathUsages = usages.filterIsInstance<ElmPathUsageInfo>()
//        for (usage in pathUsages) {
//            usage.referenceInfo = createInsideReferenceInfo(usage.element, usage.target)
//        }
//
//        val originalReferences = pathUsages.map { it.referenceInfo }
//        for (usage in pathUsages) {
//            usage.referenceInfo = convertToFullReference(usage.referenceInfo) ?: usage.referenceInfo
//
//            val target = usage.referenceInfo.target
//            target.putCopyableUserData(RS_TARGET_BEFORE_MOVE_KEY, target)
//
//            /** [RS_PATH_OLD_BEFORE_MOVE_KEY] for self references is filled in [collectOutsideReferences] */
//        }
//        return originalReferences
//    }
//
//    private fun createInsideReferenceInfo(pathOriginal: ElmPath, target: ElmQualifiedNamedElement): ElmMoveReferenceInfo {
//        val path = convertFromPathOriginal(pathOriginal, codeFragmentFactory)
//
//        val isSelfReference = pathOriginal.isInsideMovedElements(elementsToMove)
//        if (isSelfReference && path.containingMod == sourceMod) {
//            if (target.containingModStrict == sourceMod) {  // inside reference to moved item
//                // after move path will be in `targetMod`, so we can refer to moved item just with its name
//                val pathNew = target.name?.toElmPath(codeFragmentFactory, targetMod)
//                if (pathNew != null) return ElmMoveReferenceInfo(path, pathOriginal, pathNew, pathNew, target)
//            } else run {
//                val pathOldAbsolute = ElmImportHelper.findPath(path, target) ?: return@run
//                val sourceModPrefix = "crate${sourceMod.crateRelativePath}::"
//                if (pathOldAbsolute.startsWith(sourceModPrefix)) {
//                    val pathRelativeToSourceMod = pathOldAbsolute.removePrefix(sourceModPrefix)
//                    val pathNew = pathRelativeToSourceMod.toElmPath(codeFragmentFactory, targetMod)
//                    return ElmMoveReferenceInfo(path, pathOriginal, pathNew, pathNew, target)
//                }
//            }
//        }
//
//        val pathNewAccessible = pathHelper.findPathAfterMove(path, target)
//        val pathNewFallback = run {
//            val targetModPath = targetMod.qualifiedNameRelativeTo(path.containingMod) ?: return@run null
//            val targetName = target.name ?: return@run null
//            val pathNewFallbackText = "$targetModPath::$targetName"
//            pathNewFallbackText.toElmPath(codeFragmentFactory, context = path.context as? ElmElement ?: path)
//        }
//        return ElmMoveReferenceInfo(path, pathOriginal, pathNewAccessible, pathNewFallback, target)
//    }
//
//    /**
//     * This method is needed in order to work with references to [ElmItemElement]s, and not with references to [ElmMod]s.
//     * It is needed when one of moved elements is [ElmMod].
//     */
//    private fun convertToFullReference(reference: ElmMoveReferenceInfo): ElmMoveReferenceInfo? {
//        // Examples:
//        // `mod1::mod2::mod3::Struct::<T>::func::<R>();`
//        //  ^~~~~~~~~^ reference.pathOld
//        //  ^~~~~~~~~~~~~~~~~~~~~~~~~~~~^ pathOldOriginal
//        //  ^~~~~~~~~~~~~~~~~~~~~~~^ pathOld
//        //
//        // `use mod1::mod2::mod3;`
//        //      ^~~~~~~~~^ reference.pathOld
//        //      ^~~~~~~~~~~~~~~^ pathOldOriginal == pathOld
//
//        if (isSimplePath(reference.pathOld) || reference.isInsideUseDirective) return null
//        val pathOldOriginal = reference.pathOldOriginal.ancestors
//            .takeWhile { it is ElmPath }
//            .map { it as ElmPath }
//            .firstOrNull { isSimplePath(it) }
//            ?: return null
//        val pathOld = convertFromPathOriginal(pathOldOriginal, codeFragmentFactory)
//        if (!pathOld.textNormalized.startsWith(reference.pathOld.textNormalized)) {
//            LOG.error("Expected '${pathOld.text}' to start with '${reference.pathOld.text}'")
//            return null
//        }
//
//        if (pathOld.containingFile is DummyHolder) LOG.error("Path '${pathOld.text}' is inside dummy holder")
//        val target = pathOld.reference?.resolve() as? ElmQualifiedNamedElement ?: return null
//
//        fun convertPathToFull(path: ElmPath): ElmPath? {
//            val pathFullText = pathOld.textNormalized
//                .replaceFirst(reference.pathOld.textNormalized, path.textNormalized)
//            val pathFull = pathFullText.toElmPath(codeFragmentFactory, path) ?: return null
//            if (pathFull.containingFile is DummyHolder) LOG.error("Path '${pathFull.text}' is inside dummy holder")
//            return pathFull
//        }
//
//        val pathNewAccessible = reference.pathNewAccessible?.let { convertPathToFull(it) }
//        val pathNewFallback = reference.pathNewFallback?.let { convertPathToFull(it) }
//
//        return ElmMoveReferenceInfo(pathOld, pathOldOriginal, pathNewAccessible, pathNewFallback, target)
//    }

    fun performRefactoring(usages: Array<out UsageInfo>, moveElements: () -> List<ElementToMove>) {
        val element: ElmFunctionDeclarationLeft = this.elementsToMove.first().element as ElmFunctionDeclarationLeft
        targetMod.add(psiFactory.createDeclaration(element.parent.text))
        targetMod.getModuleDecl()?.exposingList?.addItem(element.name)
//        updateOutsideReferencesInVisRestrictions()

        elementsToMove = moveElements()
//        val pathMapping = createMapping(RS_PATH_OLD_BEFORE_MOVE_KEY, ElmElement::class.java)

//        val retargetReferencesProcessor = ElmMoveRetargetReferencesProcessor(project, sourceMod, targetMod)
//        restoreOutsideReferenceInfosAfterMove(pathMapping)
//        retargetReferencesProcessor.retargetReferences(outsideReferences)
//
//        traitMethodsProcessor.addTraitImportsForOutsideReferences(elementsToMove)
//        traitMethodsProcessor.addTraitImportsForInsideReferences()

//        val insideReferences = usages
//            .filterIsInstance<ElmPathUsageInfo>()
//            .map { it.referenceInfo }
//        updateInsideReferenceInfosIfNeeded(insideReferences, pathMapping)
//        retargetReferencesProcessor.retargetReferences(insideReferences)
//        retargetReferencesProcessor.optimizeImports()
    }

//    private fun updateOutsideReferencesInVisRestrictions() {
//        for (visRestriction in movedElementsDeepDescendantsOfType<ElmVisRestriction>(elementsToMove)) {
//            visRestriction.updateScopeIfNecessary(psiFactory, targetMod)
//        }
//    }
//
//    /**
//     * Each outside reference is associated with some [ElmPath] inside moved items.
//     * After move this [ElmPath] is invalidated and new [ElmPath] is created.
//     * We store [ElmMoveReferenceInfo] for outside reference in copyable user data for this [ElmPath].
//     */
//    private fun restoreOutsideReferenceInfosAfterMove(pathMapping: Map<ElmElement, ElmElement>) {
//        for (reference in outsideReferences) {
//            reference.restorePathOldAfterMove(pathMapping)
//        }
//    }
//
//    /**
//     * After move old items are invalidated and new items ([ElmElement]s) are created.
//     * Thus we have to change `target` for inside references and change `pathOld` for self references.
//     */
//    private fun updateInsideReferenceInfosIfNeeded(
//        references: List<ElmMoveReferenceInfo>,
//        pathMapping: Map<ElmElement, ElmElement>
//    ) {
//        val targetMapping = createMapping(RS_TARGET_BEFORE_MOVE_KEY, ElmQualifiedNamedElement::class.java)
//        for (reference in references) {
//            reference.restorePathOldAfterMove(pathMapping)
//
//            val targetRestored = targetMapping[reference.target]
//            // it is ok if `targetRestored` is null when target was inside a file which is a submodule of moved items
//            // (so after move existing `target` remains valid)
//            if (targetRestored != null) {
//                reference.target = targetRestored
//            } else if (reference.target.containingFile is DummyHolder) {
//                LOG.error("Can't restore target ${reference.target}" +
//                    " for reference '${reference.pathOldOriginal.text}' after move")
//            }
//        }
//    }
//
//    /** See [RS_PATH_OLD_BEFORE_MOVE_KEY] */
//    private fun <T : ElmElement> createMapping(key: Key<T>, aClass: Class<T>): Map<T, T> {
//        return movedElementsShallowDescendantsOfType(elementsToMove, aClass)
//            .mapNotNull { element ->
//                val elementOld = element.getCopyableUserData(key) ?: return@mapNotNull null
//                element.putCopyableUserData(key, null)
//                elementOld to element
//            }
//            .toMap()
//    }
//
//    private fun ElmMoveReferenceInfo.restorePathOldAfterMove(pathMapping: Map<ElmElement, ElmElement>) {
//        pathOldOriginal = pathMapping[pathOldOriginal] ?: return
//        pathOld = convertFromPathOriginal(pathOldOriginal, codeFragmentFactory)
//    }
//
//    fun updateMovedItemVisibility(item: ElmItemElement) {
//        when (item.visibility) {
//            is ElmVisibility.Private -> {
//                if (conflictsDetector.itemsToMakePublic.contains(item)) {
//                    val itemName = item.name
//                    val containingFile = item.containingFile
//                    if (item !is ElmNameIdentifierOwner) {
//                        LOG.error("Unexpected item to make public: $item")
//                        return
//                    }
//                    MakePublicFix.createIfCompatible(item, itemName, crateRestricted = false)
//                        ?.invoke(project, null, containingFile)
//                }
//            }
//            is ElmVisibility.Restricted -> run {
//                val visRestriction = item.vis?.visRestriction ?: return@run
//                visRestriction.updateScopeIfNecessary(psiFactory, targetMod)
//            }
//            ElmVisibility.Public -> Unit  // already public, keep as is
//        }
//    }

//    companion object {
//        /**
//         * Used by outside references and self references (we treat them as inside references).
//         * We store `pathOld` of such reference in `copyableUserData` for `pathOld` itself.
//         * So after move we can create mapping between original [ElmPath] in [sourceMod] and its copy in [targetMod],
//         * and use this mapping to update `pathOld` in [ElmMoveReferenceInfo].
//         * (Type is [ElmElement] and not [ElmPath] because path to nullary enum variant in bindings is [ElmPatIdent])
//         */
//        private val RS_PATH_OLD_BEFORE_MOVE_KEY: Key<ElmElement> = Key.create("RS_PATH_OLD_BEFORE_MOVE_KEY")
//
//        /**
//         * Used by inside references (to descendants of moved items - but only in same file as [sourceMod]).
//         * We store `target` of such reference in `copyableUserData` for `target` itself.
//         * So after move we can create mapping between original [ElmElement] in [sourceMod] and its copy in [targetMod],
//         * and use this mapping to update `target` in [ElmMoveReferenceInfo].
//         */
//        private val RS_TARGET_BEFORE_MOVE_KEY: Key<ElmQualifiedNamedElement> = Key.create("RS_TARGET_BEFORE_MOVE_KEY")
//    }
}
