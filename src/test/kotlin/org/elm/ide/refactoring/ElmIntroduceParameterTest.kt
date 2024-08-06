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
    fun `test no params without annotation`() = doTest("""
message =
    hello

hello =
    "Hello, " ++ {-selection-}"World"{-selection--} ++ "!"
    """, listOf("\"World\""), 0, 0, """
message =
    hello ("World")

hello x =
    "Hello, " ++ x ++ "!"
    """)

    fun `test no params with annotation`() = doTest("""
message : String
message =
    hello

hello : String
hello =
    "Hello, " ++ {-selection-}"World"{-selection--} ++ "!"
    """, listOf("\"World\""), 0, 0, """
message : String
message =
    hello ("World")

hello : unknown -> String
hello x =
    "Hello, " ++ x ++ "!"
    """)

    fun `test adds to front with existing param and annotation`() = doTest("""
message : String
message =
    hello

hello : String -> String
hello greeting =
    greeting ++ ", " ++ {-selection-}"World"{-selection--} ++ "!"
    """, listOf("\"World\""), 0, 0, """
message : String
message =
    hello ("World")

hello : unknown -> String -> String
hello x greeting =
    greeting ++ ", " ++ x ++ "!"
    """)

    fun `test example 2`() = doTest("""
parser : Bool -> Parser ( UnorderedListMarker, Int, ListItem )
parser previousWasBody =
    succeed getIntendedCodeItem
        |= getCol
        |= backtrackable unorderedListMarkerParser
        |= getCol
        |= (if previousWasBody then
                unorderedListItemBodyParser

            else
                oneOf
                    [ unorderedListEmptyItemParser
                    , unorderedListItemBodyParser
                    ]
           )

getIntendedCodeItem : Int -> b -> Int -> ( Int, ListItem ) -> ( b, Int, ListItem )
getIntendedCodeItem markerStartPos listMarker markerEndPos ( bodyStartPos, item ) =
    let
        spaceNum : Int
        spaceNum =
            bodyStartPos - markerEndPos
    in
    if spaceNum <= {-selection-}4{-selection--} then
        ( listMarker, bodyStartPos - markerStartPos, item )

    else
        let
            intendedCodeItem : ListItem
            intendedCodeItem =
                case item of
                    TaskItem completion string ->
                        TaskItem completion (String.repeat (spaceNum - 1) " " ++ string)

                    PlainItem string ->
                        PlainItem (String.repeat (spaceNum - 1) " " ++ string)

                    EmptyItem ->
                        EmptyItem
        in
        ( listMarker, markerEndPos - markerStartPos + 1, intendedCodeItem )
""", listOf("4"), 0, 0,
        """
parser : Bool -> Parser ( UnorderedListMarker, Int, ListItem )
parser previousWasBody =
    succeed (getIntendedCodeItem 4)
        |= getCol
        |= backtrackable unorderedListMarkerParser
        |= getCol
        |= (if previousWasBody then
                unorderedListItemBodyParser

            else
                oneOf
                    [ unorderedListEmptyItemParser
                    , unorderedListItemBodyParser
                    ]
           )

getIntendedCodeItem : unknown -> Int -> b -> Int -> ( Int, ListItem ) -> ( b, Int, ListItem )
getIntendedCodeItem x markerStartPos listMarker markerEndPos ( bodyStartPos, item ) =
    let
        spaceNum : Int
        spaceNum =
            bodyStartPos - markerEndPos
    in
    if spaceNum <= x then
        ( listMarker, bodyStartPos - markerStartPos, item )

    else
        let
            intendedCodeItem : ListItem
            intendedCodeItem =
                case item of
                    TaskItem completion string ->
                        TaskItem completion (String.repeat (spaceNum - 1) " " ++ string)

                    PlainItem string ->
                        PlainItem (String.repeat (spaceNum - 1) " " ++ string)

                    EmptyItem ->
                        EmptyItem
        in
        ( listMarker, markerEndPos - markerStartPos + 1, intendedCodeItem )
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
//            check(expressions.isEmpty() || shownTargetChooser) {
//                "Chooser isn't shown"
//            }
        }
    }
}

private fun replaceSelectionMarker(text: String): String = text
    .replace("{-selection-}", "<selection>")
    .replace("{-selection--}", "</selection>")
