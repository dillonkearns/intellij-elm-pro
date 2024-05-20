package org.elm.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.elm.ide.settings.experimentalFlags
import org.elm.lang.core.diagnostics.ElmDiagnostic
import org.elm.lang.core.psi.ElmPsiElement

abstract class ElmDiagnosticBasedInspection : ElmLocalInspection() {
    final override fun visitElement(element: ElmPsiElement, holder: ProblemsHolder, isOnTheFly: Boolean) {
        for (diagnostic in getElementDiagnostics(element)) {
            val fix: LocalQuickFix? = diagnostic.getFix()
            val inDevMode = element.project.experimentalFlags.wipFeaturesEnabled
            if (fix != null && inDevMode) {
                holder.registerProblem(
                    diagnostic.element,
                    "<html>${diagnostic.message}</html>",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    fix
                )
            } else {
                holder.registerProblem(
                    holder.manager.createProblemDescriptor(
                        diagnostic.element,
                        diagnostic.endElement ?: diagnostic.element,
                        "<html>${diagnostic.message}</html>",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        holder.isOnTheFly
                    )
                )
            }
        }
    }

    abstract fun getElementDiagnostics(element: ElmPsiElement): Iterable<ElmDiagnostic>
}

