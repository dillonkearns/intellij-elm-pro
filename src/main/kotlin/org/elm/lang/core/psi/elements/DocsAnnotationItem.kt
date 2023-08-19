package org.elm.lang.core.psi.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.elm.lang.core.psi.ElmStubbedElement
import org.elm.lang.core.psi.ElmTypes
import org.elm.lang.core.psi.ElmTypes.*
import org.elm.lang.core.psi.tokenSetOf
import org.elm.lang.core.resolve.ElmReferenceElement
import org.elm.lang.core.resolve.reference.DocsValueModuleReference
import org.elm.lang.core.resolve.reference.ElmReference
import org.elm.lang.core.stubs.ElmPlaceholderRefStub


/**
 * A value or type within an @docs annotation list.
 *
 * e.g. `Decoder` and `succeed` in `@docs Decoder, succeed`.
 */
class DocsAnnotationItem : ElmStubbedElement<ElmPlaceholderRefStub>, ElmReferenceElement {

    constructor(node: ASTNode) :
            super(node)

    constructor(stub: ElmPlaceholderRefStub, stubType: IStubElementType<*, *>) :
            super(stub, stubType)


    override val referenceNameElement: PsiElement
        get() =
            findChildByClass(ElmExposedOperator::class.java) ?: findNotNullChildByType(
                tokenSetOf(
                    OPERATOR_IDENTIFIER,
                    LOWER_CASE_IDENTIFIER,
                    UPPER_CASE_IDENTIFIER
                )
            )

    override val referenceName: String
        get() = stub?.refName ?: referenceNameElement.text

    override fun getReference(): ElmReference {
        return DocsValueModuleReference(this)
    }
}
