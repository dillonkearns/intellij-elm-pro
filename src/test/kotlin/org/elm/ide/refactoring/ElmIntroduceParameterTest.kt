/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.elm.ide.refactoring

import org.intellij.lang.annotations.Language
//import org.elm.MockAdditionalCfgOptions
import org.elm.lang.ElmTestBase
import org.elm.lang.core.psi.ElmExpressionTag

//import org.elm.lang.core.psi.ElmExpr
//import org.elm.lang.core.psi.ElmFunction

class ElmIntroduceParameterTest : ElmTestBase() {
    fun `test method no params`() = doTest("""
message =
    hello

hello =
    "Hello, " ++ {-selection-}"World"{-selection--} ++ "!"
    """, listOf("\"World\""), 0, 0, """
message =
    hello "World"

hello x =
    "Hello, " ++ x ++ "!"
    """)


    private fun doTest(
        @Language("Elm") before: String,
        expressions: List<String>,
        exprTarget: Int,
        methodTarget: Int,
        @Language("Elm") after: String,
        replaceAll: Boolean = false
    ) {
        var shownTargetChooser = false
        withMockTargetExpressionChooser(object : ExtractExpressionUi {
            override fun chooseTarget(exprs: List<ElmExpressionTag>): ElmExpressionTag {
                shownTargetChooser = true
                assertEquals(expressions, exprs.map { it.text })
                return exprs[exprTarget]
            }

            override fun chooseOccurrences(
                expr: ElmExpressionTag,
                occurrences: List<ElmExpressionTag>
            ): List<ElmExpressionTag> {
                return occurrences
            }
//
//            override fun chooseOccurrences(expr: ElmExpr, occurrences: List<ElmExpr>): List<ElmExpr> =
//                if (replaceAll) occurrences else listOf(expr)
//
//            override fun chooseMethod(methods: List<ElmFunction>): ElmFunction {
//                return methods[methodTarget]
//            }
        }) {
            checkEditorAction(replaceSelectionMarker(before), after, "IntroduceParameter")
            check(expressions.isEmpty() || shownTargetChooser) {
                "Chooser isn't shown"
            }
        }
    }
}

private fun replaceSelectionMarker(text: String): String = text
    .replace("{-selection-}", "<selection>")
    .replace("{-selection--}", "</selection>")
