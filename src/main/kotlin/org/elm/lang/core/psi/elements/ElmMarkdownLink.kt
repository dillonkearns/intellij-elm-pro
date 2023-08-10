package org.elm.lang.core.psi.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.elm.lang.core.psi.ElmStubbedElement
import org.elm.lang.core.stubs.MarkdownLinkStub


class ElmMarkdownLink : ElmStubbedElement<MarkdownLinkStub> {

    constructor(node: ASTNode) :
            super(node)

    constructor(stub: MarkdownLinkStub, stubType: IStubElementType<*, *>) :
            super(stub, stubType)
}
