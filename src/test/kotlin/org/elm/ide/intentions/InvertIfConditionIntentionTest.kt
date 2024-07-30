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

    fun `test negated condition with parens`() = doAvailableTest(
        """
module Foo exposing (example)

example positive =
    i{-caret-}f not (positive) then
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

    fun `test negated number comparison`() = doAvailableTest(
        """
module Foo exposing (example)

example n =
    i{-caret-}f n >= 0 then
        "non-negative"
    else
        "negative"
""", """
module Foo exposing (example)

example n =
    if n < 0 then
        "negative"
    else
        "non-negative"
""")


}
