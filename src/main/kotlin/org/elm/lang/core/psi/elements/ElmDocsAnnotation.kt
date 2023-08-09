package org.elm.lang.core.psi.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.elm.ide.presentation.getPresentation
import org.elm.lang.core.psi.*
import org.elm.lang.core.psi.ElmTypes.DOC_COMMENT
import org.elm.lang.core.stubs.ElmModuleDocCommentStub
import org.elm.lang.core.stubs.ElmNamedStub


/**
 * The module declaration at the top of a file.
 *
 * e.g. `module Data.User exposing (User, encode, decoder)`
 *
 * Role:
 * - give the module a name
 * - expose values and types
 */
class ElmDocsAnnotation : ElmStubbedElement<ElmModuleDocCommentStub>, PsiDocCommentBase, PsiComment {

    constructor(node: ASTNode) :
            super(node)

    constructor(stub: ElmModuleDocCommentStub, stubType: IStubElementType<*, *>) :
            super(stub, stubType)

//    override fun getTokenType(): IElementType {
//        return DOC_COMMENT
//    }

    override fun getOwner(): PsiElement? {
        return (containingFile as? ElmFile)?.getModuleDecl()
    }
    override fun getTokenType(): IElementType {
    //        return DOC_COMMENT
        return ElmTypes.BLOCK_COMMENT
    }



//    /**
//     * The fully-qualified name of the module
//     */
//    val upperCaseQID: ElmUpperCaseQID
//        get() = findNotNullChildByClass(ElmUpperCaseQID::class.java)
//
//    /**
//     * The values and types exposed by this module
//     *
//     * In a well-formed program, this will be non-null.
//     */
//    val exposingList: ElmExposingList?
//        get() = PsiTreeUtil.getStubChildOfType(this, ElmExposingList::class.java)


//    /**
//     * Very rare. This will only appear in Effect Manager modules.
//     */
//    val effectModuleDetailRecord: ElmRecordExpr?
//        get() = findChildByClass(ElmRecordExpr::class.java)
//
//
//    override fun getName(): String {
//        val stub = stub as? ElmNamedStub
//        return stub?.name ?: upperCaseQID.text
//    }
//
//
//    override fun getTextOffset() =
//            upperCaseQID.textOffset
//
//    override fun getPresentation() =
//            getPresentation(this)
//
//    override val docComment: PsiComment?
//        get() = (nextSiblings.withoutWs.firstOrNull() as? PsiComment)?.takeIf { it.elementType == DOC_COMMENT }
}
