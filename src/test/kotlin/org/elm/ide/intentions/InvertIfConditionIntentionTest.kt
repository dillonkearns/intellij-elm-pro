package org.elm.ide.intentions

class InvertIfConditionIntentionTest : ElmIntentionTestBase(InvertIfConditionIntention()) {


    fun `test independent let with sibling bindings`() = doAvailableTest(
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

}
