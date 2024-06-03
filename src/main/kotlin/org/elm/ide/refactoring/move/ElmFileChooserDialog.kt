package org.elm.ide.refactoring.move

import com.intellij.ide.util.AbstractTreeClassChooserDialog
import com.intellij.ide.util.TreeChooser
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.search.GlobalSearchScope
import org.elm.lang.core.psi.ElmExposableTag
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.stubs.index.ElmModulesIndex
import org.elm.workspace.ElmPackageProject
import javax.swing.tree.DefaultMutableTreeNode

class ElmFileChooserDialog(
    @NlsContexts.DialogTitle title: String,
    project: Project,
    searchScope: GlobalSearchScope?,
    packageName: String?,
    val first: ElmExposableTag
) : AbstractTreeClassChooserDialog<ElmFile>(
    title,
    project,
    ElmFile::class.java,
) {
    override fun getSelectedFromTreeUserObject(node: DefaultMutableTreeNode): ElmFile? = when (val userObject = node.userObject) {
        is ElmFile -> userObject
        else -> null
    }

    override fun getClassesByName(name: String, checkBoxState: Boolean, pattern: String, searchScope: GlobalSearchScope): List<ElmFile> {
        val modules = ElmModulesIndex.getAll(first.elmFile).filter { it.elmProject !is ElmPackageProject }
        return modules.toList().map {
            it.elmFile
        }
    }

    override fun createChooseByNameModel() = GotoFileModel(this.project)

    /**
     * Base class [AbstractTreeClassChooserDialog] unfortunately doesn't filter the file tree according to the provided "scope".
     * As a workaround we use filter preventing wrong file selection.
     */
    private class ScopeAwareClassFilter(val searchScope: GlobalSearchScope?, val packageName: String?) : TreeChooser.Filter<ElmFile> {
//        override fun isAccepted(element: PsiFile?): Boolean {
//            if (element !is ElmFile) return false
//            if (searchScope == null && packageName == null) return true
//
//            val matchesSearchScope = searchScope?.accept(element.virtualFile) ?: true
////            val matchesPackage = packageName?.let { element.packageFqName.asString() == it } ?: true
//            val matchesPackage = true
//
//            return matchesSearchScope && matchesPackage
//        }

        override fun isAccepted(p0: ElmFile?): Boolean {
            return true
        }
    }
}
