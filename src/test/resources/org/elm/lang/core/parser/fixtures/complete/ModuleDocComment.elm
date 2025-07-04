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

- [Module](Article.Tag)
- [Qualified Type](Article.Tag#Tag).
- [Qualified Type `Char`](Char#Char)
- [Qualified Operator `(++)`](Basics#++)
- [No hash character is not a reference](notAnElmReference).


- [Markdown Heading](#markdown-heading)

## Markdown Heading

This is a vanilla URL [GitHub](https://github.com/dillonkearns).

[the URI spec](https://tools.ietf.org/html/rfc3986)

Turn JSON values into Elm values. Definitely check out this [intro to
JSON decoders][guide] to get a feel for how this library works!

Parse a segment of the path if it matches a given string. It is almost
always used with [`</>`](#</>) or [`oneOf`](#oneOf). For example:


    example : Int
    example =
        1 + 1

["cat","dog","cow"]

Code comments shouldn't be parsed as link references.

    split "," "cat,dog,cow"        == ["cat","dog","cow"]


Doc comments can continue onto the next line

@docs onClick, onDoubleClick,
      onMouseDown, onMouseUp,
      onMouseEnter, onMouseLeave,
      onMouseOver, onMouseOut

But they end after an empty line.

@docs targetValue, targetChecked, keyCode
-}

import Json.Decode as Decode exposing (Decoder)
import Url.Parser exposing (Parser)

{-| Hello!

-}
toString : Slug -> String
toString (Slug str) =
    str
