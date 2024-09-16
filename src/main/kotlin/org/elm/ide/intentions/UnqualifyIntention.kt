package org.elm.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.elm.ide.refactoring.move.common.ImportInfo
import org.elm.lang.core.imports.ImportAdder
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.elements.ElmUnionVariant
import org.elm.lang.core.psi.elements.ElmUpperCaseQID

class UnqualifyIntention : ElmAtCaretIntentionActionBase<UnqualifyIntention.Context>() {
    data class Context(
        val expression: ElmUpperCaseQID,
        val definition: PsiElement
    )

    override fun getText() = "Unqualify"
    override fun showPreview(): Boolean {
        return true
    }

    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val parent = element.parent
        return if ((parent is ElmUpperCaseQID) && parent.isQualified) {
            val definition = parent.parent.reference?.resolve()
            if (definition == null || definition is ElmUnionVariant) {
                // Un-qualifying Union Variants is not supported because it could introduce conflicts so would need to check for conflicts first
                null
            } else {
                Context(parent, definition)
            }
        } else {
            null
        }
    }

    override fun invoke(project: Project, editor: Editor, context: Context) {
        val factory = ElmPsiFactory(project)
        val file = context.expression.elmFile
        val unqualifiedName: String = context.expression.text.replaceFirst(context.expression.qualifierPrefix + ".", "")
        val usages = ReferencesSearch.search(context.definition, GlobalSearchScope.fileScope(context.expression.containingFile)).findAll()
        usages.forEach {
            val element = it.element.firstChild
            if (element is ElmUpperCaseQID && element.isQualified) {
                val newExpression = factory.createUpperCaseQID(unqualifiedName)
                element.replace(newExpression)
            }
        }
        val targetModuleImports = file.getImportClauses().map {
            ImportInfo(it.referenceName, it.asClause?.name)
        }

        targetModuleImports.find { (it.aliasName ?: it.moduleName) == context.expression.qualifierPrefix }?.let { existingImport ->
            ImportAdder.addImport(
                ImportAdder.Import(existingImport.moduleName, existingImport.aliasName, unqualifiedName),
                file,
                false
            )

        }
    }
}
