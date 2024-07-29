package org.elm.ide.intentions

class InvertIfConditionIntentionTest : ElmIntentionTestBase(InvertIfConditionIntention()) {


    fun `test simple if condition`() = doAvailableTest(
            """
module Foo exposing (example)

example positive =
    i{-caret-}f positive then
        1
    else
        -1
""", """
module Foo exposing (example)

example positive =
    if not (positive) then
        -1
    else
        1
""")

    fun `test negated condition`() = doAvailableTest(
        """
module Foo exposing (example)

example positive =
    i{-caret-}f not positive then
        -1
    else
        1
""", """
module Foo exposing (example)

example positive =
    if positive then
        1
    else
        -1
""")

}
