package org.elm.ide.intentions

class UnqualifyIntentionTest : ElmIntentionTestBase(UnqualifyIntention()) {


    fun `test simple case expression`() = doAvailableTest(
            """
module Foo exposing (..)

import Example

example : Example.Ex{-caret-}ample
example = Example.value
""", """
module Foo exposing (..)

import Example exposing (Example)

example : Example
example = Example.value
""")

    fun `test import alias`() = doAvailableTest(
        """
module Foo exposing (..)

import Example as E

example : E.Ex{-caret-}ample
example = E.value
""", """
module Foo exposing (..)

import Example as E exposing (Example)

example : Example
example = E.value
""")

}
