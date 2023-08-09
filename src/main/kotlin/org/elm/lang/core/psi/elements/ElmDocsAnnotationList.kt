package org.elm.lang.core.psi.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.elm.lang.core.psi.ElmStubbedElement
import org.elm.lang.core.stubs.ElmDocsAnnotationListStub


class ElmDocsAnnotationList : ElmStubbedElement<ElmDocsAnnotationListStub> {

    constructor(node: ASTNode) :
            super(node)

    constructor(stub: ElmDocsAnnotationListStub, stubType: IStubElementType<*, *>) :
            super(stub, stubType)
}
