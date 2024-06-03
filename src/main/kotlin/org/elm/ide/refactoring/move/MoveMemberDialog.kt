package org.elm.ide.refactoring.move

import com.intellij.openapi.util.NlsContexts
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SeparatorFactory
import org.elm.lang.core.psi.ElmNamedElement
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout

class ElmMemberSelectionPanel(
    @NlsContexts.DialogTitle title: String? = null,
    memberInfo: List<ElmMemberInfo>,
    @Nls abstractColumnHeader: String? = null
) : AbstractMemberSelectionPanel<ElmNamedElement, ElmMemberInfo>() {
    private val table = createMemberSelectionTable(memberInfo, abstractColumnHeader)

    init {
        layout = BorderLayout()
        val scrollPane = ScrollPaneFactory.createScrollPane(table)
        title?.let { add(SeparatorFactory.createSeparator(title, table), BorderLayout.NORTH) }
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun createMemberSelectionTable(
        memberInfo: List<ElmMemberInfo>,
        @Nls abstractColumnHeader: String?
    ): ElmMemberSelectionTable {
        return ElmMemberSelectionTable(memberInfo, null, abstractColumnHeader)
    }

    override fun getTable() = table
}
