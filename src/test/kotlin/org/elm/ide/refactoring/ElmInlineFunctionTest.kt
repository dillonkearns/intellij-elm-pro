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

    @Test
    fun `test point-free function definition`() =
        doTest(
            """
--@ Main.elm
joinWithSpace =
    List.join " "


message =
    joinWithSpace ["Hello", "World"]
    --^

""",
            """


message =
    (List.join " ") ["Hello", "World"]
    --^
""")

    @Test
    fun `test complex point-free function definition`() =
        doTest(
            """
--@ Main.elm
joinWithSpace =
    List.join " " >> String.toUpper


message =
    joinWithSpace ["Hello", "World"]
    --^

""",
            """


message =
    (List.join " " >> String.toUpper) ["Hello", "World"]
    --^
""")

    @Test
    fun `test case expression`() =
        doTest(
            """
--@ Main.elm
intDescription int =
    case int of
        1 ->
            "one"
        2 ->
            "two"
        _ ->
            "other"


message =
    intDescription 2
    --^

""",
            """


message =
    case 2 of
        1 ->
            "one"
        2 ->
            "two"
        _ ->
            "other"
    --^
""")

    @Test
    fun `test simple custom type parameter destructure`() =
        doTest(
            """
--@ Main.elm

toString : Slug -> String
toString (Slug str) =
    str


message slug =
    toString slug
    --^

""",
            """



message slug =
    ((\(Slug str) -> str) slug)
    --^
""")

    @Test
    fun `test record destructure`() =
        doTest(
            """
--@ Main.elm


fullName { first, last } =
    first ++ " " ++ last


example3 record =
    fullName record
    --^

""",
            """


example3 record =
    record.first ++ " " ++ record.last
    --^
""")


    @Test
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

    @Test
    fun `test simple constant`() =
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

    @Test
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

    @Test
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

    @Test
    fun `test inline function with multiple call sites`() =
        doTest(
            """
--@ Main.elm
upperList : List String -> List String
upperList list =
    List.map String.toUpper list


myExample : List String
myExample =
    upperList [1, 2, 3]
    --^

myExample2 : List String
myExample2 =
    upperList [4, 5, 6]
""",
            """



myExample : List String
myExample =
    List.map String.toUpper [1, 2, 3]
    --^

myExample2 : List String
myExample2 =
    List.map String.toUpper [4, 5, 6]""")


    @Test
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
           --^
    )
        ++ "!"

""",
                    """



exclaimGreeting =
    (
         "Hello " ++ "Dillon" ++ " " ++ "Kearns"
           --^
    )
        ++ "!"
""")

    @Test
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

    @Test
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
                    """

exclaimGreeting =
    (
         "Hello " ++ "Dillon" ++ " " ++ "Kearns"
    )
        ++ "!"


anotherExclaimGreeting =
    "Hello " ++ "John" ++ " " ++ "Doe"
        ++ "!"
""")

    @Test
    fun `test removes exposing`() =
        doTest(
        """
--@ A.elm

module A exposing (value, value2)

value = {-caret-}42

value2 = value + 123
"""
        , """module A exposing (value2)



value2 = 42 + 123"""
    )

    fun `test foo`() =
        doTest(
            """
--@ Main.elm
myNum =
    123


myFn a =
    a

example =
    myFn myNum
          --^

""",
            """


myFn a =
    a

example =
    myFn 123
          --^
""")



    private fun doTest(@Language("Elm") before: String, @Language("Elm") after: String) {
        configureByFileTree(before)
        myFixture.performEditorAction("Inline")
        myFixture.checkResult(after)
    }
}