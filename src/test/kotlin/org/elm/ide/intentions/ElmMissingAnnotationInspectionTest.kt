package org.elm.ide.intentions

import org.elm.ide.inspections.ElmInspectionsTestBase


class ElmMissingAnnotationInspectionTest : ElmInspectionsTestBase(ElmMissingAnnotationInspection()) {
    override fun getProjectDescriptor() = ElmWithStdlibDescriptor

    fun `test value`() = checkFixByText("Add Annotation",
            """
module Test exposing (f)

f{-caret-} =
    1.0
"""
            , """
module Test exposing (f)

f : Float
f =
    1.0
""")

    fun `test value with docstring`() = checkFixByText("Add Annotation",
            """
{- docs -}
f{-caret-} =
    1.0
"""
            , """
{- docs -}
f : Float
f =
    1.0
""")

    fun `test value with caret in name`() = checkFixByText("Add Annotation",
            """
module Test exposing (function)

func{-caret-}tion =
    1.0
"""
            , """
module Test exposing (function)

function : Float
function =
    1.0
""")

    fun `test value with caret before name`() = checkFixByText("Add Annotation",
            """
module Test exposing (f)

{-caret-}f =
    1.0
"""
            , """
module Test exposing (f)

f : Float
f =
    1.0
""")


    fun `test function with unconstrained params`() = checkFixByText("Add Annotation",
            """
f{-caret-} a b =
    a
"""
            , """
f : a -> b -> a
f a b =
    a
""")

    fun `test function with constrained params`() = checkFixByText("Add Annotation",
            """
f{-caret-} a b =
    a < b
"""
            , """
f : comparable -> comparable -> Bool
f a b =
    a < b
""")

    fun `test nested value`() = checkFixByText("Add Annotation",
            """
f =
    let
        g{-caret-} =
            1.0
    in
        g
"""
            , """
f =
    let
        g : Float
        g =
            1.0
    in
        g
""")

    fun `test nested function`() = checkFixByText("Add Annotation",
            """
f =
    let
        g{-caret-} a b =
            a < b
    in
        g
"""
            , """
f =
    let
        g : comparable -> comparable -> Bool
        g a b =
            a < b
    in
        g
""")

    fun `test nested value with previous sibling`() = checkFixByText("Add Annotation",
            """
f =
    let
        h =
            ()
        g{-caret-} =
            1.0
    in
        g
"""
            , """
f =
    let
        h =
            ()
        g : Float
        g =
            1.0
    in
        g
""")

    fun `test nested value with caret before name`() = checkFixByText("Add Annotation",
            """
f =
    let
        {-caret-}function =
            1.0
    in
        function
"""
            , """
f =
    let
        function : Float
        function =
            1.0
    in
        function
""")

    fun `test nested value with caret in name`() = checkFixByText("Add Annotation",
            """
f =
    let
        fun{-caret-}ction =
            1.0
    in
        function
"""
            , """
f =
    let
        function : Float
        function =
            1.0
    in
        function
""")

    fun `test qualified name`() = checkFixByFileTree("Add Annotation",
            """
--@ main.elm
import Foo as F
main{-caret-} i = F.foo i
--@ Foo.elm
module Foo exposing (..)
type alias Bar = { i : Int }
foo i = Bar i
""", """
import Foo as F
main : Int -> F.Bar
main i = F.foo i
""")

    fun `test type alias is imported instead of the resolved alias`() = checkFixByFileTree("Add Annotation",
        """
--@ main.elm
import Direct exposing (Direct)
import Indirect
main{-caret-} i = Indirect.example i
--@ Indirect.elm
module Indirect exposing (..)
import Direct
type alias Indirect = Direct
example : Int -> Indirect
example i = Direct.Direct i
--@ Direct.elm
module Direct exposing (..)
type alias Direct = { i : Int }
""", """
import Direct exposing (Direct)
import Indirect
main : Int -> Indirect.Indirect
main i = Indirect.example i
""")

    fun `test add import if needed`() = checkFixByFileTree("Add Annotation",
        """
--@ main.elm
import Direct exposing (Direct)
import Indirect
main{-caret-} i = Indirect.example i
--@ Indirect.elm
module Indirect exposing (..)
import Direct
example i = Direct.Direct i
--@ Direct.elm
module Direct exposing (..)
type alias Direct = { i : Int }
""", """
import Direct exposing (Direct)
import Indirect
main : Int -> Direct
main i = Indirect.example i
""")

}
