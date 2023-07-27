package org.elm.ide.inspections.import

import com.intellij.openapi.vfs.VirtualFileFilter
import org.elm.fileTreeFromText
import org.elm.ide.inspections.ElmInspectionsTestBase
import org.elm.ide.inspections.ElmUnresolvedReferenceInspection
import org.elm.lang.core.imports.ImportAdder.Import
import org.intellij.lang.annotations.Language
import org.junit.Test

class MakeDeclarationFromUsageFixTest : ElmInspectionsTestBase(ElmUnresolvedReferenceInspection()) {
    @Test
    fun `test basic top-level value`() = checkFixByTextWithoutHighlighting("Create",
"""module Foo exposing (..)

myString = greet{-caret-}

""",
            """module Foo exposing (..)

myString = greet
greet = Debug.todo "TODO"
"""
    )

    @Test
    fun `test function with 1 argument`() = checkFixByTextWithoutHighlighting("Create",
            """module Foo exposing (..)

myString = exclaim{-caret-} "Hello"

""",
            """module Foo exposing (..)

myString = exclaim "Hello"
exclaim arg1 = Debug.todo "TODO"
"""
    )
    @Test
    fun `test function with 2 arguments`() = checkFixByTextWithoutHighlighting("Create",
            """module Foo exposing (..)

myString = greet{-caret-} "Hello" "World"

""",
            """module Foo exposing (..)

myString = greet "Hello" "World"

greet : String -> String -> unknown
greet arg1 arg2 = Debug.todo "NOT IMPLEMENTED"
"""
    )


@Test
fun `test function with 2 arguments with types`() = checkFixByTextWithoutHighlighting("Create",
    """module Foo exposing (..)


combined : String
combined = myString ++ "!"

myString = greet{-caret-} "Hello" "World"

""",
    """module Foo exposing (..)


combined : String
combined = myString ++ "!"

myString = greet "Hello" "World"

greet : String -> String -> unknown
greet arg1 arg2 = Debug.todo "NOT IMPLEMENTED"
"""
)

}
