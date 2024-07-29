package org.elm.ide.intentions

class LiftLetIntentionTest : ElmIntentionTestBase(LiftLetIntention()) {


    fun `test independent let with sibling bindings`() = doAvailableTest(
            """
module Foo exposing (example)

example =
    let
        {-caret-}a = 1
        b = 2
    in
        a + b
""", """
module Foo exposing (example)

example =
    let
        
        b = 2
    in
        a + b
a = 1""")

    fun `test independent let function with sibling bindings`() = doAvailableTest(
        """
module Foo exposing (example)

example =
    let
        {-caret-}myFn a = a + 1
        b = 2
    in
        myFn 123 + b
""", """
module Foo exposing (example)

example =
    let
        
        b = 2
    in
        myFn 123 + b
myFn a = a + 1""")


    fun `test independent let with no sibling bindings`() = doAvailableTest(
        """
module Foo exposing (example)

b = 2

example =
    let
        {-caret-}a = 1
    in
        a + b
""", """
module Foo exposing (example)

b = 2

example =

        a + b
a = 1""")

}
