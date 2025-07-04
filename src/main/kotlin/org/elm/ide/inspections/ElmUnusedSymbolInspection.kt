package org.elm.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
import com.intellij.psi.search.searches.ReferencesSearch
import org.elm.ide.settings.experimentalFlags
import org.elm.lang.core.psi.*
import org.elm.lang.core.psi.elements.*

/**
 * Find unused functions, parameters, etc.
 */
class ElmUnusedSymbolInspection : ElmLocalInspection() {

    override fun visitElement(element: ElmPsiElement, holder: ProblemsHolder, isOnTheFly: Boolean) {
        if (element is ElmAnythingPattern && element.containingTopLevelDefinition() is ElmFunctionDeclarationLeft && element.parent !is ElmPattern) {
            holder.registerProblem(
                element,
                "Wildcard can be removed",
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                RemoveUnusedParameterFix()
            )
            return
        }
        if (element !is ElmNameIdentifierOwner) return
        val project = element.project
        val scope = element.useScope
        val name = element.name

        // ignore certain kinds of declarations which we don't want to inspect
        if (element is ElmTypeAliasDeclaration ||
                element is ElmTypeDeclaration ||
                element is ElmTypeVariable ||
                element is ElmFieldType ||
                element is ElmLowerPattern && element.parent is ElmRecordPattern) {
            // TODO revisit: implementation for types is a little tricky since
            //      type annotations are optional; punting for now
            return
        }

        if (isProgramEntryPoint(element)) return

        if (scope is GlobalSearchScope) {
            // to keep inspection/analysis time brief, bail out if 'Find Usages' will be slow
            val searchCost = PsiSearchHelper.getInstance(project).isCheapEnoughToSearch(name, scope, null, null)
            if (searchCost == TOO_MANY_OCCURRENCES) return
        }

        // perform Find Usages
        val usages = ReferencesSearch.search(element).findAll()
                .filterNot { it.element is ElmTypeAnnotation || it.element is ElmExposedItemTag || it.element is DocsAnnotationItem || it.element is MarkdownElmRef }

        if (usages.isEmpty()) {
            markAsUnused(holder, element, name)
        }
    }

    private fun isProgramEntryPoint(element: ElmNameIdentifierOwner): Boolean {
        return when (element) {
            is ElmFunctionDeclarationLeft -> element.name == "main" || element.isElmTestEntryPoint()
            is ElmPortAnnotation -> isExposed(element)
            else -> false
        }
    }


    private fun markAsUnused(holder: ProblemsHolder, element: ElmNameIdentifierOwner, name: String) {
        val fixes = when (element) {
            is ElmLowerPattern ->
                if (holder.project.experimentalFlags.wipFeaturesEnabled) {
                    arrayOf(RenameToWildcardFix(), RemoveUnusedParameterFix())
                } else {
                    arrayOf(RenameToWildcardFix())
                }
            else -> emptyArray()
            // TODO should I re-enable RemoveUnused, or is it obsolete with elm-review integration?
//            else -> arrayOf(RemoveUnusedFix())
        }
        holder.registerProblem(
                element.nameIdentifier,
                "'$name' is never used",
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                *fixes
        )
    }
}

private class RenameToWildcardFix : NamedQuickFix("Rename to _") {
    override fun applyFix(element: PsiElement, project: Project) {
        (element.parent as? ElmLowerPattern)
                ?.replace(ElmPsiFactory(project).createAnythingPattern())
    }
}


private class RemoveUnusedParameterFix : NamedQuickFix("Remove parameter") {
    override fun applyFix(element: PsiElement, project: Project) {
        val targetElement = if (element is ElmAnythingPattern) { element } else { element.parent } as ElmPsiElement
        val containingDefinition = targetElement.containingTopLevelDefinition() as ElmFunctionDeclarationLeft
        val refs = ReferencesSearch.search(containingDefinition).findAll().toList().mapNotNull { (it.element.parent as? ElmFunctionCallExpr) }
        val arguments = (containingDefinition).patterns.toList()
        val argumentIndex = arguments.indexOf(targetElement)
        val maybeAnnotation: ElmTypeAnnotation? = ReferencesSearch.search(containingDefinition).findAll().toList()
            .firstNotNullOfOrNull { (it.element as? ElmTypeAnnotation) }

        maybeAnnotation?.let { annotation ->
            val segments = annotation.typeExpression?.allSegments?.map { it.text }?.filterIndexed { index, _ -> index != argumentIndex }?.joinToString(" -> ").orEmpty()
            val newAnnotation = ElmPsiFactory(project).createTypeAnnotation("${annotation.referenceName} : $segments")
            maybeAnnotation.replace(newAnnotation)
        }

        refs.forEach { ref ->
            ref.arguments.elementAt(argumentIndex).delete()
        }
        targetElement.delete()
    }
}


private class RemoveUnusedFix : NamedQuickFix("Delete") {
    override fun applyFix(element: PsiElement, project: Project) {
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
                .forEach {it.delete()}
    }
}

private fun ElmFunctionDeclarationLeft.isElmTestEntryPoint(): Boolean {
    val decl = parentOfType<ElmValueDeclaration>() ?: return false
    if (!decl.isTopLevel) return false
    val typeAnnotation = decl.typeAnnotation ?: return false

    // HACK: string suffix match is very naive, but it's cheap and easy to test.
    // The right thing to do would be to verify that the type resolves to
    // a type declared in the `elm-test` package. But setting that up for
    // `ElmUnusedSymbolInspectionTest` is a pain.
    // TODO revisit this later
    if (!typeAnnotation.text.endsWith(" : Test") && !typeAnnotation.text.endsWith(" : Test.Test"))
        return false

    // The elm-test runner requires that the entry-point be exposed by the module
    return isExposed(this)
}


private fun isExposed(decl: ElmExposableTag): Boolean {
    val exposingList = decl.elmFile.getModuleDecl()?.exposingList ?: return false
    return exposingList.exposes(decl)
}
