package org.elm.ide.refactoring.move

import org.elm.lang.ElmTestBase
import org.elm.lang.core.imports.ImportAdder
import org.elm.openapiext.runWriteCommandAction
import org.intellij.lang.annotations.Language

class ImportAdderTest : ElmTestBase() {
    fun `test add import`() = doTest(
        """
module Main  exposing (value)

value = 42
""", """
module Main  exposing (value)

import A exposing (value)
value = 42
"""
    )


    fun doTest(@Language("Elm") before: String, @Language("Elm") after: String) =
        checkByText(before.trimIndent(), after.trimIndent()) {
            project.runWriteCommandAction {
                ImportAdder.addImport(ImportAdder.Import("A", null, "value"), it, false)
            }
        }
}
