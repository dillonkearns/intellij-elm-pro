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

}
