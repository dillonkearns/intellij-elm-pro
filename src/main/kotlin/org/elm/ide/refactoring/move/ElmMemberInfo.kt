package org.elm.ide.refactoring.move

import com.intellij.refactoring.classMembers.MemberInfoBase
import org.elm.lang.core.psi.ElmNamedElement


class ElmMemberInfo(
    member: ElmNamedElement,
    initiallySelected: Boolean
) : MemberInfoBase<ElmNamedElement>(member) {
    init {
        displayName = member.name
        isChecked = initiallySelected
        overrides = false
    }
}
