package org.elm.ide.intentions

class WrapWithLambdaIntentionTest : ElmIntentionTestBase(WrapWithLambdaIntention()) {


    fun `test top-level`() = doAvailableTest(
            """
module Foo exposing (example)

myFunction name =
    "Hello, " ++ name ++ "!"

example =
    myFuncti{-caret-}on
""", """
module Foo exposing (example)

myFunction name =
    "Hello, " ++ name ++ "!"

example =
    (\a -> myFunction a)
""")

}
