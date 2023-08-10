package org.elm.lang.core.resolve.reference

import org.elm.lang.core.psi.ElmNamedElement
import org.elm.lang.core.psi.elements.MarkdownElmRef
import org.elm.lang.core.resolve.scope.DeclaredNames
import org.elm.lang.core.resolve.scope.GlobalScope
import org.elm.lang.core.resolve.scope.ModuleScope

class MarkdownElmReference(docsItem: MarkdownElmRef)
    : ElmReferenceCached<MarkdownElmRef>(docsItem) {

    override fun resolveInner(): ElmNamedElement? = getCandidates()[element.referenceName]

    override fun getVariants(): Array<ElmNamedElement> = getCandidates().array

    private fun getCandidates(): DeclaredNames {
        // TODO does this account for non-imported modules?
        val globalScope = GlobalScope.forElmFile(element.elmFile)
        val declaredValues = ModuleScope.getDeclaredValues(element.elmFile)
        val declaredTypes = ModuleScope.getDeclaredTypes(element.elmFile)
        return declaredValues.plus(declaredTypes).plus(DeclaredNames(globalScope?.getVisibleTypes() ?: emptyList()))
    }
}
