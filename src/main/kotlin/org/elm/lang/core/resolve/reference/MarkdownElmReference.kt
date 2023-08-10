package org.elm.lang.core.resolve.reference

import com.intellij.util.containers.headTail
import com.intellij.util.containers.headTailOrNull
import org.elm.lang.core.psi.ElmNamedElement
import org.elm.lang.core.psi.elements.MarkdownElmRef
import org.elm.lang.core.resolve.scope.DeclaredNames
import org.elm.lang.core.resolve.scope.GlobalScope
import org.elm.lang.core.resolve.scope.ModuleScope
import org.elm.lang.core.stubs.index.ElmModulesIndex

class MarkdownElmReference(docsItem: MarkdownElmRef) : ElmReferenceCached<MarkdownElmRef>(docsItem) {

    override fun resolveInner(): ElmNamedElement? {
        val splitList = element.text.split('#')
        val targetModule = splitList[0].ifEmpty { null }
        val targetValueTypeOrOperator = splitList.getOrNull(1)?.ifEmpty { null }
        val thisFile = element.elmFile
        if (targetModule == null) {
            return ModuleScope.getDeclaredValues(thisFile)
                .plus(ModuleScope.getDeclaredTypes(thisFile))[targetValueTypeOrOperator]
        } else {
            val resolvedTargetModule = ElmModulesIndex.get(targetModule, thisFile)?.elmFile ?: return null
            return if (targetValueTypeOrOperator == null) {
                resolvedTargetModule.getModuleDecl()
            } else {
                ModuleScope.getDeclaredValues(resolvedTargetModule).plus(ModuleScope.getDeclaredTypes(resolvedTargetModule))[targetValueTypeOrOperator]
            }
        }
    }

    override fun getVariants(): Array<ElmNamedElement> = emptyArray()
}
