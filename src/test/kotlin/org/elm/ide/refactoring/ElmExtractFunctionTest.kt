package org.elm.ide.refactoring

import org.elm.ide.refactoring.extractFunction.ElmExtractFunctionConfig
import org.elm.ide.refactoring.extractFunction.ExtractFunctionUi
import org.elm.ide.refactoring.extractFunction.withMockExtractFunctionUi
import org.elm.lang.ElmTestBase
import org.intellij.lang.annotations.Language
import org.junit.Test

//import org.rust.*
//import org.rust.ide.refactoring.extractFunction.ExtractFunctionUi
//import org.rust.ide.refactoring.extractFunction.RsExtractFunctionConfig
//import org.rust.ide.refactoring.extractFunction.withMockExtractFunctionUi

class ElmExtractFunctionTest : ElmTestBase() {

    @Test
    fun `test simple`() = doTest(
        """
hello =
    {-selection-}"Hello, World!"{-selection--}
""", """
hello =
    test

test : String
test =
    "Hello, World!"""", "test"
    )

    private fun doTest(
        @Language("Elm") code: String,
        @Language("Elm") excepted: String,
        name: String,
        pub: Boolean = false,
        noSelected: List<String> = emptyList(),
        mutabilityOverride: Map<String, Boolean> = emptyMap()
    ) {
        withMockExtractFunctionUi(object : ExtractFunctionUi {
            override fun extract(config: ElmExtractFunctionConfig, callback: () -> Unit) {
                config.name = name
                config.visibilityLevelPublic = pub
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
