package org.elm.lang.core.psi.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.elm.lang.core.psi.ElmStubbedElement
import org.elm.lang.core.resolve.ElmReferenceElement
import org.elm.lang.core.resolve.reference.ElmReference
import org.elm.lang.core.resolve.reference.MarkdownElmReference
import org.elm.lang.core.stubs.ElmPlaceholderRefStub


/**
 * A value or type within a Markdown link in an Elm doc comment.
 *
 * e.g. `Decoder` in `{-| See also [Decoder](Json.Decode.Decoder). -}`
 */
class MarkdownElmRef : ElmStubbedElement<ElmPlaceholderRefStub>, ElmReferenceElement {

    constructor(node: ASTNode) :
            super(node)

    constructor(stub: ElmPlaceholderRefStub, stubType: IStubElementType<*, *>) :
            super(stub, stubType)


    override val referenceNameElement: PsiElement
        get() = this
             // TODO consider refining down the reference name element to be more narrow for better highlighting
//            findChildByClass(ElmExposedOperator::class.java) ?: findNotNullChildByType(
//                tokenSetOf(
//                    LOWER_CASE_IDENTIFIER,
//                    UPPER_CASE_IDENTIFIER
//                )
//            )

    override val referenceName: String
        get() = this.text.replace(Regex("^#"), "").replace("#", ".") // stub?.refName ?: referenceNameElement.text

    override fun getReference(): ElmReference {
        return MarkdownElmReference(this)
    }
}
