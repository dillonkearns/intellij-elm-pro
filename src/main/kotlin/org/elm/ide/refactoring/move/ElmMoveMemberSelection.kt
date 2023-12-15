package org.elm.ide.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SeparatorFactory
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.tree.DefaultTreeModel

interface ElmMoveNodeInfo {
    fun render(renderer: ColoredTreeCellRenderer)
    val icon: Icon? get() = null
    val children: List<ElmMoveNodeInfo> get() = emptyList()
}

class ElmMoveMemberSelectionPanel(
    val project: Project,
    @NlsContexts.Separator title: String,
    nodesAll: List<ElmMoveNodeInfo>,
    nodesSelected: List<ElmMoveNodeInfo>
) : JPanel() {

    val tree: ElmMoveMemberSelectionTree = ElmMoveMemberSelectionTree(project, nodesAll, nodesSelected)

    init {
        layout = BorderLayout()
        val scrollPane = ScrollPaneFactory.createScrollPane(tree)
        add(SeparatorFactory.createSeparator(title, tree), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }
}

class ElmMoveMemberSelectionTree(
    project: Project,
    nodesAll: List<ElmMoveNodeInfo>,
    nodesSelected: List<ElmMoveNodeInfo>
) : ChangesTreeImpl<ElmMoveNodeInfo>(
    project,
    true,
    false,
    ElmMoveNodeInfo::class.java
) {

    init {
        setIncludedChanges(nodesSelected)
        setChangesToDisplay(nodesAll)
        TreeUtil.collapseAll(this, 0)
    }

    override fun buildTreeModel(nodeInfos: List<ElmMoveNodeInfo>): DefaultTreeModel {
        return ElmMoveMemberSelectionModelBuilder(project, grouping).buildTreeModel(nodeInfos)
    }
}

private class ElmMoveMemberSelectionModelBuilder(
    project: Project,
    grouping: ChangesGroupingPolicyFactory
) : TreeModelBuilder(project, grouping) {

    fun buildTreeModel(nodeInfos: List<ElmMoveNodeInfo>): DefaultTreeModel {
        fun addNode(nodeInfo: ElmMoveNodeInfo, root: ChangesBrowserNode<*>) {
            val node = ElmMoveMemberSelectionNode(nodeInfo)
            myModel.insertNodeInto(node, root, root.childCount)

            val children = nodeInfo.children
            if (children.isNotEmpty()) {
                node.markAsHelperNode()
                for (child in children) {
                    addNode(child, node)
                }
            }
        }

        for (node in nodeInfos) {
            addNode(node, myRoot)
        }
        return build()
    }
}

private class ElmMoveMemberSelectionNode(private val info: ElmMoveNodeInfo) : ChangesBrowserNode<ElmMoveNodeInfo>(info) {
    override fun render(
        renderer: ChangesBrowserNodeRenderer,
        selected: Boolean,
        expanded: Boolean,
        hasFocus: Boolean
    ) {
        info.render(renderer)
        renderer.icon = info.icon
    }
}
