package org.elm.lang.core.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import static org.elm.lang.core.psi.ElmTypes.*;

%%

%{
  public _ElmLexer() {
    this(null);
  }
%}

%class _ElmLexer
%public
%implements FlexLexer
%unicode
%function advance
%type IElementType

%{
    private int commentLevel = 0;
    private int charLength = 0;

    private void startComment() {
        commentLevel = 1;
        yybegin(COMMENT);
    }
    private void startDocComment() {
        commentLevel = 1;
        yybegin(IN_DOC_COMMENT);
    }
%}

%xstate COMMENT GLSL_CODE STRING RAW_STRING CHAR TYPE_PENDING DOCS_LINE IN_DOC_COMMENT IN_MARKDOWN_DESTINATION IN_MARKDOWN_DESTINATION_ELM_REF

Newline = (\n|\r|\r\n)
Space = " "
WhiteSpace = {Space}+
Tab = \t
LineComment = ("--")[^\r\n]*
IdentifierChar = [[:letter:][:digit:]_]
HexChar = [[:digit:]A-Fa-f]
LowerCaseIdentifier = [:lowercase:]{IdentifierChar}*
UpperCaseIdentifier = [:uppercase:]{IdentifierChar}*
NumberLiteral = ("-")?[:digit:]+(\.[:digit:]+)?(e"-"?[:digit:]+)?
HexLiteral = 0x{HexChar}+
Operator = ("!"|"$"|"^"|"|"|"*"|"/"|"?"|"+"|"~"|"."|-|=|@|#|%|&|<|>|:|€|¥|¢|£|¤)+
OperatorNoHash = ("!"|"$"|"^"|"|"|"*"|"/"|"?"|"+"|"~"|"."|-|=|@|%|&|<|>|:|€|¥|¢|£|¤)+

ValidEscapeSequence = \\(u\{{HexChar}{4,6}\}|[nrt\"'\\])
InvalidEscapeSequence = \\(u\{[^}]*\}|[^nrt\"'\\])
ThreeQuotes = \"\"\"

Protocol = [a-zA-Z]+ ":"

%%

<RAW_STRING> {
    {ThreeQuotes}\"* {
        int length = yytext().length();
        if (length <= 3) { // closing """
            yybegin(YYINITIAL);
            return CLOSE_QUOTE;
        } else { // some quotes at the end of a string, e.g. """ "foo""""
            yypushback(3); // return the closing quotes (""") to the stream
            return REGULAR_STRING_PART;
        }
    }
    [^\\\"]+    { return REGULAR_STRING_PART; }
    \"\"?       { return REGULAR_STRING_PART; }
}

<STRING> {
    [^\\\"\n]+  { return REGULAR_STRING_PART; }
    \"          { yybegin(YYINITIAL); return CLOSE_QUOTE; }
}

<CHAR> {
    "'"         { yybegin(YYINITIAL); charLength = 0; return CLOSE_CHAR; }
    [^\\\n']    {
          if (charLength++ == 0) return REGULAR_STRING_PART;
          // Rather than returing a bad character, push the text back onto the stack so that other rules can
          // parse normally.
          yypushback(1); yybegin(YYINITIAL); charLength = 0;
      }
}

<STRING, RAW_STRING, CHAR> {
    {ValidEscapeSequence}   { return STRING_ESCAPE; }
    {InvalidEscapeSequence} { return INVALID_STRING_ESCAPE; }
}

<STRING, CHAR> {
    \n          { yybegin(YYINITIAL); return TokenType.BAD_CHARACTER; }
}

<COMMENT> {
    "{-" {
        commentLevel++;
    }
    "-}" {
            if (--commentLevel == 0) {
                yybegin(YYINITIAL);
                return BLOCK_COMMENT;
            }
        }
    <<EOF>> { commentLevel = 0; yybegin(YYINITIAL); return BLOCK_COMMENT; }

    [^] { }
}

<IN_DOC_COMMENT> {
    {WhiteSpace} { return TokenType.WHITE_SPACE; }
    {Newline} { return NEWLINE; }
    ^ "@docs" {
        yybegin(DOCS_LINE);
        return DOCS_ANNOTATION;
    }
    "[" [^\]\n]* "]" {
          yybegin(IN_MARKDOWN_DESTINATION);
          return DOC_CONTENT;
    }
    [^@{\-\[]+ {
      return DOC_CONTENT;
   }
    "{-" {
        commentLevel++;
    }
    "-}" {
        if (--commentLevel == 0) {
            yybegin(YYINITIAL);
            return END_DOC_COMMENT;
        }
    }
    [@{\-] {
      return DOC_CONTENT;
   }
    <<EOF>> { commentLevel = 0; yybegin(YYINITIAL); return DOC_CONTENT; }

    [^] { }
}

<IN_MARKDOWN_DESTINATION_ELM_REF> {
    "#" { return HASH; }
    {LowerCaseIdentifier}  { return LOWER_CASE_IDENTIFIER; }
    {UpperCaseIdentifier}  { return UPPER_CASE_IDENTIFIER; }
    "."                    { return DOT; }
    "-"                    { return DOT; }
    {WhiteSpace} { return TokenType.WHITE_SPACE; }
    {Newline} { return NEWLINE; }
    ")" {
          yybegin(IN_DOC_COMMENT);
          return RIGHT_PARENTHESIS;
    }
    {OperatorNoHash}                  { return OPERATOR_IDENTIFIER; }
    {Newline} {
          return NEWLINE;
    }
    [^] {
          yybegin(IN_DOC_COMMENT);
          return DOC_CONTENT;
    }
}

<IN_MARKDOWN_DESTINATION> {
    "(" {WhiteSpace}* ")" {
          yybegin(IN_DOC_COMMENT);
    }
    "(" [^)\n]* "\n" {
        yybegin(IN_DOC_COMMENT);
      }
    "(" {Protocol} [^)]* ")" {
          yybegin(IN_DOC_COMMENT);
          return DOC_CONTENT;
    }
    "(/" [^)\n]* ")" {
          yybegin(IN_DOC_COMMENT);
          return DOC_CONTENT;
    }
    // ignore links to operators
    "(#<" [^)]* ")" {
          yybegin(IN_DOC_COMMENT);
          return DOC_CONTENT;
    }
    // module names can be referenced with no `#`, but lower case identifiers must start with a `#`
    "(" [:lowercase:] [^#)\n]* ")" {
          yybegin(IN_DOC_COMMENT);
          return DOC_CONTENT;
    }
    "(" {
          yybegin(IN_MARKDOWN_DESTINATION_ELM_REF);
          return LEFT_PARENTHESIS;
    }
    {Newline} {
        yybegin(IN_DOC_COMMENT);
        return NEWLINE;
    }
    . {
        yypushback(1);
        yybegin(IN_DOC_COMMENT);
    }
}
<DOCS_LINE> {
    "-}" {
        if (--commentLevel == 0) {
            yybegin(YYINITIAL);
            return END_DOC_COMMENT;
        }
    }
    "("                         { return LEFT_PARENTHESIS; }
    ")"                         { return RIGHT_PARENTHESIS; }
    {Operator}                  { return OPERATOR_IDENTIFIER; }
    {LowerCaseIdentifier}       { return LOWER_CASE_IDENTIFIER; }
    {UpperCaseIdentifier}       { return UPPER_CASE_IDENTIFIER; }
    ","                         { return COMMA; }
     // Two newlines in a row ends the docs line
    {Newline} "@docs" {
          yypushback(5);
          yybegin(IN_DOC_COMMENT);
          return NEWLINE;
      }
    {Newline} {WhiteSpace}* {Newline} {
        yybegin(IN_DOC_COMMENT);
        { return NEWLINE; }
    }
    // A single newline on its own can continue the docs line
    {Newline} { return TokenType.WHITE_SPACE; }
    {WhiteSpace}                { return TokenType.WHITE_SPACE; }
    . {
        yypushback(1);
        yybegin(IN_DOC_COMMENT);
    }
}

<GLSL_CODE> {
    "|]" {
        yybegin(YYINITIAL);
        return END_GLSL_CODE;
    }
    [|]+ {
        return GLSL_CODE_CONTENT;
    }
    [^|]+ {
        return GLSL_CODE_CONTENT;
    }
}

<TYPE_PENDING> {
    {WhiteSpace} { return TokenType.WHITE_SPACE; }
    "alias" {
          yybegin(YYINITIAL);
          return ALIAS;
    }
    [^] {
          yypushback(1);
          yybegin(YYINITIAL);
    }
}

<YYINITIAL> {
    "module"                    { return MODULE; }
    "where"                     { return WHERE; }
    "import"                    { return IMPORT; }
    "as"                        { return AS; }
    "exposing"                  { return EXPOSING; }
    "if"                        { return IF; }
    "then"                      { return THEN; }
    "else"                      { return ELSE; }
    "case"                      { return CASE; }
    "of"                        { return OF; }
    "let"                       { return LET; }
    "in"                        { return IN; }
    "type"                      { yybegin(TYPE_PENDING); return TYPE; }
    "port"                      { return PORT; }
    "infix"                     { return INFIX; }
    "("                         { return LEFT_PARENTHESIS; }
    ")"                         { return RIGHT_PARENTHESIS; }
    "["                         { return LEFT_SQUARE_BRACKET; }
    "]"                         { return RIGHT_SQUARE_BRACKET; }
    "{"                         { return LEFT_BRACE; }
    "}"                         { return RIGHT_BRACE; }
    ".."                        { return DOUBLE_DOT; }
    ","                         { return COMMA; }
    "="                         { return EQ; }
    "->"                        { return ARROW; }
    ":"                         { return COLON; }
    "|"                         { return PIPE; }
    "\\"                        { return BACKSLASH; }
    "_"                         { return UNDERSCORE; }
    "."                         { return DOT; }
    "[glsl|" {
            yybegin(GLSL_CODE);
            return START_GLSL_CODE;
        }
    "{-|" {
        startDocComment();
        return START_DOC_COMMENT;
    }
    "{-" {
        startComment();
    }

    \"                          { yybegin(STRING); return OPEN_QUOTE; }
    "'"                         { yybegin(CHAR); charLength = 0; return OPEN_CHAR; }
    {ThreeQuotes}               { yybegin(RAW_STRING); return OPEN_QUOTE; }
    {LineComment}               { return LINE_COMMENT; }
    {LowerCaseIdentifier}       { return LOWER_CASE_IDENTIFIER; }
    {UpperCaseIdentifier}       { return UPPER_CASE_IDENTIFIER; }
    {NumberLiteral}             { return NUMBER_LITERAL; }
    {HexLiteral}                { return NUMBER_LITERAL; }
    {Operator}                  { return OPERATOR_IDENTIFIER; }
    {WhiteSpace}                { return TokenType.WHITE_SPACE; }
    {Newline}                   { return NEWLINE; }
    {Tab}                       { return TAB; }
}

. {
    return TokenType.BAD_CHARACTER;
}
