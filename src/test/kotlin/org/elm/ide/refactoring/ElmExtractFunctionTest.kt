package org.elm.ide.refactoring

import org.elm.ide.refactoring.extractFunction.ElmExtractFunctionConfig
import org.elm.ide.refactoring.extractFunction.ExtractFunctionUi
import org.elm.ide.refactoring.extractFunction.withMockExtractFunctionUi
import org.elm.lang.ElmTestBase
import org.intellij.lang.annotations.Language
import org.junit.Test

class ElmExtractFunctionTest : ElmTestBase() {

    @Test
    fun `test simple`() = doTest(
        """
hello =
    {-selection-}"Hello, World!"{-selection--}
""", """
hello =
    test



test : String.String
test =
    "Hello, World!"
""", "test"
    )

    @Test
    fun `test depends on function parameter`() = doTest(
        """
hello name =
    let
        message = {-selection-}"Hello, " ++ name{-selection--}
    in
    "The message is:\n" ++ message
""", """
hello name =
    let
        message = test name
    in
    "The message is:\n" ++ message


test name =
    "Hello, " ++ name
""", "test"
    )

    @Test
    fun `test depends on destructured parameters`() = doTest(
        """
hello { first, last } =
    let
        message = {-selection-}"Hello, " ++ first ++ " " ++ last{-selection--}
    in
    "The message is:\n" ++ message
""", """
hello { first, last } =
    let
        message = test first last
    in
    "The message is:\n" ++ message


test first last =
    "Hello, " ++ first ++ " " ++ last
""", "test"
    )
    @Test
    fun `test depends on let values`() = doTest(
        """
topLevelValue = []
example param =
    let
        letVal : List Float
        letVal =
            [ 1.2 ]
    in
    {-selection-}[] ++ param ++ letVal ++ topLevelValue{-selection--}
""", """
topLevelValue = []
example param =
    let
        letVal : List Float
        letVal =
            [ 1.2 ]
    in
    test param letVal


test param letVal =
    [] ++ param ++ letVal ++ topLevelValue
""", "test"
    )

    fun `test sub expression`() = doTest(
        """
example param =
    [1,2,3] ++ {-selection-}param{-selection--}
""", """
example param =
    [1,2,3] ++ test param


test param =
    param
""", "test"
    )

    fun `test value with record access`() = doTest(
        """
example model =
    [1,2,3] ++ [{-selection-}model.counter + 1{-selection--}]
""", """
example model =
    [1,2,3] ++ [test model]


test model =
    model.counter + 1
""", "test"
    )

    fun `test record update syntax`() = doTest(
        """
type Msg = Msg1
example model msg feed =
    case msg of
        Msg1 ->
            {-selection-}( { model | feed = feed }, Cmd.none ){-selection--}
""", """
type Msg = Msg1
example model msg feed =
    case msg of
        Msg1 ->
            test feed model


test feed model =
    ( { model | feed = feed }, Cmd.none )
""", "test"
    )

    fun `test values with multiple references`() = doTest(
        """
example param =
    {-selection-}param ++ param{-selection--}
""", """
example param =
    test param


test param =
    param ++ param
""", "test"
    )

    fun `test record destructure pattern`() = doTest(
        """
type alias Model = { session : () }

subscriptions : Model -> Sub Msg
subscriptions { session } =
    Session.changes GotSession ({-selection-}Session.navKey session{-selection--})
""", """
type alias Model = { session : () }

subscriptions : Model -> Sub Msg
subscriptions { session } =
    Session.changes GotSession (test session)


test session =
    Session.navKey session
""", "test"
    )

    fun `test rename parameter`() = doTest(
        """
example param =
    {-selection-}param ++ param{-selection--}
""", """
example param =
    test param


test renamedParam =
    renamedParam ++ renamedParam
""", "test",
        renames = mapOf("param" to "renamedParam")
    )


    fun `test turns lambda argument into parameter in extracted function`() = doTest(
        """
example =
    [1, 2, 3]
        |> List.map {-selection-}(\item -> item + 1){-selection--}
""", """
example =
    [1, 2, 3]
        |> List.map test


test item =
    item + 1
""", "test"
    )


    private fun doTest(
        @Language("Elm") code: String,
        @Language("Elm") excepted: String,
        name: String,
        pub: Boolean = false,
        renames: Map<String, String> = emptyMap(),
        noSelected: List<String> = emptyList(),
        mutabilityOverride: Map<String, Boolean> = emptyMap()
    ) {
        withMockExtractFunctionUi(object : ExtractFunctionUi {
            override fun extract(config: ElmExtractFunctionConfig, callback: () -> Unit) {
                config.name = name
                config.visibilityLevelPublic = pub
                renames.map { (from, to) ->
                    config.parameters.find { it.name == from }?.let { param ->
                        param.name = to
                    }
                }
//                config.parameters = renames
//                noSelected.forEach { n -> config.parameters.filter { n == it.name }[0].isSelected = false }
//                mutabilityOverride.forEach { (key, mutable) ->
//                    config.parameters.filter { key == it.name }[0].isMutable = mutable
//                }
                callback()
            }
        }) {
            checkEditorAction(replaceSelectionMarker(code), excepted, "ExtractMethod", trimIndent = false)
        }
    }
}
private fun replaceSelectionMarker(text: String): String = text
    .replace("{-selection-}", "<selection>")
    .replace("{-selection--}", "</selection>")
