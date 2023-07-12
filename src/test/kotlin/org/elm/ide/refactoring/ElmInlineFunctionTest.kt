package org.elm.ide.refactoring

import org.elm.lang.ElmTestBase
import org.intellij.lang.annotations.Language
import org.junit.Test

class ElmInlineFunctionTest : ElmTestBase() {

    @Test
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

    fun `test inline function with two arguments`() =
            doTest(
                    """
--@ Main.elm

greet first last =
    "Hello " ++ first ++ " " ++ last


exclaimGreeting =
    greet "Dillon" "Kearns" ++ "!"
    --^

""",
                    """


exclaimGreeting =
    "Hello " ++ "Dillon" ++ " " ++ "Kearns" ++ "!"
    --^
""")

    fun `test inline function with two arguments and type annotation`() =
            doTest(
                    """
--@ Main.elm

greet : String -> String -> String
greet first last =
    "Hello " ++ first ++ " " ++ last


exclaimGreeting =
    greet "Dillon" "Kearns" ++ "!"
    --^

""",
                    """



exclaimGreeting =
    "Hello " ++ "Dillon" ++ " " ++ "Kearns" ++ "!"
    --^
""")

    @Test
    fun `test inline function with argument no pipeline`() =
        doTest(
            """
--@ Main.elm
upperList : List String -> List String
upperList list =
    List.map String.toUpper list


myExample : List String
myExample =
    upperList []
    --^

""",
            """



myExample : List String
myExample =
    List.map String.toUpper []
    --^
""")


    fun `test inline function piping`() =
            doTest(
                    """
--@ Main.elm

greet : String -> String -> String
greet first last =
    "Hello " ++ first ++ " " ++ last


exclaimGreeting =
    ("Kearns"
        |> greet "Dillon"
    )
        ++ "!"
    --^

""",
                    """



exclaimGreeting =
    (
         "Hello " ++ "Dillon" ++ " " ++ "Kearns"
    )
        ++ "!"
    --^
""")

    fun `test multiple inlines`() =
            doTest(
                    """
--@ Main.elm


greet : String -> String -> String
greet first last =
    "Hello " ++ first ++ " " ++ last


exclaimGreeting =
    ("Kearns"
        |> greet "Dillon"
    )
        ++ "!"


anotherExclaimGreeting =
    greet "John" "Doe"
    --^
        ++ "!"

""",
                    """



exclaimGreeting =
    (
         "Hello " ++ "Dillon" ++ " " ++ "Kearns"
    )
        ++ "!"


anotherExclaimGreeting =
    "Hello " ++ "John" ++ " " ++ "Doe"
    --^
        ++ "!"
""")

    fun `test multiple inlines in multiple modules`() =
            doTest(
                    """
--@ main.elm

import Other

exclaimGreeting =
    ("Kearns"
        |> Other.greet "Dillon"
    )
        ++ "!"


anotherExclaimGreeting =
    Other.gre{-caret-}et "John" "Doe"
        ++ "!"

--@ Other.elm
module Other exposing (greet, anotherExclaimGreeting)

greet : String -> String -> String
greet first last =
    "Hello " ++ first ++ " " ++ last


anotherExclaimGreeting =
    greet "First" "Last"
        ++ "!"
""",
                    """import Other

exclaimGreeting =
    (
         "Hello " ++ "Dillon" ++ " " ++ "Kearns"
    )
        ++ "!"


anotherExclaimGreeting =
    "Hello " ++ "John" ++ " " ++ "Doe"
        ++ "!"
""")

    private fun doTest(@Language("Elm") before: String, @Language("Elm") after: String) {
        configureByFileTree(before)
        myFixture.performEditorAction("Inline")
        myFixture.checkResult(after)
    }
}