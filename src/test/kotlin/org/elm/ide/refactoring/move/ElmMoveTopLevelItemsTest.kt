/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.refactoring.move

//@WithEnabledInspections(RsUnusedImportInspection::class)
class ElmMoveTopLevelItemsTest : ElmMoveTopLevelItemsTestBase() {
//
    fun `test simple`() = doTest(
        """
--@ A.elm

module A exposing (value, value2)

value = {-caret-}42

value2 = 123

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ A.elm

module A exposing (value2)


value2 =
    123

--@ B.elm

module B exposing (existing, value)


existing =
    "Existing"


value =
    42"""
)

    fun `test moving last exposed item adds a stub exposed value`() = doTest(
        """
--@ A.elm

module A exposing (value)

value = {-caret-}42

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ A.elm

module A exposing (stub)


stub =
    ()

--@ B.elm

module B exposing (existing, value)


existing =
    "Existing"


value =
    42"""
    )

    fun `test uses unqualified values to reference definitions in target module`() = doTest(
        """
--@ A.elm

module A exposing (value, value2)

value = {-caret-}42

value2 = 123

--@ B.elm
module B exposing (existing)

import A

existing = A.value + 1
{-target-}
"""
        , """
--@ A.elm

module A exposing (value2)


value2 =
    123

--@ B.elm

module B exposing (existing, value)

import A


existing =
    value + 1


value =
    42"""
    )

    fun `test qualified module name`() = doTest(
        """
--@ Nested/A.elm

module Nested.A exposing (value, value2)

value = {-caret-}42

value2 = 123

--@ Nested/B.elm
module Nested.B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ Nested/A.elm

module Nested.A exposing (value2)


value2 =
    123

--@ Nested/B.elm

module Nested.B exposing (existing, value)


existing =
    "Existing"


value =
    42"""
    )


    fun `test multiple`() = doTest(
        """
--@ A.elm

module A exposing (value, value2, example)

<selection>value = 42

value2 = 123
</selection>

example = value + value2

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ A.elm

module A exposing (example)

import B


example =
    B.value + B.value2

--@ B.elm

module B exposing (existing, value, value2)


existing =
    "Existing"


value =
    42


value2 =
    123"""
    )

    fun `test target module exposing all`() = doTest(
        """
--@ A.elm

module A exposing (value, value2)

value = {-caret-}42

value2 = 123

--@ B.elm
module B exposing (..)

existing = "Existing"
{-target-}
"""
        , """
--@ A.elm

module A exposing (value2)


value2 =
    123

--@ B.elm

module B exposing (..)


existing =
    "Existing"


value =
    42"""
    )


    fun `test move create file 1`() = doTestCreateFile("B.elm", """
--@ A.elm

module A exposing (value, value2)

value = {-caret-}42

value2 = 123
""", """
--@ A.elm

module A exposing (value2)


value2 =
    123

--@ B.elm

module B exposing (value)


value =
    42"""
    )

    fun `test update reference with unqualified import`() = doTest(
        """
--@ A.elm

module A exposing (value, value2)

{-| -}

{-| This is a doc comment.
 
It can be on multiple lines. -}
value : Int
value = {-caret-}42

value2 = value + 123

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ A.elm

module A exposing (value2)

{-| -}

import B


value2 =
    B.value + 123

--@ B.elm

module B exposing (existing, value)


existing =
    "Existing"


{-| This is a doc comment.

It can be on multiple lines.

-}
value : Int
value =
    42"""
    )

    fun `test remove qualified reference after moving to referenced module`() = doTest(
        """
--@ Example.elm

module Example exposing (example, toRoman)

example = toRoman -1

toRoman : Int -> String
toRoman n =
    {-caret-}if n > 0 then
        Roman.toRomanHelp
    else
        ""
    

--@ Roman.elm
module Roman exposing (toRomanHelp)

toRomanHelp n = "I"
{-target-}
"""
        , """
--@ Example.elm

module Example exposing (example)

import Roman


example =
    Roman.toRoman -1

--@ Roman.elm
module Roman exposing (toRoman, toRomanHelp)


toRomanHelp n =
    "I"


toRoman : Int -> String
toRoman n =
    if n > 0 then
        toRomanHelp

    else
        ""
"""
    )


    fun `test add import when needed`() = doTest(
        """
--@ A.elm

module A exposing (value, value2)

import Favorite

value = {-caret-}Favorite.number * 10

value2 = 123

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ A.elm

module A exposing (value2)

import Favorite


value2 =
    123

--@ B.elm

module B exposing (existing, value)

import Favorite


existing =
    "Existing"


value =
    Favorite.number * 10"""
    )

    fun `test merge import from target`() = doTest(
        """
--@ Favorite.elm
module Favorite exposing (Favorite)

type alias Favorite = Int

--@ A.elm

module A exposing (value, value2)

import Favorite exposing (Favorite)

value : Favorite
value = {-caret-}Favorite.number * 10

value2 = 123

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ Favorite.elm
module Favorite exposing (Favorite)

type alias Favorite = Int
--@ A.elm

module A exposing (value2)

import Favorite exposing (Favorite)


value2 =
    123

--@ B.elm

module B exposing (existing, value)

import Favorite


existing =
    "Existing"


value : Favorite.Favorite
value =
    Favorite.number * 10"""
    )

    fun `test qualify unqualified values`() = doTest(
        """
--@ Favorite.elm
module Favorite exposing (myFavoriteNumber)

myFavoriteNumber = 42

--@ A.elm

module A exposing (value, value2)

import Favorite exposing (myFavoriteNumber)

value = {-caret-}myFavoriteNumber * 10

value2 = 123

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ Favorite.elm
module Favorite exposing (myFavoriteNumber)

myFavoriteNumber = 42

--@ A.elm

module A exposing (value2)

import Favorite exposing (myFavoriteNumber)


value2 =
    123

--@ B.elm

module B exposing (existing, value)

import Favorite


existing =
    "Existing"


value =
    Favorite.myFavoriteNumber * 10"""
    )

    fun `test qualify unqualified types`() = doTest(
        """
--@ Favorite.elm
module Favorite exposing (Favorite)

type alias Favorite = Int

--@ A.elm

module A exposing (value, value2)

import Favorite exposing (Favorite)

value : Favorite
value = {-caret-}10

value2 = 123

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ Favorite.elm
module Favorite exposing (Favorite)

type alias Favorite = Int

--@ A.elm

module A exposing (value2)

import Favorite exposing (Favorite)


value2 =
    123

--@ B.elm

module B exposing (existing, value)

import Favorite


existing =
    "Existing"


value : Favorite.Favorite
value =
    10"""
    )



    fun `test add import with conflicting import alias`() = doTest(
        """
--@ A.elm

module A exposing (value, value2)

import Favorite as F

value = {-caret-}F.number * 10

value2 = 123

--@ B.elm
module B exposing (existing)

import Favorite

existing = Favorite.number + 1
{-target-}
"""
        , """
--@ A.elm

module A exposing (value2)

import Favorite as F


value2 =
    123

--@ B.elm

module B exposing (existing, value)

import Favorite


existing =
    Favorite.number + 1


value =
    Favorite.number * 10"""
    )

    fun `test simple type`() = doTest(
        """
--@ A.elm

module A exposing (value, MyType)

type MyType = {-caret-}MyType

value = 42

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ A.elm

module A exposing (value)


value =
    42

--@ B.elm

module B exposing (MyType, existing)


existing =
    "Existing"


type MyType
    = MyType"""
    )

    fun `test conflicting name`() = doTestConflictsError(
        """
--@ A.elm

module A exposing (value, existing)

existing = {-caret-}123

value = 42

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
    )

    fun `test move all mutual references`() = doTest(
        """
--@ A.elm

module A exposing (dependent1, dependent2, value)

<selection>
dependent1 = 123

dependent2 = dependent1 + 456

</selection>

value = "value"

--@ B.elm
module B exposing (existing)

existing = "Existing"
{-target-}
"""
        , """
--@ A.elm

module A exposing (value)

import B


value =
    "value"

--@ B.elm

module B exposing (dependent1, dependent2, existing)


existing =
    "Existing"


dependent1 =
    123


dependent2 =
    dependent1 + 456"""
    )

    fun `test unqualified type constructor references`() = doTest(
        """
--@ Example.elm

module Example exposing (example)

import Markdown.Block as Block exposing (Inline(..))


<selection>
resolveLinkInInline : Inline -> Result String Inline
resolveLinkInInline inline =
    case inline of
        Link destination title inlines ->
            destination
                |> lookupLink
                |> Result.map (\resolvedLink -> Link resolvedLink title inlines)

        _ ->
            Ok inline


lookupLink : String -> Result String String
lookupLink key =
    case key of
        "elm-lang" ->
            Ok "https://elm-lang.org"

        _ ->
            Err <| "Couldn't find key " ++ key

</selection>

example = resolveLinkInInline

--@ Markdown/Block.elm
module Markdown.Block exposing (Block(..), HeadingLevel(..), Html(..), Inline(..))

{-| An Inline block. Note that `HtmlInline`s can contain Blocks, not just nested `Inline`s.
-}
type Inline
    = Other
    | Link String (Maybe String) (List Inline)
--@ Helpers.elm
module Helpers exposing (existing)

existing = "Existing"
{-target-}

--@ Example.elm

module Example exposing (example)

import Markdown.Block as Block exposing (Inline(..))


<selection>
resolveLinkInInline : Inline -> Result String Inline
resolveLinkInInline inline =
    case inline of
        Link destination title inlines ->
            destination
                |> lookupLink
                |> Result.map (\resolvedLink -> Link resolvedLink title inlines)

        _ ->
            Ok inline


lookupLink : String -> Result String String
lookupLink key =
    case key of
        "elm-lang" ->
            Ok "https://elm-lang.org"

        _ ->
            Err <| "Couldn't find key " ++ key

</selection>

example = resolveLinkInInline

--@ Markdown/Block.elm
module Markdown.Block exposing (Block(..), HeadingLevel(..), Html(..), Inline(..))

{-| An Inline block. Note that `HtmlInline`s can contain Blocks, not just nested `Inline`s.
-}
type Inline
    = Other
    | Link String (Maybe String) (List Inline)
--@ Helpers.elm
module Helpers exposing (existing)

existing = "Existing"

{-target-}
"""
        , """
--@ Example.elm

module Example exposing (example)

import Helpers
import Markdown.Block as Block exposing (Inline(..))


example =
    Helpers.resolveLinkInInline

--@ Markdown/Block.elm
module Markdown.Block exposing (Block(..), HeadingLevel(..), Html(..), Inline(..))

{-| An Inline block. Note that `HtmlInline`s can contain Blocks, not just nested `Inline`s.
-}
type Inline
    = Other
    | Link String (Maybe String) (List Inline)
--@ Helpers.elm
module Helpers exposing (existing, lookupLink, resolveLinkInInline)

import Markdown.Block


existing =
    "Existing"


resolveLinkInInline : Markdown.Block.Inline -> Result String Markdown.Block.Inline
resolveLinkInInline inline =
    case inline of
        Markdown.Block.Link destination title inlines ->
            destination
                |> lookupLink
                |> Result.map (\resolvedLink -> Markdown.Block.Link resolvedLink title inlines)

        _ ->
            Ok inline


lookupLink : String -> Result String String
lookupLink key =
    case key of
        "elm-lang" ->
            Ok "https://elm-lang.org"

        _ ->
            Err <| "Couldn't find key " ++ key
"""
    )


}
