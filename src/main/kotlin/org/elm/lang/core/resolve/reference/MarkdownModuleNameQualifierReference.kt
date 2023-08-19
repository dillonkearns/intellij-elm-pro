package org.elm.lang.core.resolve.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.elm.lang.core.psi.ElmNamedElement
import org.elm.lang.core.resolve.ElmReferenceElement
import org.elm.lang.core.resolve.scope.GlobalScope
import org.elm.lang.core.stubs.index.ElmModulesIndex

/**
 * A module-name (or alias) reference which qualifies a name from the value or type namespaces.
 *
 * e.g. `Data.User` in the expression `[See name function](Data.User#name)` or `[See name function](Data-User#name)`.
 *
 *
 * @param elem the Psi element which owns the reference
 * @param qualifierPrefix the qualifier string, e.g. `Data.User`
 */
class MarkdownModuleNameQualifierReference<T : ElmReferenceElement>(
    elem: T,
    qualifierPrefix: String
) : ElmReferenceCached<T>(elem), ElmReference {

    private val refText: String = qualifierPrefix

    override fun resolveInner(): ElmNamedElement? {
        val targetModuleName = GlobalScope.defaultAliases[refText] ?: refText
        return ElmModulesIndex.get(targetModuleName, element.elmFile)
    }

    override fun calculateDefaultRangeInElement(): TextRange {
        return TextRange.allOf(refText)
    }
}
