package org.elm.ide.intentions

class MaybeDefaultIntentionTest : ElmIntentionTestBase(MaybeDefaultIntention()) {


    fun `test simple case expression`() = doAvailableTest(
            """
module Foo exposing (backslashesLength)

backslashesLength maybeBackslashes =
    case maybeBackslashes of
        Ju{-caret-}st backslashes ->
            String.length backslashes

        Nothing ->
            0
""", """
module Foo exposing (backslashesLength)

backslashesLength maybeBackslashes =
    (
         maybeBackslashes
        |> Maybe.map String.length
        |> Maybe.withDefault 0
        )
""")

}
