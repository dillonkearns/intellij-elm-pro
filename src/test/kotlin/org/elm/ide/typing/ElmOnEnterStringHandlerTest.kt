package org.elm.ide.typing


class ElmOnEnterStringHandlerTest : ElmTypingTestBase() {


    fun `test simple`() = doTestByText("""
f =
    "Hello, {-caret-}World!"
""", """
f =
    "Hello, <caret>" ++ "World!"
""")

}
