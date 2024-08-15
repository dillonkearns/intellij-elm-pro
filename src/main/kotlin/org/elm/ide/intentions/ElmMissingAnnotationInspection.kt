package org.elm.ide.intentions

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.elm.ide.inspections.ElmLocalInspection
import org.elm.ide.inspections.NamedQuickFix
import org.elm.lang.core.imports.ImportAdder
import org.elm.lang.core.psi.ElmPsiElement
import org.elm.lang.core.psi.elements.ElmFunctionDeclarationLeft
import org.elm.lang.core.psi.elements.ElmValueDeclaration
import org.elm.lang.core.psi.parentOfType
import org.elm.lang.core.psi.startOffset
import org.elm.lang.core.types.Ty
import org.elm.lang.core.types.findTy
import org.elm.lang.core.types.importsForAllDeclarations
import org.elm.lang.core.types.renderedText
import org.elm.utils.getIndent

class ElmMissingAnnotationInspection : ElmLocalInspection() {

    override fun visitElement(element: ElmPsiElement, holder: ProblemsHolder, isOnTheFly: Boolean) {
        val context = findApplicableContext2(element) ?: return
        holder.registerProblem(
            context.fdl.nameIdentifier,
            "Missing annotation",
            ProblemHighlightType.WEAK_WARNING,
            AddAnnotationFix()
        )
    }


    private class AddAnnotationFix : NamedQuickFix("Add Annotation") {
        override fun applyFix(element: PsiElement, project: Project) {
            val context = findApplicableContext(element) ?: return

            val editor: Editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return

            val (fdl, valueDeclaration, ty) = context
            val indent = editor.getIndent(valueDeclaration.startOffset)
            val code = "${fdl.name} : ${ty.renderedText(elmFile = fdl.elmFile).replace("→", "->")}\n$indent"
            val importsToAdd = ty.importsForAllDeclarations(fdl.elmFile)

                importsToAdd.forEach {
                    ImportAdder.addImport(it, valueDeclaration.elmFile, false)

                }
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
                editor.document.insertString(valueDeclaration.startOffset, code)
        }
    }
}

data class Context(val fdl: ElmFunctionDeclarationLeft, val valueDeclaration: ElmValueDeclaration, val ty: Ty)
fun findApplicableContext(element: PsiElement): Context? {
//    val fdl = element.parentOfType<ElmFunctionDeclarationLeft>()
//        ?: return null

    val declaration = element.parentOfType<ElmValueDeclaration>()
        ?: return null
    val fdl = declaration.functionDeclarationLeft ?: return null

    if (declaration.typeAnnotation != null) {
        // the target annotation already exists; nothing needs to be done
        return null
    }

    val ty = declaration.findTy() ?: return null

    return Context(fdl, declaration, ty)
}

fun findApplicableContext2(element: PsiElement): Context? {
    val declaration = element as? ElmValueDeclaration
        ?: return null
    val fdl = declaration.functionDeclarationLeft ?: return null

    if (declaration.typeAnnotation != null) {
        // the target annotation already exists; nothing needs to be done
        return null
    }

    val ty = declaration.findTy() ?: return null

    return Context(fdl, declaration, ty)
}
