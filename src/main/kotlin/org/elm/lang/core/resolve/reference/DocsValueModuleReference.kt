package org.elm.lang.core.resolve.reference

import org.elm.lang.core.psi.ElmNamedElement
import org.elm.lang.core.psi.elements.DocsAnnotationItem
import org.elm.lang.core.resolve.scope.DeclaredNames
import org.elm.lang.core.resolve.scope.ModuleScope

/**
 * A value reference from an '@docs' list in a module declaration (points within the same file)
 */
class DocsValueModuleReference(docsItem: DocsAnnotationItem)
    : ElmReferenceCached<DocsAnnotationItem>(docsItem) {

    override fun resolveInner(): ElmNamedElement? = getCandidates()[element.referenceName]

    override fun getVariants(): Array<ElmNamedElement> = getCandidates().array

    private fun getCandidates(): DeclaredNames {
        val declaredValues = ModuleScope.getDeclaredValues(element.elmFile)
        val declaredTypes = ModuleScope.getDeclaredTypes(element.elmFile)
        return declaredValues.plus(declaredTypes)
    }
}
