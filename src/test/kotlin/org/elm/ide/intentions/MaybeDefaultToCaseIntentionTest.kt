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

    fun `test nested function calls`() = doAvailableTest(
        """
module Foo exposing (leftFringeLength)


leftFringeLength maybeLeftFringe =
    Maybe.withDefault 0 (Maybe.map String.length maybeLeftFrin{-caret-}ge)
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
