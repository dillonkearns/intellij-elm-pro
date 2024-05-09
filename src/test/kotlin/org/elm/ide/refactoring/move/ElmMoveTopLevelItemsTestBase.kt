package org.elm.ide.refactoring.move

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.BaseRefactoringProcessor
import org.elm.TestProject
import org.elm.lang.ElmTestBase
import org.elm.lang.core.psi.ElmFile
import org.elm.openapiext.toPsiDirectory
import org.elm.openapiext.toPsiFile
import org.intellij.lang.annotations.Language
import java.nio.file.Path

abstract class ElmMoveTopLevelItemsTestBase : ElmTestBase() {

    protected fun doTest(@Language("Elm") before: String, @Language("Elm") after: String) =
        checkByDirectory(before.trimIndent(), after.trimIndent(), ::performMove)

    protected fun doTestCreateFile(
        targetFile: String,
        @Language("Elm") before: String,
        @Language("Elm") after: String
    ) = checkByDirectory(before.trimIndent(), after.trimIndent()) { performMove(it, targetFile) }

    protected fun doTestConflictsError(@Language("Elm") before: String) =
        expect<BaseRefactoringProcessor.ConflictsInTestsException> {
            checkByDirectory(before.trimIndent(), "", ::performMove)
        }

    protected fun doTestNoConflicts(@Language("Elm") before: String) =
        checkByDirectory(before.trimIndent(), "", ::performMove)

    private fun prepareSourceFile(testProject: TestProject): PsiFile {
        val fileWithCaret = testProject.fileWithCaretOrSelection
        val sourceFile = myFixture.findFileInTempDir(fileWithCaret).toPsiFile(project)!!
        myFixture.configureFromExistingVirtualFile(sourceFile.virtualFile)
        return sourceFile
    }

    private fun performMove(testProject: TestProject) {
        val sourceFile = prepareSourceFile(testProject)

        val root = myFixture.findFileInTempDir(".").toPsiDirectory(project)!!
        val targetMod = searchElementInAllFiles(root.virtualFile) { it.getElementAtMarker(TARGET_MARKER) }?.let { it.containingFile as? ElmFile }
//            ?.ancestorOrSelf<RsMod>()
            ?: error("Please add $TARGET_MARKER marker for target mod")
//        val targetMod = root.virtualFile.toPsiFile(project) as? ElmFile
//            ?.ancestorOrSelf<RsMod>()
            ?: error("Please add $TARGET_MARKER marker for target mod")
        sourceFile.putUserData(MOVE_TARGET_MOD_KEY, targetMod)

        myFixture.performEditorAction(IdeActions.ACTION_MOVE)
    }

    private fun performMove(testProject: TestProject, targetFile: String? = null) {
        val sourceFile = prepareSourceFile(testProject)

        val targetFilePath = Path.of(myFixture.findFileInTempDir(".").path, targetFile)
        sourceFile.putUserData(MOVE_TARGET_FILE_PATH_KEY, targetFilePath)

        myFixture.performEditorAction(IdeActions.ACTION_MOVE)
    }

    private fun <T> searchElementInAllFiles(root: VirtualFile, searcher: (PsiFile) -> T?): T? {
        var result: T? = null
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
            override fun visitFileEx(file: VirtualFile): Result {
                val psiFile = file.toPsiFile(project) ?: return CONTINUE
                val resultCurrent = searcher(psiFile) ?: return CONTINUE
                result = resultCurrent
                return skipTo(root)
            }
        })
        return result
    }

    companion object {
        private const val TARGET_MARKER: String = "{-target-}"
    }
}
private fun PsiFile.getElementsAtMarker(marker: String = "<caret>"): List<PsiElement> =
    extractMultipleMarkerOffsets(project, marker).map {
        if (it == textLength) this else findElementAt(it)!!
    }
private fun PsiFile.extractMultipleMarkerOffsets(project: Project, marker: String): List<Int> =
    virtualFile.findDocument()!!.extractMultipleMarkerOffsets(project, marker)

private fun Document.extractMultipleMarkerOffsets(project: Project, marker: String): List<Int> {
    if (!text.contains(marker)) return emptyList()

    val offsets = mutableListOf<Int>()
    runWriteAction {
        val text = StringBuilder(text)
        while (true) {
            val offset = text.indexOf(marker)
            if (offset >= 0) {
                text.delete(offset, offset + marker.length)
                offsets += offset
            } else {
                break
            }
        }
        setText(text.toString())
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(this)
    return offsets
}

private fun PsiFile.getElementAtMarker(marker: String = "<caret>"): PsiElement? =
    getElementsAtMarker(marker).singleOrNull()
