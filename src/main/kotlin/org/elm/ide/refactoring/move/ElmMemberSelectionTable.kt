package org.elm.ide.refactoring.move

import com.intellij.icons.AllIcons
import com.intellij.refactoring.classMembers.MemberInfoModel
import com.intellij.refactoring.ui.AbstractMemberSelectionTable
import com.intellij.ui.RowIcon
import org.elm.lang.core.psi.ElmNamedElement
import org.elm.lang.core.psi.elements.ElmFunctionDeclarationLeft
import org.elm.lang.core.psi.elements.ElmTypeAliasDeclaration
import org.elm.lang.core.psi.elements.ElmTypeDeclaration
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class ElmMemberSelectionTable(
    memberInfos: List<ElmMemberInfo>,
    memberInfoModel: MemberInfoModel<ElmNamedElement, ElmMemberInfo>?,
    @Nls abstractColumnHeader: String?
) : AbstractMemberSelectionTable<ElmNamedElement, ElmMemberInfo>(memberInfos, memberInfoModel, abstractColumnHeader) {
    override fun getAbstractColumnValue(memberInfo: ElmMemberInfo): Any {
        return false
    }

    override fun isAbstractColumnEditable(rowIndex: Int): Boolean {
        return false
    }

    override fun getMemberIcon(memberInfo: ElmMemberInfo?, flags: Int): Icon {
        // don't show any member icons, not applicable for Elm
        return AllIcons.Nodes.EmptyNode
    }

    override fun setVisibilityIcon(memberInfo: ElmMemberInfo, icon: RowIcon) {
        // TODO support moving Type, Port... anything else?
        when (memberInfo.member) {
            is ElmFunctionDeclarationLeft -> {
                icon.setIcon(AllIcons.Nodes.Function, 1)
            }
            is ElmTypeDeclaration, is ElmTypeAliasDeclaration -> {
                icon.setIcon(AllIcons.Nodes.Type, 1)
            }
        }
    }

    override fun getOverrideIcon(memberInfo: ElmMemberInfo): Icon? {
        val defaultIcon = EMPTY_OVERRIDE_ICON

        val member = memberInfo.member
//        if (member !is KtNamedFunction && member !is KtProperty && member !is KtParameter) return defaultIcon

//        return when (memberInfo.overrides) {
//            true -> AllIcons.General.OverridingMethod
//            false -> AllIcons.General.ImplementingMethod
//            else -> defaultIcon
//        }
        return null
    }
}
