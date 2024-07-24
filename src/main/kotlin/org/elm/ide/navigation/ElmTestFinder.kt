package org.elm.ide.navigation

import com.intellij.psi.PsiElement
import com.intellij.testIntegration.TestFinder
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.elements.ElmModuleDeclaration
import org.elm.lang.core.psi.moduleName
import org.elm.lang.core.stubs.index.ElmModulesIndex

class ElmTestFinder : TestFinder {
    override fun findSourceElement(element: PsiElement): PsiElement? {
        return (element.containingFile as? ElmFile)?.getModuleDecl()
    }

    override fun findTestsForClass(element: PsiElement): Collection<PsiElement> {
        val implementationModule = (element.containingFile as? ElmFile)?.getModuleDecl() ?: return mutableListOf()

        val psiElements: Collection<PsiElement> = ElmModulesIndex.getAllTestModules((element.containingFile as ElmFile).elmProject!!, element.project).firstOrNull {
            (it.containingFile as ElmFile).isInTestsDirectory &&
                    nonTestName(it) == implementationModule.moduleName
        }?.let {
            mutableListOf(it)
        } ?: mutableListOf()
        return psiElements
    }

    override fun findClassesForTest(element: PsiElement): MutableCollection<PsiElement> {
        val testModule = (element.containingFile as? ElmFile)?.getModuleDecl() ?: return mutableListOf()

        return ElmModulesIndex.getAll(element.containingFile as ElmFile).firstOrNull { it.moduleName == nonTestName(testModule) }?.let {
            mutableListOf(it)
        } ?: mutableListOf()
    }

    override fun isTest(element: PsiElement): Boolean {
        return (element.containingFile as ElmFile).isInTestsDirectory
    }
}

private fun nonTestName(module: ElmModuleDeclaration): String {
    return module.moduleName.replace("Test(s)$".toRegex(), "")
}
