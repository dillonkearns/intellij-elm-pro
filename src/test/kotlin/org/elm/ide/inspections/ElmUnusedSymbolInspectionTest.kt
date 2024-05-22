package org.elm.ide.inspections

import org.junit.Ignore


class ElmUnusedSymbolInspectionTest : ElmInspectionsTestBase(ElmUnusedSymbolInspection()) {


    // FUNCTIONS

    fun `test detects unused functions`() = checkFixIsUnavailable("Rename to _", """
        <warning descr="'f' is never used">f{-caret-}</warning> = g
        g = ()
        """.trimIndent())


    fun `test the main function is never marked as unused`() = checkByText("""
        main = ()
        """.trimIndent())


    // NOTE: This test plays fast-and-loose by simulating elm-test rather than bringing in
    //       the real package dependency. But this should be good enough.
    fun `test elm-test entry points are never marked as unused, qualified`() = checkByFileTree("""
        --@ main.elm
        module MyTests exposing (fooTest)
        import Test
        fooTest : Test.Test{-caret-}
        fooTest = ()

        --@ Test.elm
        module Test exposing (Test)
        type alias Test = ()
        """.trimIndent())


    // same as previous test but referring to the `Test` type without a module qualifier
    fun `test elm-test entry points are never marked as unused, unqualified`() = checkByFileTree("""
        --@ main.elm
        module MyTests exposing (fooTest)
        import Test exposing (Test)
        fooTest : Test{-caret-}
        fooTest = ()

        --@ Test.elm
        module Test exposing (Test)
        type alias Test = ()
        """.trimIndent())


    fun `test an elm-test entry point that is not exposed should be checked for usage`() = checkByFileTree("""
        --@ main.elm
        module MyTests exposing (main)
        import Test exposing (Test)
        fooTest : Test{-caret-}
        <warning descr="'fooTest' is never used">fooTest</warning> = ()
        main = ()

        --@ Test.elm
        module Test exposing (Test)
        type alias Test = ()
        """.trimIndent())


    fun `test port annotations are never marked as unused`() = checkByText("""
        port module Foo exposing (foo)
        port foo : () -> msg
        """.trimIndent())


    fun `test a port annotation that is not exposed should be checked for usage`() = checkByText("""
        port module Foo exposing (foo)
        port foo : () -> msg
        port <warning descr="'bar' is never used">bar</warning> : () -> msg
        """.trimIndent())


    fun `test the type annotation does not count as usage`() = checkByText("""
        f : ()
        <warning descr="'f' is never used">f</warning> = ()
    """.trimIndent())


    fun `test exposing a function does not count as usage`() = checkByText("""
        module Foo exposing (f)
        <warning descr="'f' is never used">f</warning> = ()
    """.trimIndent())


    fun `test but a reference to a function from another file DOES count as usage`() = checkByFileTree("""
        --@ Foo.elm
        module Foo exposing (f)
        f = ()
        --^

        --@ Bar.elm
        import Foo
        g = Foo.f
    """.trimIndent())


    // PARAMETERS

    fun `test renames unused function parameters`() = checkFixByText("Rename to _", """
        f <warning descr="'x' is never used">x{-caret-}</warning> = ()
        main = f
        """.trimIndent(),"""
        f _ = ()
        main = f
        """.trimIndent())

    fun `test remove param`() = checkFixByText("Remove parameter", """
        f <warning descr="'x' is never used">x{-caret-}</warning> = ()
        main = f 123
        """.trimIndent(),"""
        f = ()
        main = f 
        """.trimIndent())

    fun `test remove param with annotation`() = checkFixByText("Remove parameter", """
        f : Int -> ()
        f <warning descr="'x' is never used">x{-caret-}</warning> = ()
        main = f 123
        """.trimIndent(),"""
        f : ()
        f = ()
        main = f 
        """.trimIndent())

    fun `test remove param with annotation with multiple params`() = checkFixByText("Remove parameter", """
        f : String -> Int -> String -> String -> String
        f w <warning descr="'x' is never used">x{-caret-}</warning> y z = w ++ y ++ z
        main = f "w" 123 "y" "z"
        """.trimIndent(),"""
        f : String -> String -> String -> String
        f w  y z = w ++ y ++ z
        main = f "w"  "y" "z"
        """.trimIndent())

    fun `test renames lambda parameters`() = checkFixByText("Rename to _",
            """main = (\<warning descr="'x' is never used">x{-caret-}</warning> -> ())""",
            """main = (\_ -> ())""")

    fun `test used record field patterns`() = checkByText("""
        type alias X  = { x : () }
        f : X -> ()
        f { x } = x
        main = f
        """.trimIndent())

    // TYPES


    // TODO revisit this in the future: maybe only record aliases should be ignored?
    fun `test type aliases are never marked as unused`() = checkByText("""
        type alias Foo = ()
    """.trimIndent())


    // TODO revisit this in the future: maybe we can check to see if none of the
    //      variant constructors are used, and, if so, then the type can be
    //      considered unused (assuming that there are also no refs to the type
    //      name itself).
    fun `test the union type is never marked as unused`() = checkByText("""
        type Foo = Bar
        main = Bar
        """.trimIndent())


    fun `test detects unused union variant constructor`() = checkFixIsUnavailable("Rename to _", """
        type Foo = Bar | <warning descr="'Quux' is never used">Quux</warning>
        main : Foo
        main = Bar
        """.trimIndent())

    fun `test renames unused case branch patterns`() = checkFixByText("Rename to _", """
        type T = T () ()
        main : T -> ()
        main t = 
            case t of
                T <warning descr="'x' is never used">x{-caret-}</warning> y ->
                    y
        """.trimIndent(), """
        type T = T () ()
        main : T -> ()
        main t = 
            case t of
                T _ y ->
                    y
        """.trimIndent())


    // MISC

    fun `test detects unused alias when importing a module`() = checkByFileTree("""
        --@ main.elm
        import FooBar as <warning descr="'FB' is never used">FB</warning>
        --^

        --@ FooBar.elm
        module FooBar exposing (..)
        """.trimIndent())

    // REMOVE FIX

    @Ignore("Delete action is disabled since it duplicates elm-review unused actions.")
    fun `test deletes unused`() = checkFixByText("Delete",
            """import Html exposing (text)


main = text <| String.fromInt a

a =
    123

<warning descr="'b' is never used">b{-caret-}</warning> =
    456
""".trimIndent(),
            """
import Html exposing (text)


main = text <| String.fromInt a

a =
    123



            """.trimIndent())

    @Ignore
    fun `test delete unused value with annotation`() = checkFixByText("Delete",
            """import Html exposing (text)


main = text <| String.fromInt a

a : Int
a =
    123

b : Int
<warning descr="'b' is never used">b{-caret-}</warning> =
    456
""".trimIndent(),
            """
import Html exposing (text)


main = text <| String.fromInt a

a : Int
a =
    123




            """.trimIndent())

    @Ignore
    fun `test delete single unused let binding`() = checkFixByText("Delete",
            """import Html exposing (text)


main = text <| String.fromInt a

a : Int
a =
    let
        b : Int
        <warning descr="'b' is never used">b{-caret-}</warning> =
            456
        
        c = 789
    in
    c
""".trimIndent(),
            """
import Html exposing (text)


main = text <| String.fromInt a

a : Int
a =
    let
        
        
        
        c = 789
    in
    c
            """.trimIndent())

    @Ignore
    fun `test delete unused let binding with no other bindings left`() = checkFixByText("Delete",
            """import Html exposing (text)


main = text <| String.fromInt a

a : Int
a =
    let
        b : Int
        <warning descr="'b' is never used">b{-caret-}</warning> =
            456
    in
    789
""".trimIndent(),
            """import Html exposing (text)


main = text <| String.fromInt a

a : Int
a =
    789
""".trimIndent())
}
