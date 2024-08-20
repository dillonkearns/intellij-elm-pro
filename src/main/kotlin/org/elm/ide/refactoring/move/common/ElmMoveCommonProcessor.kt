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
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.addIfNotNull
import org.elm.lang.core.imports.ImportAdder
import org.elm.lang.core.psi.*
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.resolve.ElmReferenceElement
import org.elm.lang.core.resolve.reference.QualifiedValueReference
import org.elm.lang.core.resolve.reference.SimpleUnionConstructorReference
import org.elm.lang.core.resolve.reference.SimpleUnionOrRecordConstructorReference
import org.elm.lang.core.resolve.scope.ModuleScope
import org.elm.openapiext.saveAllDocuments

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
        .map { it.element.elmFile }
        .toSet()
        .toList()
        .singleOrNull()
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

    fun findUsages(): Array<ElmMoveUsageInfo> {
        val referencesDirect = findDirectInsideReferences()
        return referencesDirect
            .mapNotNull { createMoveUsageInfo(it) }
            // sorting is needed for stable results in tests
            .sortedWith(compareBy({ it.element.elmFile.name }, { it.element.startOffset }))
            .toTypedArray()
    }

    private fun findDirectInsideReferences(): List<PsiReference> = elementsToMove
        .flatMap { ReferencesSearch.search(it.element, GlobalSearchScope.projectScope(project)) }

    private fun createMoveUsageInfo(reference: PsiReference): ElmMoveUsageInfo? {
        val element = reference.element as? ElmValueExpr ?: return null
        val target = reference.resolve() as? ElmNamedElement ?: return null

        return ElmPathUsageInfo(element, reference, target)
    }

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
        val containingDefinitions = elementsToMove.map {
            it.element.containingTopLevelDefinition()
        }
        usages.forEach { usage ->
            val ref = (usage as ElmPathUsageInfo).element
            ImportAdder.addImport(ImportAdder.Import(targetMod.getModuleDecl()!!.upperCaseQID.text, null, ref.referenceName), ref.elmFile, true)
            val usageIsBeingMoved = containingDefinitions.contains(ref.containingTopLevelDefinition()) || usage.element.containingFile == targetMod
            ref.replace(psiFactory.createValueQID(
                if (usageIsBeingMoved) {
                    ref.referenceName
                } else {
                    "${targetMod.getModuleDecl()?.name}.${ref.referenceName}"
                }
            ))
        }
        this.elementsToMove.forEach { moveElement(it.element) }
        invokeLater {
            saveAllDocuments()
        }
    }

    private fun moveElement(
        element: ElmExposableTag
    ) {
        val exposedItem = sourceMod.getModuleDecl()?.exposingList?.findMatchingItemFor(element)
        val annotation = (element.parent as? ElmValueDeclaration)?.typeAnnotation
        val docComment = (element.parent as? ElmValueDeclaration)?.docComment
        (element as? ElmFunctionDeclarationLeft)?.body?.descendantsOfType<ElmValueExpr>()
            ?.forEach { e ->
                if (e.reference.resolve()?.moduleName == targetMod.getModuleDecl()?.name) {
                    e.replace(psiFactory.createValueQID(e.referenceName))
                }
            }
        val targetModuleImports = targetMod.getImportClauses().map {
            ImportInfo(it.referenceName, it.asClause?.name)
        }
        val sourceImports = sourceMod.getImportClauses().map {
            ImportInfo(it.referenceName, it.asClause?.name)
        }
        val conflictingImports = targetModuleImports.mapNotNull { targetImport ->
            sourceImports.find { targetImport.moduleName == it.moduleName && targetImport.aliasName != it.aliasName }?.let { conflict ->
                ConflictingImport(conflict, targetImport)
            }

        }
        val annotationImportsToAdd = annotation?.typeExpression?.allSegmentsRecursively.orEmpty().mapNotNull { segment ->
            when (segment) {
                is ElmTypeRef -> {
                    if (!segment.upperCaseQID.isQualified) {
                        // make the type reference fully qualified
                        val moduleName = ModuleScope.getVisibleTypes(sourceMod)[segment.upperCaseQID.refName]?.moduleName
                        val conflictingImport = targetModuleImports.find { importInfo -> importInfo.moduleName == moduleName }
                        val qualifiedName = listOf(conflictingImport?.resolveModuleName() ?: moduleName, segment.upperCaseQID.refName).mapNotNull { it }.joinToString(".")
                        segment.upperCaseQID.replace(psiFactory.createUpperCaseQID(qualifiedName))
                        if (moduleName != null) {
                            ImportAdder.Import(moduleName, null, segment.upperCaseQID.refName)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                else -> {
                    null
                }
            }
        }
        (element as? ElmFunctionDeclarationLeft)?.body?.descendantsOfType<ElmUnionPattern>()
        val fdlBody = (element as? ElmFunctionDeclarationLeft)?.body
        val list2: Collection<ElmReferenceElement> = fdlBody?.descendantsOfType<ElmUnionPattern>().orEmpty()//.map { it.upperCaseQID }
        val expressionsToMap: Collection<ElmReferenceElement> = fdlBody?.descendantsOfType<ElmValueExpr>().orEmpty().plus(list2)
         expressionsToMap.mapNotNull {
            val text = it.text
            when (val ref = it.reference) {
                is QualifiedValueReference -> {
                    val conflictingImport = conflictingImports.find { importInfo -> importInfo.source.resolveModuleName() == ref.qualifierPrefix }
                    val sourceImport = sourceImports.find { importInfo -> importInfo.resolveModuleName() == ref.qualifierPrefix }
                    if (conflictingImport == null) {
                        // TODO if it's not fully qualified, fully qualify it here. Could revist a feature to pull in the exposed values from `import ... exposing (...) `, but that introduces
                        // the complexity of introducing possible name collisions to the target module. So even with that feature, fully qualifying values could be a good strategy for avoiding name collisions since that's the only way to resolve it gracefully.

                        if (sourceImport != null) {
                            ImportAdder.Import(sourceImport.moduleName, sourceImport.aliasName, ref.canonicalText)
                        } else {
                            null
                        }
                    } else {
                        val valueExpr = (ref.element as ElmValueExpr).valueQID!!
                        updateQid(valueExpr, conflictingImport)
                        null
                    }
                }
                is SimpleUnionOrRecordConstructorReference, is SimpleUnionConstructorReference -> {

                    val visibleValue = ModuleScope.getVisibleConstructors(sourceMod)[ref.canonicalText]
                    if (visibleValue == null) {
                        // TODO make sure this won't introduce a circular import
                        null
                    } else {
                        val (newImport, newReferenceName) = findConflictingImport(targetModuleImports, visibleValue.moduleName, ref.canonicalText)
                        it.directChildrenOfType<ElmUpperCaseQID>().first().replace(psiFactory.createUpperCaseQID(
                            newReferenceName
                        ))
                        newImport
                    }


                }
                else -> {
                    val visibleValue = ModuleScope.getVisibleValues(sourceMod)[ref.canonicalText]
                    val conflictingImport = targetModuleImports.find { importInfo -> importInfo.moduleName == visibleValue?.moduleName }
                    if (visibleValue == null) {
                        // TODO make sure this won't introduce a circular import
                        null

                    } else {
                        val moduleName = visibleValue.moduleName
                        it.replace(psiFactory.createValueQID("${conflictingImport?.resolveModuleName() ?: moduleName}.${ref.canonicalText}"))
                        ImportAdder.Import(
                            ModuleScope.getVisibleValues(sourceMod)[ref.canonicalText]!!.moduleName,
                            null,
                            ref.canonicalText
                        )
                    }
                }
            }
        }.let { importsToAdd ->
            importsToAdd.forEach { import ->
                ImportAdder.addImport(import, targetMod, true)
            }
            annotationImportsToAdd.forEach { import ->
                ImportAdder.addImport(import, targetMod, true)
            }
        }
        // TODO add imports in case of type declaration or type alias as well
        val targetText = element.parent.text
        when (element) {
            is ElmFunctionDeclarationLeft -> {
                if (annotation != null) {
                    val elements = mutableListOf<PsiElement>()
                    elements.addIfNotNull(docComment)

                    elements.addAll(psiFactory.createTopLevelFunctionWithAnnotation(annotation.text, targetText))
                    targetMod.addAll(elements)
                    docComment?.delete()
                    annotation.delete()
                    elements.filterIsInstance<ElmValueDeclaration>().singleOrNull()!!.modificationTracker.incModificationCount()
                } else {
                    targetMod.add(psiFactory.createDeclaration(targetText + "\n\n"))
                    targetMod.add(psiFactory.createWhitespace("\n"))
                    targetMod.add(psiFactory.createWhitespace("\n"))
                }
            }
            is ElmTypeDeclaration -> {
                targetMod.add(psiFactory.createTypeDeclaration(targetText + "\n\n"))
                targetMod.add(psiFactory.createWhitespace("\n"))
                targetMod.add(psiFactory.createWhitespace("\n"))
            }
            else -> {
                TODO()
            }
        }
        targetMod.getModuleDecl()?.exposingList?.addItem(element.name)
        val todoRemoveThis =
            targetMod.getModuleDecl()?.exposingList?.allExposedItems?.find { it.text == "todoRemoveThis" }
        if (todoRemoveThis != null) {
            targetMod.getModuleDecl()?.exposingList?.removeItem(todoRemoveThis)
        }
        when (element) {
            is ElmFunctionDeclarationLeft -> {
                element.parent.delete()
            }
            is ElmTypeDeclaration -> {
                element.delete()
            }
            else -> {
                TODO()
            }
        }
        if (exposedItem != null) {
            if ((exposedItem.elmFile.getModuleDecl()?.exposingList?.allExposedItems?.size ?: 0) == 1) {
                exposedItem.elmFile.getModuleDecl()?.exposingList?.addItem("stub")
                sourceMod.add(psiFactory.createDeclaration("stub = ()"))
            }
            sourceMod.getModuleDecl()?.exposingList?.removeItem(exposedItem)
        }

        //        updateOutsideReferencesInVisRestrictions()

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

    private fun findConflictingImport(
        targetModuleImports: List<ImportInfo>,
        moduleName: String,
        canonicalText: String
    ): Pair<ImportAdder.Import?, String> {

        val existingImport = targetModuleImports.find { importInfo -> importInfo.moduleName == moduleName }
        return Pair(
            if (existingImport == null) {
                ImportAdder.Import(
                    moduleName,
                    null,
                    canonicalText
                )
            } else {
                null
            }

            ,
            "${existingImport?.resolveModuleName() ?: moduleName}.${canonicalText}")
    }

    private fun updateQid(valueExpr: ElmValueQID, conflictingImport: ConflictingImport) {
        valueExpr.replace(
            psiFactory.createValueQID(
                valueExpr.text.replace(
                    conflictingImport.source.resolveModuleName(),
                    conflictingImport.target.resolveModuleName()
                )
            )
        )
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

data class ImportInfo(val moduleName: String, val aliasName: String?) {
   fun resolveModuleName(): String = aliasName ?: moduleName
}
data class ConflictingImport(val source: ImportInfo, val target: ImportInfo)

sealed class ElmMoveUsageInfo(open val element: ElmPsiElement) : UsageInfo(element)

class ElmPathUsageInfo(
    override val element: ElmValueExpr,
    private val elmReference: PsiReference,
    val target: ElmNamedElement
) : ElmMoveUsageInfo(element) {
//    lateinit var referenceInfo: ElmMoveReferenceInfo

    override fun getReference(): PsiReference = elmReference
}
