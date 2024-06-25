package org.elm.ide.inspections.inference

import org.elm.ide.inspections.ElmInspectionsTestBase
import org.elm.ide.inspections.ElmTypeInferenceInspection

// The type inference tests are broken up arbitrarily into several files, since IntelliJ gets bogged
// down when they're all combined.
class TypeInferenceInspectionTestFixes : ElmInspectionsTestBase(ElmTypeInferenceInspection()) {
    override fun getProjectDescriptor() = ElmWithStdlibDescriptor

    fun `test too many arguments to value`() = checkFixByFileTree("Add Parameter",
        """
--@ main.elm
module Main exposing (main)

foo = ()

main = <error descr="This value is not a function, but it was given 1 argument.">{-caret-}foo 1</error>
""", """
module Main exposing (main)

foo x = ()

main = foo 1
"""
    )

    fun `test add second parameter`() = checkFixByFileTree("Add Parameter",
        """
--@ main.elm
module Main exposing (main)

foo x = ()

main = <error descr="The function expects 1 argument, but it got 2 instead.">{-caret-}foo 1 2</error>
""", """
module Main exposing (main)

foo x x2 = ()

main = foo 1 2
"""
    )

}
