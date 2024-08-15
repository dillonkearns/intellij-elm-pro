package org.elm.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.elm.lang.core.imports.ImportAdder
import org.elm.lang.core.psi.ElmPsiElement
import org.elm.lang.core.psi.ElmPsiFactory
import org.elm.lang.core.psi.elements.ElmUpperCaseQID

class UnqualifyIntention : ElmAtCaretIntentionActionBase<UnqualifyIntention.Context>() {
    data class Context(
        val expression: ElmUpperCaseQID,
    )

    override fun getText() = "Unqualify"
    override fun showPreview(): Boolean {
        return true
    }

    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val parent = element.parent
        return if ((parent is ElmUpperCaseQID) && parent.isQualified) {
            return Context(parent)
        } else {
            null
        }
    }

    override fun invoke(project: Project, editor: Editor, context: Context) {

        val factory = ElmPsiFactory(project)
        val unqualifiedName: String = context.expression.text.replaceFirst(context.expression.qualifierPrefix + ".", "")
        val replaced: PsiElement = factory.createUpperCaseQID(unqualifiedName).let {
            context.expression.replace(it)
        }
        ImportAdder.addImport(ImportAdder.Import(context.expression.qualifierPrefix, null, unqualifiedName), (replaced as ElmPsiElement).elmFile, false)
    }
}
