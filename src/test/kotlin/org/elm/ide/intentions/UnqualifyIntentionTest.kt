package org.elm.ide.intentions

class UnqualifyIntentionTest : ElmIntentionTestBase(UnqualifyIntention()) {


    fun `test simple case expression`() = doAvailableTestWithFileTree(
            """
--@ Foo.elm
module Foo exposing (..)

import Example

example : Example.Ex{-caret-}ample
example = Example.value
--@ Example.elm

module Example exposing (Example, value)

type Example = Example

value : Example
value = Example
""", """
module Foo exposing (..)

import Example exposing (Example)

example : Example
example = Example.value
""")

    fun `test import alias`() = doAvailableTestWithFileTree(
        """
--@ Foo.elm
module Foo exposing (..)

import Example as E

example : E.Ex{-caret-}ample
example = E.value
--@ Example.elm

module Example exposing (Example, value)

type Example = Example

value : Example
value = Example
""", """
module Foo exposing (..)

import Example as E exposing (Example)

example : Example
example = E.value
""")

    fun `test import alias with multiple usages`() = doAvailableTestWithFileTree(
        """
--@ Foo.elm
module Foo exposing (..)

import Example as E

example : E.Ex{-caret-}ample
example = E.value

example2 : E.Example
example2 = E.value

--@ Example.elm

module Example exposing (Example, value)

type Example = Example

value : Example
value = Example
""", """
module Foo exposing (..)

import Example as E exposing (Example)

example : Example
example = E.value

example2 : Example
example2 = E.value

""")

    fun `test unqualifying is not available for variants since it could cause conflicts`() = doUnavailableTestWithFileTree(
        """
--@ Foo.elm
module Foo exposing (..)

import Example

example : Example.Example
example = Example.Var{-caret-}iant1
--@ Example.elm

module Example exposing (..)

type Example = Variant1 | Variant2

value : Example
value = Variant1
""")

    fun `test unqualifying is not available when it would introduce a conflict`() = doUnavailableTestWithFileTree(
        """
--@ Foo.elm
module Foo exposing (..)

import Example

type Example = FooExample

example : Example.Examp{-caret-}le
example = Example.value
--@ Example.elm

module Example exposing (..)

type Example = Variant1 | Variant2

value : Example
value = Variant1
""")


}
