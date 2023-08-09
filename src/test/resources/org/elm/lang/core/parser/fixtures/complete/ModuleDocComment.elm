module Article.Slug exposing
    ( decoder, urlParser
    , toString
    , Slug
    )

{-|

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
