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


message =
    "Hello, " ++ "World" ++ "!"
                  --^
""")

    fun `test inline function with argument`() =
            doTest(
                    """
--@ Main.elm
value append =
    "World" ++ append


message =
    "Hello, " ++ value "!"
                  --^

""",
                    """


message =
    "Hello, " ++ "World" ++ "!"
                  --^
""")



    private fun doTest(@Language("Elm") before: String, @Language("Elm") after: String) {
        configureByFileTree(before)
        myFixture.performEditorAction("Inline")
        myFixture.checkResult(after)
    }
}