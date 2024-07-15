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

    fun `test Just with record destructure`() = doAvailableTest(
        """
module Foo exposing (regexMatchLength)

regexMatchLength maybeRegexMatch =
    case maybeRegexMatch of
        Jus{-caret-}t { match } ->
            String.length match

        Nothing ->
            0
""", """
module Foo exposing (regexMatchLength)

regexMatchLength maybeRegexMatch =
    (
         maybeRegexMatch
        |> Maybe.map (\({ match }) -> String.length match)
        |> Maybe.withDefault 0
        )
""")

}
