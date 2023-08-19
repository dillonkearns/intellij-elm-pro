package org.elm.lang.core.psi.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.elm.lang.core.psi.ElmStubbedElement
import org.elm.lang.core.psi.ElmTypes.*
import org.elm.lang.core.psi.tokenSetOf
import org.elm.lang.core.resolve.ElmReferenceElement
import org.elm.lang.core.resolve.reference.*
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
        get() = //this
            // TODO consider refining down the reference name element to be more narrow for better highlighting
            findChildByClass(ElmExposedOperator::class.java) ?: (findChildByType(
                tokenSetOf(
                    LOWER_CASE_IDENTIFIER,
                )
            ) ?: findChildrenByType<PsiElement>(
                tokenSetOf(
                    UPPER_CASE_IDENTIFIER,
                )
            ).last()
                    )

    override val referenceName: String
        get() = referenceNameElement.text

    val isQualified: Boolean
        get() = findChildByType<PsiElement>(HASH) != null

    private val flavor: Flavor
        get() {
            val lowerCaseId = this.node.findChildByType(tokenSetOf(LOWER_CASE_IDENTIFIER))
            return when {
                lowerCaseId == null ->
                    if (isQualified)
                        Flavor.QualifiedConstructor
                    else
                        Flavor.BareConstructor

                else ->
                    if (isQualified)
                        Flavor.QualifiedValue
                    else
                        Flavor.BareValue
            }
        }

    override fun getReference(): ElmReference =
        references.first()

    override fun getReferences(): Array<ElmReference> {

        return when (flavor) {
            Flavor.BareValue -> arrayOf(MarkdownElmReference(this))
            Flavor.BareConstructor -> arrayOf(MarkdownElmReference(this))
            Flavor.QualifiedValue -> {
                val qualifierPrefix = this.text.split("#").first()
                if (qualifierPrefix == "") {
                    arrayOf(MarkdownElmReference(this))
                } else {
                    arrayOf(
                        MarkdownElmReference(this),
                        MarkdownModuleNameQualifierReference(this, qualifierPrefix)
                    )
                }
            }

            Flavor.QualifiedConstructor -> arrayOf(
                MarkdownElmReference(this),
            )
        }
    }
}
