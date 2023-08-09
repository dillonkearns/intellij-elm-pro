module Article.Slug exposing
    ( decoder, urlParser
    , toString
    , Slug
    )

{-| This is a doc comment.

This is NOT an annotation @docs because it is not at the start of the line.

@docs (</>), map, oneOf, top, custom
@docs decoder, urlParser
@docs toString

-}

import Json.Decode as Decode exposing (Decoder)
import Url.Parser exposing (Parser)

{-| Hello!

-}
toString : Slug -> String
toString (Slug str) =
    str
