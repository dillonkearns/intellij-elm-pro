package org.elm.ide.intentions

class MaybeDefaultToCaseIntentionTest : ElmIntentionTestBase(MaybeDefaultToCaseIntention()) {


    fun `test simple case expression`() = doAvailableTest(
            """
module Foo exposing (leftFringeLength)


leftFringeLength maybeLeftFringe =
    maybeLeftFringe
        |> Maybe.ma{-caret-}p String.length
        |> Maybe.withDefault 0
""", """
module Foo exposing (leftFringeLength)


leftFringeLength maybeLeftFringe =
    case maybeLeftFringe of
        Just something ->
            String.length something

        Nothing ->
            0
""")

}
