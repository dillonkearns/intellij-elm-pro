package org.elm.ide.refactoring

import org.elm.lang.ElmTestBase
import org.intellij.lang.annotations.Language

class ElmInlineFunctionTest : ElmTestBase() {


    fun `test inline function`() =
            doTest(
                    """
--@ Main.elm
value =
    "World"


message =
    "Hello, " ++ value ++ "!"
                  --^
""",
                    """
--@ Main.elm
message =
    "Hello, " ++ "World" ++ "!"
                  --^
""")



    private fun doTest(@Language("Elm") before: String, @Language("Elm") after: String) {
        configureByFileTree(before)
        myFixture.performEditorAction("Inline")
        myFixture.checkResult(after.trimStart())
    }
}