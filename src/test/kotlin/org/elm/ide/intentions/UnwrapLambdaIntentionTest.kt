package org.elm.ide.intentions

class UnwrapLambdaIntentionTest : ElmIntentionTestBase(UnwrapLambdaIntention()) {


    fun `test top-level`() = doAvailableTest(
            """
module Foo exposing (example)

myFunction name =
    "Hello, " ++ name ++ "!"

example =
    ({-caret-}\a -> myFunction a)
""", """
module Foo exposing (example)

myFunction name =
    "Hello, " ++ name ++ "!"

example =
    (myFunction)
""")

}
